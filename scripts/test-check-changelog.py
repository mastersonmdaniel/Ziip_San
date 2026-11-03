#!/usr/bin/env python3
"""Testes do check-changelog.py (P0.41). Stdlib + PyYAML.

    python3 scripts/test-check-changelog.py
"""
import importlib.util
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_changelog", HERE / "check-changelog.py")
MOD = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(MOD)


def tmp_dir(files: dict) -> Path:
    d = Path(tempfile.mkdtemp())
    for name, text in files.items():
        (d / name).write_text(text, encoding="utf-8")
    return d


GOOD = """version: "1.2.0"
date: "2026-11-20"
entries:
  - type: security
    title: "Corrige vazamento de token"
    commits: ["abc1234567"]
"""


class TestChangelog(unittest.TestCase):
    def test_valid_passes(self):
        d = tmp_dir({"1.2.0.yaml": GOOD})
        errors, _ = MOD.validate_file(d / "1.2.0.yaml")
        self.assertEqual(errors, [])

    def test_filename_mismatch_fails(self):
        d = tmp_dir({"9.9.9.yaml": GOOD})
        errors, _ = MOD.validate_file(d / "9.9.9.yaml")
        self.assertTrue(any("diverge" in e for e in errors))

    def test_bad_type_fails(self):
        d = tmp_dir({"1.2.0.yaml": GOOD.replace("security", "novidade")})
        errors, _ = MOD.validate_file(d / "1.2.0.yaml")
        self.assertTrue(any("type invalido" in e for e in errors))

    def test_marked_commit_without_entry_blocks(self):
        d = tmp_dir({"1.2.0.yaml": GOOD})
        _, doc = MOD.validate_file(d / "1.2.0.yaml")
        commits = Path(tempfile.mktemp())
        commits.write_text("deadbeef1234 [security] corrige algo\n", encoding="utf-8")
        errors = MOD.check_release_rule([doc], commits)
        self.assertEqual(len(errors), 1)
        self.assertIn("deadbeef1234", errors[0])

    def test_marked_commit_with_entry_passes(self):
        d = tmp_dir({"1.2.0.yaml": GOOD})
        _, doc = MOD.validate_file(d / "1.2.0.yaml")
        commits = Path(tempfile.mktemp())
        commits.write_text("abc1234567 [security] corrige vazamento\n", encoding="utf-8")
        self.assertEqual(MOD.check_release_rule([doc], commits), [])

    def test_unmarked_commit_ignored(self):
        d = tmp_dir({"1.2.0.yaml": GOOD})
        _, doc = MOD.validate_file(d / "1.2.0.yaml")
        commits = Path(tempfile.mktemp())
        commits.write_text("ffff11112222 feat: coisa nova sem marca\n", encoding="utf-8")
        self.assertEqual(MOD.check_release_rule([doc], commits), [])

    def test_wrong_type_does_not_count(self):
        d = tmp_dir({"1.2.0.yaml": GOOD})
        _, doc = MOD.validate_file(d / "1.2.0.yaml")
        commits = Path(tempfile.mktemp())
        commits.write_text("abc1234567 [legislation] muda regra legal\n", encoding="utf-8")
        errors = MOD.check_release_rule([doc], commits)
        self.assertEqual(len(errors), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
