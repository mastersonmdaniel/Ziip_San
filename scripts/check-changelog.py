#!/usr/bin/env python3
"""P0.41: valida content/release-notes/<version>.yaml e a regra de release.

    # So o esquema (qualquer arquivo invalido falha):
    python3 scripts/check-changelog.py --dir content/release-notes

    # Esquema + regra: todo commit marcado precisa de item correspondente:
    git log BASE..HEAD --format='%H %s' > /tmp/commits.txt
    python3 scripts/check-changelog.py --dir content/release-notes --commits-file /tmp/commits.txt

Marcadores (assunto do commit, case-insensitive): [security], [legislation],
[regulatory-parameter] e os prefixos security:, legislation:, regulatory-parameter:.
Item correspondente = mesmo `type` + lista `commits` referenciando o SHA (>= 7 chars).
Exit 0 = ok; 1 = falha de validacao/regra; 2 = uso/arquivo.
"""
import argparse
import datetime
import re
import sys
from pathlib import Path

import yaml

SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
DATE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
TYPES = {"feature", "fix", "security", "legislation", "regulatory-parameter"}
MARKERS = [
    (re.compile(r"\[security\]", re.I), "security"),
    (re.compile(r"\[legislation\]", re.I), "legislation"),
    (re.compile(r"\[regulatory-parameter\]", re.I), "regulatory-parameter"),
    (re.compile(r"^security:", re.I), "security"),
    (re.compile(r"^legislation:", re.I), "legislation"),
    (re.compile(r"^regulatory-parameter:", re.I), "regulatory-parameter"),
]


def validate_file(path: Path):
    """Retorna a lista de erros do arquivo (vazia = valido) e o documento."""
    errors = []
    try:
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        return [f"{path.name}: YAML invalido: {exc}"], None
    if not isinstance(doc, dict):
        return [f"{path.name}: documento nao e um mapa"], None
    version = doc.get("version")
    if not isinstance(version, str) or not SEMVER.match(version):
        errors.append(f"{path.name}: version invalida (esperado SemVer): {version!r}")
    elif path.stem != version:
        errors.append(f"{path.name}: nome do arquivo diverge da version ({version})")
    date = doc.get("date")
    # PyYAML converte 2026-11-20 sem aspas para datetime.date: aceita e normaliza.
    if isinstance(date, (datetime.date, datetime.datetime)):
        date = date.strftime("%Y-%m-%d")
    if not isinstance(date, str) or not DATE.match(date):
        errors.append(f"{path.name}: date invalida (esperado YYYY-MM-DD): {date!r}")
    for key in doc:
        if key not in ("version", "date", "notes", "entries"):
            errors.append(f"{path.name}: campo desconhecido: {key}")
    entries = doc.get("entries")
    if not isinstance(entries, list) or not entries:
        errors.append(f"{path.name}: entries vazio ou ausente")
        entries = []
    for i, entry in enumerate(entries):
        where = f"{path.name} entries[{i}]"
        if not isinstance(entry, dict):
            errors.append(f"{where}: nao e um mapa")
            continue
        if entry.get("type") not in TYPES:
            errors.append(f"{where}: type invalido: {entry.get('type')!r}")
        if not isinstance(entry.get("title"), str) or not entry["title"].strip():
            errors.append(f"{where}: title vazio ou ausente")
        for key in entry:
            if key not in ("type", "title", "commits", "issue"):
                errors.append(f"{where}: campo desconhecido: {key}")
        commits = entry.get("commits") or []
        if not isinstance(commits, list) or any(not isinstance(c, str) or len(c) < 7 for c in commits):
            errors.append(f"{where}: commits deve ser lista de SHAs (>= 7 chars)")
    return errors, doc


def marked_type(subject: str):
    for pattern, kind in MARKERS:
        if pattern.search(subject):
            return kind
    return None


def check_release_rule(docs, commits_file: Path):
    """docs: lista de documentos validos. Retorna erros da regra de release."""
    errors = []
    entries = []
    for doc in docs:
        if doc:
            entries.extend(doc.get("entries") or [])
    for line in commits_file.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split(None, 1)
        sha = parts[0]
        subject = parts[1] if len(parts) > 1 else ""
        kind = marked_type(subject)
        if kind is None:
            continue
        found = False
        for entry in entries:
            if entry.get("type") != kind:
                continue
            for ref in entry.get("commits") or []:
                if sha.startswith(ref) or ref.startswith(sha):
                    found = True
        if not found:
            errors.append(f"commit {sha} ({kind}) sem item correspondente no changelog: {subject}")
    return errors


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="diretorio com os <version>.yaml")
    ap.add_argument("--commits-file", default=None)
    args = ap.parse_args()
    d = Path(args.dir)
    if not d.is_dir():
        print(f"ERRO: diretorio nao encontrado: {d}", file=sys.stderr)
        return 2
    errors, docs = [], []
    files = sorted(d.glob("*.yaml"))
    for path in files:
        errs, doc = validate_file(path)
        errors.extend(errs)
        docs.append(doc)
    print(f"{len(files)} arquivo(s) de release notes em {d}")
    if args.commits_file:
        cf = Path(args.commits_file)
        if not cf.is_file():
            print(f"ERRO: commits-file nao encontrado: {cf}", file=sys.stderr)
            return 2
        errors.extend(check_release_rule(docs, cf))
    if errors:
        for e in errors:
            print(f"FALHOU: {e}", file=sys.stderr)
        return 1
    print("OK: release notes validos e regra de release satisfeita")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
