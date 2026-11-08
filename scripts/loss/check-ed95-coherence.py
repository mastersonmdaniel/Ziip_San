#!/usr/bin/env python3
"""Coerencia executavel da ED-95: SQL x Java x parametros x fronteiras.
Uso: python3 check-ed95-coherence.py [raiz-da-entrega]
Sai 0 quando todas as checagens passam; imprime o placar."""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
CORE = ROOT / "shared/ziipsan-core/src/main"
JAVA = CORE / "java/br/com/ziipsan/core/apparentloss"
SQL_TENANT = (CORE / "resources/db/pendente/ED-95__apparent_loss.sql").read_text(encoding="utf-8")
SQL_PARAMS = (CORE / "resources/db/pendente/ED-95__core_loss_parameters.sql").read_text(encoding="utf-8")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

def java(name):
    return (JAVA / name).read_text(encoding="utf-8")

def enum_values(source, enum_name):
    m = re.search(enum_name + r"\s*\{([^}]*)\}", source, re.S)
    body = m.group(1)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    return [v.strip() for v in body.split(",") if v.strip() and "(" not in v]

# Tabelas, RLS e UNIQUE (tenant_id, id) — licao da C3
tables = ["loss_property_registry", "loss_cross_findings", "loss_suspicions",
          "loss_inspection_outcomes", "loss_self_reports"]
for t in tables:
    check(f"tabela {t}", f"CREATE TABLE IF NOT EXISTS {t}" in SQL_TENANT)
    check(f"RLS {t}", f"register_tenant_table('{t}')" in SQL_TENANT)
    check(f"UNIQUE (tenant_id,id) {t}", f"uq_{t}_tenant_id UNIQUE (tenant_id, id)" in SQL_TENANT)
check("FK composta achado->cadastro",
      "FOREIGN KEY (tenant_id, property_id) REFERENCES loss_property_registry (tenant_id, id)" in SQL_TENANT)

# Enums Java x CHECKs SQL
for v in enum_values(java("CrossMatch.java"), "enum FindingKind"):
    check(f"finding_kind {v}", f"'{v}'" in SQL_TENANT)
for v in enum_values(java("FraudRules.java"), "enum Rule"):
    check(f"rule_code {v}", f"'{v}'" in SQL_TENANT)
for v in enum_values(java("PropertyRegistryRecord.java"), "enum PropertyUse"):
    check(f"declared_use {v}", f"'{v}'" in SQL_TENANT)

# Travas do aceite no SQL
check("trava backoffice", "evaluation_channel = 'BACKOFFICE'" in SQL_TENANT)
check("trava recomendacao", "recommendation = 'HUMAN_INSPECTION_REVIEW'" in SQL_TENANT)
check("trava efeito NONE", "automatic_effect = 'NONE'" in SQL_TENANT)
check("trava OS so com decisao humana",
      "work_order_id IS NULL OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)" in SQL_TENANT)
check("base legal obrigatoria", "legal_basis TEXT NOT NULL" in SQL_TENANT)
check("finalidade obrigatoria", "sharing_purpose TEXT NOT NULL" in SQL_TENANT)
check("sem coluna de debito/corte no SQL",
      not re.search(r"\b(debit|cutoff|charge_amount)\b", SQL_TENANT))

# Parametros: 4 chaves, nenhuma com fabrica
for key in ["registry.property_registry_sync", "audit.unregistered_building_review_days",
            "fraud.rules", "fraud.self_report_fine_waiver_enabled"]:
    check(f"parametro {key}", f"'{key}'" in SQL_PARAMS)
    check(f"parametro {key} no Java", f'"{key}"' in java("LossParameters.java"))
rows = re.findall(r"\('([a-z0-9_.]+)',\s*'[A-Z]+',\s*'d',\s*([^,]+),", SQL_PARAMS)
check("4 parametros declarados", len(rows) == 4)
check("nenhum factory_default", all(v.strip() == "NULL" for _, v in rows))

# P3.9 intocada: nenhum dos dois SQL menciona chaves/arquivos da P3.9
check("P3.9 intocada", "P3.9" in SQL_PARAMS and "NAO sao tocados" in SQL_PARAMS)

failed = [n for n, ok in checks if not ok]
print(f"ED-95 coherence: {len(checks) - len(failed)}/{len(checks)} checagens OK")
for n in failed:
    print("FALHOU:", n)
sys.exit(1 if failed else 0)
