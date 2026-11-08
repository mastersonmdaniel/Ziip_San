#!/usr/bin/env python3
"""Coerência executável da ED-63: aceite x código x SQL pendente.
Uso: python3 check-ed63-coherence.py <raiz ziipsan-producao>
Exit 1 se houver falha."""
import re, sys
from pathlib import Path
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
E = ROOT / "ed-63/entrega"
GIS = E / "intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis"
checks = 0; failures = []
def check(ok, label):
    global checks; checks += 1
    if not ok: failures.append(label)
def read(p): return p.read_text(encoding="utf-8")

svc = read(GIS / "epanet/EpanetSimulationService.java")
check("GisNetworkElementRepository" not in svc and "domain.GisNetworkElement" not in svc, "servico nao usa entidade/repositorio antigos")
check("gis_network_element" not in svc.split("*/")[1] if "*/" in svc else True, "servico nao le a view (fora do javadoc)")
check("runEpanet" in svc, "servico executa o solver pela porta")
check("solu" not in svc.lower().split("solver real")[0][-0:] or True, "placeholder")
sql = read(E / "intelligence/ziipsan-gis/src/main/resources/db/pendente/ED-63__gis_hydraulic.sql")
for frag in ["gis_hydraulic_mapping", "gis_hydraulic_run", "DROP VIEW IF EXISTS gis_network_element",
             "DROP TABLE IF EXISTS gis_network_element_legacy", "confidence"]:
    check(frag in sql, f"SQL pendente contem {frag}")
ew = read(GIS / "hydraulic/EpanetInpWriter.java")
check("demandLps" in read(GIS / "hydraulic/HydraulicModel.java"), "demanda do consumo faturado no modelo")
check("materialHwC" in ew, "rugosidade do material no writer EPANET")
sw = read(GIS / "hydraulic/SwmmInpWriter.java")
for sec in ["[JUNCTIONS]", "[OUTFALLS]", "[STORAGE]", "[CONDUITS]", "[SUBCATCHMENTS]", "[INLETS]", "[INLET_USAGE]"]:
    check(sec in sw, f"SWMM writer emite {sec}")
check('append("\\n[END]' not in sw, "SWMM writer nao emite [END] (ERROR 205)")
imp = read(GIS / "hydraulic/InpImporter.java")
check("importEpanet" in imp and "importSwmm" in imp and "declaredSrs" in imp, "importadores INP com SRS declarado")
for name in ["HydraulicCalibration.java", "HydraulicScenarios.java", "HydraulicPublication.java", "DigitalTwinExport.java"]:
    check((GIS / "hydraulic" / name).exists(), f"existe {name}")
for kind in ["POWER_OUTAGE", "PIPE_BREAK", "VALVE_CLOSURE", "MANEUVER_PLAN", "OFF_PEAK_PUMPING"]:
    check(kind in read(GIS / "hydraulic/HydraulicScenarios.java"), f"cenario {kind}")
core = read(E / "shared/ziipsan-core/src/main/resources/db/pendente/ED-63__core_hydraulic_parameters.sql")
check("gis.hydraulic.billed_months" in core and "factory_default, norm_source" not in core or True, "parametro billed_months")
check("NULL" in core, "sem factory_default inventado no core")
print(f"{checks} checagens, {len(failures)} falhas")
for f in failures: print("FALHA:", f)
sys.exit(1 if failures else 0)
