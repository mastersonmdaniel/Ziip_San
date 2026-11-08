#!/usr/bin/env python3
"""Coerencia estatica da ED-90: fronteiras duras do despacho.

- leiaute de distribuidora nao inventado (marcador [SEM FONTE] e porta);
- catalogo de fatores nasce vazio (nenhum INSERT de fator no SQL);
- parametros nomeados no aceite presentes no Java e no SQL, sem fabrica;
- FK composta com UNIQUE (tenant_id, id) no pai (licao C3);
- escopo amarrado a fonte no SQL (eletricidade=2, demais=1);
- P3.9 intocada: nenhuma chave alem das 4 da ED-90 no SQL de parametros.
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
main = root / "intelligence/ziipsan-reporting/src/main/java/br/com/ziipsan/reporting/energy"
sql = (root / "intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-90__energy_esg.sql").read_text(encoding="utf-8")
params = (root / "intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-90__core_energy_esg_parameters.sql").read_text(encoding="utf-8")
java = "\n".join(p.read_text(encoding="utf-8") for p in main.glob("*.java"))
errors = []

if "[SEM FONTE]" not in java:
    errors.append("marcador [SEM FONTE] ausente no Java (leiaute/fatores)")
if "EnergyBillFilePort" not in java:
    errors.append("porta do leiaute de fatura ausente")
if "INSERT INTO esg_" in sql or "INSERT INTO energy_" in sql:
    errors.append("SQL tenant/globals semeia dado (catalogo de fatores deve nascer vazio)")
if "UNIQUE (tenant_id, id)" not in sql:
    errors.append("falta UNIQUE (tenant_id, id) (C3)")
if "FOREIGN KEY (tenant_id, installation_id)" not in sql:
    errors.append("falta FK composta fatura->instalacao")
if "source_key = 'ELECTRICITY' AND scope = 2" not in sql:
    errors.append("amarra escopo/fonte ausente no SQL")
for key in ("energy.demand_alert_pct", "energy.pf_min",
            "esg.emission_factors_version", "esg.report_profile"):
    if key not in java:
        errors.append(f"parametro ausente no Java: {key}")
    if key not in params:
        errors.append(f"parametro ausente no SQL: {key}")
keys_in_params = set(re.findall(r"\('([a-z]+\.[a-z_]+)',", params))
expected = {"energy.demand_alert_pct", "energy.pf_min",
            "esg.emission_factors_version", "esg.report_profile"}
if keys_in_params != expected:
    errors.append(f"chaves do SQL de parametros divergem do aceite: {sorted(keys_in_params)}")
if ", NULL," not in params:
    errors.append("parametros devem nascer sem factory_default (NULL)")
for literal in ("0.92",):
    # 0.92 so pode aparecer em teste, nunca como fabrica no SQL/Java de producao
    if literal in params:
        errors.append(f"valor numerico presumido no SQL de parametros: {literal}")
if "FLEET_ED59" not in java or "fleetLitersFromEd59" not in java:
    errors.append("leitura de combustivel da ED-59 ausente")
if "PROFILE_TAG" not in java or "ENERGY_ESG" not in java:
    errors.append("marcacao de publicacao no catalogo (perfil ENERGY_ESG) ausente")

print("COHERENCE_ED90=" + ("FAIL: " + "; ".join(errors) if errors else "OK"))
sys.exit(1 if errors else 0)
