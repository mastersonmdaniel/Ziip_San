#!/usr/bin/env python3
"""Coerencia estatica da ED-78: catalogo da NR 15 com a data de
norma de maio/2028; nenhum limiar de norma como constante de
calculo; SQL sem semente, com UNIQUE (tenant_id, id) por tabela e
IVI travado ao ILI; nenhum calculo proprio de balanco no pacote
nr15 (a ED-85 e a unica calculadora)."""
import pathlib, re, sys

root = pathlib.Path(__file__).resolve().parents[2]
main = root / "intelligence/ziipsan-losses/src/main/java/br/com/ziipsan/intelligence/losses/nr15"
java = "\n".join(p.read_text(encoding="utf-8") for p in main.glob("*.java"))
sql = (root / "intelligence/ziipsan-losses/src/main/resources/db/pendente/ED-78__nr15_outputs.sql").read_text(encoding="utf-8")
errors = []

# Catalogo da norma: codigo da P1.34, resolucao e data de verificacao.
for token in ["ana-nr-15-res-275-2025", "Res. ANA 275/2025", "LocalDate.of(2028, Month.MAY, 20)"]:
    if token not in java:
        errors.append("catalogo da NR 15 sem: " + token)

# Sem calculo proprio de balanco: o pacote nr15 nao pode subtrair
# consumo autorizado de entrada liquida (isso e a ED-85).
if re.search(r"netInput\w*\s*-\s*\w*[Aa]uthorized", java):
    errors.append("pacote nr15 recompoe balanco (subtracao entrada-autorizado)")

# SQL: sem semente; UNIQUE (tenant_id, id) nas 7 tabelas; FKs compostas.
if re.search(r"(?i)INSERT\s+INTO", sql):
    errors.append("SQL com linha semeada")
for table in ["nr15_annual_balances", "nr15_annual_declarations", "nr15_management_plans",
              "nr15_plan_actions", "nr15_plan_metering_evolution",
              "nr15_plan_declared_targets", "nr15_plan_monitoring"]:
    if "CREATE TABLE IF NOT EXISTS " + table not in sql:
        errors.append("tabela ausente: " + table)
    if f"register_tenant_table('{table}')" not in sql:
        errors.append("tabela sem registro RLS: " + table)
if sql.count("UNIQUE (tenant_id, id)") < 7:
    errors.append("menos de 7 UNIQUE (tenant_id, id) (uma por tabela, licao C3)")
for fk in ["FOREIGN KEY (tenant_id, balance_id)", "FOREIGN KEY (tenant_id, declaration_id)",
           "FOREIGN KEY (tenant_id, plan_id)", "FOREIGN KEY (tenant_id, revision_of_id)"]:
    if fk not in sql:
        errors.append("FK composta ausente: " + fk)
if "ivi IS NOT DISTINCT FROM ili" not in sql:
    errors.append("IVI nao travado igual ao ILI no SQL")
if "iviOrNull" not in java:
    errors.append("IVI ausente do nucleo Java")

print("COHERENCE_ED78=" + ("FAIL: " + "; ".join(errors) if errors else "OK"))
sys.exit(1 if errors else 0)
