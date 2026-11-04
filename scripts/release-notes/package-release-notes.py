#!/usr/bin/env python3
"""P1.24: embala content/release-notes/*.yaml como release-notes.json no classpath.

Uso:
    python3 scripts/release-notes/package-release-notes.py
    python3 scripts/release-notes/package-release-notes.py --check

Sem --check, escreve platform/ziipsan-app/src/main/resources/release-notes/
release-notes.json a partir dos arquivos YAML validados pela P0.41
(scripts/check-changelog.py). Com --check, só confere se o arquivo
embalado está atualizado (o mesmo padrão do gerador de conteúdo de ajuda
da P1.21, que barra o arquivo desatualizado no CI).

Regras (as da P0.41, sem inventar nada):
- um arquivo por versão lançada; version == nome do arquivo, SemVer X.Y.Z;
- entries não vazio, cada item com type e title não vazio;
- a saída é um array ordenado por versão crescente; o loader do backend
  (ReleaseNotesLoader) reordena para a mais recente primeiro e revalida
  tudo — este script é o empacotador, não a única validação.
- sem arquivo YAML, a saída é `[]`: nenhuma versão lançada, nenhuma
  novidade — nunca um item inventado para "ter algo para mostrar".
"""

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NOTES_DIR = ROOT / "content" / "release-notes"
OUT = ROOT / "platform" / "ziipsan-app" / "src" / "main" / "resources" / "release-notes" / "release-notes.json"

VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
DATE_RE = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")


def semver_key(version: str):
    return tuple(int(p) for p in version.split("."))


def build() -> list:
    import yaml

    notes = []
    for path in sorted(NOTES_DIR.glob("*.yaml")):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise SystemExit(f"{path.name}: YAML não é um mapa")
        version = str(data.get("version") or "")
        if version != path.stem or not VERSION_RE.match(version):
            raise SystemExit(f"{path.name}: version {version!r} != nome do arquivo ou fora do SemVer X.Y.Z")
        date = str(data.get("date") or "")
        if not DATE_RE.match(date):
            raise SystemExit(f"{path.name}: date {date!r} fora de YYYY-MM-DD")
        entries = data.get("entries") or []
        if not entries:
            raise SystemExit(f"{path.name}: entries vazio")
        out_entries = []
        for entry in entries:
            title = str(entry.get("title") or "")
            if not title.strip():
                raise SystemExit(f"{path.name}: item sem title")
            out_entry = {"type": entry.get("type"), "title": title}
            if entry.get("commits"):
                out_entry["commits"] = list(entry["commits"])
            if entry.get("issue"):
                out_entry["issue"] = entry["issue"]
            out_entries.append(out_entry)
        note = {"version": version, "date": date, "entries": out_entries}
        if data.get("notes"):
            note["notes"] = data["notes"]
        notes.append(note)
    notes.sort(key=lambda n: semver_key(n["version"]))
    return notes


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="só confere se o embalado está atualizado")
    args = ap.parse_args()
    notes = build()
    text = json.dumps(notes, indent=2, ensure_ascii=False) + "\n"
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.exists() else None
        if current != text:
            print(f"release-notes desatualizado: rode scripts/release-notes/package-release-notes.py ({OUT})")
            return 1
        print(f"release-notes atualizado ({len(notes)} notas)")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"{OUT} ({len(notes)} notas)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
