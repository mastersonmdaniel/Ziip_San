#!/usr/bin/env python3
"""P0.40: bloqueia a release quando third-party-licenses.json tem componente sem licenca
permitida.

    python3 scripts/check-third-party-licenses.py \
        --licenses third-party-licenses.json \
        --allowed third-party-licenses-allowed.json

REGRA (licenciamento dual, convencao dos poms Maven): um componente com varias licencas
listadas passa se PELO MENOS UMA estiver na lista permitida — o distribuidor escolhe
uma delas (ex.: Jakarta APIs, EPL-2.0 ou GPL-2.0-with-classpath-exception; basta o
EPL-2.0 permitido). Bloqueia quando NENHUMA licenca esta permitida, ou quando nao ha
licenca conhecida no SBOM. Um componente licenciado por varias licencas EXIGIDAS
simultaneamente (AND) chega aqui como um unico token combinado (ver generate) e
bloqueia para revisao manual.

Exit 0 = tudo permitido; exit 1 = ha bloqueio (lista os componentes); exit 2 = uso/arquivo.
"""
import argparse
import json
import sys
from pathlib import Path


def check(licenses_path: Path, allowed_path: Path):
    data = json.loads(licenses_path.read_text(encoding="utf-8"))
    allowed = set(json.loads(allowed_path.read_text(encoding="utf-8"))["allowed"])
    failures = []
    for comp in data.get("components") or []:
        label = f"{comp.get('name')} {comp.get('version')}".strip()
        lics = comp.get("licenses") or []
        if not lics:
            failures.append(f"{label}: SEM LICENCA no SBOM (desconhecida bloqueia)")
            continue
        if not any(lic in allowed for lic in lics):
            failures.append(f"{label}: nenhuma licenca permitida (tem: {', '.join(lics)})")
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--licenses", required=True)
    ap.add_argument("--allowed", required=True)
    args = ap.parse_args()
    for p in (args.licenses, args.allowed):
        if not Path(p).is_file():
            print(f"ERRO: arquivo nao encontrado: {p}", file=sys.stderr)
            return 2
    failures = check(Path(args.licenses), Path(args.allowed))
    if failures:
        for f in failures:
            print(f"BLOQUEADO: {f}", file=sys.stderr)
        return 1
    print("OK: todas as licencas de terceiros estao na lista permitida")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
