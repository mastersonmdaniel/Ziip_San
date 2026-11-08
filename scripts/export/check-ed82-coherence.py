#!/usr/bin/env python3
"""Coerencia estatica da ED-82: PNQS nunca como certificacao/leiaute,
nenhum mapeamento ISO de fabrica, checklist sempre [SEM FONTE]."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
main = root / "intelligence/ziipsan-reporting/src/main/java/br/com/ziipsan/reporting/exportprofile"
sql = (root / "intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-82__export_profiles.sql").read_text(encoding="utf-8")
java = "\n".join(p.read_text(encoding="utf-8") for p in main.glob("*.java"))
errors = []
if "INSERT INTO" in sql: errors.append("SQL semeia dado")
if "UNIQUE (tenant_id, id)" not in sql: errors.append("falta UNIQUE (tenant_id, id) (C3)")
if "FOREIGN KEY (tenant_id, document_id)" not in sql: errors.append("falta FK composta das linhas")
if "PNQS_NATURE_NOTICE" not in java: errors.append("aviso de natureza do PNQS ausente")
if "PNQS_NEUTRAL_STRUCTURE_NOTICE" not in java: errors.append("aviso de estrutura neutra ausente")
if "[SEM FONTE]" not in java: errors.append("marcador [SEM FONTE] do checklist ausente")
if "ABNT NBR ISO 37120:2021" not in java: errors.append("referencia ISO 37120 ausente")
for n in ("12586", "12587", "13133", "14166"):
    if n not in java: errors.append(f"NBR {n} ausente do checklist")
print("COHERENCE_ED82=" + ("FAIL: " + "; ".join(errors) if errors else "OK"))
sys.exit(1 if errors else 0)
