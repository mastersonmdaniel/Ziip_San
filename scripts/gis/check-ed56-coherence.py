#!/usr/bin/env python3
"""Coerencia executavel da ED-56: SQL pendente x Java da entrega x aceite GIS-19.
Uso: python3 check-ed56-coherence.py <raiz ziipsan-producao>
Exit 1 se houver falha."""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
E = ROOT / "ed-56/entrega"
GIS = E / "intelligence/ziipsan-gis"
SQL = (GIS / "src/main/resources/db/pendente/ED-56__gis_assets_maintenance.sql").read_text(encoding="utf-8")
CORE_SQL = (E / "shared/ziipsan-core/src/main/resources/db/pendente/ED-56__core_maintenance_parameters.sql").read_text(encoding="utf-8")
JAVA = GIS / "src/main/java/br/com/ziipsan/intelligence/gis/asset"
checks, failures = 0, []

def check(ok, label):
    global checks
    checks += 1
    if not ok:
        failures.append(label)

def read(p):
    return p.read_text(encoding="utf-8")

model = read(JAVA / "AssetInventoryModel.java")
mcpsb = read(JAVA / "McpsbCodes.java")
pm = read(JAVA / "PmPlanService.java")
valve = read(JAVA / "ValveHydrantProgram.java")
cctv = read(JAVA / "CctvInspectionService.java")
cond = read(JAVA / "ConditionMaintenance.java")
risk = read(JAVA / "RiskRenewalService.java")
recon = read(JAVA / "ReconciliationService.java")
ports = read(JAVA / "AssetPorts.java")
coverage = read(JAVA / "AssetCoverage.java")
alljava = "\n".join(read(p) for p in JAVA.rglob("*.java"))

# 1. Tabelas do SQL com RLS
for t in ["asset_pm_plan_line", "asset_valve_exercise", "asset_hydrant_test",
          "asset_cctv_inspection", "asset_cctv_defect", "asset_risk_assessment",
          "asset_reconciliation_run"]:
    check(f"CREATE TABLE IF NOT EXISTS {t} " in SQL, f"tabela {t} no SQL")
    check("register_tenant_table('" + t + "')" in SQL, f"RLS de {t}")

# 2. SQL nao recria a entidade da P1.35 nem o codigo patrimonial (DAD-26)
check("CREATE TABLE IF NOT EXISTS asset (" not in SQL, "SQL nao recria a tabela asset (P1.35)")
check("REFERENCES asset (tenant_id, id)" in SQL, "FKs apontam para o asset da P1.35")

# 3. Parametros do aceite no SQL do core
for p in ["maint.pm_generation_lead_days", "maint.pm_overdue_tolerance_days",
          "maint.checklist_templates", "maint.valve_exercise_years_by_class",
          "maint.hydrant_test_months", "maint.condition_triggers",
          "inv.critical_spares", "asset.cctv_standard"]:
    check("'" + p + "'" in CORE_SQL, f"parametro {p} no SQL do core")
    check(p in alljava, f"parametro {p} citado no Java")

# 4. Fabrica: so o AWWA tem valor; os demais NULL
check('{"CRITICAL": 1, "DEFAULT": 5}' in CORE_SQL, "fabrica AWWA (1/5) registrada")
for line in CORE_SQL.splitlines():
    if "factory" in line.lower():
        continue
check(CORE_SQL.count("'b', NULL") == 7, "7 parametros sem factory_default (NULL)")
check("P3.9" in CORE_SQL and "NAO sao tocados" in CORE_SQL, "14 valores da P3.9 declarados intocados")

# 5. Gatilhos/estados do SQL == Java
for s in ["TIME", "RUNNING_HOURS", "PUMPED_VOLUME"]:
    check(f"'{s}'" in SQL or f"('{s}'" in SQL or f", '{s}'" in SQL or f"IN ('{s}'" in SQL or s in SQL, f"gatilho {s} no SQL")
    check(s in pm or s in cond, f"gatilho {s} no Java")
for s in ["PACP", "WRC"]:
    check(s in SQL, f"padrao CCTV {s} no CHECK do SQL")
    check(s in cctv, f"padrao CCTV {s} no Java")
for s in ["LOW", "MEDIUM", "HIGH", "CRITICAL"]:
    check(s in SQL, f"faixa de risco {s} no SQL")
    check(s in risk, f"faixa de risco {s} no Java")

# 6. Portas para as dependencias executadas
check("WorkOrderPort" in ports and "ED-36" in ports, "porta de OS referencia a ED-36")
check("AccountingPort" in ports and "ED-32" in ports, "porta contabil referencia a ED-32")
check("ValveOperabilityPort" in ports and "P-ED54-05" in ports, "porta de operabilidade responde a P-ED54-05")
check("InventoryPort" in ports and "P1.35" in ports, "porta de inventario referencia a P1.35")

# 7. Sem dado inventado: marcadores honestos presentes
check("[SEM FONTE]" in mcpsb, "nts_sds marcado [SEM FONTE] sem a lista oficial")
check("[A DEFINIR]" in cctv, "padrao CCTV marcado [A DEFINIR]")
check("AWWA" in valve and "AWWA_CRITICAL_YEARS = 1" in valve and "AWWA_DEFAULT_YEARS = 5" in valve,
      "referencia AWWA 1/5 no Java (unica fabrica, do aceite)")

# 8. Cobertura do aceite
for code in ["OPE-09", "OPE-12", "OPE-20", "OPE-38", "GES-14", "COE-05", "DAD-26",
             "TR-155", "TR-1010", "SG-1180"]:
    check(code in coverage, f"cobertura {code}")

# 9. OS so pela porta: nenhum acesso direto a servico de OS no pacote
for forbidden in ["ServiceOrderService", "MultichannelDispatchService", "sendSms", "JpaRepository"]:
    check(forbidden not in alljava.replace("MultichannelDispatchService da ED-41", ""),
          f"sem acoplamento direto ({forbidden} ausente do codigo)")

# 10. Recorte O8-1: nenhum solver de equipes
def solver_hits(text):
    hits = []
    for line in text.splitlines():
        low = line.lower()
        if "bloquead" in low or "recorte o8-1" in low or "sem solver" in low:
            continue
        if re.search(r"pgr_[a-z]", low):
            hits.append("pgr_*")
        for pat in [r"dijkstra", r"travelling salesman", r"travel\s*time", r"tempo de viagem"]:
            if re.search(pat, low):
                hits.append(pat)
    return hits

check(not solver_hits(alljava), "sem solver de equipes no Java (O8-1)")
check(not solver_hits(SQL), "sem solver de equipes no SQL (O8-1)")

print(f"{checks} checagens, {len(failures)} falhas")
for f in failures:
    print("FALHA:", f)
sys.exit(1 if failures else 0)
