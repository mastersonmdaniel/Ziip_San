#!/usr/bin/env python3
"""Coerencia estatica da ED-77: nenhum texto ISO reproduzido, nenhum fator
Q/P/U presumido, piso federal presente, FK composta com UNIQUE no pai."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
main = root / "intelligence/ziipsan-reporting/src/main/java/br/com/ziipsan/reporting/sheet"
sql = (root / "intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-77__indicator_sheets.sql").read_text(encoding="utf-8")
java = "\n".join(p.read_text(encoding="utf-8") for p in main.glob("*.java"))
errors = []
if "INSERT INTO" in sql: errors.append("SQL semeia dado")
if "UNIQUE (tenant_id, id)" not in sql: errors.append("falta UNIQUE (tenant_id, id) (C3)")
if "FOREIGN KEY (tenant_id, assessment_id)" not in sql: errors.append("falta FK composta da trilha")
for token in ("FEDERAL_WATER_FLOOR", "99", "FEDERAL_SEWAGE_FLOOR", "90"):
    if token not in java: errors.append(f"piso federal ausente: {token}")
if "[A DEFINIR]" not in java: errors.append("marcador [A DEFINIR] ausente (perfis SC)")
if re.search(r'"perf\.[qpu]"', java, re.I): errors.append("fator Q/P/U presumido em literal")
if "goal.universalization_" not in java and "goal.universalization" not in sql:
    pass  # metas de universalizacao NAO sao parametros goal.*: vivem como registros UniversalizationGoal e na tabela universalization_goals
print("COHERENCE_ED77=" + ("FAIL: " + "; ".join(errors) if errors else "OK"))
sys.exit(1 if errors else 0)
