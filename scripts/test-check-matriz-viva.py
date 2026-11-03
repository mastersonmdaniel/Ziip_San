#!/usr/bin/env python3
"""Testes do check-matriz-viva.py (P1.25). Stdlib.

    python3 scripts/test-check-matriz-viva.py
"""
import importlib.util
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("check_matriz_viva",
                                              HERE / "check-matriz-viva.py")
MOD = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(MOD)


class TestExpandIds(unittest.TestCase):
    def test_single(self):
        self.assertEqual(MOD.expand_ids("TR-129, SG-066"),
                         ["TR-129", "SG-066"])

    def test_padding(self):
        # Matrizes usam TR-69; extracoes usam TR-069.
        self.assertEqual(MOD.expand_ids("TR-69"), ["TR-069"])
        self.assertEqual(MOD.expand_ids("SG-15"), ["SG-015"])

    def test_range_a(self):
        self.assertEqual(MOD.expand_ids("TR-1082 a TR-1084"),
                         ["TR-1082", "TR-1083", "TR-1084"])

    def test_range_dash(self):
        self.assertEqual(MOD.expand_ids("TR-068–TR-070"),
                         ["TR-068", "TR-069", "TR-070"])

    def test_range_no_repeat_prefix(self):
        self.assertEqual(MOD.expand_ids("SG-429-431"),
                         ["SG-429", "SG-430", "SG-431"])

    def test_dedupe(self):
        self.assertEqual(MOD.expand_ids("TR-129, TR-129"), ["TR-129"])


class TestVocabulary(unittest.TestCase):
    def test_valid(self):
        for v in ["V:POC-01–POC-03", "V:POC-26", "V:possible",
                  "V:POC-rules", "S:critical", "S:essential",
                  "S:assessable", "none", "PENDENTE"]:
            self.assertRegex(v, MOD.POC_RE, v)

    def test_invalid(self):
        for v in ["TR: Possível", "VI Sim", "POC-01", "S:critico", ""]:
            self.assertNotRegex(v, MOD.POC_RE, v)


class TestSplitCells(unittest.TestCase):
    def test_row(self):
        self.assertEqual(MOD.split_cells("| a | b |"), ["a", "b"])
        self.assertEqual(MOD.split_cells("| POC-01 | a) (4.2) | texto |"),
                         ["POC-01", "a) (4.2)", "texto"])


if __name__ == "__main__":
    unittest.main()
