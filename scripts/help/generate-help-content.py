#!/usr/bin/env python3
"""P1.21: compila content/help nas duas formas publicadas (AJUDA §3).

    python3 scripts/help/generate-help-content.py [--check]

Saídas (sempre a partir de content/help, única fonte):
  1. shared/ziipsan-core/.../db/migration/core/R__help_content.sql — migration
     repetível do Flyway: o job `migrate` a reaplica sempre que o conteúdo
     muda (o checksum muda), sem nova imagem. Artigos, versões e erros
     explicados entram por upsert de chave estável (slug / (artigo, versão) /
     code). Tours e dicas NÃO vão ao banco: não há tabela reservada para a
     definição delas na V9_3 (só `tour_completion`, a conclusão) — vão ao
     bundle do frontend, abaixo.
  2. frontend/src/help/helpBundle.generated.ts — bundle lido pelo hook
     `useContextualHelp` e pelo componente `<Hint>`. É arquivo gerado: não
     edite à mão (o CI barra com --check desatualizado, como os gerados de
     marca/temas).

Limites honestos, por desenho (não bugs a esconder):
  * o gerador republica sempre a versão 1 do artigo (upsert em place). O
    histórico de versões (article_version com version > 1) exige um fluxo
    editorial que decide quando uma edição vira versão nova — fora da P1.21;
    até lá, a edição atualiza a versão 1 e o hash denuncia a mudança.
  * o gerador nunca apaga linha publicada (mesma regra das R__ de catálogo):
    remover um artigo dos arquivos não o remove do banco; a remoção é manual.

Exit 0 = ok; 1 = conteúdo inválido ou --check desatualizado; 2 = uso.
"""
import argparse
import hashlib
import importlib.util
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
_spec = importlib.util.spec_from_file_location(
    "validate_help_content", HERE.parent / "validate-help-content.py")
VALIDATOR = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(VALIDATOR)

SQL_PATH = ROOT / "shared/ziipsan-core/src/main/resources/db/migration/core/R__help_content.sql"
TS_PATH = ROOT / "frontend/src/help/helpBundle.generated.ts"


def sql_str(value):
    return "'" + value.replace("'", "''") + "'"


def sql_array(values):
    if not values:
        return "'{}'::text[]"
    return "ARRAY[" + ", ".join(sql_str(v) for v in values) + "]::text[]"


def sql_bool(value):
    return "TRUE" if value else "FALSE"


def load_content(root):
    """Lê content/help já validado. Devolve (artigos, tours, dicas, erros)."""
    errors, articles, tours, hints, err_docs = VALIDATOR.validate_all(root)
    if errors:
        for error in errors:
            print(f"ERRO: {error}", file=sys.stderr)
        sys.exit(1)
    help_dir = root / "content" / "help"
    bodies = {}
    for path in sorted((help_dir / "articles").rglob("*.md")):
        _, body = VALIDATOR.parse_frontmatter(path.read_text(encoding="utf-8"), path)
        bodies[path.stem] = body.strip()
    return articles, tours, hints, err_docs, bodies


def render_sql(articles, err_docs, bodies):
    lines = [
        "-- GERADO por scripts/help/generate-help-content.py a partir de content/help.",
        "-- NÃO EDITE À MÃO: regenere (o CI barra o arquivo desatualizado com --check).",
        "--",
        "-- P1.21: publicação do conteúdo de ajuda pelo migrate (migration repetível",
        "-- do Flyway, reaplicada quando o conteúdo muda, sem nova imagem). Upsert por",
        "-- chave estável; nunca apaga linha publicada. Tours e dicas não entram aqui:",
        "-- não há tabela de definição delas na V9_3 — vão ao bundle do frontend.",
        "-- A versão do artigo é sempre 1 nesta fase (ver o docstring do gerador).",
        "",
    ]
    for slug in sorted(articles):
        fm = articles[slug]
        lines.append(f"-- Artigo: {slug}")
        lines.append("INSERT INTO support.article")
        lines.append("    (tenant_id, slug, module, routes, roles, audience, min_version, current_version)")
        lines.append("VALUES (NULL, %s, %s, %s, %s, %s, %s, 1)" % (
            sql_str(slug), sql_str(fm["module"]), sql_array(fm["routes"]),
            sql_array(fm["roles"]), sql_str(fm["audience"]), sql_str(fm["min_version"])))
        lines.append("ON CONFLICT (slug) DO UPDATE SET module = EXCLUDED.module,")
        lines.append("    routes = EXCLUDED.routes, roles = EXCLUDED.roles,")
        lines.append("    audience = EXCLUDED.audience, min_version = EXCLUDED.min_version,")
        lines.append("    updated_at = NOW();")
        body = bodies[slug]
        digest = hashlib.sha256(body.encode("utf-8")).hexdigest()
        lines.append("INSERT INTO support.article_version")
        lines.append("    (tenant_id, article_id, version, title, body, keywords, content_hash)")
        lines.append("SELECT NULL, a.id, 1, %s, %s, %s, %s" % (
            sql_str(fm["title"]), sql_str(body), sql_array(fm["keywords"]), sql_str(digest)))
        lines.append("FROM support.article a WHERE a.slug = %s" % sql_str(slug))
        lines.append("ON CONFLICT (article_id, version) DO UPDATE SET title = EXCLUDED.title,")
        lines.append("    body = EXCLUDED.body, keywords = EXCLUDED.keywords,")
        lines.append("    content_hash = EXCLUDED.content_hash;")
        lines.append("")
    for code in sorted(err_docs):
        doc = err_docs[code]
        article = doc.get("article")
        lines.append(f"-- Erro explicado: {code}")
        lines.append("INSERT INTO support.error_explanation")
        lines.append("    (code, tenant_id, title, explanation, causes, actions, article_slug, suggest_ticket)")
        lines.append("VALUES (%s, NULL, %s, %s, %s, %s, %s, %s)" % (
            sql_str(code), sql_str(doc["title"]), sql_str(doc["explanation"]),
            sql_array(doc.get("causes") or []), sql_array(doc["actions"]),
            sql_str(article) if article else "NULL", sql_bool(doc.get("suggest_ticket") or False)))
        lines.append("ON CONFLICT (code) DO UPDATE SET title = EXCLUDED.title,")
        lines.append("    explanation = EXCLUDED.explanation, causes = EXCLUDED.causes,")
        lines.append("    actions = EXCLUDED.actions, article_slug = EXCLUDED.article_slug,")
        lines.append("    suggest_ticket = EXCLUDED.suggest_ticket, updated_at = NOW();")
        lines.append("")
    return "\n".join(lines)


def render_ts(articles, tours, hints, err_docs, bodies):
    bundle = {
        "articles": [
            {
                "slug": slug,
                "title": articles[slug]["title"],
                "module": articles[slug]["module"],
                "routes": articles[slug]["routes"],
                "roles": articles[slug]["roles"],
                "audience": articles[slug]["audience"],
                "keywords": articles[slug]["keywords"],
                "body": bodies[slug],
            }
            for slug in sorted(articles)
        ],
        "tours": [
            {
                "id": tours[tid]["id"],
                "route": tours[tid]["route"],
                "version": tours[tid]["version"],
                "title": tours[tid]["title"],
                "steps": tours[tid]["steps"],
            }
            for tid in sorted(tours)
        ],
        "hints": {key: hints[key] for key in sorted(hints)},
        "errors": [
            {
                "code": code,
                "title": err_docs[code]["title"],
                "explanation": err_docs[code]["explanation"],
                "causes": err_docs[code].get("causes") or [],
                "actions": err_docs[code]["actions"],
                "article": err_docs[code].get("article"),
                "suggestTicket": err_docs[code].get("suggest_ticket") or False,
            }
            for code in sorted(err_docs)
        ],
    }
    payload = json.dumps(bundle, ensure_ascii=False, indent=2)
    return (
        "// GERADO por scripts/help/generate-help-content.py a partir de content/help.\n"
        "// NÃO EDITE À MÃO: regenere (o CI barra o arquivo desatualizado com --check).\n"
        "export interface HelpArticle {\n"
        "  slug: string\n  title: string\n  module: string\n  routes: string[]\n"
        "  roles: string[]\n  audience: string\n  keywords: string[]\n  body: string\n}\n"
        "export interface HelpTourStep {\n  target: string\n  title: string\n  text: string\n}\n"
        "export interface HelpTour {\n"
        "  id: string\n  route: string\n  version: number\n  title: string\n  steps: HelpTourStep[]\n}\n"
        "export interface HelpError {\n"
        "  code: string\n  title: string\n  explanation: string\n  causes: string[]\n"
        "  actions: string[]\n  article: string | null\n  suggestTicket: boolean\n}\n"
        "export interface HelpBundle {\n"
        "  articles: HelpArticle[]\n  tours: HelpTour[]\n  hints: Record<string, string>\n  errors: HelpError[]\n}\n"
        f"export const HELP_BUNDLE: HelpBundle = {payload}\n"
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description="Gera R__help_content.sql e helpBundle.generated.ts.")
    parser.add_argument("--check", action="store_true", help="só confere se os gerados estão atualizados")
    parser.add_argument("--root", default=str(ROOT), help="raiz do repositório")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    articles, tours, hints, err_docs, bodies = load_content(root)
    sql = render_sql(articles, err_docs, bodies)
    ts = render_ts(articles, tours, hints, err_docs, bodies)
    sql_path = root / "shared/ziipsan-core/src/main/resources/db/migration/core/R__help_content.sql"
    ts_path = root / "frontend/src/help/helpBundle.generated.ts"
    if args.check:
        stale = []
        if not sql_path.is_file() or sql_path.read_text(encoding="utf-8") != sql:
            stale.append(str(sql_path))
        if not ts_path.is_file() or ts_path.read_text(encoding="utf-8") != ts:
            stale.append(str(ts_path))
        if stale:
            for path in stale:
                print(f"ERRO: gerado desatualizado: {path} (rode scripts/help/generate-help-content.py)", file=sys.stderr)
            return 1
        print("gerados de ajuda atualizados")
        return 0
    ts_path.parent.mkdir(parents=True, exist_ok=True)
    sql_path.write_text(sql, encoding="utf-8")
    ts_path.write_text(ts, encoding="utf-8")
    print(f"gerados: {len(articles)} artigo(s), {len(tours)} tour(s), "
          f"{len(hints)} dica(s), {len(err_docs)} erro(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
