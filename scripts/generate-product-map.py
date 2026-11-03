#!/usr/bin/env python3
"""Gera docs/arquitetura/product-map.md a partir de packages/sanitation/vertical.json (P0.47).

O mapa é conferido no CI por VerticalManifestTest: cada módulo do manifesto aparece com
um marcador <!-- module: <id> --> e cada rota de menu aparece no mapa. Regenerar depois
de qualquer mudança no manifesto; nunca edite o mapa à mão sem regenerar.
Uso: python3 scripts/generate-product-map.py (a partir da raiz do repositório).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "packages/sanitation/vertical.json"
OUT = ROOT / "docs/arquitetura/product-map.md"


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    lines = []
    lines.append("# Mapa do produto — pacote sanitation (gerado, P0.47)")
    lines.append("")
    lines.append("> Gerado por `scripts/generate-product-map.py` a partir de")
    lines.append("> `packages/sanitation/vertical.json`. Não edite à mão: regenere.")
    lines.append("> Status: `active` = rota e menu no produto; `declared` = módulo existe,")
    lines.append("> sem rota/menu até esta onda; `skeleton` = esqueleto (fiscal, P0.32).")
    lines.append("> Permissão `null` = sem permissão catalogada na P0.13 (não inventada).")
    lines.append("")
    lines.append(f"Pacote: **{manifest['id']}** — versão {manifest['version']} "
                 f"(plataforma mínima {manifest['minPlatformVersion']})")
    lines.append("")
    lines.append("| Módulo | Nome | Status | Módulo Maven | Permissão | Tarefa de origem |")
    lines.append("|---|---|---|---|---|---|")
    for m in manifest["modules"]:
        perm = m.get("permission") or "— (sem permissão catalogada)"
        lines.append(f"| {m['id']} | {m['name']} | {m['status']} | `{m['mavenModule']}` "
                     f"| `{perm}` | {m['originTask']} |")
    lines.append("")
    for m in manifest["modules"]:
        lines.append(f"## {m['name']} (`{m['id']}`) <!-- module: {m['id']} -->")
        lines.append("")
        lines.append(f"- Status: `{m['status']}` · módulo Maven: `{m['mavenModule']}`")
        lines.append(f"- Permissão: `{m.get('permission') or '— (sem permissão catalogada)'}`")
        lines.append(f"- Tarefa de origem: {m['originTask']}")
        if m["menu"]:
            lines.append("- Itens de menu:")
            for item in m["menu"]:
                lines.append(f"  - {item['label']} — rota `{item['route']}`, ícone "
                             f"`{item['icon']}`, permissão `{item.get('permission') or '—'}`")
        else:
            lines.append("- Itens de menu: nenhum até esta onda.")
        if m["apiRoutes"]:
            lines.append("- Rotas de API:")
            for route in m["apiRoutes"]:
                lines.append(f"  - `{route}`")
        else:
            lines.append("- Rotas de API: nenhuma até esta onda.")
        if m["unitSheetTabs"]:
            lines.append("- Abas da ficha da unidade: " + ", ".join(f"`{t}`" for t in m["unitSheetTabs"]))
        else:
            lines.append("- Abas da ficha da unidade: nenhuma.")
        lines.append("")
    lines.append("## Domínios do GIS (declarados; carga na P1.26, V13_1)")
    lines.append("")
    for d in manifest["gis"]["domains"]:
        lines.append(f"- `{d['key']}` — {d['label']}, cor `{d['color']}`, ordem {d['order']}")
    lines.append("")
    lines.append("## Regra das abas da ficha da unidade (P0.47)")
    lines.append("")
    lines.append(manifest["unitSheetTabRule"])
    lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Mapa gerado: {OUT} ({len(manifest['modules'])} módulos)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
