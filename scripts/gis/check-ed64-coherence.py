#!/usr/bin/env python3
"""Coerencia executavel da ED-64: SQL pendente x DDL real dos espelhos
(V13_1/V13_2) x Java da entrega. Uso:
  python3 check-ed64-coherence.py <raiz ziipsan-producao>
Sai com o total de checagens e as falhas; exit 1 se houver falha.
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
ED64 = ROOT / "ed-64/entrega"
V131 = ROOT / "p2.21/espelho/intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/V13_1__gis_classification_catalog.sql"
V132 = ROOT / "p2.21/espelho/intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/V13_2__gis_features_history_migration.sql"
SQL = (ED64 / "intelligence/ziipsan-gis/src/main/resources/db/pendente/ED-64__gis_drainage.sql").read_text(encoding="utf-8")
CORE_SQL = (ED64 / "shared/ziipsan-core/src/main/resources/db/pendente/ED-64__core_drain_parameters.sql").read_text(encoding="utf-8")
JAVA_DIR = ED64 / "intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis/drain"
CATALOG = (JAVA_DIR / "GisDrainCatalog.java").read_text(encoding="utf-8")
RULES = (JAVA_DIR / "GisDrainRules.java").read_text(encoding="utf-8")
SERVICE = (JAVA_DIR / "GisDrainService.java").read_text(encoding="utf-8")

checks = 0
failures = []


def check(ok, label):
    global checks
    checks += 1
    if not ok:
        failures.append(label)


def ddl_tables(path):
    text = path.read_text(encoding="utf-8")
    tables = {}
    for m in re.finditer(r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\);", text, re.S):
        cols = set(re.findall(r"^\s{4}(\w+)\s", m.group(2), re.M))
        tables[m.group(1)] = cols
    return tables


ddl = {}
ddl.update(ddl_tables(V131))
ddl.update(ddl_tables(V132))

# Tabelas novas criadas pelo proprio SQL pendente
for m in re.finditer(r"CREATE TABLE IF NOT EXISTS (\w+) \((.*?)\n\);", SQL, re.S):
    ddl[m.group(1)] = set(re.findall(r"^\s{4}(\w+)\s", m.group(2), re.M))

# 1. Tipos Java == tipos SQL (factory gis13:* e linhas do VALUES de tipos)
java_types = re.findall(r'new DrainType\("(\w+)"', CATALOG)
sql_types = re.findall(r"\('STORM_\w+', '(\w+)', '", SQL)
check(len(java_types) == 20, f"Java tem 20 tipos (tem {len(java_types)})")
check(sorted(java_types) == sorted(sql_types),
      f"tipos Java == SQL: java={sorted(java_types)} sql={sorted(sql_types)}")

# 2. requiredAttributes Java == required do attributes_schema SQL
req_java = {}
for m in re.finditer(r'new DrainType\("(\w+)".*?List\.of\((.*?)\)\)', CATALOG, re.S):
    body = m.group(2)
    req_java[m.group(1)] = re.findall(r'"(\w+)"', body) if body.strip() else []
for m in re.finditer(r"\('STORM_\w+', '(\w+)', '[^']*',\s*'(\{.*?\})',", SQL):
    schema = m.group(2)
    req = re.search(r'"required":\[(.*?)\]', schema)
    req_sql = re.findall(r'"(\w+)"', req.group(1)) if req else []
    check(req_sql == req_java.get(m.group(1), []),
          f"required {m.group(1)}: sql={req_sql} java={req_java.get(m.group(1))}")

# 3. Enums das listas Java existem no SQL (value lists ou schemas)
for name, const in [("INLET_KINDS", "INLET_KINDS"), ("LOAD_CLASSES", "LOAD_CLASSES"),
                    ("CHANNEL_SECTIONS", "CHANNEL_SECTIONS"),
                    ("CHANNEL_LININGS", "CHANNEL_LININGS"),
                    ("DISSIPATOR_KINDS", "DISSIPATOR_KINDS"),
                    ("CULVERT_SHAPES", "CULVERT_SHAPES"),
                    ("INFILTRATION_MODELS", "INFILTRATION_MODELS"),
                    ("RAIN_SOURCES", "RAIN_SOURCES"),
                    ("CLEANING_ELEMENT_CLASSES", "CLEANING_ELEMENT_CLASSES"),
                    ("INSPECTION_METHODS", "INSPECTION_METHODS")]:
    m = re.search(const + r" = (?:Set|List)\.of\((.*?)\)", CATALOG, re.S)
    if not m:
        check(False, f"constante {const} nao encontrada no catalogo")
        continue
    for value in re.findall(r'"(\w+)"', m.group(1)):
        check(f"'{value}'" in SQL, f"enum {const}.{value} presente no SQL")

# 4. Tabelas/colunas referenciadas pelo servico existem no DDL real+novo
for table in ["gis_node", "gis_arc", "gis_structure", "gis_zone", "gis_attachment",
              "gis_feature_type", "gis_drain_inspection", "gis_drain_cleaning_plan",
              "gis_drain_cleaning_request", "gis_drain_rain_station",
              "gis_drain_rain_reading", "gis_drain_flood_occurrence"]:
    check(table in SERVICE or table in SQL, f"tabela {table} referenciada")
    check(table in ddl or table.startswith("gis_drain"),
          f"tabela {table} existe no DDL (real ou novo)")

for col in ["upstream_invert_m", "downstream_invert_m", "flow_direction",
            "condition_rating", "node_1_id", "node_2_id", "length_m"]:
    check(col in ddl.get("gis_arc", set()), f"gis_arc.{col} existe na V13_2")
for col in ["zone_type", "derivation"]:
    check(col in ddl.get("gis_zone", set()), f"gis_zone.{col} existe na V13_2")
check("STORM_SUBCATCHMENT" in V132.read_text(encoding="utf-8"),
      "zone_type STORM_SUBCATCHMENT existe na V13_2")
check("STORM" in V131.read_text(encoding="utf-8"), "dominio STORM existe na V13_1")

# 5. Views dos relatorios/tematico batem com GisDrainReports
for view in ["rpt_gis_drenagem_cadastro", "rpt_gis_drenagem_indicadores",
             "gis_thematic_drain_condition"]:
    check(view in SQL, f"view {view} criada no SQL")
    check(view in (JAVA_DIR / "GisDrainReports.java").read_text(encoding="utf-8")
          or view in SERVICE, f"view {view} referenciada no Java")

# 6. Parametros do core: os tres drain.* e NENHUM factory_default numerico
for key in ["drain.cleaning_interval_by_class", "drain.rain_source",
            "drain.gravity_min_slope"]:
    check(key in CORE_SQL, f"parametro {key} definido no SQL do core")
    check(key in SERVICE or key in (JAVA_DIR / "GisDrainMaintenance.java").read_text(encoding="utf-8")
          or key in (JAVA_DIR / "GisDrainRain.java").read_text(encoding="utf-8")
          or key in RULES, f"parametro {key} citado no Java")
check(CORE_SQL.count("NULL,") >= 3, "parametros drain.* sem factory_default (NULL)")

# 7. Marcadores declarados presentes
check("ALAGAMENTO" in SQL and "ALAGAMENTO" in SERVICE, "marcador de causa ED-92 coerente")
check("CCTV_EXTERNAL_REF" in SQL, "metodo de video ED-56 no SQL")
check("service_order_id" in SQL, "service_order_id (ED-36) na tabela de pedidos")

print(f"[check-ed64] {checks} checagens, {len(failures)} falhas")
for f in failures:
    print("FALHA:", f)
sys.exit(1 if failures else 0)
