#!/usr/bin/env python3
"""Testes do check-metrics-budget.py (P0.8). Sem dependencias alem de PyYAML + stdlib.

    python3 scripts/test-check-metrics-budget.py
"""
import sys
import tempfile
import unittest
from pathlib import Path

import importlib.util
_spec = importlib.util.spec_from_file_location(
    "check_metrics_budget", Path(__file__).resolve().parent / "check-metrics-budget.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
check = _mod.check


def write_tmp(text: str) -> Path:
    fd, path = tempfile.mkstemp(suffix=".yml")
    Path(path).write_text(text, encoding="utf-8")
    return Path(path)


class TestMetricsBudget(unittest.TestCase):
    def test_within_budget_passes(self):
        f = write_tmp("""
services:
  svc-a:
    budget: 100
    components:
      - {name: x, estimated_series: 40}
      - {name: y, estimated_series: 50}
""")
        self.assertEqual(check(f), [])

    def test_over_budget_fails(self):
        f = write_tmp("""
services:
  svc-a:
    budget: 100
    components:
      - {name: x, estimated_series: 101}
""")
        failures = check(f)
        self.assertEqual(len(failures), 1)
        self.assertIn("101 > orcamento 100", failures[0])

    def test_missing_budget_fails(self):
        f = write_tmp("""
services:
  svc-a:
    components:
      - {name: x, estimated_series: 10}
""")
        self.assertTrue(check(f))

    def test_invalid_estimate_fails(self):
        f = write_tmp("""
services:
  svc-a:
    budget: 100
    components:
      - {name: x, estimated_series: "muito"}
""")
        self.assertTrue(check(f))


if __name__ == "__main__":
    unittest.main(verbosity=2)
