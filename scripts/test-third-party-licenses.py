#!/usr/bin/env python3
"""Testes dos scripts de licencas (P0.40). Stdlib apenas.

    python3 scripts/test-third-party-licenses.py
"""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), HERE / name)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


GEN = load("generate-third-party-licenses.py")
CHECK = load("check-third-party-licenses.py")

ALLOWED = HERE.parent / "third-party-licenses-allowed.json"


def tmp_json(obj):
    fd, path = tempfile.mkstemp(suffix=".json")
    Path(path).write_text(json.dumps(obj), encoding="utf-8")
    return Path(path)


class TestLicenses(unittest.TestCase):
    def test_allowed_passes(self):
        f = tmp_json({"components": [
            {"name": "spring-core", "version": "6.0", "licenses": ["Apache-2.0"]},
            {"name": "hibernate", "version": "7.0", "licenses": ["LGPL-2.1-or-later"]},
        ]})
        self.assertEqual(CHECK.check(f, ALLOWED), [])

    def test_disallowed_blocks(self):
        f = tmp_json({"components": [
            {"name": "algo-gpl", "version": "1.0", "licenses": ["GPL-3.0-only"]},
        ]})
        failures = CHECK.check(f, ALLOWED)
        self.assertEqual(len(failures), 1)
        self.assertIn("GPL-3.0-only", failures[0])

    def test_dual_license_passes_on_any_allowed(self):
        # Jakarta: EPL-2.0 (permitida) ou GPL-2.0-with-classpath-exception (fora).
        # Regra do licenciamento dual: basta uma permitida.
        f = tmp_json({"components": [
            {"name": "jakarta-x", "version": "1.0", "licenses": ["EPL-2.0", "GPL-2.0-with-classpath-exception"]},
        ]})
        self.assertEqual(CHECK.check(f, ALLOWED), [])

    def test_missing_license_blocks(self):
        f = tmp_json({"components": [{"name": "sem-licenca", "version": "1.0", "licenses": []}]})
        failures = CHECK.check(f, ALLOWED)
        self.assertEqual(len(failures), 1)
        self.assertIn("SEM LICENCA", failures[0])

    def test_generate_override_fills_only_empty(self):
        sbom = tmp_json({"components": [
            {"name": "gap-lib", "version": "1.0", "licenses": []},
            {"name": "has-lib", "version": "1.0", "licenses": [{"license": {"id": "MIT"}}]},
        ]})
        ov = tmp_json({"overrides": [
            {"name": "gap-lib", "version": "1.0", "licenses": ["Apache-2.0"], "reason": "x"},
            {"name": "has-lib", "version": "1.0", "licenses": ["GPL-3.0-only"], "reason": "nao deve sobrescrever"},
        ]})
        out = Path(tempfile.mkdtemp()) / "out.json"
        import sys
        old = sys.argv
        sys.argv = ["gen", "--sbom", str(sbom), "--overrides", str(ov), "--out", str(out)]
        try:
            GEN.main()
        finally:
            sys.argv = old
        data = json.loads(out.read_text(encoding="utf-8"))
        by_name = {c["name"]: c for c in data["components"]}
        self.assertEqual(by_name["gap-lib"]["licenses"], ["Apache-2.0"])
        self.assertEqual(by_name["has-lib"]["licenses"], ["MIT"])

    def test_generate_normalizes_names_and_merges(self):
        sbom = tmp_json({"components": [
            {"name": "a-lib", "version": "1.0",
             "licenses": [{"license": {"name": "Apache License, Version 2.0"}}]},
            {"name": "a-lib", "version": "1.0", "licenses": [{"license": {"id": "MIT"}}]},
            {"name": "b-lib", "version": "2.0", "licenses": []},
        ]})
        out = Path(tempfile.mkdtemp()) / "out.json"
        # chama a logica via CLI simulada
        import sys
        old = sys.argv
        sys.argv = ["gen", "--sbom", str(sbom), "--out", str(out)]
        try:
            GEN.main()
        finally:
            sys.argv = old
        data = json.loads(out.read_text(encoding="utf-8"))
        by_name = {c["name"]: c for c in data["components"]}
        self.assertEqual(by_name["a-lib"]["licenses"], ["Apache-2.0", "MIT"])
        self.assertEqual(by_name["b-lib"]["licenses"], [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
