#!/usr/bin/env python3
"""Explicitly triggered knowledge reading and draft submission over MCP stdio."""

import argparse
import hashlib
import json
import os
import secrets
import socket
import sys
import time
from urllib import error, parse, request

import coverage_check

PROTOCOLS = ("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
IDENTITY = ("systemCode", "projectCode", "pagePath", "componentId")
SCOPE = ("systemCode", "projectCode")
OBJECT = (*SCOPE, "objectType", "objectId")
LEGACY_ENTITIES = (
    "PROJECT", "SERVICE", "PAGE", "COMPONENT", "FLOW", "PAGE_FLOW", "INTERFACE", "ENTRY",
    "APPLICABILITY", "INTERFACE_FIELD", "INTERFACE_EXAMPLE", "INTERFACE_ERROR",
    "COMPONENT_INTERACTION", "COMPONENT_RELATION",
)
VERSION_MANAGED_LEGACY = {"COMPONENT_INTERACTION", "COMPONENT_RELATION"}
_ADMIN_WRITE_TOOLS = {"upsert_legacy_record", "delete_legacy_record"}
APPROVAL_TTL_SECONDS = 1800
_APPROVAL_TOKENS = {}

TOOLS = {
    "knowledge_coverage_scope": (None, (*SCOPE, "pagePath", "root"), ("registrySnapshot",), "从注册表生成整理清单"),
    "validate_knowledge_coverage": (None, (*SCOPE, "pagePath", "coveragePlan"), (), "检查整批覆盖和候选，返回审批汇总所用摘要"),

    "read_component_knowledge": ("context", IDENTITY, ("interfaces",), "读取当前组件基准及完整知识，不发布"),
    "search_knowledge_objects": ("objects/search", SCOPE, ("objectType", "keyword", "offset", "limit"), "搜索同项目对象，结果不替代明确版本全文"),
    "read_component_interactions": ("interactions/graph", IDENTITY, ("versionId",), "读取当前或指定版本的交互图，不发布"),
    "read_knowledge_object_version": ("objects/version", (*OBJECT, "versionId"), (), "读取指定对象版本完整正文，不回退最新版本"),
    "list_knowledge_object_versions": ("objects/history", OBJECT, ("offset", "limit"), "列出对象历史版本"),
    "submit_knowledge_draft": ("drafts/create", (*IDENTITY, "requestId", "baseVersionId", "changes", "coveragePlan", "coverageDigest", "expectedPendingDraft", "approvalToken"), (), "仅提交会话候选草稿，必须由用户到组件详情页确认"),
    "submit_interaction_candidates": ("drafts/create", (*IDENTITY, "requestId", "baseVersionId", "changes", "coveragePlan", "coverageDigest", "expectedPendingDraft", "approvalToken"), (), "提交交互与联动候选草稿，必须由用户确认发布；与 submit_knowledge_draft 同一后端端点"),
    "submit_initial_knowledge_draft": ("baseline", (*IDENTITY, "requestId", "sourceRevision", "interfaces", "changes", "coveragePlan", "coverageDigest", "expectedPendingDraft", "approvalToken"), (), "提交首次基线候选草稿，不发布；origin 由服务端决定为 MIGRATION"),
    "read_knowledge_draft": ("drafts/read", (*SCOPE, "draftId"), (), "读取草稿、审核地址与发布结果"),
    "list_knowledge_drafts": ("drafts/list", IDENTITY, ("offset", "limit"), "分页发现待确认草稿"),
    "list_component_versions": ("versions", IDENTITY, ("offset", "limit"), "分页发现组件正式版本"),
    "read_legacy_record": ("legacy/get", ("entity", "keys"), (), "只读核验基础表单行"),
    "list_legacy_records": ("legacy/list", ("entity",), ("filters", "offset", "limit"), "只读核验基础表多行"),
    "upsert_legacy_record": ("legacy/upsert", ("entity", "row", "confirmWrite"), (), "写入基础表；需显式确认与管理开关"),
    "delete_legacy_record": ("legacy/delete", ("entity", "keys", "confirmWrite"), (), "删除基础表行；需显式确认与管理开关"),
    "publish_approved_draft": ("drafts/publish", (*IDENTITY, "requestId", "draftId", "revision", "selectedChangeIds", "confirmPublish", "approvalToken"), (), "发布已审批草稿；令牌绑定组件、requestId 与所选候选，且一次性生效"),
    "discard_knowledge_draft": ("drafts/discard", (*SCOPE, "draftId", "revision", "confirmDiscard"), (), "丢弃待审草稿；需显式确认与修订号"),
}
WRITE_TOOLS = ({name for name in TOOLS if name.startswith("submit_")} | _ADMIN_WRITE_TOOLS
               | {"publish_approved_draft", "discard_knowledge_draft"})


def property_schema(name):
    """Return the actual input shapes accepted by the fixed HTTP bridge."""
    if name in ("offset", "limit"):
        return {"type": "integer", "minimum": 0 if name == "offset" else 1, **({"maximum": 100} if name == "limit" else {})}
    if name == "interfaces":
        return {"type": "array", "items": {"type": "object", "properties": {
            "method": {"type": "string", "minLength": 1},
            "address": {"type": "string", "minLength": 1},
        }, "required": ["method", "address"], "additionalProperties": False}}
    if name in ("coveragePlan", "registrySnapshot", "keys", "filters", "row"):
        return {"type": "object"}
    if name == "entity":
        return {"type": "string", "enum": list(LEGACY_ENTITIES)}
    if name == "revision":
        return {"type": "integer", "minimum": 1, "maximum": 2147483647}
    if name == "selectedChangeIds":
        return {"type": "array", "items": {"type": "string", "minLength": 1}}
    if name in ("confirmWrite", "confirmPublish", "confirmDiscard"):
        return {"type": "boolean", "enum": [True]}
    if name == "expectedPendingDraft":
        return {"oneOf": [
            {"type": "object", "maxProperties": 0},
            {"type": "object", "properties": {"id": {"type": "string", "minLength": 1},
             "revision": {"type": "integer", "minimum": 1, "maximum": 2147483647}},
             "required": ["id", "revision"], "additionalProperties": False},
        ]}
    if name == "changes":
        fields = {key: {"type": "string", "minLength": 1} for key in ("changeId", "objectType", "objectId")}
        fields["operation"] = {"type": "string", "enum": ["CREATE", "UPDATE", "LINK", "UNLINK", "UPGRADE_REFERENCE", "EXPIRE"]}
        fields.update({key: {"type": ["string", "null"]} for key in ("baseObjectVersionId", "targetVersionId", "atomicGroupId")})
        fields["confirmationStatus"] = {"type": ["string", "null"], "enum": ["CONFIRMED", "PENDING", None]}
        fields["content"] = {"type": ["object", "null"]}
        fields["evidence"] = {"type": "array", "items": {"type": "object", "properties": {
            "kind": {"type": "string", "enum": ["FACT", "REQUIREMENT", "INFERENCE"]},
            "source": {"type": "string", "minLength": 1},
            "excerpt": {"type": "string", "minLength": 1},
        }, "required": ["kind", "source", "excerpt"], "additionalProperties": False}}
        return {"type": "array", "items": {"type": "object", "properties": fields, "required": list(fields), "additionalProperties": False}}
    return {"type": "string", "minLength": 1}


def describe():
    """Only these named tools can reach the backend."""
    result = []
    for name, (_, required, optional, description) in TOOLS.items():
        result.append({"name": name, "description": description, "inputSchema": {"type": "object", "properties": {key: property_schema(key) for key in (*required, *optional)}, "required": list(required), "additionalProperties": False}, "annotations": {"readOnlyHint": name not in WRITE_TOOLS, "destructiveHint": name in ("delete_legacy_record", "discard_knowledge_draft")}})
    return result


def approval_fingerprint(plan):
    payload = json.dumps(plan, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def issue_approval_token(digest, plan):
    """Issue a short-lived token whose consumption is tracked per publish target."""
    now = time.monotonic()
    for key, record in list(_APPROVAL_TOKENS.items()):
        if now - record["issuedAt"] > APPROVAL_TTL_SECONDS:
            _APPROVAL_TOKENS.pop(key, None)
    identity = plan.get("registrySnapshot", {}).get("pageIdentity", {})
    components = {}
    for key, row in plan.get("components", {}).items():
        request = row.get("request") if isinstance(row, dict) else None
        if not isinstance(request, dict):
            continue
        components[key] = {
            "requestId": request.get("requestId"),
            "changeIds": [item.get("changeId") for item in request.get("changes", [])
                          if isinstance(item, dict) and isinstance(item.get("changeId"), str)],
        }
    token = secrets.token_hex(16)
    _APPROVAL_TOKENS[token] = {
        "digest": digest,
        "fingerprint": approval_fingerprint(plan),
        "identity": {key: identity.get(key) for key in coverage_check.PAGE_KEYS},
        "components": components,
        "issuedAt": now,
        "consumedTargets": set(),
    }
    return token


def verify_approval_token(token, digest=None, plan=None, component=None, page=None, request_id=None,
                          selected_change_ids=None, publish_target=None):
    """Validate every available binding; the same token may serve several publish targets."""
    record = _APPROVAL_TOKENS.get(token)
    if not isinstance(record, dict):
        raise ValueError("审批令牌无效，请先运行 validate_knowledge_coverage")
    if time.monotonic() - record["issuedAt"] > APPROVAL_TTL_SECONDS:
        raise ValueError("审批令牌已过期，请重新校验并取得批准")
    if publish_target is not None and publish_target in record["consumedTargets"]:
        raise ValueError("该草稿已使用本次审批令牌发布，请先回读发布结果")
    if digest is not None and record["digest"] != digest:
        raise ValueError("审批令牌与覆盖摘要不匹配")
    if plan is not None and record["fingerprint"] != approval_fingerprint(plan):
        raise ValueError("审批令牌与整理清单不匹配")
    if component is not None:
        if component not in record["components"]:
            raise ValueError("审批令牌不包含该提交组件")
        if page is not None and any(record["identity"].get(key) != page.get(key) for key in coverage_check.PAGE_KEYS):
            raise ValueError("审批令牌与页面身份不匹配")
        if request_id is not None and record["components"][component]["requestId"] != request_id:
            raise ValueError("审批令牌与 requestId 不匹配")
        if selected_change_ids is not None:
            allowed = set(record["components"][component]["changeIds"])
            if not set(selected_change_ids) <= allowed:
                raise ValueError("审批令牌不包含所选 changeId")


def consume_approval_target(token, publish_target):
    """Mark one (componentId, draftId) target as published after a successful response."""
    record = _APPROVAL_TOKENS.get(token)
    if not isinstance(record, dict):
        raise ValueError("审批令牌无效，请先运行 validate_knowledge_coverage")
    if time.monotonic() - record["issuedAt"] > APPROVAL_TTL_SECONDS:
        raise ValueError("审批令牌已过期，请重新校验并取得批准")
    record["consumedTargets"].add(publish_target)


def admin_writes_enabled():
    """Legacy table writes are off unless explicitly enabled by the operator."""
    return os.environ.get("KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES", "false").strip().lower() in ("1", "true", "yes")


def request_timeout():
    """Read the MCP bridge timeout from the environment, with a safe default."""
    try:
        value = int(os.environ.get("KNOWLEDGE_MCP_TIMEOUT", "10"))
    except ValueError:
        return 10
    return value if 1 <= value <= 120 else 10


CHANGE_OPERATIONS = ("CREATE", "UPDATE", "LINK", "UNLINK", "UPGRADE_REFERENCE", "EXPIRE")
EVIDENCE_KINDS = ("FACT", "REQUIREMENT", "INFERENCE")


def validate_changes_runtime(changes):
    """Reject malformed changes at the bridge with a changeId-specific message."""
    if not isinstance(changes, list):
        raise ValueError("changes 必须是数组")
    seen = set()
    for index, change in enumerate(changes):
        label = f"changes[{index}]"
        if not isinstance(change, dict):
            raise ValueError(f"{label} 必须是对象")
        for field in ("changeId", "operation", "objectType", "objectId"):
            value = change.get(field)
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{label}.{field} 必须是非空字符串")
        if change["changeId"] in seen:
            raise ValueError(f"changeId 重复：{change['changeId']}")
        seen.add(change["changeId"])
        if change["operation"] not in CHANGE_OPERATIONS:
            raise ValueError(f"{label}.operation 不是允许的操作：{change['operation']}")
        confirmation = change.get("confirmationStatus")
        if confirmation not in (None, "CONFIRMED", "PENDING"):
            raise ValueError(f"{label}.confirmationStatus 不是允许的状态")
        evidence = change.get("evidence")
        if not isinstance(evidence, list):
            raise ValueError(f"{label}.evidence 必须是数组")
        for item in evidence:
            if not isinstance(item, dict) or item.get("kind") not in EVIDENCE_KINDS:
                raise ValueError(f"{label}.evidence.kind 不是允许的类型")
            if not isinstance(item.get("source"), str) or not item["source"].strip():
                raise ValueError(f"{label}.evidence.source 必须是非空字符串")
            if not isinstance(item.get("excerpt"), str) or not item["excerpt"].strip():
                raise ValueError(f"{label}.evidence.excerpt 必须是非空字符串")


def ensure_submit_target(body, plan):
    """Reject a submit target outside the validated plan before indexing it."""
    if not isinstance(plan, dict) or not isinstance(plan.get("components"), dict):
        raise ValueError("整理清单缺少 components 对象")
    component = body.get("componentId")
    if component not in plan["components"]:
        raise ValueError("提交组件不在已核对的整理清单内")
    if not isinstance(plan["components"][component], dict) or not isinstance(plan["components"][component].get("request"), dict):
        raise ValueError("整理清单中该组件的 request 无效")


def call_tool(name, arguments):
    """Forward a validated named operation; never accept a path from the model."""
    if name not in TOOLS or not isinstance(arguments, dict):
        raise ValueError("未知工具或参数格式不合法")
    path, required, optional, _ = TOOLS[name]
    if set(arguments) - set((*required, *optional)):
        raise ValueError("包含工具未声明的参数")
    for key in required:
        value = arguments.get(key)
        if key == "interfaces":
            if not isinstance(value, list):
                raise ValueError("缺少必填参数 interfaces 或格式不是数组")
            continue
        if key in ("coveragePlan", "keys", "filters", "row"):
            if not isinstance(value, dict):
                raise ValueError(f"{key} 必须是对象")
        elif key == "entity":
            if value not in LEGACY_ENTITIES:
                raise ValueError(f"entity 必须是基础表白名单中的名称：{value}")
        elif key == "revision":
            if type(value) is not int or not 1 <= value <= 2147483647:
                raise ValueError("revision 必须是正整数")
        elif key == "selectedChangeIds":
            if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
                raise ValueError("selectedChangeIds 必须是非空字符串数组")
        elif key in ("confirmWrite", "confirmPublish", "confirmDiscard"):
            if value is not True:
                raise ValueError(f"{key} 必须显式为 true")
        elif key == "changes":
            if not isinstance(value, list):
                raise ValueError("changes 必须是数组")
        elif not isinstance(value, str) or not value.strip():
            raise ValueError(f"缺少必填参数 {key}")
    if "expectedPendingDraft" in arguments:
        expected = arguments["expectedPendingDraft"]
        if not isinstance(expected, dict) or (expected and (
            set(expected) != {"id", "revision"} or not isinstance(expected["id"], str)
            or not expected["id"].strip() or type(expected["revision"]) is not int
            or not 1 <= expected["revision"] <= 2147483647
        )):
            raise ValueError("expectedPendingDraft 必须为空对象或包含 id 和正整数 revision")
    if "changes" in arguments:
        validate_changes_runtime(arguments["changes"])
    if "interfaces" in arguments:
        interfaces = arguments["interfaces"]
        if not isinstance(interfaces, list) or any(
            not isinstance(item, dict)
            or set(item) != {"method", "address"}
            or any(not isinstance(item[field], str) or not item[field].strip() for field in ("method", "address"))
            for item in interfaces
        ):
            raise ValueError("interfaces 必须为 method、address 非空的对象数组")
    if name in _ADMIN_WRITE_TOOLS and arguments.get("entity") in VERSION_MANAGED_LEGACY:
        raise ValueError("交互基础表由发布链维护，禁止通过 legacy 写工具修改")
    if name in _ADMIN_WRITE_TOOLS and not admin_writes_enabled():
        raise ValueError("基础表写工具未启用，请设置 KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES=true 后重试")
    if name == "publish_approved_draft":
        verify_approval_token(arguments["approvalToken"], component=arguments["componentId"],
                              page={key: arguments[key] for key in coverage_check.PAGE_KEYS},
                              request_id=arguments["requestId"],
                              selected_change_ids=arguments["selectedChangeIds"],
                              publish_target=(arguments["componentId"], arguments["draftId"]))
    if name == "knowledge_coverage_scope":
        return coverage_check.scope(arguments, arguments["root"], arguments.get("registrySnapshot"))
    if name == "validate_knowledge_coverage":
        digest = coverage_check.validate(arguments, arguments["coveragePlan"])
        return {"coverageDigest": digest, "approvalToken": issue_approval_token(digest, arguments["coveragePlan"])}
    body = dict(arguments)
    if name == "submit_interaction_candidates":
        coverage_check.validate_interaction_changes(body["changes"], body["componentId"])
    if name.startswith("submit_"):
        plan = body.pop("coveragePlan", None)
        digest = body.pop("coverageDigest", None)
        approval = body.pop("approvalToken", None)
        actual = coverage_check.validate(body, plan)
        ensure_submit_target(body, plan)
        if digest != actual or plan["components"][body["componentId"]]["request"] != body:
            raise ValueError("实际请求与已核对的整理清单不一致")
        verify_approval_token(approval, digest=digest, plan=plan)
    for local_field in ("confirmWrite", "confirmPublish", "confirmDiscard", "approvalToken"):
        body.pop(local_field, None)
    base = os.environ.get("KNOWLEDGE_API_BASE", "").rstrip("/")
    address = parse.urlparse(base)
    if address.scheme not in ("http", "https") or not address.netloc or address.query or address.fragment or address.username:
        raise ValueError("请配置合法的 KNOWLEDGE_API_BASE，不在地址中包含凭据")
    if name in ("submit_knowledge_draft", "submit_interaction_candidates"):
        body["origin"] = "SESSION"
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = request.Request(base + "/api/v1/knowledge/maintenance/" + path, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with request.urlopen(req, timeout=request_timeout()) as response:
            envelope = json.load(response)
    except socket.timeout as cause:
        raise ValueError(f"知识服务响应超时（{request_timeout()} 秒），请先查询草稿状态再决定是否重试") from cause
    except (error.URLError, OSError, ValueError) as cause:
        raise ValueError("知识服务请求失败，请检查连接与服务配置") from cause
    if not isinstance(envelope, dict) or envelope.get("code") != 200 or envelope.get("success") is not True:
        code = envelope.get("code") if isinstance(envelope, dict) else None
        raise ValueError(f"知识服务拒绝请求，业务码 {code}；请在页面检查基准或草稿状态")
    if name == "publish_approved_draft":
        consume_approval_target(arguments["approvalToken"], (arguments["componentId"], arguments["draftId"]))
    return envelope.get("data")


def dispatch(message, initialized):
    """Handle initialization, discovery and the fixed tool capability."""
    method = message.get("method")
    params = message.get("params", {})
    if not isinstance(params, dict):
        return None, {"code": -32602, "message": "参数必须为对象"}
    if method == "initialize":
        version = params.get("protocolVersion")
        return {"protocolVersion": version if version in PROTOCOLS else PROTOCOLS[0], "capabilities": {"tools": {"listChanged": False}}, "serverInfo": {"name": "component-knowledge", "version": "1.0.0"}}, None
    if method == "ping":
        return {}, None
    if not initialized:
        return None, {"code": -32002, "message": "请先初始化会话"}
    if method == "tools/list":
        return {"tools": describe()}, None
    if method == "tools/call":
        try:
            data = call_tool(params.get("name"), params.get("arguments", {}))
            return {"content": [{"type": "text", "text": json.dumps(data, ensure_ascii=False)}], "isError": False}, None
        except ValueError as cause:
            return {"content": [{"type": "text", "text": str(cause)}], "isError": True}, None
        except Exception as cause:
            return {"content": [{"type": "text", "text": f"内部错误：{type(cause).__name__}: {cause}"}], "isError": True}, None
    return None, {"code": -32601, "message": "不支持的方法"}


def main():
    """Read newline-delimited JSON-RPC. Stdout is reserved for MCP messages."""
    cli = argparse.ArgumentParser()
    cli.add_argument("--describe", action="store_true", help="打印工具声明，不连接后端")
    args = cli.parse_args()
    if args.describe:
        sys.stdout.write(json.dumps({"tools": describe()}, ensure_ascii=False) + "\n")
        return
    initialized = False
    for line in sys.stdin:
        try:
            message = json.loads(line)
        except ValueError:
            response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "JSON 解析失败"}}
        else:
            if not isinstance(message, dict) or message.get("jsonrpc") != "2.0":
                response = {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "JSON-RPC 请求不合法"}}
            elif "id" not in message:
                if message.get("method") == "notifications/initialized":
                    initialized = True
                continue
            else:
                try:
                    result, failure = dispatch(message, initialized)
                    response = {"jsonrpc": "2.0", "id": message["id"], "error" if failure else "result": failure if failure else result}
                except Exception as cause:
                    response = {"jsonrpc": "2.0", "id": message["id"],
                                "error": {"code": -32603, "message": f"内部错误：{type(cause).__name__}: {cause}"}}
        sys.stdout.write(json.dumps(response, ensure_ascii=False) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
