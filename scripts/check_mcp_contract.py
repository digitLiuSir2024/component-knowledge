#!/usr/bin/env python3
"""Cross-check MCP tool declarations against the contract doc and backend DTOs/routes."""
import importlib.util
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKILL = ROOT / "skills" / "component-knowledge-maintenance"
DOC = SKILL / "references" / "contracts" / "knowledge-ingestion.md"
CONTROLLERS = ROOT / "src" / "main" / "java" / "com" / "cmbchina" / "adapter" / "knowledge" / "web"
DTO_DIR = ROOT / "src" / "main" / "java" / "com" / "cmbchina" / "application" / "knowledge" / "api" / "dto" / "maintenance"
LOCAL_FIELDS = {"coveragePlan", "coverageDigest", "approvalToken", "confirmWrite", "confirmPublish", "confirmDiscard"}
EXPECTED_TOOL_COUNT = 19


def load_server():
    spec = importlib.util.spec_from_file_location("knowledge_server_contract", SKILL / "server.py")
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(SKILL))
    spec.loader.exec_module(module)
    return module


def document_rows():
    text = DOC.read_text(encoding="utf-8")
    section = text.split("## 工具清单", 1)[1].split("## ", 1)[0]
    rows = {}
    for line in section.splitlines():
        match = re.match(r"\|\s*`([a-z_]+)`\s*\|", line)
        if match:
            rows[match.group(1)] = [part.strip() for part in line.split("|")]
    return rows


def java_routes():
    routes = set()
    for source in CONTROLLERS.glob("*.java"):
        text = source.read_text(encoding="utf-8")
        base_match = re.search(r'@RequestMapping\("([^"]+)"\)', text)
        base = base_match.group(1) if base_match else ""
        for post in re.findall(r'@PostMapping\("([^"]+)"\)', text):
            routes.add((base + post).replace("//", "/"))
    return routes


def dto_fields(name):
    text = (DTO_DIR / name).read_text(encoding="utf-8")
    return set(re.findall(r"private\s+[\w<>,. ]+\s+(\w+)\s*;", text))


def verify():
    server = load_server()
    rows = document_rows()
    routes = java_routes()
    maintenance_fields = dto_fields("MaintenanceRequest.java")
    legacy_fields = dto_fields("LegacyCrudRequest.java")
    errors = []
    if len(server.TOOLS) != EXPECTED_TOOL_COUNT:
        errors.append(f"工具数应为 {EXPECTED_TOOL_COUNT}，实际 {len(server.TOOLS)}")
    if len(rows) != len(server.TOOLS):
        errors.append(f"文档工具行数 {len(rows)} 与实际工具数 {len(server.TOOLS)} 不一致")
    for name, (path, required, optional, _) in server.TOOLS.items():
        cells = rows.get(name)
        if cells is None:
            errors.append(f"文档缺少工具行：{name}")
            continue
        if len(cells) < 6:
            errors.append(f"{name} 文档表格列数不足")
            continue
        doc_required = {part.split("=")[0].strip() for part in cells[2].split(",") if part.strip()}
        if doc_required != set(required):
            errors.append(f"{name} 文档必填列与工具声明不一致：{cells[2]}")
        if path:
            expected_route = "/api/v1/knowledge/maintenance/" + path
            if expected_route not in routes:
                errors.append(f"{name} 后端缺少路由：{expected_route}")
            fields = legacy_fields if path.startswith("legacy/") else maintenance_fields
            forwarded = (set(required) | set(optional)) - LOCAL_FIELDS
            for field in sorted(forwarded - fields):
                errors.append(f"{name} 参数 {field} 不在后端 DTO 中")
            if f"`/{path}`" not in "".join(cells):
                errors.append(f"{name} 文档后端端点与声明不一致：{path}")
    for name in rows:
        if name not in server.TOOLS:
            errors.append(f"文档列出未知工具：{name}")
    if "interfaces" not in server.TOOLS["submit_initial_knowledge_draft"][1]:
        errors.append("首次基线工具缺少 interfaces 必填声明")
    return errors


def main():
    errors = verify()
    if errors:
        print("MCP 契约检查失败：")
        for error in errors:
            print("- " + error)
        return 1
    print("MCP 契约检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
