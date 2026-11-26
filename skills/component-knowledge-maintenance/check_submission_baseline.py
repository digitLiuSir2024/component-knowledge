"""Check an approved component request against a fresh MCP baseline."""
import argparse
import json
from pathlib import Path

from coverage_check import PAGE_KEYS, require, scope, validate


def verify(plan, digest, component, fresh, pending=None, approved_pending=None):
    request = plan["components"][component]["request"]
    require(isinstance(request, dict), "组件没有已审批的提交请求")
    require(validate(request, plan) == digest, "计划与获批摘要不一致")
    require(isinstance(fresh, dict) and "currentVersionId" in fresh, "缺少成功读取的组件基准")
    require(fresh["currentVersionId"] == request.get("baseVersionId"), "正式版本已变化，须重新审批")
    if "sourceRevision" in request:
        require(fresh.get("sourceRevision") == request["sourceRevision"], "首次基准已变化，须重新审批")
    expected = request["expectedPendingDraft"]
    require(isinstance(expected, dict), "待审草稿基准无效")
    ids = fresh.get("pendingDraftIds")
    if not expected:
        require(ids == [] and pending is None and approved_pending is None, "待审草稿已变化，须重新审批")
        return
    require(ids == [expected["id"]] and isinstance(pending, dict) and isinstance(approved_pending, dict),
            "须读取唯一待审草稿并保留获批时的快照")
    require(pending.get("id") == expected["id"] and pending.get("revision") == expected["revision"]
            and pending.get("status") == "DRAFT" and pending.get("origin") != "RESTORE"
            and pending == approved_pending, "待审草稿已变化或不可替换，须重新审批")
    identity = pending.get("identity")
    require(isinstance(identity, dict) and all(identity.get(key) == request[key] for key in (*PAGE_KEYS, "componentId")),
            "待审草稿身份与获批组件不匹配")


def verify_batch(plan, digest, inputs):
    requests = [row["request"] for row in plan["components"].values() if row.get("request") is not None]
    require(bool(requests), "批次没有候选")
    identity = requests[0]
    require(validate(identity, plan) == digest, "批次计划与获批摘要不一致")
    require(isinstance(inputs, dict), "批次基准格式无效")
    ready = []
    blocked = {}
    for entry in scope(identity, plan["root"], plan.get("registrySnapshot")):
        key = entry["componentId"]
        if plan["components"][key].get("request") is None:
            continue
        baseline = inputs.get(key)
        if not isinstance(baseline, dict):
            blocked[key] = "缺少本轮 MCP 基准"
            continue
        try:
            verify(plan, digest, key, baseline.get("context"), baseline.get("pending"), baseline.get("approvedPending"))
        except (ValueError, KeyError, TypeError) as error:
            blocked[key] = str(error)
        else:
            ready.append(key)
    return {"ready": ready, "blocked": blocked}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("plan", "context", "pending", "approved-pending"):
        parser.add_argument(f"--{name}", type=Path, required=name == "plan")
    parser.add_argument("--digest", required=True)
    parser.add_argument("--component")
    parser.add_argument("--batch-context", type=Path)
    args = parser.parse_args()
    if args.batch_context:
        if args.component or args.context or args.pending or args.approved_pending:
            parser.error("批次模式仅接受 --plan、--digest 和 --batch-context")
    elif not args.component or not args.context:
        parser.error("单组件模式须指定 --component 和 --context")

    def read(path):
        return json.loads(path.read_text()) if path is not None else None

    try:
        if args.batch_context:
            result = verify_batch(read(args.plan), args.digest, read(args.batch_context))
            print(json.dumps(result, ensure_ascii=False))
            return int(bool(result["blocked"]))
        verify(read(args.plan), args.digest, args.component, read(args.context),
               read(args.pending), read(args.approved_pending))
    except (OSError, ValueError, KeyError, TypeError) as error:
        parser.exit(1, f"{error}\n")
    print("获批摘要及当前提交基准一致")


if __name__ == "__main__":
    raise SystemExit(main())
