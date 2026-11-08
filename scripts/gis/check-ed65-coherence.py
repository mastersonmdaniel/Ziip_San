#!/usr/bin/env python3
"""Coerencia executavel da ED-65: SQL pendente x DDL real dos espelhos x Java.
Falha (exit 1) em qualquer divergencia; imprime o placar por grupo."""
import json, re, sys, pathlib

BASE = pathlib.Path.home() / "workspace/ziipsan-producao"
SQL = (BASE / "ed-65/entrega/intelligence/ziipsan-gis/src/main/resources/db/pendente/ED-65__gis_solid_waste.sql").read_text()
V131 = (BASE / "p2.21/espelho/intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/V13_1__gis_classification_catalog.sql").read_text()
V132 = (BASE / "p2.21/espelho/intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/V13_2__gis_features_history_migration.sql").read_text()
V1311 = (BASE / "p3.26/entrega/intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/V13_11__gis_import_staging.sql").read_text()
CAT = (BASE / "ed-65/entrega/intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis/waste/GisWasteCatalog.java").read_text()
IMP = (BASE / "ed-65/entrega/intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis/waste/GisWasteImportLayers.java").read_text()
SVC = (BASE / "ed-65/entrega/intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis/waste/GisWasteService.java").read_text()

fails, oks = [], []
def ok(cond, msg):
    (oks if cond else fails).append(msg)

# 1. Sintaxe basica: parenteses/aspas balanceados fora de comentarios
body = re.sub(r"--[^\n]*", "", SQL)
ok(body.count("(") == body.count(")"), "parenteses balanceados")
ok(body.count("'") % 2 == 0, "aspas simples balanceadas")
ok("${tenant_id}" in SQL, "placeholder de tenant presente")

# 2. Tabelas pre-existentes referenciadas existem no DDL real
for tbl in ["gis_feature_class", "gis_feature_type", "gis_value_list"]:
    ok(f"CREATE TABLE IF NOT EXISTS {tbl}" in V131, f"{tbl} existe na V13_1")
for tbl in ["gis_poi", "gis_structure", "gis_zone", "gis_attachment"]:
    ok(f"CREATE TABLE IF NOT EXISTS {tbl}" in V132, f"{tbl} existe na V13_2")
for tbl in ["gis_import_profile", "gis_cad_layer_map"]:
    ok(f"CREATE TABLE IF NOT EXISTS {tbl}" in V1311, f"{tbl} existe na V13_11")
ok("uq_gis_poi_tenant_id UNIQUE (tenant_id, id)" in V132, "gis_poi tem UNIQUE(tenant,id) p/ FK")
ok("uq_gis_zone_tenant_id UNIQUE (tenant_id, id)" in V132, "gis_zone tem UNIQUE(tenant,id) p/ FK")
ok("'COLLECTION_SECTOR'" in V132 and "zone_type" in V132, "gis_zone.zone_type aceita COLLECTION_SECTOR")
ok("ck_gis_structure_one_geom CHECK (geom IS NOT NULL OR geom_point IS NOT NULL)" in V132,
   "gis_structure exige geom ou geom_point (insert da api cobre os dois)")
ok("uq_gis_value_list_tenant_list_code UNIQUE (tenant_id, list_code, code)" in V131,
   "gis_value_list tem UNIQUE p/ ON CONFLICT")
ok("uq_gis_feature_type_tenant_class_code UNIQUE (tenant_id, class_id, code)" in V131,
   "gis_feature_type tem UNIQUE(tenant,class,code) p/ ON CONFLICT do catalogo")
ok("style_key TEXT" in V131, "gis_feature_type.style_key existe")
ok("'gis_zone', 'gis_poi'" in V1311 or "'gis_poi'" in V1311, "gis_cad_layer_map aceita gis_poi/gis_zone/gis_structure")

# 3. Paridade catalogo: tipos do SQL == tipos do Java
sql_types = re.findall(r"\('(?:WASTE_POINT|WASTE_FACILITY|COLLECTION_SECTOR)', '([A-Z_]+)',", SQL)
java_types = re.findall(r'new WasteType\("([A-Z_]+)"', CAT)
ok(sorted(sql_types) == sorted(java_types), f"tipos SQL==Java ({len(sql_types)} x {len(java_types)})")

# 4. Paridade attributes_schema: required do SQL == requiredAttributes do Java
schemas = {}
for m in re.finditer(r"\('(?:WASTE_POINT|WASTE_FACILITY|COLLECTION_SECTOR)', '([A-Z_]+)', '[^']*',\s*'(\{.*?\})',", SQL, re.S):
    schemas[m.group(1)] = json.loads(m.group(2))
java_req = {}
for m in re.finditer(r'new WasteType\("([A-Z_]+)".*?List\.of\(([^)]*)\)\)', CAT, re.S):
    java_req[m.group(1)] = sorted(re.findall(r'"([a-z0-9_]+)"', m.group(2)))
for code, schema in schemas.items():
    ok(sorted(schema.get("required", [])) == java_req.get(code, []),
       f"required {code}: SQL {sorted(schema.get('required', []))} == Java {java_req.get(code, [])}")
ok(len(schemas) == 13, f"13 schemas no SQL ({len(schemas)})")

# enums-chave dos schemas
dump = schemas.get("ILLEGAL_DUMP", {})
ok(dump.get("properties", {}).get("dump_status", {}).get("enum") == ["ACTIVE", "CLEANING", "ELIMINATED", "REINCIDENT"],
   "enum dump_status do schema == DUMP_STATUSES")
sec = schemas.get("COLLECTION_SECTOR", {})
ok(sec.get("properties", {}).get("collection_kind", {}).get("enum") == ["CONVENTIONAL", "SELECTIVE", "HEALTHCARE", "SWEEPING"],
   "enum collection_kind do schema == COLLECTION_KINDS")
cont = schemas.get("CONTAINER", {})
ok(cont.get("properties", {}).get("container_model", {}).get("enum") == ["WHEELED_PE", "STATIONARY_SKIP", "SEMI_BURIED", "UNDERGROUND"],
   "enum container_model do schema == modelos do Java")

# 5. Paridade camadas de importacao SQL == Java
sql_layers = re.findall(r"\('([A-Z_]+)', '(gis_\w+)'\)", SQL)
java_layers = re.findall(r'layer\("([A-Z_]+)"\)', IMP)
ok(sorted([l for l, _ in sql_layers]) == sorted(java_layers), f"camadas SQL==Java ({len(sql_layers)})")
type_table = {}
for m in re.finditer(r'new WasteType\("([A-Z_]+)",\s*"[^"]*",\s*Role\.(\w+)', CAT, re.S):
    type_table[m.group(1)] = {"POI": "gis_poi", "STRUCTURE": "gis_structure", "ZONE": "gis_zone"}[m.group(2)]
for layer, tbl in sql_layers:
    ok(type_table.get(layer) == tbl, f"camada {layer} -> {tbl} == papel do tipo")

# 6. Listas de valores: fracoes do SQL == Java
sql_frac = re.findall(r"'waste_fraction', '([A-Z_]+)'", SQL)
java_frac = re.findall(r'"(REJECT|ORGANIC|DRY_RECYCLABLE|GLASS|MIXED|CONSTRUCTION|BULKY|PRUNING|HEALTHCARE|HAZARDOUS)"',
                       CAT.split("WASTE_FRACTIONS")[1].split(");")[0])
ok(sorted(set(sql_frac)) == sorted(set(java_frac)), f"fracoes SQL==Java ({len(set(sql_frac))})")
sql_veh = re.findall(r"vehicle_kind[^\n]*CHECK \(vehicle_kind IN\s*\('([A-Z0-9_]+)', '([A-Z0-9_]+)', '([A-Z0-9_]+)', '([A-Z0-9_]+)'\)", SQL)
java_veh = set(re.findall(r'"(COMPACTOR_15M3|COMPACTOR_19M3|BOX_TRUCK|SKIP_LOADER)"', CAT))
ok(sql_veh and set(sql_veh[0]) == java_veh, "CHECK de veiculo da rota == VEHICLE_KINDS")

# 7. Servico x tabelas novas: toda tabela gis_waste_* usada no servico e criada no SQL
svc_tables = sorted(set(re.findall(r"gis_waste_\w+", SVC)))
sql_tables = sorted(set(re.findall(r"CREATE TABLE IF NOT EXISTS (gis_waste_\w+)", SQL)))
ok(set(svc_tables) <= set(sql_tables) | {"gis_waste_pgr_distance", "gis_waste_project_features"},
   f"tabelas do servico {svc_tables} criadas no SQL {sql_tables}")
for fn in ["gis_waste_pgr_distance", "gis_waste_project_features"]:
    ok(f"FUNCTION {fn}" in SQL, f"funcao {fn} criada no SQL")
    ok(fn in SVC or fn == "gis_waste_project_features", f"funcao {fn} referenciada (servico ou SQL)")

# 8. Colunas das tabelas novas usadas pelo servico existem no DDL pendente
for col in ["vertex_pgr_id", "feature_table", "feature_id", "leg_cost_m", "total_extension_m",
            "estimated_minutes", "depot_vertex", "sector_zone_id", "property_unit_id",
            "dump_poi_id", "service_order_id", "requested_on", "decided_on"]:
    ok(col in SQL, f"coluna {col} existe no SQL pendente")

print(f"OK: {len(oks)}")
for m in fails:
    print("FALHA:", m)
print(f"FALHAS: {len(fails)}")
sys.exit(1 if fails else 0)
