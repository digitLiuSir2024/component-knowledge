#!/usr/bin/env python3
"""Migrate one-off interaction seed rows into versioned knowledge drafts.

Default mode creates drafts only. Pass --publish to confirm and publish them.
All paths are relative to this repository; no /tmp or personal paths are used.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from urllib import error, parse, request

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SQL = ROOT / "database" / "component-interaction-data.sql"
DEFAULT_MAPPING = ROOT / "scripts" / "interaction-migration-mapping.json"
INSERT = re.compile(
    r"INSERT\s+INTO\s+`?(knowledge_component|knowledge_component_interaction|knowledge_component_relation)`?\s*"
    r"\(([^)]*)\)\s*VALUES\s*\(((?:[^()]|\([^()]*\))*)\)\s*;",
    re.IGNORECASE | re.DOTALL,
)


def split_values(raw):
    """Split a SQL VALUES tuple without breaking quoted commas or escaped quotes."""
    values = []
    buffer = []
    quoted = False
    index = 0
    while index < len(raw):
        char = raw[index]
        if char == "'":
            if quoted and index + 1 < len(raw) and raw[index + 1] == "'":
                buffer.append("''")
                index += 2
                continue
            buffer.append(char)
            quoted = not quoted
        elif char == "," and not quoted:
            values.append("".join(buffer).strip())
            buffer = []
        else:
            buffer.append(char)
        index += 1
    values.append("".join(buffer).strip())
    return values


def cell(value):
    if value.upper() == "NULL":
        return None
    if value.startswith("'") and value.endswith("'"):
        return value[1:-1].replace("''", "'")
    return int(value) if re.fullmatch(r"-?\d+", value) else value


def read_seed(path):
    text = Path(path).read_text(encoding="utf-8")
    components = {}
    interactions = []
    relations = []
    pages = {}
    for match in INSERT.finditer(text):
        table = match.group(1).lower()
        columns = [part.strip().strip("`") for part in match.group(2).split(",")]
        cells = [cell(part) for part in split_values(match.group(3))]
        row = dict(zip(columns, cells))
        if table == "knowledge_component":
            components[row["id"]] = row
        elif table == "knowledge_component_interaction":
            interactions.append(row)
        else:
            relations.append(row)
    for match in re.finditer(
        r"INSERT\s+INTO\s+`?knowledge_page`?\s*\(([^)]*)\)\s*VALUES\s*\(((?:[^()]|\([^()]*\))*)\)\s*;",
        text,
        re.IGNORECASE | re.DOTALL,
    ):
        columns = [part.strip().strip("`") for part in match.group(1).split(",")]
        row = dict(zip(columns, [cell(part) for part in split_values(match.group(2))]))
        pages[row["id"]] = row
    return components, interactions, relations, pages


def evidence(excerpt):
    return [{"kind": "FACT", "source": "docs/component-interaction-registration.md", "excerpt": excerpt}]


def build_plan(components, interactions, relations, system_code, project_code, page_path):
    by_component = {}
    for row in interactions:
        by_component.setdefault(row["component_id"], []).append(row)
    owned = {row["id"]: row["component_id"] for row in interactions}
    mapping = {}
    for row in components.values():
        mapping[row["id"]] = row["component_id"]
    grouped = {}
    for component_id, rows in by_component.items():
        identity = {
            "systemCode": system_code,
            "projectCode": project_code,
            "pagePath": page_path,
            "componentId": mapping.get(component_id, component_id),
        }
        changes = []
        for row in rows:
            content = {
                "componentId": component_id,
                "action": row["action_name"],
                "triggerCondition": row["trigger_condition"],
                "providedContent": row["provided_content"],
                "response": row["response_text"],
                "sortOrder": row["sort_order"],
            }
            changes.append({
                "changeId": "migration:INTERACTION:" + row["id"],
                "operation": "UPDATE",
                "objectType": "INTERACTION",
                "objectId": row["id"],
                "baseObjectVersionId": None,
                "content": content,
                "evidence": evidence("交互登记迁移：" + row["action_name"]),
                "atomicGroupId": "interaction-migration:" + component_id,
            })
        for row in relations:
            owner = owned.get(row["interaction_id"])
            if owner != component_id:
                continue
            content = {
                "interactionId": row["interaction_id"],
                "targetComponentId": row["target_component_id"],
                "effect": row["effect_text"],
                "condition": row["condition_text"],
                "sortOrder": row["sort_order"],
            }
            changes.append({
                "changeId": "migration:COMPONENT_RELATION:" + row["id"],
                "operation": "UPDATE",
                "objectType": "COMPONENT_RELATION",
                "objectId": row["id"],
                "baseObjectVersionId": None,
                "content": content,
                "evidence": evidence("联动登记迁移：" + row["effect_text"]),
                "atomicGroupId": "interaction-migration:" + component_id,
            })
        digest = hashlib.sha256(json.dumps(changes, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()[:16]
        grouped[component_id] = {
            "identity": identity,
            "requestId": "interaction-migration:" + digest,
            "changes": changes,
        }
    return grouped


def load_mapping(path):
    if not Path(path).exists():
        return {"version": 1, "components": {}}
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("version") != 1 or not isinstance(data.get("components"), dict):
        raise ValueError("迁移映射文件格式无效")
    return data


def save_mapping(path, data):
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(str(temporary), str(target))


def post_json(base, endpoint, body):
    address = parse.urlparse(base)
    if address.scheme not in ("http", "https") or not address.netloc or address.query or address.fragment:
        raise ValueError("请配置合法的 KNOWLEDGE_API_BASE")
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = request.Request(
        base + "/api/v1/knowledge/maintenance/" + endpoint,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=20) as response:
            envelope = json.load(response)
    except (error.URLError, OSError, ValueError) as cause:
        raise ValueError("知识服务请求失败：" + str(cause)) from cause
    if envelope.get("code") != 200 or envelope.get("success") is not True:
        raise ValueError("知识服务拒绝请求，业务码 " + str(envelope.get("code")))
    return envelope.get("data")


def migrate(base, plan, mapping_path, publish):
    mapping = load_mapping(mapping_path)
    for component_id, item in plan.items():
        record = mapping["components"].get(component_id, {})
        if publish and record.get("publishedVersionId"):
            print("skip published " + component_id)
            continue
        if not publish and record.get("draftId"):
            print("skip existing draft " + component_id)
            continue
        context = post_json(base, "context", item["identity"])
        if context.get("currentVersionId"):
            print("skip registered " + component_id)
            mapping["components"][component_id] = record or {"reason": "already-registered"}
            save_mapping(mapping_path, mapping)
            continue
        if not record.get("draftId"):
            body = dict(item["identity"])
            body.update({
                "requestId": item["requestId"],
                "sourceRevision": context.get("sourceRevision"),
                "changes": item["changes"],
            })
            draft = post_json(base, "baseline", body)
            record["draftId"] = draft.get("id")
            record["revision"] = draft.get("revision")
            record["changeCount"] = len(item["changes"])
            mapping["components"][component_id] = record
            save_mapping(mapping_path, mapping)
        if publish and record.get("draftId"):
            body = {
                "systemCode": item["identity"]["systemCode"],
                "projectCode": item["identity"]["projectCode"],
                "draftId": record["draftId"],
                "revision": record["revision"],
                "selectedChangeIds": [change["changeId"] for change in item["changes"]],
            }
            result = post_json(base, "drafts/publish", body)
            record["publishedVersionId"] = result.get("componentVersionId")
            mapping["components"][component_id] = record
            save_mapping(mapping_path, mapping)
        print("migrated " + component_id + " changes=" + str(len(item["changes"])))


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--sql", default=str(DEFAULT_SQL))
    cli.add_argument("--mapping", default=str(DEFAULT_MAPPING))
    cli.add_argument("--system-code", default="LQ13.03")
    cli.add_argument("--project-code", default="zhituo")
    cli.add_argument("--page-path", help="页面内登记路径，必须与现有 knowledge_page 一致")
    cli.add_argument("--api-base", default=os.environ.get("KNOWLEDGE_API_BASE", ""))
    cli.add_argument("--publish", action="store_true", help="确认并发布草稿，默认只创建草稿")
    cli.add_argument("--dry-run", action="store_true", help="只打印计划，不连接服务、不写映射")
    args = cli.parse_args()
    components, interactions, relations, pages = read_seed(args.sql)
    page_path = args.page_path
    if not page_path and pages:
        page_path = next(iter(pages.values())).get("page_path")
    if not page_path:
        _, _, _, seed_pages = read_seed(ROOT / "database" / "knowledge-data.sql")
        if seed_pages:
            page_path = next(iter(seed_pages.values())).get("page_path")
    if not page_path:
        cli.error("缺少 --page-path，且种子 SQL 中未找到页面路径")
    plan = build_plan(components, interactions, relations, args.system_code, args.project_code, page_path)
    if args.dry_run:
        print(json.dumps(plan, ensure_ascii=False, indent=2))
        return 0
    if not args.api_base:
        cli.error("缺少 --api-base 或 KNOWLEDGE_API_BASE")
    migrate(args.api_base, plan, args.mapping, args.publish)
    return 0


if __name__ == "__main__":
    sys.exit(main())
