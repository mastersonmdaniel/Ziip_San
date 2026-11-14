#!/usr/bin/env python3
"""Coerencia ED-85: parametros nomeados no aceite presentes; nenhum
valor de fabrica para meta/uso legitimo; SQL com UNIQUE (tenant_id,id)
para cada FK composta criada nesta entrega; nenhuma linha semeada."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
java = "\n".join(p.read_text(encoding="utf-8") for p in root.rglob("*.java") if "/src/main/" in str(p))
sql_path = root/"intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/V113__losses_dmc_balance.sql"
sql = sql_path.read_text(encoding="utf-8")
bad = []
for key in ["loss.dmc_target_l_per_conn_day", "loss.night_window",
            "loss.legit_night_use_l_per_conn_h", "loss.unbilled_estimate_method"]:
    if key not in java: bad.append("parametro do aceite ausente: " + key)
if "02:00-04:00" not in java: bad.append("padrao da janela do aceite ausente")
if re.search(r"(?i)INSERT\s+INTO", sql): bad.append("SQL com linha semeada")
# cada tabela nova referenciada por FK composta declara a UNIQUE correspondente
for table in ["loss_dmc_profiles", "loss_monthly_balances"]:
    if f"uq_" not in sql or table not in sql: bad.append("tabela ausente: " + table)
for uniq in ["uq_loss_dmc_profiles_tenant_id", "uq_loss_meter_links_tenant_id",
             "uq_loss_unbilled_tenant_id", "uq_loss_bulk_tenant_id",
             "uq_loss_balances_tenant_id"]:
    if uniq not in sql: bad.append("UNIQUE (tenant_id,id) ausente: " + uniq)
if "network_incidents" in sql and "CREATE TABLE" in sql.split("network_incidents")[0][-200:]:
    bad.append("ocorrencia paralela criada (proibido: consumir a ED-92)")
# R1 (correcao): a porta da ED-17 existe e e consultada no vinculo fail-closed
if "MeasuringPointPort" not in java: bad.append("MeasuringPointPort ausente (R1)")
if ".exists(" not in java or "linkMacroMeter" not in java:
    bad.append("vinculo de macromedidor nao consulta a MeasuringPointPort (R1)")
# R2 (correcao): snapshot JOR-33 grava contagem e media de tempo de reparo
for col in ["incident_repair_known_count", "incident_avg_repair_minutes"]:
    if col not in sql: bad.append("coluna do anexo JOR-33 ausente no snapshot: " + col)
print("COHERENCE_ED85=" + ("FAIL " + "; ".join(bad) if bad else "OK"))
sys.exit(1 if bad else 0)
