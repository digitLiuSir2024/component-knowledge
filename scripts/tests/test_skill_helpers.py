"""Bridge helper and readback contract tests for the maintenance skill."""
import importlib.util
import os
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILL = ROOT / "skills" / "component-knowledge-maintenance"
sys.path.insert(0, str(SKILL))


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


server = load_module("knowledge_server", SKILL / "server.py")
coverage_check = importlib.import_module("coverage_check")
readback = importlib.import_module("check_draft_readback")
baseline = importlib.import_module("check_submission_baseline")


class ServerHelperTest(unittest.TestCase):
    def test_changes_runtime_validation(self):
        good = [{
            "changeId": "c1", "operation": "UPDATE", "objectType": "COMPONENT", "objectId": "id-1",
            "evidence": [{"kind": "FACT", "source": "src/a.tsx", "excerpt": "line 1"}],
        }]
        server.validate_changes_runtime(good)
        bad = [dict(good[0], changeId="")]
        with self.assertRaises(ValueError):
            server.validate_changes_runtime(bad)
        bad = [dict(good[0], operation="DROP")]
        with self.assertRaises(ValueError):
            server.validate_changes_runtime(bad)

    def test_submit_target_must_exist_in_plan(self):
        with self.assertRaises(ValueError):
            server.ensure_submit_target({"componentId": "OTHER"}, {"components": {"C1": {"request": {}}}})
        server.ensure_submit_target({"componentId": "C1"}, {"components": {"C1": {"request": {}}}})

    def test_approval_token_is_plan_bound(self):
        first = server.issue_approval_token("digest", {"a": 1})
        self.assertEqual(first, server.issue_approval_token("digest", {"a": 1}))
        self.assertNotEqual(first, server.issue_approval_token("digest", {"a": 2}))

    def test_describe_requires_approval_token(self):
        tools = {tool["name"]: tool for tool in server.describe()}
        for name in ("submit_knowledge_draft", "submit_interaction_candidates", "submit_initial_knowledge_draft"):
            self.assertIn("approvalToken", tools[name]["inputSchema"]["required"])

    def test_request_timeout_from_environment(self):
        previous = os.environ.get("KNOWLEDGE_MCP_TIMEOUT")
        try:
            os.environ["KNOWLEDGE_MCP_TIMEOUT"] = "25"
            self.assertEqual(25, server.request_timeout())
            os.environ["KNOWLEDGE_MCP_TIMEOUT"] = "bad"
            self.assertEqual(10, server.request_timeout())
        finally:
            if previous is None:
                os.environ.pop("KNOWLEDGE_MCP_TIMEOUT", None)
            else:
                os.environ["KNOWLEDGE_MCP_TIMEOUT"] = previous


class ReadbackContractTest(unittest.TestCase):
    def request(self, **overrides):
        value = {
            "systemCode": "S", "projectCode": "P", "pagePath": "/page.html", "componentId": "C1",
            "requestId": "r1", "baseVersionId": None, "expectedPendingDraft": {},
            "changes": [{
                "changeId": "c1", "operation": "UPDATE", "objectType": "COMPONENT", "objectId": "db-1",
                "content": {"componentId": "C1"}, "evidence": [{"kind": "FACT", "source": "s", "excerpt": "e"}],
            }],
        }
        value.update(overrides)
        return value

    def draft(self, request, **overrides):
        value = {
            "id": "d1", "status": "DRAFT", "origin": "SESSION", "requestId": "r1",
            "systemCode": "S", "projectCode": "P", "baseVersionId": None, "issues": [],
            "identity": request, "changes": request["changes"],
        }
        value.update(overrides)
        return value

    def test_readback_checks_expected_pending_draft_and_origin(self):
        request = self.request()
        result = readback.verify(request, self.draft(request), "d1")
        self.assertEqual(["c1"], result["verifiedChanges"])
        with self.assertRaises(ValueError):
            readback.verify(request, self.draft(request, origin="MIGRATION"), "d1")
        with self.assertRaises(ValueError):
            bad = self.draft(request)
            bad["identity"] = dict(request, expectedPendingDraft={"id": "x", "revision": 1})
            readback.verify(request, bad, "d1")

    def test_baseline_snapshot_ignores_transient_fields(self):
        left = {"id": "d1", "revision": 1, "status": "DRAFT", "origin": "SESSION",
                "updatedAt": "2026-01-01T00:00:00Z", "identity": {"requestId": "r1"}}
        right = dict(left, updatedAt="2026-01-02T00:00:00Z")
        self.assertEqual(baseline.draft_snapshot(left), baseline.draft_snapshot(right))

    def test_extractor_has_no_python39_only_api(self):
        source = (SKILL / "extract_review_clues.py").read_text(encoding="utf-8")
        self.assertNotIn("removeprefix", source)
        self.assertNotIn("removesuffix", source)

    def test_page_identity_helper(self):
        request = self.request()
        self.assertEqual({"systemCode": "S", "projectCode": "P", "pagePath": "/page.html"},
                         baseline.page_identity(request))


if __name__ == "__main__":
    unittest.main()
