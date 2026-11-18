#!/usr/bin/env python3
"""Explicitly triggered knowledge reading and draft submission over MCP stdio."""

import argparse
import json
import os
import sys
from urllib import error, parse, request

import coverage_check

PROTOCOLS = ("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
IDENTITY = ("systemCode", "projectCode", "pagePath", "componentId")
SCOPE = ("systemCode", "projectCode")
OBJECT = (*SCOPE, "objectType", "objectId")
TOOLS = {
    "knowledge_coverage_scope": (None, (*SCOPE, "pagePath", "root"), ("registrySnapshot",), "从注册表生成整理清单"),
    "validate_knowledge_coverage": (None, (*SCOPE, "pagePath", "coveragePlan"), (), "检查整批覆盖和候选，返回审批汇总所用摘要"),

    "read_component_knowledge": ("context", IDENTITY, ("interfaces",), "读取当前组件基准及完整知识，不发布"),
    "search_knowledge_objects": ("objects/search", SCOPE, ("objectType", "keyword", "offset", "limit"), "搜索同项目对象，结果不替代明确版本全文"),
    "read_knowledge_object_version": ("objects/version", (*OBJECT, "versionId"), (), "读取指定对象版本完整正文，不回退最新版本"),
    "list_knowledge_object_versions": ("objects/history", OBJECT, ("offset", "limit"), "列出对象历史版本"),
    "submit_knowledge_draft": ("drafts/create", (*IDENTITY, "requestId", "baseVersionId", "changes", "coveragePlan", "coverageDigest"), ("expectedPendingDraft",), "仅提交会话候选草稿，必须由用户到组件详情页确认"),
    "submit_initial_knowledge_draft": ("baseline", (*IDENTITY, "requestId", "sourceRevision", "interfaces", "changes", "coveragePlan", "coverageDigest"), ("expectedPendingDraft",), "提交首次基线候选草稿，不发布；接口列表必须与读取时一致"),
    "read_knowledge_draft": ("drafts/read", (*SCOPE, "draftId"), (), "读取草稿、审核地址与发布结果"),
}


def property_schema(name):
    """Return the actual input shapes accepted by the fixed HTTP bridge."""
    if name in ("offset", "limit"):
        return {"type": "integer", "minimum": 0 if name == "offset" else 1, **({"maximum": 100} if name == "limit" else {})}
    if name == "interfaces":
        return {"type": "array", "items": {"type": "object", "properties": {"method": {"type": "string"}, "address": {"type": "string"}}, "required": ["method", "address"], "additionalProperties": False}}
    if name in ("coveragePlan", "registrySnapshot"):
        return {"type": "object"}
    if name == "expectedPendingDraft":
        return {"oneOf": [
            {"type": "object", "maxProperties": 0},
            {"type": "object", "properties": {"id": {"type": "string", "minLength": 1},
             "revision": {"type": "integer", "minimum": 1, "maximum": 2147483647}},
             "required": ["id", "revision"], "additionalProperties": False},
        ]}
    if name == "changes":
        fields = {key: {"type": "string"} for key in ("changeId", "operation", "objectType", "objectId")}
        fields.update({key: {"type": ["string", "null"]} for key in ("baseObjectVersionId", "targetVersionId", "confirmationStatus", "atomicGroupId")})
        fields["content"] = {"type": ["object", "null"]}
        fields["evidence"] = {"type": "array", "items": {"type": "object", "properties": {key: {"type": "string"} for key in ("kind", "source", "excerpt")}, "required": ["kind", "source", "excerpt"], "additionalProperties": False}}
        return {"type": "array", "items": {"type": "object", "properties": fields, "required": list(fields), "additionalProperties": False}}
    return {"type": "string", "minLength": 1}


def describe():
    """Only these named tools can reach the backend."""
    result = []
    for name, (_, required, optional, description) in TOOLS.items():
        result.append({"name": name, "description": description, "inputSchema": {"type": "object", "properties": {key: property_schema(key) for key in (*required, *optional)}, "required": list(required), "additionalProperties": False}, "annotations": {"readOnlyHint": not name.startswith("submit_"), "destructiveHint": False}})
    return result


def call_tool(name, arguments):
    """Forward a validated named operation; never accept a path from the model."""
    if name not in TOOLS or not isinstance(arguments, dict):
        raise ValueError("未知工具或参数格式不合法")
    path, required, optional, _ = TOOLS[name]
    if set(arguments) - set((*required, *optional)):
        raise ValueError("包含工具未声明的参数")
    for key in required:
        value = arguments.get(key)
        if key == "interfaces" and key in arguments:
            continue
        if key == "coveragePlan" and isinstance(value, dict):
            continue
        if key == "changes":
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
    if "interfaces" in arguments:
        interfaces = arguments["interfaces"]
        if not isinstance(interfaces, list) or any(
            not isinstance(item, dict)
            or set(item) != {"method", "address"}
            or any(not isinstance(item[field], str) or not item[field].strip() for field in ("method", "address"))
            for item in interfaces
        ):
            raise ValueError("interfaces 必须为 method、address 非空的对象数组")
    if name == "knowledge_coverage_scope":
        return coverage_check.scope(arguments, arguments["root"], arguments.get("registrySnapshot"))
    if name == "validate_knowledge_coverage":
        return {"coverageDigest": coverage_check.validate(arguments, arguments["coveragePlan"])}
    body = dict(arguments)
    if name.startswith("submit_"):
        plan = body.pop("coveragePlan", None)
        digest = body.pop("coverageDigest", None)
        actual = coverage_check.validate(body, plan)
        if digest != actual or plan["components"][body["componentId"]]["request"] != body:
            raise ValueError("实际请求与已核对的整理清单不一致")
    base = os.environ.get("KNOWLEDGE_API_BASE", "").rstrip("/")
    address = parse.urlparse(base)
    if address.scheme not in ("http", "https") or not address.netloc or address.query or address.fragment or address.username:
        raise ValueError("请配置合法的 KNOWLEDGE_API_BASE，不在地址中包含凭据")
    if name == "submit_knowledge_draft":
        body["origin"] = "SESSION"
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = request.Request(base + "/api/v1/knowledge/maintenance/" + path, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with request.urlopen(req) as response:
            envelope = json.load(response)
    except (error.URLError, OSError, ValueError) as cause:
        raise ValueError("知识服务请求失败，请检查连接与服务配置") from cause
    if not isinstance(envelope, dict) or envelope.get("code") != 200 or envelope.get("success") is not True:
        code = envelope.get("code") if isinstance(envelope, dict) else None
        raise ValueError(f"知识服务拒绝请求，业务码 {code}；请在页面检查基准或草稿状态")
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
                result, failure = dispatch(message, initialized)
                response = {"jsonrpc": "2.0", "id": message["id"], "error" if failure else "result": failure if failure else result}
        sys.stdout.write(json.dumps(response, ensure_ascii=False) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
