#!/usr/bin/env python3
"""Coerencia ED-81: parametros reais do core V3; fonte ED-85 sem
recalculo (nenhuma referencia a perdas/ILI do balanco); edicao 2 nao
adotavel; SQL com UNIQUE (tenant_id,id) para cada FK composta nova,
sem INSERT e sem DELETE; objetivos via cert_objectives."""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parents[2]
java = "\n".join(p.read_text(encoding="utf-8") for p in root.rglob("*.java") if "/src/main/" in str(p))
sql_path = root/"shared/ziipsan-core/src/main/resources/db/pendente/ED-81__certification_water_iso46001.sql"
sql = sql_path.read_text(encoding="utf-8")
bad = []
for key in ["cert.iso46001.enabled", "cert.iso46001.edition", "cert.scope_statement"]:
    if key not in java: bad.append("parametro do core V3 ausente no codigo: " + key)
if "PUBLISHED_UNDER_REVISION" not in java or "DRAFT_TRACKED" not in java:
    bad.append("acompanhamento de edicoes do aceite ausente (2019 em revisao / projeto ed.2)")
if re.search(r"(?i)INSERT\s+INTO", sql): bad.append("SQL com linha semeada")
if re.search(r"(?i)DELETE\s+FROM", sql): bad.append("SQL com DELETE (desligar nao apaga)")
for uniq in ["uq_w46001_edition_tenant_id", "uq_w46001_units_tenant_id",
             "uq_w46001_sources_tenant_id", "uq_w46001_reviews_tenant_id",
             "uq_w46001_plans_tenant_id", "uq_w46001_items_tenant_id"]:
    if uniq not in sql: bad.append("UNIQUE (tenant_id,id) ausente: " + uniq)
for fk in ["REFERENCES consumer_units (tenant_id, id)",
           "REFERENCES cert_objectives (tenant_id, id)",
           "REFERENCES cert_w46001_source_snapshots (tenant_id, id)",
           "REFERENCES cert_w46001_action_plans (tenant_id, id)"]:
    if fk not in sql: bad.append("FK composta esperada ausente: " + fk)
if "ck_w46001_draft_not_adoptable" not in sql:
    bad.append("trava de projeto nao adotavel ausente no SQL")
if "ck_w46001_closed_needs_baseline" not in sql:
    bad.append("trava de fechamento sem linha de base ausente no SQL")
# fronteira ED-85: a fonte nao pode recalcular o balanco
for forbidden in ["systemInput", "nonRevenue", "ili(", "realLosses"]:
    if forbidden in java: bad.append("fronteira violada: recalculo do balanco da ED-85 (" + forbidden + ")")
if "SOCIAL_TANKER" in java: bad.append("uso social da ED-85 incluido (aceite nomeia operacionais/emergenciais/exportada)")
print("COHERENCE_ED81=" + ("FAIL " + "; ".join(bad) if bad else "OK"))
sys.exit(1 if bad else 0)
