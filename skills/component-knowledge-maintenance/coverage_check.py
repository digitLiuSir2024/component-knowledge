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


def check_code_evidence(record, extracted, label):
    check = record.get("codeCheck")
    require(isinstance(check, dict), f"{label} 缺少源码对照")
    status = check.get("status")
    allowed = ("confirmed", "corrected", "excluded", "unresolved") if extracted else ("added", "unresolved")
    require(status in allowed, f"{label} 源码对照状态与线索来源不一致")
    for field in ("source", "finding"):
        require(isinstance(check.get(field), str) and check[field].strip(), f"{label} 源码对照缺少 {field}")
    require((status == "unresolved") == bool(record.get("gap")), f"{label} 未证实项须保留具体缺口")
    return status


def check_structured_review(component, row, clues):
    key = component["componentId"]
    review = row.get("review")
    require(isinstance(clues, dict) and isinstance(review, dict), f"{key} 缺少线索或结构化盘点")
    sections = ("metrics", "interactions", "interfaces")
    for section in sections:
        require(isinstance(clues.get(section), dict) and isinstance(review.get(section), dict),
                f"{key}/{section} 缺少清单")
        require(set(clues[section]) <= set(review[section]), f"{key}/{section} 未处置："
                + ", ".join(sorted(set(clues[section]) - set(review[section]))))
        for name, source in clues[section].items():
            require(isinstance(source, str) and source.strip(), f"{key}/{section}/{name} 缺少线索来源")
        for name, record in review[section].items():
            label = f"{key}/{section}/{name}"
            require(isinstance(record, dict), f"{label} 盘点格式无效")
            evidence = record.get("evidence")
            gap = record.get("gap")
            require(isinstance(evidence, str) and evidence.strip(), f"{label} 缺少证据")
            require(gap is None or isinstance(gap, str) and gap.strip(), f"{label} 缺口说明无效")
            item_key = record.get("item")
            require(isinstance(item_key, str) and item_key in row["items"], f"{label} 缺少覆盖项关联")
            item = row["items"][item_key]
            require(isinstance(item, dict), f"{label} 覆盖项无效")
            status = check_code_evidence(record, name in clues[section], label)
            if status == "excluded":
                require(item.get("status") == "inapplicable", f"{label} 已排除线索须关联不适用项")
            if gap is not None:
                require(item.get("status") == "blocked", f"{label} 有缺口须关联受阻项")

    metrics = {name: record for name, record in review["metrics"].items()
               if record["codeCheck"]["status"] != "excluded"}
    for name, record in metrics.items():
        label = f"{key}/指标/{name}"
        kind = record.get("kind")
        require(kind in ("derived", "base"), f"{label} 缺少指标类别")
        if kind == "derived":
            dependencies = record.get("dependencies")
            require(isinstance(dependencies, list) and all(isinstance(dep, str) for dep in dependencies),
                    f"{label} 缺少公式依赖列表")
            for dep in dependencies:
                require(dep != name and dep in metrics, f"{label} 缺少独立指标口径：{dep}")
            if record.get("gap") is None:
                require(bool(dependencies) and isinstance(record.get("formula"), str)
                        and record["formula"].strip(), f"{label} 缺少公式或其依赖")
        elif record.get("gap") is None:
            for field in ("population", "source", "filter", "window", "grouping", "deduplication", "empty"):
                require(isinstance(record.get(field), str) and record[field].strip(),
                        f"{label} 缺少基础口径字段：{field}")

    active = set()
    dependency_gaps = {}

    def visit(name):
        require(name not in active, f"{key}/指标/{name} 公式依赖存在循环")
        if name in dependency_gaps:
            return dependency_gaps[name]
        active.add(name)
        record = metrics[name]
        gaps = {name} if record.get("gap") is not None else set()
        for dep in record.get("dependencies", []) if record["kind"] == "derived" else []:
            gaps.update(visit(dep))
        if row["items"][record["item"]].get("status") == "candidate":
            require(not gaps, f"{key}/指标/{name} 依赖受阻：{', '.join(sorted(gaps))}")
        active.remove(name)
        dependency_gaps[name] = gaps
        return gaps

    for name in metrics:
        visit(name)

    for direction in ("inbound", "outbound"):
        directions = review.get("directions")
        require(isinstance(directions, dict) and isinstance(directions.get(direction), str)
                and directions[direction].strip(), f"{key} 缺少联动方向结论：{direction}")
    for name, record in review["interactions"].items():
        if record["codeCheck"]["status"] == "excluded":
            continue
        require(record.get("direction") in ("inbound", "outbound"), f"{key}/联动/{name} 缺少方向")
        if record.get("gap") is None:
            for field in ("source", "action", "state", "target", "response"):
                require(isinstance(record.get(field), str) and record[field].strip(),
                        f"{key}/联动/{name} 缺少链路字段：{field}")

    for name in component["requiredItems"]:
        if name.startswith(("direct:", "upstream:")):
            require(name in clues["interfaces"], f"{key} 缺少登记接口线索：{name}")
    field_clues = clues.get("interfaceFields", {})
    require(isinstance(field_clues, dict) and set(field_clues) <= set(clues["interfaces"]),
            f"{key} DTO 字段线索未关联接口")
    for name, record in review["interfaces"].items():
        require(record["item"] == name, f"{key}/接口/{name} 覆盖项关联无效")
        fields = record.get("fields")
        if record["codeCheck"]["status"] == "excluded" and not field_clues.get(name):
            continue
        require(isinstance(fields, dict), f"{key}/接口/{name} 缺少字段盘点")
        expected = record.get("identifiedFields")
        require(isinstance(expected, list) and all(isinstance(field, str) and field for field in expected),
                f"{key}/接口/{name} 缺少已识别字段清单")
        require(set(expected) <= set(fields), f"{key}/接口/{name} 未盘点字段："
                + ", ".join(sorted(set(expected) - set(fields))))
        discovered = field_clues.get(name, {})
        require(isinstance(discovered, dict) and all(isinstance(source, str) and source.strip()
                for source in discovered.values()), f"{key}/接口/{name} DTO 字段线索无效")
        require(set(discovered) <= set(fields), f"{key}/接口/{name} 缺少 DTO 字段："
                + ", ".join(sorted(set(discovered) - set(fields))))
        if not fields:
            require(record.get("gap") or isinstance(record.get("noFieldsEvidence"), str)
                    and record["noFieldsEvidence"].strip(), f"{key}/接口/{name} 缺少无字段依据")
        for field, data in fields.items():
            label = f"{key}/接口/{name}/{field}"
            require(isinstance(data, dict), f"{label} 字段盘点无效")
            status = check_code_evidence(data, field in discovered, label)
            if record["codeCheck"]["status"] == "excluded":
                require(status == "excluded", f"{label} 已排除接口的字段须记录排除依据")
            if status == "excluded":
                continue
            if data.get("gap"):
                require(isinstance(data["gap"], str) and data["gap"].strip() and record.get("gap"),
                        f"{label} 字段缺口须汇总到接口")
            else:
                require(isinstance(data.get("type"), str) and data["type"].strip()
                        and isinstance(data.get("required"), bool) and isinstance(data.get("nullable"), bool)
                        and isinstance(data.get("meaning"), str) and data["meaning"].strip(),
                        f"{label} 缺少类型、含义、必填或可空性")


def validate(identity, plan):
    require(isinstance(plan, dict) and isinstance(plan.get("root"), str), "缺少完整整理清单")
    expected = scope(identity, plan["root"], plan.get("registrySnapshot"))
    page = {key: identity[key] for key in PAGE_KEYS}
    rows = plan.get("components")
    require(isinstance(rows, dict) and set(rows) == {x["componentId"] for x in expected},
            "整理清单有遗漏或包含范围外组件")
    discovery = plan.get("discovery")
    if discovery is not None:
        require(isinstance(discovery, dict) and set(discovery) == set(rows), "线索清单与组件范围不一致")
    shared_interfaces = {}
    shared_mutations = {}
    for component in expected:
        key = component["componentId"]
        row = rows[key]
        require(isinstance(row, dict) and isinstance(row.get("items"), dict), f"{key} 缺少盘点项")
        items = row["items"]
        require(set(component["requiredItems"]) <= set(items), f"{key} 缺少知识类型或接口盘点项")
        if discovery is not None:
            check_structured_review(component, row, discovery[key])
        request = row.get("request")
        require(request is None or isinstance(request, dict), f"{key} 候选请求无效")
        changes = request.get("changes") if request is not None else []
        require(isinstance(changes, list) and all(isinstance(c, dict) for c in changes), f"{key} 候选请求无效")
        for change in changes:
            operation, kind, object_id = (change.get(field) for field in ("operation", "objectType", "objectId"))
            if operation in ("UPDATE", "EXPIRE") and kind != "COMPONENT" and isinstance(object_id, str) and object_id:
                object_key = (kind, object_id)
                require(object_key not in shared_mutations or shared_mutations[object_key] == key,
                        f"{key} 重复修改共享对象 {object_key}，已由 {shared_mutations.get(object_key)} 提出")
                shared_mutations[object_key] = key
            if operation in ("LINK", "UPGRADE_REFERENCE"):
                version = change.get("targetVersionId")
                require(isinstance(version, str) and version.strip() and not version.startswith("new:"),
                        f"{key}/{change.get('changeId')} 引用须使用正式对象版本")
            if change.get("operation") == "CREATE" and change.get("objectType") == "INTERFACE":
                content = change.get("content")
                require(isinstance(content, dict), f"{key} 新增接口缺少正文")
                method, address = content.get("method"), content.get("address")
                require(all(isinstance(value, str) and value.strip() for value in (method, address)),
                        f"{key} 新增接口缺少方法或地址")
                identity_key = (method.strip().upper(), address.strip())
                require(identity_key not in shared_interfaces,
                        f"{key} 重复新增接口 {identity_key}，已由 {shared_interfaces.get(identity_key)} 提出")
                shared_interfaces[identity_key] = key
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
