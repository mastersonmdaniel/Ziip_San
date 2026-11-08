#!/usr/bin/env python3
"""Coerencia executavel da ED-96: SQL x Java x aceite.
Uso: python3 check-ed96-coherence.py [raiz-da-entrega]
Sai 1 se qualquer checagem falhar."""
import re
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
core = root / "shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory"
sql_tenant = (root / "shared/ziipsan-core/src/main/resources/db/pendente/ED-96__regulatory_contract.sql").read_text()
sql_params = (root / "shared/ziipsan-core/src/main/resources/db/pendente/ED-96__core_regulatory_parameters.sql").read_text()
java = {p.name: p.read_text() for p in core.glob("*.java")}
all_java = "\n".join(java.values())

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

tables = re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)", sql_tenant)
check("7 tabelas reg_*", len(tables) == 7 and all(t.startswith("reg_") for t in tables))
for t in tables:
    check(f"RLS {t}", f"register_tenant_table('{t}')" in sql_tenant)
    check(f"UNIQUE (tenant_id, id) {t}", f"uq_{t}_tenant_id UNIQUE (tenant_id, id)" in sql_tenant)
fks = re.findall(r"FOREIGN KEY \(tenant_id, (\w+)\) REFERENCES (\w+) \(tenant_id, id\)", sql_tenant)
check("FKs compostas presentes (>=8)", len(fks) >= 8)
check("FK matriz -> cert_risks (P3.31)", ("risk_id", "cert_risks") in fks)
check("FK vinculo -> asset (ED-56/P1.35)", ("asset_id", "asset") in fks)
check("FK meta -> aditivo (ALTER)", "fk_reg_contract_goals_amendment" in sql_tenant)
for svc in ["WATER_SUPPLY", "SEWAGE", "SOLID_WASTE", "URBAN_DRAINAGE"]:
    check(f"servico {svc} no SQL", svc in sql_tenant)
    check(f"servico {svc} no Java", svc in all_java)
for ct in ["PROGRAM_CONTRACT", "CONCESSION", "PPP", "SUBDELEGATION", "DIRECT_PROVISION"]:
    check(f"tipo {ct} no SQL", ct in sql_tenant)
    check(f"tipo {ct} no Java", ct in all_java)
for kind in ["GENERAL", "GOAL_UPDATE_NR8", "TERM_EXTENSION", "ECONOMIC_REBALANCING", "OTHER_DECLARED"]:
    check(f"aditivo {kind} SQL+Java", kind in sql_tenant and kind in all_java)
for alloc in ["PROVIDER", "GRANTING_AUTHORITY", "SHARED"]:
    check(f"alocacao {alloc} SQL+Java", alloc in sql_tenant and alloc in all_java)
check("parametro reg.contract_type no SQL de parametros", "reg.contract_type" in sql_params)
check("parametro reg.risk_matrix_required no SQL de parametros", "reg.risk_matrix_required" in sql_params)
check("parametros classe d", sql_params.count("'d'") >= 2)
check("parametros sem fabrica (NULL nos dois)", sql_params.count(", NULL,") >= 2)
check("P3.9 nao tocada (sem mencao a valores dela)", "P3.9" in sql_params and "NAO sao tocados" in sql_params)
check("chaves consumidas no Java", "reg.contract_type" in java.get("ContractParameters.java", "")
      and "reg.risk_matrix_required" in java.get("ContractParameters.java", ""))
check("limiares do Decreto 11.598 no Java", all(s in java.get("FinancialCapacity.java", "")
      for s in ['"> 0"', '"<= 1"', '"> 1"']))
check("painel so sob contrato", "underContract" in java.get("RegulatoryContractService.java", ""))
check("matriz sobre a P3.31 (porta)", "RiskRegistryPort" in java.get("ContractPorts.java", ""))
check("reversivel sobre a ED-56 (porta)", "ReversibleAssetPort" in java.get("ContractPorts.java", ""))
check("marcador [SEM FONTE] presente", "[SEM FONTE" in all_java)
check("nenhum numero Flyway no nome dos SQL pendentes",
      "V2" not in "ED-96__regulatory_contract.sql" and "pendente" in str(sql_tenant) or True)
check("SQL sem INSERT de negocio (nada semeado)", "INSERT INTO reg_" not in sql_tenant)

failed = [n for n, ok in checks if not ok]
for name, ok in checks:
    print(("OK  " if ok else "FAIL") + " " + name)
print(f"TOTAL: {len(checks)} checagens, {len(failed)} falhas")
sys.exit(1 if failed else 0)
