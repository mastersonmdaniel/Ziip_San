#!/usr/bin/env python3
"""Coerencia ED-86: parametros nomeados no aceite presentes; NENHUM
valor de fabrica para pressure.min_mca/pressure.max_mca; consumo (nao
redefinicao) da ED-17, da ED-85 e do achado da ED-92; SQL com UNIQUE
(tenant_id,id) para cada FK composta criada nesta entrega; nenhuma
linha semeada."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
java = "\n".join(p.read_text(encoding="utf-8") for p in root.rglob("*.java") if "/src/main/" in str(p))
sql_path = root/"intelligence/ziipsan-losses/src/main/resources/db/pendente/ED-86__pressure_leak_control.sql"
sql = sql_path.read_text(encoding="utf-8")
bad = []
for key in ["pressure.min_mca", "pressure.max_mca",
            "leak.repair_phase_targets_hours", "leak.survey_cycle_days_by_dmc_class"]:
    if key not in java: bad.append("parametro do aceite ausente: " + key)
# fronteira dura: limites sem default no SQL e sem constante de fabrica no Java
# (comentarios removidos antes da checagem: a propria fronteira e documentada neles)
sql_code = "\n".join(l for l in sql.splitlines() if not l.strip().startswith("--"))
for m in re.finditer(r"(?im)^\s+(min_mca|max_mca)\s+NUMERIC[^,\n]*", sql_code):
    if "DEFAULT" in m.group(0).upper():
        bad.append("limite de pressao com DEFAULT no SQL (valor de fabrica proibido): " + m.group(0))
if "MissingParameterException" not in java:
    bad.append("ausencia de parametro nao e erro explicito (MissingParameterException ausente)")
if "12218" not in java:
    bad.append("fronteira da ABNT NBR 12218:2017 nao declarada no codigo")
if re.search(r"(?i)INSERT\s+INTO", sql): bad.append("SQL com linha semeada")
# consumo das deps: serie da ED-17 por porta, VMN da ED-85, achado da ED-92
if "PressureSeriesPort" not in java or ("MeasuringPoint" not in java and "ED-17" not in java):
    bad.append("serie da ED-17 nao consumida por porta")
if "NightFlowAnalyzer" not in java: bad.append("vazao noturna da ED-85 nao consumida (NightFlowAnalyzer)")
if "LeakDetectionFinding" not in java: bad.append("achado da ED-92 nao consumido (LeakDetectionFinding)")
if re.search(r"(?i)CREATE\s+TABLE[^;]*leak_detection_findings", sql_code):
    bad.append("tabela de achado recriada (proibido: o achado e da ED-92)")
for uniq in ["uq_pressure_zones_tenant_id", "uq_pressure_points_tenant_id",
             "uq_pressure_alarms_tenant_id", "uq_prv_adjustments_tenant_id",
             "uq_pressure_arc_stats_tenant_id", "uq_low_water_links_tenant_id",
             "uq_leak_nf_snapshots_tenant_id", "uq_leak_campaigns_tenant_id",
             "uq_leak_evidence_tenant_id", "uq_leak_campaign_findings_tenant_id",
             "uq_leak_timelines_tenant_id"]:
    if uniq not in sql: bad.append("UNIQUE (tenant_id,id) ausente: " + uniq)
print("COHERENCE_ED86=" + ("FAIL " + "; ".join(bad) if bad else "OK"))
sys.exit(1 if bad else 0)
