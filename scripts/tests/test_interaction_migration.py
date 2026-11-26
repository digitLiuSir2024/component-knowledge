"""Interaction seed migration and MCP-side validation tests."""
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "skills" / "component-knowledge-maintenance"))


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


migration = load_module("migration", ROOT / "scripts" / "migrate-interaction-seed.py")
coverage = importlib.import_module("coverage_check")


class MigrationTest(unittest.TestCase):
    def test_seed_counts_match_registration_document(self):
        components, interactions, relations, _ = migration.read_seed(ROOT / "database" / "component-interaction-data.sql")
        self.assertEqual(25, len(components))
        self.assertEqual(39, len(interactions))
        self.assertEqual(56, len(relations))

    def test_split_values_keeps_quoted_commas(self):
        values = migration.split_values("'a,b','''c',NULL,10")
        self.assertEqual(["'a,b'", "'''c'", "NULL", "10"], values)

    def test_plan_is_deterministic(self):
        components, interactions, relations, _ = migration.read_seed(ROOT / "database" / "component-interaction-data.sql")
        first = migration.build_plan(components, interactions, relations, "S", "P", "/page")
        second = migration.build_plan(components, interactions, relations, "S", "P", "/page")
        self.assertEqual(first, second)
        self.assertEqual(39, sum(1 for item in first.values() for c in item["changes"] if c["objectType"] == "INTERACTION"))
        self.assertEqual(56, sum(1 for item in first.values() for c in item["changes"] if c["objectType"] == "COMPONENT_RELATION"))

    def test_mapping_roundtrip(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "mapping.json"
            data = {"version": 1, "components": {"a": {"draftId": "d1"}}}
            migration.save_mapping(path, data)
            self.assertEqual(data, migration.load_mapping(path))


class InteractionChangeTest(unittest.TestCase):
    def candidate(self, change_id, object_type, content, object_id=None):
        return {
            "changeId": change_id,
            "objectId": object_id or change_id,
            "objectType": object_type,
            "content": content,
            "evidence": [{"kind": "FACT", "source": "src/a.tsx", "excerpt": "onClick"}],
        }

    def test_valid_group(self):
        changes = [
            self.candidate("i1", "INTERACTION", {"componentId": "c1", "action": "筛选"}),
            self.candidate("r1", "COMPONENT_RELATION",
                           {"interactionId": "i1", "targetComponentId": "c2", "effect": "更新"}),
        ]
        self.assertTrue(coverage.validate_interaction_changes(changes, "c1"))

    def test_relation_references_object_id_not_change_id(self):
        changes = [
            self.candidate("c-i1", "INTERACTION", {"componentId": "c1", "action": "筛选"}, "i1"),
            self.candidate("c-r1", "COMPONENT_RELATION",
                           {"interactionId": "i1", "targetComponentId": "c2", "effect": "更新"}),
        ]
        self.assertTrue(coverage.validate_interaction_changes(changes))
        changes[1]["content"]["interactionId"] = "c-i1"
        with self.assertRaises(ValueError):
            coverage.validate_interaction_changes(changes)

    def test_relation_must_reference_group_member(self):
        changes = [self.candidate("r1", "COMPONENT_RELATION",
                                  {"interactionId": "missing", "targetComponentId": "c2", "effect": "更新"})]
        with self.assertRaises(ValueError):
            coverage.validate_interaction_changes(changes)

    def test_self_relation_rejected(self):
        changes = [
            self.candidate("i1", "INTERACTION", {"componentId": "c1", "action": "筛选"}),
            self.candidate("r1", "COMPONENT_RELATION",
                           {"interactionId": "i1", "targetComponentId": "c1", "effect": "更新"}),
        ]
        with self.assertRaises(ValueError):
            coverage.validate_interaction_changes(changes)

    def test_non_integer_sort_order_rejected(self):
        changes = [self.candidate("i1", "INTERACTION", {"componentId": "c1", "action": "筛选", "sortOrder": "x"})]
        with self.assertRaises(ValueError):
            coverage.validate_interaction_changes(changes)

    def test_temporary_owner_rejected(self):
        changes = [self.candidate("i1", "INTERACTION", {"componentId": "new:component", "action": "筛选"})]
        with self.assertRaises(ValueError):
            coverage.validate_interaction_changes(changes, "c1")


if __name__ == "__main__":
    unittest.main()
