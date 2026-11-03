#!/usr/bin/env python3
"""Gera docs/arquitetura/business-event-catalog.md a partir de packages/sanitation/vertical.json (P1.38).

O documento é conferido no CI por BusinessEventCatalogDocTest: cada evento do
manifesto aparece com um marcador <!-- event: <nome>@v<versão> -->, cada
consumidor declarado com <!-- consumer: <tarefa> --> e cada lacuna com
<!-- gap: <nome> -->. Regenerar depois de qualquer mudança no catálogo; nunca
edite o documento à mão sem regenerar.
Uso: python3 scripts/generate-business-event-catalog.py (a partir da raiz do repositório).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "packages/sanitation/vertical.json"
OUT = ROOT / "docs/arquitetura/business-event-catalog.md"


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    lines = []
    lines.append("# Catálogo de eventos de negócio — pacote sanitation (gerado, P1.38)")
    lines.append("")
    lines.append("> Gerado por `scripts/generate-business-event-catalog.py` a partir de")
    lines.append("> `packages/sanitation/vertical.json`. Não edite à mão: regenere.")
    lines.append("> Regra: a carga de um evento carrega só identificadores e códigos,")
    lines.append("> sem dado pessoal; mudança incompatível de carga exige nova versão")
    lines.append("> do evento. Publicação fora do catálogo, em versão não declarada ou")
    lines.append("> com carga fora do esquema é recusada no caminho de publicação")
    lines.append("> (`BusinessEventPublisher` e a outbox validada da P1.9).")
    lines.append("")
    lines.append(f"Pacote: **{manifest['id']}** — versão {manifest['version']} "
                 f"(plataforma mínima {manifest['minPlatformVersion']})")
    lines.append("")
    events = manifest.get("events", [])
    if events:
        lines.append("## Eventos")
        lines.append("")
        for e in events:
            lines.append(f"### `{e['name']}` v{e['version']} <!-- event: {e['name']}@v{e['version']} -->")
            lines.append("")
            lines.append(f"- Descrição: {e['description']}")
            lines.append(f"- Permissão exigida de quem recebe: `{e['requiredPermission']}`")
            lines.append(f"- Consumidores declarados: {', '.join(e['consumers'])}")
            lines.append(f"- Tarefa de origem: {e['originTask']}")
            schema = e["payloadSchema"]
            props = schema.get("properties", {})
            required = set(schema.get("required", []))
            lines.append("- Carga (só identificadores e códigos):")
            for name, prop in props.items():
                mark = "obrigatório" if name in required else "opcional"
                lines.append(f"  - `{name}` ({prop.get('type', '?')}, {mark})")
            lines.append("")
    else:
        lines.append("## Eventos")
        lines.append("")
        lines.append("Nenhum evento de negócio registrado até esta onda.")
        lines.append("")
        if manifest.get("eventsNote"):
            lines.append(manifest["eventsNote"])
            lines.append("")
    lines.append("## Consumidores declarados (só declarados, não construídos)")
    lines.append("")
    for c in manifest.get("declaredEventConsumers", []):
        lines.append(f"- {c['name']} (`{c['task']}`) <!-- consumer: {c['task']} -->")
    lines.append("")
    gaps = manifest.get("eventGaps", [])
    if gaps:
        lines.append("## Lacunas (tipos fora do catálogo)")
        lines.append("")
        for g in gaps:
            lines.append(f"- `{g['name']}` <!-- gap: {g['name']} --> — {g['reason']}")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Catálogo gerado: {OUT} ({len(events)} eventos, "
          f"{len(manifest.get('declaredEventConsumers', []))} consumidores, {len(gaps)} lacunas)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
