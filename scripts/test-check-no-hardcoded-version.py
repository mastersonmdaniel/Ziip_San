#!/usr/bin/env python3
"""Testes do check-no-hardcoded-version.py (P0.39). Stdlib apenas.

    python3 scripts/test-check-no-hardcoded-version.py
"""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_version", HERE / "check-no-hardcoded-version.py")
MOD = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(MOD)

ROOT_POM = """<project xmlns="http://maven.apache.org/POM/4.0.0">
<modelVersion>4.0.0</modelVersion>
<groupId>br.com.ziipsan</groupId><artifactId>ziipsan</artifactId>
<version>${revision}</version><packaging>pom</packaging></project>"""


def make_root(hardcoded_child=False, pkg_version="0.0.0"):
    root = Path(tempfile.mkdtemp())
    (root / "pom.xml").write_text(ROOT_POM, encoding="utf-8")
    child = root / "platform/ziipsan-app"
    child.mkdir(parents=True)
    child_version = "1.2.3" if hardcoded_child else "${revision}"
    (child / "pom.xml").write_text(
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
<modelVersion>4.0.0</modelVersion>
<parent><groupId>br.com.ziipsan</groupId><artifactId>ziipsan</artifactId>
<version>{child_version}</version></parent>
<artifactId>ziipsan-app</artifactId></project>""", encoding="utf-8")
    fe = root / "frontend"
    fe.mkdir()
    (fe / "package.json").write_text(json.dumps({"version": pkg_version}), encoding="utf-8")
    (fe / "vite.config.ts").write_text("export default {}", encoding="utf-8")
    return root


class TestNoHardcodedVersion(unittest.TestCase):
    def test_clean_passes(self):
        self.assertEqual(MOD.check(make_root()), [])

    def test_hardcoded_parent_fails(self):
        failures = MOD.check(make_root(hardcoded_child=True))
        self.assertEqual(len(failures), 1)
        self.assertIn("parent version", failures[0])

    def test_package_json_version_fails(self):
        failures = MOD.check(make_root(pkg_version="1.2.3"))
        self.assertTrue(any("package.json" in f for f in failures))

    def test_comment_mentioning_package_json_passes(self):
        root = make_root()
        (root / "frontend/vite.config.ts").write_text(
            "// A versao NUNCA vem do package.json (sentinela).\nexport default {}", encoding="utf-8")
        self.assertEqual(MOD.check(root), [])

    def test_reading_package_json_fails(self):
        root = make_root()
        (root / "frontend/vite.config.ts").write_text(
            "import pkg from './package.json'\nexport default {}", encoding="utf-8")
        failures = MOD.check(root)
        self.assertTrue(any("le package.json" in f for f in failures))


if __name__ == "__main__":
    unittest.main(verbosity=2)
