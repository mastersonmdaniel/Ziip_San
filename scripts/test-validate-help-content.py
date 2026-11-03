#!/usr/bin/env python3
"""Testes do validate-help-content.py (P1.21). Stdlib + PyYAML.

    python3 scripts/test-validate-help-content.py
"""
import importlib.util
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("validate_help_content", HERE / "validate-help-content.py")
MOD = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(MOD)

GOOD_ARTICLE = """---
id: pesquisar-imoveis
title: Pesquisar imóveis
module: cadastro
routes:
  - /cadastro/imoveis
roles:
  - tenant-user
audience: tenant
min_version: 1.0.0
keywords:
  - imóvel
---

# Pesquisar imóveis

Veja [clientes](/cadastro/clientes) e [outro](outro.md).
"""

GOOD_TOUR = """id: cadastro-imoveis
route: /cadastro/imoveis
version: 1
title: Pesquisar imóveis
steps:
  - target: imoveis-search
    title: Busque
    text: Digite algo.
"""


def tmp_file(name, text):
    d = Path(tempfile.mkdtemp())
    path = d / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


class TestRoutes(unittest.TestCase):
    def test_nested_routes_resolved(self):
        app = '<Route path="/" element={<Layout />}>'
        app += '<Route path="cadastro/imoveis" element={<X />} />'
        app += '<Route path="cadastro/imoveis/:id" element={<Y />} />'
        app += "</Route>"
        routes = MOD.collect_routes(app)
        self.assertIn("/cadastro/imoveis", routes)
        self.assertTrue(any(MOD.route_matches(p, "/cadastro/imoveis/42") for p in routes))

    def test_param_matches_single_segment_only(self):
        self.assertTrue(MOD.route_matches("/cadastro/imoveis/:id", "/cadastro/imoveis/42"))
        self.assertFalse(MOD.route_matches("/cadastro/imoveis/:id", "/cadastro/imoveis/42/editar"))


class TestArticle(unittest.TestCase):
    def test_valid_passes(self):
        d = Path(tempfile.mkdtemp())
        (d / "outro.md").write_text("# Outro\n", encoding="utf-8")
        path = tmp_file("pesquisar-imoveis.md", GOOD_ARTICLE)
        import shutil
        shutil.copy(d / "outro.md", path.parent / "outro.md")
        errors, fm = MOD.validate_article(path, ["/cadastro/imoveis", "/cadastro/clientes"], d)
        self.assertEqual(errors, [])
        self.assertEqual(fm["id"], "pesquisar-imoveis")

    def test_unknown_route_fails(self):
        path = tmp_file("a.md", GOOD_ARTICLE.replace("/cadastro/imoveis", "/faturamento"))
        errors, _ = MOD.validate_article(path, ["/cadastro/imoveis"], Path("/tmp"))
        self.assertTrue(any("rota inexistente" in e for e in errors))

    def test_missing_frontmatter_key_fails(self):
        path = tmp_file("a.md", GOOD_ARTICLE.replace("audience: tenant\n", ""))
        errors, _ = MOD.validate_article(path, ["/cadastro/imoveis"], Path("/tmp"))
        self.assertTrue(any("audience" in e for e in errors))

    def test_broken_relative_link_fails(self):
        path = tmp_file("a.md", GOOD_ARTICLE)
        errors, _ = MOD.validate_article(path, ["/cadastro/imoveis", "/cadastro/clientes"], Path("/tmp"))
        self.assertTrue(any("inexistente" in e for e in errors))


class TestTour(unittest.TestCase):
    def test_valid_passes(self):
        path = tmp_file("cadastro-imoveis.yaml", GOOD_TOUR)
        errors, _ = MOD.validate_tour(path, ["/cadastro/imoveis"], {"imoveis-search"})
        self.assertEqual(errors, [])

    def test_missing_target_fails(self):
        path = tmp_file("cadastro-imoveis.yaml", GOOD_TOUR)
        errors, _ = MOD.validate_tour(path, ["/cadastro/imoveis"], set())
        self.assertTrue(any("data-tour inexistente" in e for e in errors))

    def test_css_selector_fails(self):
        bad = GOOD_TOUR.replace("imoveis-search", ".search-box")
        path = tmp_file("cadastro-imoveis.yaml", bad)
        errors, _ = MOD.validate_tour(path, ["/cadastro/imoveis"], {".search-box"})
        self.assertTrue(any("seletor CSS" in e for e in errors))


class TestHintsAndErrors(unittest.TestCase):
    def test_hints_valid_and_bad_key(self):
        path = tmp_file("c.yaml", "cadastro.property.search: texto\n")
        self.assertEqual(MOD.validate_hints(path)[0], [])
        path = tmp_file("c.yaml", "Busca Livre: texto\n")
        self.assertTrue(any("fora do padrão" in e for e in MOD.validate_hints(path)[0]))

    def test_error_valid_and_bad_code(self):
        good = ("code: urn:ziipsan:problem:not-found\ntitle: Não achado\n"
                "explanation: Não existe.\nactions:\n  - Tente de novo.\narticle: pesquisar-imoveis\n")
        path = tmp_file("not-found.yaml", good)
        self.assertEqual(MOD.validate_error(path, {"pesquisar-imoveis"})[0], [])
        path = tmp_file("x.yaml", good.replace("urn:ziipsan:problem:not-found", "not-found"))
        self.assertTrue(any("code inválido" in e for e in MOD.validate_error(path, set())[0]))

    def test_error_unknown_article_fails(self):
        doc = ("code: urn:ziipsan:problem:not-found\ntitle: T\nexplanation: E\n"
               "actions:\n  - A.\narticle: artigo-que-nao-existe\n")
        path = tmp_file("not-found.yaml", doc)
        errors, _ = MOD.validate_error(path, {"pesquisar-imoveis"})
        self.assertTrue(any("artigo referenciado inexistente" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
