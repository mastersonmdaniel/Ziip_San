#!/usr/bin/env python3
"""P1.21: valida content/help (AJUDA-SUPORTE-SOBRE.md §3, plano v3.0 P1.21).

    python3 scripts/validate-help-content.py [--root .]

O que valida (cabeçalho, links, rotas e alvos data-tour existentes):
  * artigos: frontmatter completo (id, title, module, routes, roles, audience,
    min_version, keywords); id == nome do arquivo; rotas existem no frontend
    (App.tsx); links internos resolvem (rota absoluta conhecida ou arquivo .md
    existente); âncoras (#...) apontam para um título do próprio artigo.
  * tours: rota existe; cada passo tem target, title e text; o target é um
    atributo data-tour existente no código — nunca um seletor CSS.
  * dicas: chave estável (segmentos minúsculos separados por ponto) e texto
    não vazio.
  * erros explicados: código exato (urn:ziipsan:problem:<código>, nfage:<cStat>
    ou nfse:<código>), título, explicação e ações; o artigo referenciado
    existe.

O que NÃO valida, de propósito: a cobertura total do catálogo de erros (todo
urn:ziipsan:problem:* do código ter explicação) é o teste da P1.22 — esta
frente valida o formato dos arquivos e as referências, sem duplicar o teste
da frente vizinha.

Exit 0 = ok; 1 = falha de validação; 2 = uso/arquivo.
"""
import argparse
import re
import sys
from pathlib import Path

import yaml

SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
ARTICLE_KEYS = {"id", "title", "module", "routes", "roles", "audience", "min_version", "keywords"}
AUDIENCES = {"tenant", "internal"}
HINT_KEY = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$")
ERROR_CODE = re.compile(r"^(urn:ziipsan:problem:[a-z0-9][a-z0-9_-]*|nfage:[0-9]+|nfse:[a-z0-9][a-z0-9_-]*)$")
LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)\)")
HEADING = re.compile(r"^#{1,6}\s+(.+)$", re.M)
ROUTE_TAG = re.compile(r"<Route\s+[^>]*?path=\"([^\"]+)\"[^>]*?(/?)>|</Route>")
DATA_TOUR = re.compile(r'data-tour="([^"]+)"')


def slugify_heading(text):
    text = re.sub(r"[^a-z0-9 _-]", "", text.strip().lower())
    return re.sub(r"\s+", "-", text)


def parse_frontmatter(text, path):
    """Devolve (frontmatter dict, corpo) ou (None, erro)."""
    if not text.startswith("---\n"):
        return None, f"{path}: sem frontmatter (esperado --- no início)"
    end = text.find("\n---", 4)
    if end < 0:
        return None, f"{path}: frontmatter sem fechamento (---)"
    try:
        doc = yaml.safe_load(text[4:end])
    except yaml.YAMLError as exc:
        return None, f"{path}: frontmatter YAML inválido: {exc}"
    if not isinstance(doc, dict):
        return None, f"{path}: frontmatter não é um mapa"
    return doc, text[end + 4:].lstrip("\n")


def collect_routes(app_tsx):
    """Rotas cheias a partir de App.tsx, resolvendo o aninhamento sob path="/".

    Devolve a lista de padrões (segmentos :param preservados, "*" excluído).
    """
    routes = []
    stack = []
    for m in ROUTE_TAG.finditer(app_tsx):
        if m.group(0) == "</Route>":
            if stack:
                stack.pop()
            continue
        path, self_closing = m.group(1), m.group(2)
        if path == "*":
            continue
        parent = stack[-1] if stack else ""
        if parent == "/":
            full = "/" + path.lstrip("/")
        elif parent:
            full = parent.rstrip("/") + "/" + path.lstrip("/")
        else:
            full = path if path.startswith("/") else "/" + path
        routes.append(full)
        if not self_closing:
            stack.append(full)
    return routes


def route_matches(pattern, route):
    """Casa rota concreta com padrão (:param casa um segmento)."""
    p, r = pattern.split("/"), route.split("/")
    if len(p) != len(r):
        return False
    return all(pp == rr or pp.startswith(":") for pp, rr in zip(p, r))


def validate_article(path, routes, articles_dir):
    errors = []
    fm, body = parse_frontmatter(path.read_text(encoding="utf-8"), path)
    if fm is None:
        return [body], None
    for key in sorted(ARTICLE_KEYS - set(fm)):
        errors.append(f"{path.name}: frontmatter sem {key}")
    for key in sorted(set(fm) - ARTICLE_KEYS):
        errors.append(f"{path.name}: frontmatter com campo desconhecido: {key}")
    if fm.get("id") != path.stem:
        errors.append(f"{path.name}: id diverge do nome do arquivo ({fm.get('id')!r})")
    if not isinstance(fm.get("title"), str) or not fm["title"].strip():
        errors.append(f"{path.name}: title vazio")
    if not isinstance(fm.get("module"), str) or not fm["module"].strip():
        errors.append(f"{path.name}: module vazio")
    if not isinstance(fm.get("routes"), list) or not fm["routes"]:
        errors.append(f"{path.name}: routes vazio (esperada lista de rotas)")
    else:
        for route in fm["routes"]:
            if not isinstance(route, str) or not any(route_matches(p, route) for p in routes):
                errors.append(f"{path.name}: rota inexistente no frontend: {route!r}")
    if not isinstance(fm.get("roles"), list) or not fm["roles"]:
        errors.append(f"{path.name}: roles vazio")
    if fm.get("audience") not in AUDIENCES:
        errors.append(f"{path.name}: audience inválido: {fm.get('audience')!r}")
    if not isinstance(fm.get("min_version"), str) or not SEMVER.match(fm["min_version"]):
        errors.append(f"{path.name}: min_version inválida (esperado SemVer): {fm.get('min_version')!r}")
    if not isinstance(fm.get("keywords"), list):
        errors.append(f"{path.name}: keywords deve ser lista")
    if not body.strip():
        errors.append(f"{path.name}: corpo vazio")
    anchors = {slugify_heading(h) for h in HEADING.findall(body)}
    for target in LINK.findall(body):
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        if target.startswith("#"):
            if target[1:] not in anchors:
                errors.append(f"{path.name}: âncora inexistente no artigo: {target}")
            continue
        if target.startswith("/"):
            route = target.split("#")[0]
            if not any(route_matches(p, route) for p in routes):
                errors.append(f"{path.name}: link para rota inexistente: {target}")
            continue
        rel = target.split("#")[0]
        if rel.endswith(".md"):
            if not (path.parent / rel).resolve().is_file():
                errors.append(f"{path.name}: link para arquivo inexistente: {target}")
        elif rel:
            errors.append(f"{path.name}: link relativo fora do padrão (esperado .md): {target}")
    return errors, fm


def validate_tour(path, routes, targets):
    errors = []
    try:
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        return [f"{path.name}: YAML inválido: {exc}"], None
    if not isinstance(doc, dict):
        return [f"{path.name}: documento não é um mapa"], None
    if doc.get("id") != path.stem:
        errors.append(f"{path.name}: id diverge do nome do arquivo ({doc.get('id')!r})")
    route = doc.get("route")
    if not isinstance(route, str) or not any(route_matches(p, route) for p in routes):
        errors.append(f"{path.name}: rota inexistente no frontend: {route!r}")
    if not isinstance(doc.get("version"), int) or doc["version"] < 1:
        errors.append(f"{path.name}: version inválida: {doc.get('version')!r}")
    if not isinstance(doc.get("title"), str) or not doc["title"].strip():
        errors.append(f"{path.name}: title vazio")
    steps = doc.get("steps")
    if not isinstance(steps, list) or not steps:
        errors.append(f"{path.name}: steps vazio")
    else:
        for i, step in enumerate(steps):
            if not isinstance(step, dict):
                errors.append(f"{path.name}: passo {i} não é um mapa")
                continue
            target = step.get("target")
            if not isinstance(target, str) or not target:
                errors.append(f"{path.name}: passo {i} sem target")
            elif target[0] in ".#[" or any(c in target for c in " >+~"):
                errors.append(f"{path.name}: passo {i} usa seletor CSS, não data-tour: {target!r}")
            elif target not in targets:
                errors.append(f"{path.name}: passo {i} aponta para data-tour inexistente: {target!r}")
            for key in ("title", "text"):
                if not isinstance(step.get(key), str) or not step[key].strip():
                    errors.append(f"{path.name}: passo {i} sem {key}")
    return errors, doc


def validate_hints(path):
    errors = []
    try:
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        return [f"{path.name}: YAML inválido: {exc}"], None
    if not isinstance(doc, dict):
        return [f"{path.name}: documento não é um mapa"], None
    for key, value in doc.items():
        if not HINT_KEY.match(str(key)):
            errors.append(f"{path.name}: chave de dica fora do padrão: {key!r}")
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{path.name}: dica vazia: {key!r}")
    return errors, doc


def validate_error(path, article_slugs):
    errors = []
    try:
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        return [f"{path.name}: YAML inválido: {exc}"], None
    if not isinstance(doc, dict):
        return [f"{path.name}: documento não é um mapa"], None
    code = doc.get("code")
    if not isinstance(code, str) or not ERROR_CODE.match(code):
        errors.append(f"{path.name}: code inválido: {code!r}")
    elif code.startswith("urn:ziipsan:problem:") and path.stem != code.split(":")[-1]:
        errors.append(f"{path.name}: nome do arquivo diverge do sufixo do code ({code})")
    for key in ("title", "explanation"):
        if not isinstance(doc.get(key), str) or not doc[key].strip():
            errors.append(f"{path.name}: {key} vazio")
    actions = doc.get("actions")
    if not isinstance(actions, list) or not actions or not all(isinstance(a, str) and a.strip() for a in actions):
        errors.append(f"{path.name}: actions deve ser lista não vazia de textos")
    causes = doc.get("causes")
    if causes is not None and (not isinstance(causes, list) or not all(isinstance(c, str) for c in causes)):
        errors.append(f"{path.name}: causes deve ser lista de textos")
    article = doc.get("article")
    if article is not None and article not in article_slugs:
        errors.append(f"{path.name}: artigo referenciado inexistente: {article!r}")
    suggest = doc.get("suggest_ticket")
    if suggest is not None and not isinstance(suggest, bool):
        errors.append(f"{path.name}: suggest_ticket deve ser booleano")
    return errors, doc


def scan_data_tour_targets(frontend_src):
    targets = set()
    for path in sorted(frontend_src.rglob("*.tsx")) + sorted(frontend_src.rglob("*.ts")):
        targets.update(DATA_TOUR.findall(path.read_text(encoding="utf-8", errors="ignore")))
    return targets


def validate_all(root):
    """Valida todo content/help. Devolve (erros, artigos, tours, dicas, erros)."""
    errors = []
    help_dir = root / "content" / "help"
    app_tsx = root / "frontend" / "src" / "App.tsx"
    if not help_dir.is_dir():
        return ["content/help não encontrado"], {}, {}, {}, {}
    if not app_tsx.is_file():
        return ["frontend/src/App.tsx não encontrado"], {}, {}, {}, {}
    routes = collect_routes(app_tsx.read_text(encoding="utf-8"))
    targets = scan_data_tour_targets(root / "frontend" / "src")

    articles, tours, hints, err_docs = {}, {}, {}, {}
    for path in sorted((help_dir / "articles").rglob("*.md")):
        file_errors, fm = validate_article(path, routes, help_dir / "articles")
        errors.extend(file_errors)
        if fm is not None:
            if fm.get("id") in articles:
                errors.append(f"{path.name}: id duplicado: {fm.get('id')!r}")
            articles[fm.get("id")] = fm
    for path in sorted((help_dir / "tours").glob("*.yaml")):
        file_errors, doc = validate_tour(path, routes, targets)
        errors.extend(file_errors)
        if doc is not None:
            tours[doc.get("id")] = doc
    for path in sorted((help_dir / "hints").glob("*.yaml")):
        file_errors, doc = validate_hints(path)
        errors.extend(file_errors)
        if doc is not None:
            hints.update(doc)
    seen_codes = set()
    for path in sorted((help_dir / "errors").glob("*.yaml")):
        file_errors, doc = validate_error(path, set(articles))
        errors.extend(file_errors)
        if doc is not None:
            if doc.get("code") in seen_codes:
                errors.append(f"{path.name}: code duplicado: {doc.get('code')!r}")
            seen_codes.add(doc.get("code"))
            err_docs[doc.get("code")] = doc
    return errors, articles, tours, hints, err_docs


def main(argv=None):
    parser = argparse.ArgumentParser(description="Valida content/help (P1.21).")
    parser.add_argument("--root", default=".", help="raiz do repositório")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    errors, _, _, _, _ = validate_all(root)
    for error in errors:
        print(f"ERRO: {error}", file=sys.stderr)
    if errors:
        print(f"content/help inválido: {len(errors)} erro(s)", file=sys.stderr)
        return 1
    print("content/help válido")
    return 0


if __name__ == "__main__":
    sys.exit(main())
