"""Bridge helper and readback contract tests for the maintenance skill."""
import importlib.util
import os
import sys
import tempfile
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

    def approval_plan(self):
        return {
            "registrySnapshot": {"pageIdentity": {"systemCode": "S", "projectCode": "P", "pagePath": "/p"}},
            "components": {
                "C1": {"request": {"requestId": "r1", "changes": [{"changeId": "c1"}]}},
                "C2": {"request": {"requestId": "r2", "changes": [{"changeId": "c2"}]}},
            },
        }

    def token_page(self):
        return {"systemCode": "S", "projectCode": "P", "pagePath": "/p"}

    def test_approval_token_binding(self):
        plan = self.approval_plan()
        token = server.issue_approval_token("digest", plan)
        self.assertNotEqual(token, server.issue_approval_token("digest", plan))
        server.verify_approval_token(token, digest="digest", plan=plan, component="C1",
                                     page=self.token_page(), request_id="r1", selected_change_ids=["c1"])
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, digest="other")
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, plan={"changed": True})
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, component="C9")
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, component="C1", request_id="r2")
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, component="C1", selected_change_ids=["c9"])

    def test_approval_token_consumed_per_publish_target(self):
        plan = self.approval_plan()
        token = server.issue_approval_token("digest", plan)
        server.verify_approval_token(token, component="C1", page=self.token_page(),
                                     request_id="r1", selected_change_ids=["c1"],
                                     publish_target=("C1", "d1"))
        server.consume_approval_target(token, ("C1", "d1"))
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, component="C1", page=self.token_page(),
                                         request_id="r1", selected_change_ids=["c1"],
                                         publish_target=("C1", "d1"))
        server.verify_approval_token(token, component="C2", page=self.token_page(),
                                     request_id="r2", selected_change_ids=["c2"],
                                     publish_target=("C2", "d2"))
        server.consume_approval_target(token, ("C2", "d2"))
        with self.assertRaises(ValueError):
            server.verify_approval_token(token, component="C2", page=self.token_page(),
                                         request_id="r2", selected_change_ids=["c2"],
                                         publish_target=("C2", "d2"))

    def test_approval_token_expiry(self):
        token = server.issue_approval_token("digest", self.approval_plan())
        server._APPROVAL_TOKENS[token]["issuedAt"] -= server.APPROVAL_TTL_SECONDS + 1
        with self.assertRaises(ValueError):
            server.verify_approval_token(token)
        with self.assertRaises(ValueError):
            server.consume_approval_target(token, ("C1", "d1"))

    def test_describe_required_fields(self):
        tools = {tool["name"]: tool for tool in server.describe()}
        for name in ("submit_knowledge_draft", "submit_interaction_candidates", "submit_initial_knowledge_draft",
                     "publish_approved_draft"):
            self.assertIn("approvalToken", tools[name]["inputSchema"]["required"])
        for name in ("list_knowledge_drafts", "list_component_versions"):
            required = tools[name]["inputSchema"]["required"]
            self.assertIn("pagePath", required)
            self.assertIn("componentId", required)
        self.assertIn("requestId", tools["publish_approved_draft"]["inputSchema"]["required"])

    def test_admin_write_gate(self):
        previous = os.environ.get("KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES")
        try:
            os.environ.pop("KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES", None)
            self.assertFalse(server.admin_writes_enabled())
            with self.assertRaises(ValueError):
                server.call_tool("upsert_legacy_record", {
                    "entity": "PAGE", "row": {"id": "p1"}, "confirmWrite": True,
                })
            with self.assertRaises(ValueError):
                server.call_tool("upsert_legacy_record", {
                    "entity": "COMPONENT_INTERACTION", "row": {"id": "i1"}, "confirmWrite": True,
                })
            os.environ["KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES"] = "true"
            self.assertTrue(server.admin_writes_enabled())
            with self.assertRaises(ValueError):
                server.call_tool("delete_legacy_record", {
                    "entity": "COMPONENT_RELATION", "keys": {"id": "r1"}, "confirmWrite": True,
                })
        finally:
            if previous is None:
                os.environ.pop("KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES", None)
            else:
                os.environ["KNOWLEDGE_MCP_ENABLE_ADMIN_WRITES"] = previous

    def test_publish_rejects_unbound_token(self):
        token = server.issue_approval_token("digest", self.approval_plan())
        base = {
            "systemCode": "S", "projectCode": "P", "pagePath": "/p", "componentId": "C1",
            "requestId": "r1", "draftId": "d1", "revision": 1,
            "selectedChangeIds": ["c1"], "confirmPublish": True, "approvalToken": token,
        }
        server.verify_approval_token(token, component="C1", page={"systemCode": "S", "projectCode": "P", "pagePath": "/p"},
                                     request_id="r1", selected_change_ids=["c1"])
        with self.assertRaises(ValueError):
            server.call_tool("publish_approved_draft", dict(base, requestId="other"))
        with self.assertRaises(ValueError):
            server.call_tool("publish_approved_draft", dict(base, componentId="C2"))
        missing = dict(base)
        missing.pop("approvalToken")
        with self.assertRaises(ValueError):
            server.call_tool("publish_approved_draft", missing)

    def test_initial_draft_requires_interfaces(self):
        args = {
            "systemCode": "S", "projectCode": "P", "pagePath": "/p", "componentId": "C1",
            "requestId": "r1", "sourceRevision": "rev", "changes": [],
            "coveragePlan": {"components": {}}, "coverageDigest": "d",
            "expectedPendingDraft": {}, "approvalToken": "t",
        }
        with self.assertRaises(ValueError):
            server.call_tool("submit_initial_knowledge_draft", args)

    def test_new_tools_have_correct_hints(self):
        tools = {tool["name"]: tool for tool in server.describe()}
        self.assertTrue(tools["read_legacy_record"]["annotations"]["readOnlyHint"])
        self.assertFalse(tools["upsert_legacy_record"]["annotations"]["readOnlyHint"])
        self.assertTrue(tools["delete_legacy_record"]["annotations"]["destructiveHint"])
        self.assertFalse(tools["publish_approved_draft"]["annotations"]["readOnlyHint"])
        self.assertTrue(tools["discard_knowledge_draft"]["annotations"]["destructiveHint"])

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


class ContractDocumentTest(unittest.TestCase):
    def test_tool_declarations_match_document(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        checker = importlib.import_module("check_mcp_contract")
        self.assertEqual([], checker.verify())

    def test_checker_detects_document_drift(self):
        sys.path.insert(0, str(ROOT / "scripts"))
        checker = importlib.import_module("check_mcp_contract")
        original = checker.DOC.read_text(encoding="utf-8")
        old = "| `list_knowledge_drafts` | systemCode, projectCode, pagePath, componentId |"
        new = "| `list_knowledge_drafts` | systemCode, projectCode |"
        self.assertIn(old, original)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.md"
            path.write_text(original.replace(old, new, 1), encoding="utf-8")
            previous = checker.DOC
            checker.DOC = path
            try:
                errors = checker.verify()
            finally:
                checker.DOC = previous
        self.assertTrue(any("list_knowledge_drafts" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
