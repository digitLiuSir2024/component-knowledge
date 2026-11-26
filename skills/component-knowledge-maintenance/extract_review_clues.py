"""Extract review clues from a component registry and explicit source files."""
import argparse
import json
from pathlib import Path
import re

from coverage_check import scope


SERIES = re.compile(r"const\s+\w+\s*=\s*\[((?:\s*\[[^\[\]]+\]\s*,?)+)\s*\]\s*as const", re.S)
SERIES_FIELD = re.compile(
    r"""\[\s*['"]([A-Za-z_]\w*)['"]\s*,\s*['"][^'"\n]+['"]\s*,\s*['"]#[0-9a-fA-F]{3,8}['"]\s*\]"""
)
RATE = re.compile(
    r"\.(\w+)\((?:\w+\.)?rate\("
    r"(?:\w+\.(get\w+)\(\)|(\w+)),\s*(\w+)\)\)"
)
GETTER_MAPPING = re.compile(r"\.(\w+)\(\w+\.(get\w+)\(\)\)")
ASSIGNMENT = re.compile(r"\blong\s+(\w+)\s*=\s*\w+\.(get\w+)\(\);")


def location(path, source, offset):
    return f"{path}:{source.count(chr(10), 0, offset) + 1}"


def enrich_rates(metrics, path):
    source = path.read_text()
    getters = {getter: field for field, getter in GETTER_MAPPING.findall(source)}
    variables = dict(ASSIGNMENT.findall(source))
    for match in RATE.finditer(source):
        rate, getter, numerator, denominator = match.groups()
        if rate not in metrics:
            continue
        for dependency in (getter or variables.get(numerator), variables.get(denominator)):
            if dependency in getters:
                metrics.setdefault(getters[dependency], location(path, source, match.start()))


DTO_FIELD = re.compile(
    r"(?m)^[ \t]*(?P<annotations>(?:@\w+[^\n]*\n[ \t]*)*)"
    r"private\s+(?P<type>\w+(?:<\w+>)?)\s+(?P<name>\w+)\s*(?:=|;)"
)


def dto_fields(path, prefix, active=()):
    if path in active:
        raise ValueError(f"DTO 循环引用，需人工盘点：{path}")
    source = path.read_text()
    fields = {}
    for match in DTO_FIELD.finditer(source):
        field_type = match["type"]
        nested_type = field_type.removeprefix("List<").removesuffix(">")
        nested_path = path.with_name(nested_type + ".java")
        unwrapped = "@JsonUnwrapped" in match["annotations"]
        key = f"{prefix}.{match['name']}"
        if not unwrapped:
            fields[key] = location(path, source, match.start("name"))
        if nested_path.is_file():
            child_prefix = prefix if unwrapped else key + ("[]" if field_type.startswith("List<") else "")
            fields.update(dto_fields(nested_path, child_prefix, (*active, path)))
        elif unwrapped:
            raise ValueError(f"无法展开 DTO 字段：{key}")
    return fields


def component_tags(source):
    return re.finditer(r"<([A-Z]\w*)\b([^<]*?)\s*/>", source, re.S)


def state_links(snapshot, project_root, page_path, target):
    page = page_path.read_text()
    tracks = {}
    for control in component_tags(page):
        control_id, props = control.groups()
        if control_id not in snapshot["components"]:
            continue
        for callback in re.finditer(r"\bon\w+=\{(\w+)\}", props):
            trace_state_links(snapshot, project_root, page_path, page, control, callback[1], target, tracks)
    return tracks


def trace_state_links(snapshot, project_root, page_path, page, control, setter, target, tracks):
    control_id = control[1]
    state = re.search(r"\[\s*(\w+)\s*,\s*" + re.escape(setter) + r"\s*\]\s*=\s*useState", page)
    if not state:
        return
    # Follow named request results written into a state setter and handed to a child.
    for request in re.finditer(r"\b(?:\w+\.)*\w+\(" + re.escape(state[1]) + r"\)\.then\(\s*\((\w+)\)", page):
        effect_end = page.find("}, [", request.end())
        if effect_end < 0 or not re.search(r"\b" + re.escape(state[1]) + r"\b", page[effect_end:page.find("]", effect_end)]):
            continue
        for store in re.finditer(r"\b(\w+)\(" + re.escape(request[1]) + r"\)", page[request.end():effect_end]):
            result = re.search(r"\[\s*(\w+)\s*,\s*" + re.escape(store[1]) + r"\s*\]\s*=\s*useState", page)
            if not result:
                continue
            for parent in component_tags(page):
                parent_id, parent_props = parent.groups()
                prop = re.search(r"\b(\w+)=\{" + re.escape(result[1]) + r"\}", parent_props)
                if not prop or parent_id not in snapshot["components"]:
                    continue
                target_matches = parent_id == target
                if not target_matches and parent_id in snapshot["components"][target]["assemblyParents"]:
                    parent_path = project_root / snapshot["components"][parent_id]["source"]
                    target_matches = any(
                        child[1] == target and re.search(r"=\{" + re.escape(prop[1]) + r"\}", child[2])
                        for child in component_tags(parent_path.read_text())
                    )
                if target_matches and control_id != target:
                    tracks[f"{control_id}:{state[1]}:{target}"] = (
                        f"{location(page_path, page, control.start())}; "
                        f"{location(page_path, page, request.start())}; "
                        f"{location(page_path, page, parent.start())}"
                    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, required=True)
    parser.add_argument("--project-root", type=Path, required=True)
    parser.add_argument("--root", required=True)
    parser.add_argument("--rate-source", type=Path, help="Java source containing a rate calculation and metric accessors")
    parser.add_argument("--check-plan", type=Path, help="Verify extracted keys exist in a saved coverage plan")
    parser.add_argument("--page-source", type=Path, help="React source with local state and component assembly")
    parser.add_argument("--observations", type=Path,
                        help="JSON array of observed component interactions from a project runtime probe")
    parser.add_argument("--dto", nargs=3, action="append", default=[],
                        metavar=("INTERFACE_KEY", "REQUEST_DTO", "RESPONSE_DTO"))
    args = parser.parse_args()
    dto_inputs = {key: (Path(request), Path(response)) for key, request, response in args.dto}
    if len(dto_inputs) != len(args.dto):
        parser.error("接口 DTO 参数重复")
    snapshot = json.loads(args.snapshot.read_text())
    discovery = {}
    used_dtos = set()
    for entry in scope(snapshot["pageIdentity"], args.root, snapshot):
        path = args.project_root / entry["source"]
        source = path.read_text()
        metrics = {}
        for series in SERIES.finditer(source):
            for field in SERIES_FIELD.finditer(series[1]):
                metrics[field[1]] = location(path, source, series.start(1) + field.start())
        if args.rate_source:
            enrich_rates(metrics, args.rate_source)
        discovery[entry["componentId"]] = {
            "metrics": metrics,
            "interactions": state_links(snapshot, args.project_root, args.page_source, entry["componentId"])
            if args.page_source else {},
            "interfaces": {
                item: str(args.snapshot) for item in entry["requiredItems"]
                if item.startswith(("direct:", "upstream:"))
            },
        }
        for key in discovery[entry["componentId"]]["interfaces"].keys() & dto_inputs.keys():
            request, response = dto_inputs[key]
            row = discovery[entry["componentId"]]
            row.setdefault("interfaceFields", {})[key] = {
                **dto_fields(request, "request"), **dto_fields(response, "response.data"),
            }
            used_dtos.add(key)
    if args.observations:
        observations = json.loads(args.observations.read_text())
        if not isinstance(observations, list):
            parser.error("运行证据须为数组")
        for observation in observations:
            names = ("key", "sourceComponent", "targetComponent", "triggerEvidence", "effectEvidence")
            if not isinstance(observation, dict) or any(
                not isinstance(observation.get(name), str) or not observation[name].strip() for name in names
            ):
                parser.error("运行证据缺少稳定键、组件身份或触发与响应证据")
            source_id, target_id = observation["sourceComponent"], observation["targetComponent"]
            if source_id == target_id or not {source_id, target_id} <= snapshot["components"].keys():
                parser.error("运行证据的组件身份与快照不一致")
            for component in (source_id, target_id):
                if component in discovery:
                    key = f"runtime:{observation['key']}"
                    interactions = discovery[component]["interactions"]
                    if key in interactions:
                        parser.error(f"运行证据键重复：{key}")
                    interactions[key] = f"{observation['triggerEvidence']}; {observation['effectEvidence']}"
    if dto_inputs.keys() - used_dtos:
        parser.error("DTO 对应接口不在提取范围：" + ", ".join(sorted(dto_inputs.keys() - used_dtos)))
    if args.check_plan:
        plan = json.loads(args.check_plan.read_text())
        if plan.get("root") != args.root or plan.get("registrySnapshot") != snapshot:
            parser.error("计划与提取输入的范围或快照不一致")
        for component, sections in discovery.items():
            for section, clues in sections.items():
                recorded = plan.get("discovery", {}).get(component, {}).get(section, {})
                if section == "interfaceFields":
                    for interface, fields in clues.items():
                        missing_fields = fields.keys() - recorded.get(interface, {}).keys()
                        if missing_fields:
                            parser.error(f"{component}/{interface} 缺少 DTO 字段线索：{', '.join(sorted(missing_fields))}")
                    continue
                missing = set(clues) - set(recorded)
                if missing:
                    parser.error(f"{component}/{section} 缺少源码线索：{', '.join(sorted(missing))}")
    print(json.dumps(discovery, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
