#!/usr/bin/env python3
"""Coerencia executavel da ED-59: SQL x Java x aceite. Uso: python3 check-ed59-coherence.py"""
import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
CORE = ROOT / "shared/ziipsan-core/src/main"
TENANT_SQL = (CORE / "resources/db/pendente/ED-59__fleet.sql").read_text()
PARAM_SQL = (CORE / "resources/db/pendente/ED-59__core_fleet_parameters.sql").read_text()
JAVA = {p.name: p.read_text() for p in (CORE / "java/br/com/ziipsan/core/fleet").glob("*.java")}
ALL_JAVA = "\n".join(JAVA.values())
fails = []

def check(cond, msg):
    print(("OK  " if cond else "FAIL") + " " + msg)
    if not cond:
        fails.append(msg)

tables = re.findall(r"CREATE TABLE IF NOT EXISTS (\w+)", TENANT_SQL)
check(len(tables) == 10, f"10 tabelas tenant (achadas {len(tables)})")
for t in tables:
    check(f"CONSTRAINT uq_{t}_tenant_id UNIQUE (tenant_id, id)" in TENANT_SQL,
          f"{t}: UNIQUE (tenant_id, id) para FK composta (licao C3)")
    check(f"register_tenant_table('{t}')" in TENANT_SQL, f"{t}: RLS via register_tenant_table")
for fk in ["asset (tenant_id, id)", "service_orders (tenant_id, id)", "fleet_vehicle (tenant_id, asset_id)",
           "fleet_crew (tenant_id, id)", "fleet_crew_assignment (tenant_id, id)"]:
    ok = fk in TENANT_SQL
    if fk.startswith("fleet_vehicle"):
        # Convencao R1: as 5 filhas referenciam o asset_id da P1.35 (identidade
        # do Java); a forma antiga (PK surrogate) NAO pode existir no SQL.
        ok = ok and "fleet_vehicle (tenant_id, id)" not in TENANT_SQL \
            and TENANT_SQL.count("REFERENCES fleet_vehicle (tenant_id, asset_id)") == 5
    check(ok, f"FK composta para {fk}")
for code in ["HIDROJATO", "VACUO", "LIMPA_FOSSA", "RETROESCAVADEIRA"]:
    check(code in TENANT_SQL and code in ALL_JAVA, f"equipamento especial {code} no SQL e no Java")
check("ck_fleet_vehicle_pm_trigger_interval" in TENANT_SQL, "PM de veiculo: gatilho x intervalo amarrados no SQL")
for key in ["fleet.checklist_required", "fleet.docs_alert_days", "fleet.checklist_items",
            "fleet.consumption_deviation_percent"]:
    check(key in PARAM_SQL and key in ALL_JAVA, f"parametro {key} no SQL do core e no Java")
check(PARAM_SQL.count("NULL,") >= 4 and "'{\"CRITICAL\"" not in PARAM_SQL,
      "parametros fleet.* sem factory_default (NULL)")
check("maint.pm_generation_lead_days" in ALL_JAVA and "'maint." not in PARAM_SQL,
      "manutencao reusa parametros maint.* da ED-56 (nao recria)")
check("P3.9" in PARAM_SQL and "NAO sao tocados" in PARAM_SQL, "14 valores da P3.9 declarados intocados")
check("FLEET_ASSET" in ALL_JAVA and "existsFleetTarget" in ALL_JAVA,
      "verificador de existencia do alvo FLEET_ASSET (P1.31) presente")
check("EQUIPMENT_WHEN_ED59" in ALL_JAVA, "restricao dura da ED-36 referenciada (fato equipmentAvailable)")
check("VENCIDO" in JAVA["FieldCrewScheduling.java"], "recusa de documento vencido no portao de alocacao")
check("VEHICLE_PREVENTIVE" in ALL_JAVA and "VEHICLE_CORRECTIVE" in ALL_JAVA,
      "finalidades de OS de veiculo pela rotina da ED-56")
print(f"\n{('FALHAS: ' + str(len(fails))) if fails else 'COERENCIA OK'}")
sys.exit(1 if fails else 0)
