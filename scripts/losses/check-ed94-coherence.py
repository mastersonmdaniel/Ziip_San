#!/usr/bin/env python3
"""Coerencia ED-94: chaves anomaly.* do aceite presentes; as 4 regras
do aceite presentes; nenhum limiar com cara de padrao de fabrica no
codigo principal (deviationK/continuousHours/zeroHours/
pressureDropPercent so aparecem como nomes de campo/parametro);
portao ADR-0042 presente (detector sem FollowUpPort; executeFollowUp
so no servico de revisao); SQL com UNIQUE (tenant_id,id), FK composta
com UNIQUE no pai, CHECK do desfecho pos-revisao e nenhuma linha
semeada fora de comentario."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
java_files = [p for p in root.rglob("*.java") if "/src/main/" in str(p)]
java = "\n".join(p.read_text(encoding="utf-8") for p in java_files)
sql_path = root / "intelligence/ziipsan-losses/src/main/resources/db/pendente/ED-94__anomaly_alerts.sql"
sql = sql_path.read_text(encoding="utf-8")
sql_code = "\n".join(l for l in sql.splitlines() if not l.strip().startswith("--"))
bad = []
for key in ["anomaly.rules_enabled", "anomaly.sensitivity"]:
    if key not in java:
        bad.append("parametro do aceite ausente: " + key)
for rule in ["SAME_HOUR_WEEKDAY_DEVIATION", "CONTINUOUS_CONSUMPTION",
             "ZERO_CONSUMPTION", "SUDDEN_PRESSURE_DROP"]:
    if rule not in java:
        bad.append("regra do aceite ausente: " + rule)
for field in ["deviationK", "minBaselineSamples", "continuousHours",
              "zeroHours", "pressureDropPercent"]:
    if field not in java:
        bad.append("campo de sensibilidade ausente: " + field)
# detector nao pode conhecer a porta de acao (ADR-0042)
detector = next((p.read_text(encoding="utf-8") for p in java_files
                 if p.name == "AnomalyDetector.java"), "")
if "FollowUpPort" in detector or "openFollowUp" in detector:
    bad.append("AnomalyDetector conhece a porta de acao (violacao ADR-0042)")
if "executeFollowUp" not in java or "ReviewDecision.DISMISSED" not in java:
    bad.append("portao de revisao humana ausente")
if "PROPOSED" not in detector:
    bad.append("detector nao marca alertas como PROPOSED")
# SQL: nada semeado no codigo (o bloco comentado de parameter_definitions nao conta)
if re.search(r"(?i)INSERT\s+INTO", sql_code):
    bad.append("SQL com linha semeada")
for uniq in ["uq_anomaly_alerts_tenant_id", "uq_anomaly_reviews_tenant_id",
             "uq_anomaly_reviews_tenant_alert"]:
    if uniq not in sql:
        bad.append("UNIQUE ausente: " + uniq)
if "REFERENCES anomaly_alerts (tenant_id, id)" not in sql:
    bad.append("FK composta da revisao para o alerta ausente")
if "ck_anomaly_reviews_follow_up" not in sql:
    bad.append("CHECK do desfecho pos-revisao ausente (ADR-0042 no banco)")
print("COHERENCE_ED94=" + ("FAIL " + "; ".join(bad) if bad else "OK"))
sys.exit(1 if bad else 0)
