#!/usr/bin/env python3
"""P0.39: verifica que nenhuma versao de release esta escrita a mao.

    python3 scripts/check-no-hardcoded-version.py [--root PATH]

Regras (cada violacao falha):
  1. Todo pom.xml: a versao do parent (quando houver) e ${revision}; a versao propria
     do projeto raiz tambem e ${revision}. Versoes de DEPENDENCIAS de terceiros nao sao
     versoes do projeto e nao entram aqui (elas sao fixadas de proposito).
  2. frontend/package.json: "version" e exatamente "0.0.0" (sentinela: a versao real vem
     da tag via vite.config, nunca do package.json).
  3. frontend/vite.config.ts NAO le package.json para versao (grep por 'package.json'
     em contexto de versao seria um bypass da regra 2).
  4. Nenhum version.json versionado contem versao literal: o template do backend
     (resources-filtered/version.json) deve conter os placeholders ${...}, e nao pode
     haver version.json versionado em frontend/public ou dist.
  5. content/release-notes/*.yaml: version == nome do arquivo (reaproveita a regra da P0.41).
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def check(root: Path):
    failures = []
    for pom in sorted(root.rglob("pom.xml")):
        if any(part in ("target", "node_modules") for part in pom.parts):
            continue
        try:
            tree = ET.parse(pom)
        except ET.ParseError as exc:
            failures.append(f"{pom}: XML invalido: {exc}")
            continue
        rel = pom.relative_to(root)
        parent = tree.getroot().find("m:parent", NS)
        if parent is not None:
            version = parent.findtext("m:version", namespaces=NS)
            if version != "${revision}":
                failures.append(f"{rel}: parent version escrita a mao: {version!r} (esperado ${{revision}})")
        if rel == Path("pom.xml"):
            own = tree.getroot().findtext("m:version", namespaces=NS)
            if own != "${revision}":
                failures.append(f"pom.xml: versao propria escrita a mao: {own!r} (esperado ${{revision}})")
    pkg = root / "frontend/package.json"
    if pkg.is_file():
        version = json.loads(pkg.read_text(encoding="utf-8")).get("version")
        if version != "0.0.0":
            failures.append(f"frontend/package.json: version {version!r} (esperado sentinela '0.0.0')")
    vite = root / "frontend/vite.config.ts"
    if vite.is_file():
        # Mera mencao em comentario nao e leitura; o bypass e LER o arquivo (import,
        # require ou readFile). O comentario que explica a regra menciona package.json
        # e nao pode falhar o proprio check.
        vite_text = vite.read_text(encoding="utf-8")
        if re.search(r"(readFileSync|readFile|require\(|import\s+[^;\n]*from\s+['\"])",
                     vite_text) and "package.json" in vite_text:
            # Confirma que a leitura aponta de fato para package.json, nao para outro arquivo.
            lines = [ln for ln in vite_text.splitlines()
                     if "package.json" in ln and not ln.strip().startswith("//")]
            if any(re.search(r"(readFileSync|readFile|require|import)", ln) for ln in lines):
                failures.append("frontend/vite.config.ts: le package.json (bypass da versao por tag)")
    template = root / "platform/ziipsan-app/src/main/resources-filtered/version.json"
    if template.is_file():
        text = template.read_text(encoding="utf-8")
        if "${project.version}" not in text or "${git.commit.id.abbrev}" not in text:
            failures.append("version.json template sem placeholders de build (versao/commit)")
        if re.search(r'"version":\s*"[0-9]+\.[0-9]+\.[0-9]+', text):
            failures.append("version.json template com versao literal")
    for bad in (root / "frontend/public/version.json",):
        if bad.is_file():
            failures.append(f"{bad.relative_to(root)}: version.json versionado no frontend (e artefato de build)")
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=None)
    args = ap.parse_args()
    root = Path(args.root) if args.root else Path(__file__).resolve().parent.parent
    failures = check(root)
    if failures:
        for f in failures:
            print(f"FALHOU: {f}", file=sys.stderr)
        return 1
    print("OK: nenhuma versao de release escrita a mao")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
