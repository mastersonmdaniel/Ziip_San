#!/usr/bin/env python3
"""Coerencia executavel da ED-54: SQL pendente x Java da entrega x aceite.
Uso: python3 check-ed54-coherence.py <raiz ziipsan-producao>
Exit 1 se houver falha."""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
E = ROOT / "ed-54/entrega"
GIS = E / "intelligence/ziipsan-gis"
SQL = (GIS / "src/main/resources/db/pendente/ED-54__gis_maneuver.sql").read_text(encoding="utf-8")
JAVA = GIS / "src/main/java/br/com/ziipsan/intelligence/gis/maneuver"
checks, failures = 0, []

def check(ok, label):
    global checks
    checks += 1
    if not ok:
        failures.append(label)

def read(p):
    return p.read_text(encoding="utf-8")

model = read(JAVA / "ManeuverModel.java")
engine = read(JAVA / "IsolationEngine.java")
plan = read(JAVA / "ManeuverPlanService.java")
ports = read(JAVA / "ManeuverPorts.java")
coverage = read(JAVA / "ManeuverCoverage.java")

# 1. Tabelas do SQL == tabelas citadas no relatorio/doc
for t in ["gis_maneuver_sector", "gis_maneuver_plan", "gis_maneuver_plan_valve",
          "gis_maneuver_outage_period"]:
    check(f"CREATE TABLE IF NOT EXISTS {t} " in SQL, f"tabela {t} no SQL")
    check("register_tenant_table('" + t + "')" in SQL, f"RLS de {t}")

# 2. Funcoes SQL existem
for fn in ["gis_trace_downstream", "gis_trace_upstream", "gis_routing_polygon"]:
    check(f"FUNCTION {fn}" in SQL, f"funcao {fn} no SQL")
check("ST_ConvexHull" in SQL, "poligono usa ST_ConvexHull (PostGIS)")

# 3. Estados do plano no SQL == enum Java
for st in ["PLANNED", "NOTICE_SENT", "ORDERS_CREATED", "ISOLATED", "COMPLETED"]:
    check(f"'{st}'" in SQL, f"status {st} no CHECK do SQL")
    check(st in plan, f"status {st} no PlanStatus Java")

# 4. Pareamento papel (SQL) x finalidade (ED-36) verificado DE FATO
# (correcao D4 da revisao: a variavel purpose do laco era ignorada e
# o par INOPERABLE_TRAVERSED x VALVE_REOPEN era semanticamente
# errado). Regra verdadeira do desenho:
#   TO_CLOSE              -> ganha OS de fechamento (VALVE_CLOSE) e
#                            de reabertura (VALVE_REOPEN);
#   INOPERABLE_TRAVERSED  -> NAO ganha OS de especie nenhuma (quem
#                            prova e o ManeuverPlanServiceTest).
plan_test = read(GIS / "src/test/java/br/com/ziipsan/intelligence/gis/maneuver/ManeuverPlanServiceTest.java")
for role, purpose in [("TO_CLOSE", "VALVE_CLOSE"), ("TO_CLOSE", "VALVE_REOPEN")]:
    check(role in SQL, f"papel {role} no SQL")
    check(purpose in ports, f"finalidade {purpose} da ED-36 na porta (pareada ao papel {role})")
    check(purpose in plan, f"finalidade {purpose} usada pelo servico de plano (papel {role})")
check("INOPERABLE_TRAVERSED" in SQL, "papel INOPERABLE_TRAVERSED no SQL")
check("inoperableTraversed" in engine, "inoperavel atravessada calculada pelo motor (IsolationResult.inoperableTraversed)")
check("noneMatch" in plan_test and "VX" in plan_test,
      "teste prova que a inoperavel atravessada nao ganha OS (nenhuma finalidade)")

# 5. Aviso so pela ED-41: nenhum gateway de canal no pacote da GIS-11
alljava = "\n".join(read(p) for p in JAVA.rglob("*.java"))
for forbidden in ["SmsGateway", "WhatsAppGateway", "PushGateway", "sendSms", "sendWhatsApp"]:
    check(forbidden not in alljava, f"sem envio direto de canal ({forbidden} ausente)")
check("NoticePort" in ports and "MultichannelDispatchService" in ports,
      "porta de aviso referencia a ED-41")
check("WorkOrderBatchPort" in ports, "porta de lote referencia a ED-36")

# 6. Cobertura SG/TR/SAMAE citada no aceite
for code in ["SG-852", "SG-887", "SG-889", "TR-1064", "TR-1065", "SAMAE-2.27"]:
    check(code in coverage, f"cobertura {code}")

# 7. Recorte O8-1: nenhum solver de equipes (correcao D4 da revisao:
# a cobertura era so 4 tokens case-sensitive). Agora:
#   (a) qualquer funcao pgRouting: padrao pgr_* generico,
#       case-insensitive, no Java e no SQL;
#   (b) solvers de rota escritos em Java puro e tempo de viagem:
#       dijkstra, astar/a*, tsp/travelling salesman, travel time,
#       tempo de viagem/deslocamento, shortest path.
# As unicas ocorrencias legitimas sao as MENCOS que declaram o
# bloqueio (javadoc/cabecalhos), filtradas linha a linha abaixo.
def solver_hits(text):
    hits = []
    for line in text.splitlines():
        low = line.lower()
        if "bloquead" in low or "recorte o8-1" in low or "sem solver" in low:
            continue  # mencao declarando o bloqueio, nao uso
        if re.search(r"pgr_[a-z]", low):
            hits.append("pgr_*")
        for pat in [r"dijkstra", r"astar", r"\ba\*", r"\btsp\b",
                    r"travelling salesman", r"travel\s*time",
                    r"traveltime", r"tempo de viagem",
                    r"tempo de deslocamento", r"shortest\s*path"]:
            if re.search(pat, low):
                hits.append(pat)
    return hits

check(not solver_hits(alljava), "sem solver de equipes no Java (pgr_* generico + padroes de solver/tempo de viagem)")
check(not solver_hits(SQL), "sem solver de equipes no SQL (pgr_* generico + padroes de solver/tempo de viagem)")

# 8. flow_direction do SQL == valores da V13_2 usados pelo modelo Java
for v in ["WITH_GEOMETRY", "AGAINST_GEOMETRY", "BOTH"]:
    check(v in SQL, f"flow_direction {v} no SQL")
    check(v in model, f"flow_direction {v} no modelo Java")
check("UNKNOWN" in model, "UNKNOWN no modelo Java (sem aresta dirigida)")

print(f"[check-ed54] {checks} checagens, {len(failures)} falhas")
for f in failures:
    print("FALHA:", f)
sys.exit(1 if failures else 0)
