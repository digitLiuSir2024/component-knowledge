"""Compare a submitted knowledge request with an MCP draft readback."""
import argparse
from copy import deepcopy
import json
from pathlib import Path

from coverage_check import PAGE_KEYS, require


def index_changes(changes, label):
    require(isinstance(changes, list), f"{label} 缺少 changes")
    indexed = {}
    for change in changes:
        require(isinstance(change, dict), f"{label} 候选格式无效")
        key = change.get("changeId")
        require(isinstance(key, str) and key and key not in indexed, f"{label} changeId 缺失或重复：{key}")
        indexed[key] = change
    return indexed


def compare(expected, actual, path, differences):
    if type(expected) is not type(actual):
        differences.append(f"{path} 类型不同")
    elif isinstance(expected, dict):
        for key in sorted(expected.keys() - actual.keys()):
            differences.append(f"{path}.{key} 缺失")
        for key in sorted(actual.keys() - expected.keys()):
            differences.append(f"{path}.{key} 多出")
        for key in sorted(expected.keys() & actual.keys()):
            compare(expected[key], actual[key], f"{path}.{key}", differences)
    elif isinstance(expected, list):
        if len(expected) != len(actual):
            differences.append(f"{path} 数量不同：预期 {len(expected)}，实际 {len(actual)}")
        for index, (left, right) in enumerate(zip(expected, actual)):
            compare(left, right, f"{path}[{index}]", differences)
    elif expected != actual:
        differences.append(f"{path} 值不同：预期 {expected!r}，实际 {actual!r}")


def verify(request, draft, draft_id):
    require(isinstance(request, dict) and isinstance(draft, dict), "请求或回读格式无效")
    require(draft.get("id") == draft_id and draft.get("status") == "DRAFT", "草稿 ID 或状态不匹配")
    identity = draft.get("identity")
    require(isinstance(identity, dict), "回读缺少请求身份")
    for key in (*PAGE_KEYS, "componentId", "requestId"):
        require(isinstance(request.get(key), str) and request[key], f"请求缺少 {key}")
        require(identity.get(key) == request[key], f"回读请求身份不匹配：{key}")
    for key in ("systemCode", "projectCode", "requestId"):
        require(draft.get(key) == request[key], f"草稿身份不匹配：{key}")
    require("expectedPendingDraft" in request
            and identity.get("expectedPendingDraft") == request["expectedPendingDraft"],
            "回读请求基准不匹配：expectedPendingDraft")
    expected_origin = "MIGRATION" if "sourceRevision" in request else "SESSION"
    require(draft.get("origin") == expected_origin, f"草稿来源不匹配：{draft.get('origin')}，预期 {expected_origin}")
    require("baseVersionId" in draft and draft["baseVersionId"] == request.get("baseVersionId"),
            "草稿正式版本基准不匹配")
    if "sourceRevision" in request:
        require(identity.get("sourceRevision") == request["sourceRevision"]
                and identity.get("interfaces") == request.get("interfaces"), "首次基准或有序接口清单不匹配")
    require(draft.get("issues") == [], "草稿有问题或回读缺少问题列表")
    expected = index_changes(deepcopy(request.get("changes")), "请求")
    actual = index_changes(draft.get("changes"), "回读")
    require(bool(expected), "请求没有候选")
    registering = any(item.get("objectType") == "COMPONENT" and item.get("operation") == "CREATE"
                      and item.get("objectId") == "new:component" for item in expected.values())
    if registering:
        component_id = draft.get("componentId")
        before = draft.get("beforeObjects")
        require(isinstance(component_id, str) and component_id and not component_id.startswith("new:")
                and isinstance(before, list)
                and any(isinstance(item, dict) and item.get("objectType") == "COMPONENT"
                        and item.get("objectId") == component_id for item in before), "首次登记正式 ID 无法核实")
        for change in expected.values():
            kind = change.get("objectType")
            if kind == "COMPONENT" and change.get("operation") == "CREATE" and change.get("objectId") == "new:component":
                change.update(operation="UPDATE", objectId=component_id)
            content = change.get("content")
            if not isinstance(content, dict):
                continue
            if kind == "APPLICABILITY" and content.get("targetType") == "COMPONENT" and content.get("targetId") == "new:component":
                content["targetId"] = component_id
            if kind == "COMPONENT_RELATION" and content.get("targetComponentId") == "new:component":
                content["targetComponentId"] = component_id
    differences = []
    compare(expected, actual, "changes", differences)
    require(not differences, "\n".join(differences))
    return {"draftId": draft_id, "verifiedChanges": sorted(expected), "registrationMapped": registering}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", type=Path, required=True)
    parser.add_argument("--readback", type=Path, required=True)
    parser.add_argument("--draft-id", required=True)
    args = parser.parse_args()
    try:
        request = json.loads(args.request.read_text())
        draft = json.loads(args.readback.read_text())
        result = verify(request, draft, args.draft_id)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
