"""Validate component knowledge inventory before MCP forwards a draft."""
import hashlib
import json
import os
from pathlib import Path

PAGE_KEYS = ("systemCode", "projectCode", "pagePath")
DEFAULT_BINDINGS = Path.home() / ".codex" / "component-knowledge-pages.json"
TYPES = ("COMPONENT", "PAGE", "FLOW", "KNOWLEDGE", "INTERFACE", "APPLICABILITY",
         "PAGE_FLOW", "INTERACTION", "COMPONENT_RELATION")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def page_binding(identity):
    path = Path(os.environ.get("KNOWLEDGE_PAGE_BINDINGS", str(DEFAULT_BINDINGS))).expanduser()
    try:
        entries = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise ValueError("无法读取页面注册表绑定配置") from exc
    require(isinstance(entries, list) and all(isinstance(entry, dict) for entry in entries),
            "页面注册表绑定必须是对象数组")
    matches = [entry for entry in entries
               if all(isinstance(identity.get(key), str) and identity[key]
                      and identity[key] == entry.get(key) for key in PAGE_KEYS)]
    require(len(matches) == 1, "页面注册表绑定缺失或重复")
    return path, matches[0]


def scope(identity, root, registry_snapshot=None):
    binding = {}
    if registry_snapshot is not None:
        data = registry_snapshot
        require(isinstance(data, dict) and isinstance(data.get("components"), dict),
                "会话注册表必须包含 components 对象")
        declared = data.get("pageIdentity")
        require(isinstance(declared, dict) and all(declared.get(k) == identity.get(k)
                for k in PAGE_KEYS), "会话注册表的页面身份与请求不一致")
        require(all(isinstance(item, dict) and "assemblyParents" in item
                    for item in data["components"].values()), "会话注册表缺少组件装配信息")
    else:
        config_path, binding = page_binding(identity)
        registry = binding.get("registry")
        require(isinstance(registry, str) and registry.strip(), "页面绑定缺少 registry")
        try:
            data = json.loads((config_path.parent / Path(registry).expanduser()).read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise ValueError("无法读取组件注册表") from exc
    shared_children = binding.get("sharedChildren", {})
    require(isinstance(shared_children, dict)
            and all(isinstance(values, list) and all(isinstance(v, str) for v in values)
                    for values in shared_children.values()), "sharedChildren 格式无效")
    require(isinstance(data, dict) and isinstance(data.get("components"), dict), "组件注册表格式无效")
    components = data["components"]
    require(root == "page" or root in components, "整理入口未在注册表中登记")
    if all("assemblyParents" in item for item in components.values()):
        children = {"page": []}
        for key, item in components.items():
            require(key and key != "page" and item.get("id") == key, f"组件 ID 无效或与索引不一致：{key}")
            require(all(isinstance(item.get(field), str) and item[field].strip()
                        for field in ("name", "source")), f"组件缺少名称或源码位置：{key}")
            dependencies = item.get("apiDependencies", [])
            require(isinstance(dependencies, list) and all(
                isinstance(api, dict) and api.get("role") in ("direct", "upstream")
                and isinstance(api.get("apiId"), str) and api["apiId"].strip()
                for api in dependencies), f"接口依赖无效：{key}")
            parents = item["assemblyParents"]
            require(isinstance(parents, list) and parents and all(isinstance(p, str) for p in parents)
                    and len(parents) == len(set(parents)), f"组件缺少装配方：{key}")
            for parent in parents:
                require(parent == "page" or parent in components, f"装配方未登记：{parent}")
                children.setdefault(parent, []).append(key)
        ordered = []
        visited = set()
        active = set()

        def visit_assembly(key):
            require(key not in active, f"组件装配存在循环：{key}")
            if key in visited:
                return
            active.add(key)
            for child in children.get(key, []):
                visit_assembly(child)
            active.remove(key)
            visited.add(key)
            if key != "page":
                item = components[key]
                ordered.append({
                    "componentId": key,
                    "source": item["source"],
                    "name": item["name"],
                    "requiredItems": [
                        *TYPES,
                        *(f'{api["role"]}:{api["apiId"]}' for api in item.get("apiDependencies", [])),
                    ],
                })

        visit_assembly(root)
        if root == "page":
            require(visited - {"page"} == set(components), "注册表含未从页面根节点到达的组件")
        return ordered

    identity_only = all("name" in item and "source" in item for item in components.values())
    bindings = components
    if identity_only:
        bindings_file = binding.get("componentBindings")
        bindings = {}
        if bindings_file:
            bindings = json.loads(
                (config_path.parent / Path(bindings_file).expanduser()).read_text(encoding="utf-8")
            )
            require(isinstance(bindings, dict) and set(bindings) <= set(components),
                    "组件装配绑定格式无效或包含未知组件")
    order = []
    visiting = set()
    seen = set()

    def visit(key, parent):
        require(key in components and key not in visiting, f"子组件缺失或存在循环：{key}")
        item = components[key]
        wiring = bindings.get(key, {})
        shared = key in shared_children.get(parent, [])
        require(item["id"] == key and (wiring.get("parent", "page") == parent or shared), f"父子关系冲突：{key}")
        if key in seen:
            return
        visiting.add(key)
        children = wiring.get("children", [])
        require({k for k, v in bindings.items() if v.get("parent") == key} <= set(children),
                f"子组件清单不一致：{key}")
        for child in children:
            visit(child, key)
        visiting.remove(key)
        seen.add(key)
        order.append({
            "componentId": key,
            "source": item["source"],
            "name": item.get("name") or item.get("title") or key,
            "requiredItems": [*TYPES, *(f'{a["role"]}:{a["apiId"]}' for a in wiring.get("apiDependencies", []))],
        })

    if root == "page":
        roots = ([key for key in components if bindings.get(key, {}).get("parent", "page") == "page"]
                 if identity_only else data.get("tree", {}).get("page", []))
        for key in roots:
            visit(key, "page")
        require(seen == set(components), "注册表含未从页面根节点到达的组件")
    else:
        visit(root, bindings.get(root, {}).get("parent", "page"))
    return order


def validate(identity, plan):
    require(isinstance(plan, dict) and isinstance(plan.get("root"), str), "缺少完整整理清单")
    expected = scope(identity, plan["root"], plan.get("registrySnapshot"))
    page = {key: identity[key] for key in PAGE_KEYS}
    rows = plan.get("components")
    require(isinstance(rows, dict) and set(rows) == {x["componentId"] for x in expected},
            "整理清单有遗漏或包含范围外组件")
    for component in expected:
        key = component["componentId"]
        row = rows[key]
        require(isinstance(row, dict) and isinstance(row.get("items"), dict), f"{key} 缺少盘点项")
        items = row["items"]
        require(set(component["requiredItems"]) <= set(items), f"{key} 缺少知识类型或接口盘点项")
        request = row.get("request")
        require(request is None or isinstance(request, dict), f"{key} 候选请求无效")
        changes = request.get("changes") if request is not None else []
        require(isinstance(changes, list) and all(isinstance(c, dict) for c in changes), f"{key} 候选请求无效")
        covered = []
        for name, item in items.items():
            require(isinstance(item, dict), f"{key}/{name} 缺少处置记录")
            status = item.get("status")
            require(status in ("candidate", "unchanged", "inapplicable", "blocked"), f"{key}/{name} 状态无效")
            evidence = item.get("evidence")
            require(isinstance(evidence, str) and evidence.strip(), f"{key}/{name} 缺少证据或阻塞依据")
            ids = item.get("changeIds", [])
            require(isinstance(ids, list) and all(isinstance(i, str) and i for i in ids)
                    and bool(ids) == (status == "candidate"), f"{key}/{name} 候选归属无效")
            if name in TYPES:
                require(all(any(c.get("changeId") == i and c.get("objectType") == name for c in changes)
                            for i in ids), f"{key}/{name} 候选类型不匹配")
            covered.extend(ids)
        ids = [change.get("changeId") for change in changes]
        require(all(isinstance(i, str) and i for i in ids) and len(ids) == len(set(ids))
                and set(ids) == set(covered), f"{key} 候选与盘点记录不一致")
        if request is not None:
            require(bool(changes) and request.get("componentId") == key
                    and all(request.get(k) == v for k, v in page.items())
                    and "expectedPendingDraft" in request, f"{key} 提交身份或草稿基准缺失")
    payload = json.dumps([page, expected, plan], sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode()).hexdigest()
