#!/usr/bin/env python3
"""P0.40: gera third-party-licenses.json a partir de SBOMs CycloneDX (Maven e npm).

    python3 scripts/generate-third-party-licenses.py \
        --sbom platform/ziipsan-app/target/bom.json \
        --sbom frontend/sbom.cdx.json \
        --overrides third-party-licenses-overrides.json \
        --out third-party-licenses.json

O arquivo de saida e ARTEFATO DE BUILD, nao e versionado: o CI o regenera a cada run
e o job licenses o confere contra third-party-licenses-allowed.json. Componentes sem
licenca no SBOM saem com licenses=[] e o check os BLOQUEIA (licenca desconhecida nao
vira permissao silenciosa).
"""
import argparse
import json
import sys
from pathlib import Path

# Nomes livres que aparecem nos SBOMs, normalizados para SPDX. So entram aqui variantes
# de licencas que JA estao na lista permitida; um nome novo e desconhecido (nao mapeado)
# continua bloqueando no check, em vez de ser "adivinhado" como permitido.
NAME_TO_SPDX = {
    "apache license, version 2.0": "Apache-2.0",
    "apache-2.0": "Apache-2.0",
    "the apache software license, version 2.0": "Apache-2.0",
    "mit license": "MIT",
    "mit": "MIT",
    "bsd 2-clause": "BSD-2-Clause",
    "bsd 2-clause license": "BSD-2-Clause",
    "bsd 3-clause": "BSD-3-Clause",
    "bsd 3-clause license": "BSD-3-Clause",
    "eclipse public license 2.0": "EPL-2.0",
    "eclipse public license - v 2.0": "EPL-2.0",
    "eclipse public license v2.0": "EPL-2.0",
    "gnu lesser general public license v2.1 only": "LGPL-2.1-only",
    "gnu lesser general public license, version 2.1": "LGPL-2.1-or-later",
    "lgpl 2.1": "LGPL-2.1-or-later",
    "mozilla public license 2.0": "MPL-2.0",
    "isc license": "ISC",
    "zlib license": "Zlib",
    "the unlicense": "Unlicense",
    "cc0 1.0 universal": "CC0-1.0",
    "boost software license 1.0": "BSL-1.0",
    # Verificado no jar instalado (spring-session-core 4.1.1, META-INF/LICENSE.txt):
    # o texto E o Apache License 2.0, embora o pom o rotule "Broadcom Foundation License".
    # O mapeamento e para o TEXTO verificado, nao para o rotulo.
    "broadcom foundation license": "Apache-2.0",
}


def normalize(raw: str) -> str:
    key = (raw or "").strip()
    if not key:
        return ""
    if key in NAME_TO_SPDX:
        return NAME_TO_SPDX[key]
    lowered = key.lower()
    if lowered in NAME_TO_SPDX:
        return NAME_TO_SPDX[lowered]
    return key  # id SPDX ja, ou desconhecido (o check decide)


def licenses_of(component: dict) -> list:
    out = []
    for entry in component.get("licenses") or []:
        lic = entry.get("license") or {}
        raw = lic.get("id") or lic.get("name") or ""
        norm = normalize(raw)
        if norm and norm not in out:
            out.append(norm)
    # npm sbom as vezes usa `expression` em vez de lista. Expressao com OR e lista de
    # alternativas (cada termo vira uma licenca; o check passa se QUALQUER uma for
    # permitida — regra do licenciamento dual). Expressao com AND e mantida como um
    # unico token combinado, que NAO esta na lista permitida e bloqueia para revisao
    # manual: AND exige TODAS, e aprovar por um termo seria errado.
    expr = component.get("license_expression") or component.get("expression")
    if expr and isinstance(expr, str):
        tokens = expr.replace("(", " ").replace(")", " ").split()
        if "AND" in tokens:
            combined = " AND ".join(normalize(t) for t in tokens if t not in ("AND", "OR", "WITH"))
            if combined:
                out.append(combined)
        else:
            for part in tokens:
                if part in ("AND", "OR", "WITH"):
                    continue
                norm = normalize(part)
                if norm and norm not in out:
                    out.append(norm)
    return sorted(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sbom", action="append", required=True, help="SBOM CycloneDX JSON (repetivel)")
    ap.add_argument("--overrides", default=None,
                    help="JSON com licencas verificadas para componentes que o SBOM deixa sem "
                         "licenca (ex.: expressao que o gerador nao traduz). So preenche o VAZIO, "
                         "nunca sobrescreve licenca do SBOM, e a licenca do override ainda precisa "
                         "estar na lista permitida do check — o override nao e um bypass.")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    merged = {}
    skipped_first_party = []
    for sbom_path in args.sbom:
        data = json.loads(Path(sbom_path).read_text(encoding="utf-8"))
        for comp in data.get("components") or []:
            # P0.40 e sobre TERCEIROS: os proprios modulos do projeto (group
            # br.com.ziipsan) aparecem no SBOM do app como dependencias, mas nao sao
            # licenca de terceiro — excluidos aqui, com a lista registrada na saida
            # para auditoria (nada some em silencio).
            purl = comp.get("purl") or ""
            if purl.startswith("pkg:maven/br.com.ziipsan/"):
                skipped_first_party.append(comp.get("name"))
                continue
            name = comp.get("name") or "unknown"
            version = comp.get("version") or ""
            key = (name, version)
            existing = merged.get(key)
            lics = licenses_of(comp)
            if existing is None:
                merged[key] = {"name": name, "version": version, "licenses": lics,
                               "sources": [sbom_path]}
            else:
                existing["licenses"] = sorted(set(existing["licenses"]) | set(lics))
                if sbom_path not in existing["sources"]:
                    existing["sources"].append(sbom_path)
    if args.overrides:
        overrides = json.loads(Path(args.overrides).read_text(encoding="utf-8")).get("overrides") or []
        for ov in overrides:
            key = (ov.get("name"), ov.get("version"))
            comp = merged.get(key)
            if comp is not None and not comp["licenses"]:
                comp["licenses"] = sorted(ov.get("licenses") or [])
                comp["licenses_source"] = "override: " + (ov.get("reason") or "")
    components = [merged[k] for k in sorted(merged)]
    out = {"generated_from": args.sbom, "components": components,
           "skipped_first_party": sorted(set(skipped_first_party))}
    Path(args.out).write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    unknown = [c for c in components if not c["licenses"]]
    print(f"{len(components)} componentes; {len(unknown)} sem licenca no SBOM")
    for c in unknown:
        print(f"  sem licenca: {c['name']} {c['version']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
