#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador determinístico da BASE FICTÍCIA DE TESTES do ZiipSan (decisão do dono nº 34).

DADOS 100% FICTÍCIOS. Nenhum nome, documento, endereço ou empresa real é usado.
Python 3, somente biblioteca padrão. Semente fixa declarada: SEED = 20261109.

Uso:
    python3 gerar-base-ficticia.py [diretorio-de-saida]

Sem argumento, escreve no diretório deste script. Saídas:
    carga-NNNN.sql  — massa em COPY ... FROM STDIN, cada arquivo <= ~110 KB
    matriz.csv      — matriz de situações legível por máquina (soma = 12000)
    manifesto.json  — contagens por tabela + sha256 de cada saída

Dimensões e valores (fontes no domínio — ver RELATORIO-COBERTURA.md):
  * consumer_units.status IN ('ACTIVE','PROVISIONAL') — CHECK da V3_1 (registry).
  * categorias/situações de ligação são catálogos do tenant (código VARCHAR livre,
    V3_1): os códigos abaixo são política DESTE seed fictício, declarada como tal.
  * meters.status — CHECK da V5_1 (metering).
  * consumption_records.consumption_type — CHECK da V5_1.
  * bills.status — CHECK da V4_1 (billing): NORMAL/PAID/CANCELLED/INSTALLMENT usados;
    "vencida" NÃO é status no domínio: é conta NORMAL com due_date vencida.
  * social_tariff_enrollments.status/basis/source — CHECKs da V4_2.
  * installment_agreements.status — CHECK do DDL pendente da P4.1 (collection).
  * service_orders.status — CHECK + trigger de transição da V9_1 (core): toda OS
    nasce DRAFT e é promovida por UPDATEs válidos no próprio arquivo de carga.

COMPLEMENTO EMASA (09/11/2026, adendo ao item 34): a prestadora fictícia passa a
espelhar o PERFIL ESTRUTURAL da EMASA (só a forma: autarquia, DOIS municípios,
água+esgoto) — nenhum dado real copiado. Dois municípios 100% fictícios:
  * MAIOR (cidade costeira turística): "Porto das Gaivotas", IBGE fictício 9990001
  * MENOR (vizinha): "Vale do Rio Claro", IBGE fictício 9990002
Mecanismo do domínio (V3_1 registry): localities(municipality_ibge) ->
commercial_sectors(locality_id) -> blocks(sector_id) -> consumer_units(block_id),
mais addresses.city/municipality_ibge via consumer_units(address_id) e rotas
dedicadas por município (consumer_units.reading_route_id / blocks.reading_route_id).
Repartição declarada: 9.000 UCs no MAIOR (75%) e 3.000 no MENOR (25%) — escolha de
modelagem justificada no RELATORIO-COBERTURA.md (a fonte do perfil não traz
repartição por cidade). Por situação: MENOR = floor(q/4); os 2 restantes para
fechar 3.000 vão às primeiras situações com maior resto fracionário (S14, S15).
Dentro de cada situação, as primeiras UCs geradas são do MAIOR e as demais do MENOR.
"""
import csv
import hashlib
import io
import json
import random
import sys
import uuid
from pathlib import Path

SEED = 20261109
TENANT_ID = "11111111-2222-4333-8444-555555555555"
OPERADOR_ID = "99999999-8888-4777-8666-555555555555"  # created_by fictício das OS
MAX_FILE_BYTES = 100_000  # margem sob o teto de ~110 KB por arquivo

AVISO = (
    "-- SEED FICTICIO - BASE FICTICIA DE TESTES DO ZIIPSAN (decisao do dono n. 34)\n"
    "-- Dados 100% ficticios, gerados por gerar-base-ficticia.py (semente 20261109).\n"
    "-- PROIBIDO EM PRODUCAO. Tenant ficticio: " + TENANT_ID + "\n"
    "-- Limpeza exata: limpeza-base-ficticia.sql (so toca neste tenant).\n"
)


def det_uuid(kind: str, key) -> str:
    """UUID determinístico (md5 -> versão 4) a partir de (tipo, chave)."""
    digest = hashlib.md5(f"bf12k|{kind}|{key}".encode("utf-8")).digest()
    return str(uuid.UUID(bytes=digest, version=4))


def sha256_hex(texto: str) -> str:
    return hashlib.sha256(texto.encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# MATRIZ DE SITUAÇÕES — fonte única das contagens. Soma obrigatória: 12000.
# Campos: id, status UC, categoria, situação de água, tarifa social (bool),
# estado do hidrômetro, estado da leitura, estado da conta, débito (bool),
# estado da OS, contagem, descrição.
# Estados de hidrômetro: NONE | INSTALLED | MAINTENANCE | WRITTEN_OFF
# Estados de leitura:    NONE | NORMAL | ESTIMADA | ANOMALIA
# Estados de conta:      SEM_CONTA | ABERTA | VENCIDA | PAGA | CANCELADA | PARCELADA
# Estados de OS:         SEM_OS | OS_ABERTA | OS_FECHADA
# ---------------------------------------------------------------------------
SITUACOES = [
    ("S01", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "ABERTA", False, "SEM_OS", 1800, "Residencial ligada, com hidrometro, leitura real, conta aberta"),
    ("S02", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "PAGA", False, "SEM_OS", 1500, "Residencial ligada, leitura real, conta paga"),
    ("S03", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "VENCIDA", True, "SEM_OS", 900, "Residencial ligada, conta vencida (NORMAL vencida) com debito em aberto"),
    ("S04", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "VENCIDA", True, "OS_ABERTA", 400, "Idem S03, com ordem de servico aberta"),
    ("S05", "ACTIVE", "RESIDENCIAL", "LIGADA", True, "INSTALLED", "NORMAL", "ABERTA", False, "SEM_OS", 700, "Tarifa social ativa (CadUnico), leitura real, conta aberta"),
    ("S06", "ACTIVE", "RESIDENCIAL", "LIGADA", True, "INSTALLED", "NORMAL", "VENCIDA", True, "SEM_OS", 300, "Tarifa social ativa, conta vencida com debito"),
    ("S07", "ACTIVE", "COMERCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "ABERTA", False, "SEM_OS", 800, "Comercial ligada, leitura real, conta aberta"),
    ("S08", "ACTIVE", "COMERCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "PAGA", False, "SEM_OS", 600, "Comercial ligada, leitura real, conta paga"),
    ("S09", "ACTIVE", "INDUSTRIAL", "LIGADA", False, "INSTALLED", "NORMAL", "ABERTA", False, "SEM_OS", 300, "Industrial ligada, leitura real, conta aberta"),
    ("S10", "ACTIVE", "PUBLICA", "LIGADA", False, "INSTALLED", "NORMAL", "ABERTA", False, "SEM_OS", 200, "Categoria publica ligada, leitura real, conta aberta"),
    ("S11", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "ESTIMADA", "ABERTA", False, "SEM_OS", 500, "Com hidrometro, consumo faturado ESTIMATED (sem leitura do mes), conta aberta"),
    ("S12", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "NONE", "ESTIMADA", "ABERTA", False, "SEM_OS", 600, "SEM hidrometro, faturada por estimativa (ESTIMATED), conta aberta"),
    ("S13", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "NONE", "ESTIMADA", "VENCIDA", True, "SEM_OS", 300, "Sem hidrometro, estimativa, conta vencida com debito"),
    ("S14", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "ANOMALIA", "ABERTA", False, "OS_ABERTA", 350, "Leitura com anormalidade de consumo alto, conta aberta, OS aberta"),
    ("S15", "ACTIVE", "RESIDENCIAL", "CORTADA", False, "INSTALLED", "NORMAL", "VENCIDA", True, "OS_ABERTA", 450, "Ligacao CORTADA (supplies=false), conta vencida com debito, OS aberta"),
    ("S16", "ACTIVE", "RESIDENCIAL", "SUSPENSA", False, "INSTALLED", "NONE", "SEM_CONTA", False, "SEM_OS", 300, "Ligacao SUSPENSA (nao fatura), hidrometro instalado, sem leitura e sem conta"),
    ("S17", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "CANCELADA", False, "OS_FECHADA", 250, "Conta CANCELLED, OS fechada executada"),
    ("S18", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "PARCELADA", False, "OS_ABERTA", 400, "Conta INSTALLMENT com parcelamento ACTIVE e cronograma; OS do acordo aberta"),
    ("S19", "PROVISIONAL", "RESIDENCIAL", "SEM_LIGACAO", False, "NONE", "NONE", "SEM_CONTA", False, "SEM_OS", 400, "Cadastro PROVISIONAL, sem ligacao, sem hidrometro, sem conta"),
    ("S20", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "MAINTENANCE", "ESTIMADA", "ABERTA", False, "SEM_OS", 300, "Hidrometro IN_MAINTENANCE, consumo estimado, conta aberta"),
    ("S21", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "WRITTEN_OFF", "ESTIMADA", "ABERTA", False, "SEM_OS", 200, "Hidrometro WRITTEN_OFF (instalacao encerrada), consumo estimado, conta aberta"),
    ("S22", "ACTIVE", "RESIDENCIAL", "LIGADA", True, "NONE", "ESTIMADA", "ABERTA", False, "SEM_OS", 250, "Tarifa social ativa, sem hidrometro, estimativa, conta aberta"),
    ("S23", "ACTIVE", "RESIDENCIAL", "LIGADA", False, "INSTALLED", "NORMAL", "ABERTA", True, "SEM_OS", 200, "Conta aberta com debito avulso NORMAL (sem conta vencida)"),
]

TOTAL = sum(s[10] for s in SITUACOES)
assert TOTAL == 12000, f"soma da matriz = {TOTAL}, esperado 12000"

# --- Complemento EMASA: dois municípios fictícios (perfil estrutural, sem dado real)
MUN_MAIOR = {"chave": "MAIOR", "nome": "Porto das Gaivotas", "ibge": "9990001", "ucs": 9000}
MUN_MENOR = {"chave": "MENOR", "nome": "Vale do Rio Claro", "ibge": "9990002", "ucs": 3000}
MUNICIPIOS = (MUN_MAIOR, MUN_MENOR)


def reparticao_menor():
    """UCs do município MENOR por situação: floor(q/4) + maiores restos até 3.000."""
    base = {s[0]: s[10] // 4 for s in SITUACOES}
    falta = MUN_MENOR["ucs"] - sum(base.values())
    ordem = sorted(SITUACOES, key=lambda s: (-(s[10] % 4), s[0]))
    for s in ordem[:falta]:
        base[s[0]] += 1
    assert sum(base.values()) == MUN_MENOR["ucs"]
    return base


REPARTICAO_MENOR = reparticao_menor()  # situacao -> UCs no município menor
TENANT_NOME = "Autarquia Ficticia Intermunicipal de Aguas — Porto das Gaivotas e Vale do Rio Claro (TESTE FICTICIO)"

# Catálogos (ids determinísticos)
CAT = {c: det_uuid("categoria", c) for c in ("RESIDENCIAL", "COMERCIAL", "INDUSTRIAL", "PUBLICA")}
SUB = {c: det_uuid("subcategoria", c) for c in CAT}
WATER = {c: det_uuid("water_status", c) for c in ("LIGADA", "CORTADA", "SUSPENSA", "SEM_LIGACAO")}
SEWER = {c: det_uuid("sewer_status", c) for c in ("LIGADO", "SEM_LIGACAO")}
LOC_IDS = {m["chave"]: det_uuid("localidade", m["chave"]) for m in MUNICIPIOS}
SEC_IDS = {m["chave"]: det_uuid("setor", m["chave"]) for m in MUNICIPIOS}
ROUTES = {m["chave"]: [det_uuid("rota", f'{m["chave"]}-1'), det_uuid("rota", f'{m["chave"]}-2')] for m in MUNICIPIOS}
ROUTE_CODES = {ROUTES["MAIOR"][0]: 1, ROUTES["MAIOR"][1]: 2, ROUTES["MENOR"][0]: 3, ROUTES["MENOR"][1]: 4}
BLOCKS = {m["chave"]: [det_uuid("quadra", f'{m["chave"]}-{i}') for i in range(1, 21)] for m in MUNICIPIOS}
TARIFF_ID = det_uuid("tarifa", "TARIFA_FICTICIA")
TVERSION_ID = det_uuid("tarifa_versao", 1)
ANOM_SEM_LEITURA = det_uuid("anomalia_leitura", "SEM_LEITURA")
ANOM_CONSUMO_ALTO = det_uuid("anomalia_leitura", "CONSUMO_ALTO")
CONS_ANOM_ALTO = det_uuid("anomalia_consumo", "HIGH_CONSUMPTION")
END_REASON_ID = det_uuid("social_end_reason", "ENCERRAMENTO_FICTICIO")
STYPE_ID = det_uuid("service_type", "OS-FICTICIA-LEITURA")
CLOSE_REASON_CODE = "EXECUTADA_FICTICIA"

AGORA = "2026-10-01 10:00:00+00"  # created_at/updated_at fixos (determinismo)


def esc(valor):
    """Valor para COPY formato texto: None -> \\N; demais sem tab/quebra (dados gerados)."""
    if valor is None:
        return "\\N"
    return str(valor)


class Escritor:
    """Acumula blocos SQL e os reparte em arquivos carga-NNNN.sql <= MAX_FILE_BYTES."""

    def __init__(self):
        self.arquivos = []  # lista de (nome, conteudo)
        self.buf = io.StringIO()
        self.buf.write(AVISO)
        self.contagens = {}

    def _flush(self):
        conteudo = self.buf.getvalue()
        nome = f"carga-{len(self.arquivos) + 1:04d}.sql"
        self.arquivos.append((nome, conteudo))
        self.buf = io.StringIO()
        self.buf.write(AVISO)

    def bloco(self, sql: str):
        # Bloco maior que o teto (ex.: lote de INSERTs de OS) é repartido por linhas.
        if len(sql.encode("utf-8")) > MAX_FILE_BYTES - len(AVISO.encode("utf-8")):
            pedaco = ""
            for linha in sql.splitlines(keepends=True):
                if len(pedaco.encode("utf-8")) + len(linha.encode("utf-8")) > MAX_FILE_BYTES - len(AVISO.encode("utf-8")):
                    self.bloco(pedaco)
                    pedaco = ""
                pedaco += linha
            if pedaco:
                self.bloco(pedaco)
            return
        if len(self.buf.getvalue().encode("utf-8")) + len(sql.encode("utf-8")) > MAX_FILE_BYTES:
            self._flush()
        self.buf.write(sql)

    def copy(self, tabela: str, colunas, linhas):
        """Emite COPY em pedaços que cabem no arquivo corrente."""
        self.contagens[tabela] = self.contagens.get(tabela, 0) + len(linhas)
        cab = f"COPY {tabela} ({', '.join(colunas)}) FROM STDIN;\n"
        rodape = "\\.\n"
        atual = [cab]
        tamanho = len(cab.encode("utf-8"))
        for linha in linhas:
            texto = "\t".join(esc(v) for v in linha) + "\n"
            t = len(texto.encode("utf-8"))
            if tamanho + t + len(rodape) > MAX_FILE_BYTES - len(AVISO.encode("utf-8")) and len(atual) > 1:
                atual.append(rodape)
                self.bloco("".join(atual))
                atual = [cab]
                tamanho = len(cab.encode("utf-8"))
            atual.append(texto)
            tamanho += t
        atual.append(rodape)
        self.bloco("".join(atual))

    def finalizar(self):
        if len(self.buf.getvalue().encode("utf-8")) > len(AVISO.encode("utf-8")):
            self._flush()
        return self.arquivos


def gerar():
    rng = random.Random(SEED)
    w = Escritor()

    # ------------------------- catálogos do tenant fictício -----------------
    w.copy("localities", ["id", "tenant_id", "code", "name", "municipality_ibge"],
           [[LOC_IDS[m["chave"]], TENANT_ID, i + 1, m["nome"], m["ibge"]] for i, m in enumerate(MUNICIPIOS)])
    w.copy("commercial_sectors", ["id", "tenant_id", "locality_id", "code", "name"],
           [[SEC_IDS[m["chave"]], TENANT_ID, LOC_IDS[m["chave"]], 1, f'Setor Ficticio {m["nome"]}'] for m in MUNICIPIOS])
    w.copy("reading_routes", ["id", "tenant_id", "code", "name", "active"],
           [[rid, TENANT_ID, ROUTE_CODES[rid], f'Rota Ficticia {ROUTE_CODES[rid]} — {m["nome"]}', "t"]
            for m in MUNICIPIOS for rid in ROUTES[m["chave"]]])
    w.copy("blocks", ["id", "tenant_id", "sector_id", "code", "reading_route_id", "has_water_network", "has_sewer_network"],
           [[BLOCKS[m["chave"]][i], TENANT_ID, SEC_IDS[m["chave"]], i + 1,
             ROUTES[m["chave"]][0] if i % 2 == 0 else ROUTES[m["chave"]][1], "t", "t"]
            for m in MUNICIPIOS for i in range(20)])
    w.copy("categories", ["id", "tenant_id", "code", "name", "public_entity", "active"],
           [[CAT[c], TENANT_ID, c, f"Categoria Ficticia {c}", "t" if c == "PUBLICA" else "f", "t"] for c in CAT])
    w.copy("subcategories", ["id", "tenant_id", "category_id", "code", "name", "active"],
           [[SUB[c], TENANT_ID, CAT[c], f"{c}-PADRAO", f"Subcategoria Ficticia {c}", "t"] for c in CAT])
    # flags das situações de água (V3_1: bills/requires_network/connected/supplies/minimum_consumption)
    w.copy("water_statuses", ["id", "tenant_id", "code", "name", "bills", "requires_network", "connected", "supplies", "minimum_consumption", "active"],
           [[WATER["LIGADA"], TENANT_ID, "LIGADA", "Ligada (ficticia)", "t", "t", "t", "t", "t", "t"],
            [WATER["CORTADA"], TENANT_ID, "CORTADA", "Cortada (ficticia)", "t", "t", "t", "f", "f", "t"],
            [WATER["SUSPENSA"], TENANT_ID, "SUSPENSA", "Suspensa (ficticia)", "f", "t", "t", "f", "f", "t"],
            [WATER["SEM_LIGACAO"], TENANT_ID, "SEM_LIGACAO", "Sem ligacao (ficticia)", "f", "f", "f", "f", "f", "t"]])
    w.copy("sewer_statuses", ["id", "tenant_id", "code", "name", "bills", "requires_network", "connected", "minimum_consumption", "active"],
           [[SEWER["LIGADO"], TENANT_ID, "LIGADO", "Esgoto ligado (ficticio)", "t", "t", "t", "t", "t"],
            [SEWER["SEM_LIGACAO"], TENANT_ID, "SEM_LIGACAO", "Sem ligacao de esgoto (ficticia)", "f", "f", "f", "f", "t"]])
    w.copy("tariffs", ["id", "tenant_id", "code", "name", "calc_type", "active"],
           [[TARIFF_ID, TENANT_ID, "TARIFA_FICTICIA", "Tarifa Ficticia de Testes", "BLOCKS", "t"]])
    w.copy("tariff_versions", ["id", "tenant_id", "tariff_id", "valid_from", "valid_to", "social_min_charge_discount_pct"],
           [[TVERSION_ID, TENANT_ID, TARIFF_ID, "2026-01-01", None, "50.00"]])
    w.copy("social_tariff_end_reasons", ["id", "tenant_id", "code", "description"],
           [[END_REASON_ID, TENANT_ID, "ENCERRAMENTO_FICTICIO", "Motivo ficticio de encerramento (catalogo do seed)"]])
    w.copy("reading_anomalies", ["id", "tenant_id", "code", "description", "for_unit_without_meter", "requires_reading",
                                 "generates_service_order", "reading_effect_without_reading", "reading_effect_with_reading",
                                 "consumption_effect_without_reading", "consumption_effect_with_reading", "loses_social_tariff", "active"],
           [[ANOM_SEM_LEITURA, TENANT_ID, "SEM_LEITURA", "Sem leitura no mes (ficticio)", "t", "f", "f",
             "PREVIOUS_PLUS_AVERAGE", "PREVIOUS", "AVERAGE", "AVERAGE", "f", "t"],
            [ANOM_CONSUMO_ALTO, TENANT_ID, "CONSUMO_ALTO", "Consumo alto (ficticio)", "f", "t", "t",
             "PREVIOUS", "INFORMED", "MINIMUM", "NORMAL", "f", "t"]])
    w.copy("consumption_anomalies", ["id", "tenant_id", "code", "kind", "description", "counts_for_average", "active"],
           [[CONS_ANOM_ALTO, TENANT_ID, "CONS_ALTO", "HIGH_CONSUMPTION", "Consumo alto (ficticio)", "t", "t"]])
    w.copy("service_types", ["id", "tenant_id", "code", "name", "operation", "effect_code", "indicator",
                             "requires_approval", "operational", "origin", "target_classes", "active"],
           [[STYPE_ID, TENANT_ID, "OS-FICTICIA-LEITURA", "OS Ficticia de Leitura", "LEITURA", None, "FIELD",
             "f", "t", "INSPECTION", "{CONSUMER_UNIT}", "t"]])
    w.copy("service_order_close_reasons", ["id", "tenant_id", "code", "description"],
           [[det_uuid("close_reason", CLOSE_REASON_CODE), TENANT_ID, CLOSE_REASON_CODE, "Executada (motivo ficticio do seed)"]])

    # ------------------------- massa por UC ---------------------------------
    customers, addresses, units, unit_customers, unit_subs = [], [], [], [], []
    enrollments, meters, installations, readings, consumptions = [], [], [], [], []
    bills, bill_items, debits, agreements, schedules = [], [], [], [], []
    os_inserts, os_updates, os_targets = [], [], []
    bloco_contador = {m["chave"]: [0] * 20 for m in MUNICIPIOS}
    cont_mun = {m["chave"]: 0 for m in MUNICIPIOS}  # UCs já geradas por município
    n = 0

    def flush_lotes():
        if customers:
            w.copy("customers", ["id", "tenant_id", "person_type", "name"], customers); customers.clear()
        if addresses:
            w.copy("addresses", ["id", "tenant_id", "entity_type", "entity_id", "type", "street", "number",
                                 "neighborhood", "city", "state", "country", "is_primary", "municipality_ibge"],
                   addresses); addresses.clear()
        if units:
            w.copy("consumer_units", ["id", "tenant_id", "matricula", "dv", "block_id", "lot", "sublot", "address_id",
                                      "water_status_id", "sewer_status_id", "reading_route_id", "reading_sequence",
                                      "built_area_m2", "due_day", "status"], units); units.clear()
        if unit_customers:
            w.copy("unit_customers", ["id", "tenant_id", "unit_id", "customer_id", "role", "start_date", "show_on_bill"],
                   unit_customers); unit_customers.clear()
        if unit_subs:
            w.copy("unit_subcategories", ["id", "tenant_id", "unit_id", "subcategory_id", "economies"], unit_subs); unit_subs.clear()
        if enrollments:
            w.copy("social_tariff_enrollments", ["id", "tenant_id", "unit_id", "holder_customer_id", "family_code_hash",
                                                 "eligibility_basis", "enrollment_source", "cadunico_updated_on", "valid_from",
                                                 "valid_to", "status", "legal_basis", "social_economies"], enrollments); enrollments.clear()
        if meters:
            w.copy("meters", ["id", "tenant_id", "meter_number", "digits", "status"], meters); meters.clear()
        if installations:
            w.copy("meter_installations", ["id", "tenant_id", "meter_id", "unit_id", "kind", "installed_on",
                                           "installed_reading", "removed_on", "removed_reading", "is_replacement"],
                   installations); installations.clear()
        if readings:
            w.copy("meter_readings", ["id", "tenant_id", "unit_id", "installation_id", "kind", "reference", "read_on",
                                      "reading", "reading_status", "anomaly_id", "source", "captured_at"], readings); readings.clear()
        if consumptions:
            w.copy("consumption_records", ["id", "tenant_id", "unit_id", "service_kind", "reference", "billed_consumption",
                                           "calculated_consumption", "billable", "billing_paused", "consumption_type",
                                           "anomaly_id", "reading_id", "discarded_remainder"], consumptions); consumptions.clear()
        if bills:
            w.copy("bills", ["id", "tenant_id", "unit_id", "reference_month", "status", "water_consumption", "sewer_volume",
                             "water_value", "sewer_value", "debits_value", "credits_value", "taxes_value", "total_value",
                             "due_date", "issued_on", "completed"], bills); bills.clear()
        if bill_items:
            w.copy("bill_items", ["id", "tenant_id", "bill_id", "service_kind", "category_id", "subcategory_id",
                                  "tariff_version_id", "period_from", "period_to", "proration_factor", "line_type",
                                  "economies", "social_economies", "from_m3", "to_m3", "price_per_m3", "consumption",
                                  "gross_value", "discount_value", "net_value"], bill_items); bill_items.clear()
        if debits:
            w.copy("debit_receivables", ["id", "tenant_id", "unit_id", "type_code", "description", "total_amount",
                                         "installments", "installments_done", "status", "generates_bill"], debits); debits.clear()
        if agreements:
            w.copy("installment_agreements", ["id", "tenant_id", "unit_id", "customer_id", "policy_version_id", "protocol_id",
                                              "service_order_id", "total_debt", "down_payment", "down_payment_due_date",
                                              "interest_method", "interest_rate", "status", "source"], agreements); agreements.clear()
        if schedules:
            w.copy("installment_schedule", ["id", "tenant_id", "agreement_id", "installment_number", "due_date", "amount"],
                   schedules); schedules.clear()
        if os_inserts:
            txt = "".join(os_inserts)
            w.bloco(txt); w.contagens["service_orders"] = w.contagens.get("service_orders", 0) + txt.count("INSERT INTO service_orders")
            os_inserts.clear()
        if os_targets:
            w.copy("service_order_targets", ["id", "tenant_id", "order_id", "target_type", "target_id"], os_targets); os_targets.clear()
        if os_updates:
            w.bloco("".join(os_updates)); os_updates.clear()

    for (sid, cu_status, categoria, agua, social, medidor, leitura, conta, debito, os_estado, qtd, _desc) in SITUACOES:
        for pos in range(qtd):
            n += 1
            # Município desta UC: as primeiras (qtd - REPARTICAO_MENOR[sid]) da
            # situação são do MAIOR; as restantes, do MENOR (regra declarada).
            mun = MUN_MAIOR if pos < qtd - REPARTICAO_MENOR[sid] else MUN_MENOR
            chave = mun["chave"]
            idx_mun = cont_mun[chave]
            cont_mun[chave] += 1
            unit_id = det_uuid("unit", n)
            cust_id = det_uuid("customer", n)
            # Endereços compartilhados (1 a cada 6 UCs DO MESMO município, como num
            # prédio): a FK da UC é para addresses(tenant_id, id); entity_id guarda
            # a 1ª UC do grupo. Grupo nunca mistura municípios (cidade é do endereço).
            addr_grupo = idx_mun // 6
            addr_id = det_uuid("address", f"{chave}-{addr_grupo}")
            bloco_idx = idx_mun % 20
            lote = bloco_contador[chave][bloco_idx]
            bloco_contador[chave][bloco_idx] += 1

            customers.append([cust_id, TENANT_ID, "PF", f"Titular Ficticio {n:05d}"])
            if idx_mun % 6 == 0:
                addresses.append([addr_id, TENANT_ID, "CONSUMER_UNIT", unit_id, "RESIDENTIAL",
                                  f"Rua Ficticia {(addr_grupo % 200) + 1:03d}", str(addr_grupo + 1), "Bairro Ficticio",
                                  mun["nome"], "SC", "BR", "t", mun["ibge"]])
            units.append([unit_id, TENANT_ID, f"{n:08d}", str(n % 10), BLOCKS[chave][bloco_idx], lote, 0, addr_id,
                          WATER[agua], SEWER["SEM_LIGACAO" if agua == "SEM_LIGACAO" else "LIGADO"],
                          ROUTES[chave][0] if idx_mun % 2 == 0 else ROUTES[chave][1], n, "80.00", 10, cu_status])
            unit_customers.append([det_uuid("unit_customer", n), TENANT_ID, unit_id, cust_id, "USER", "2026-01-05", "t"])
            unit_subs.append([det_uuid("unit_sub", n), TENANT_ID, unit_id, SUB[categoria], 1])

            if social:
                enrollments.append([det_uuid("enrollment", n), TENANT_ID, unit_id, cust_id,
                                    sha256_hex(f"familia-ficticia-{n}"), "CADUNICO", "OFFICIAL_LIST",
                                    "2026-06-01", "2026-01-01", None, "ACTIVE",
                                    "Base legal ficticia do seed de testes", 1])

            inst_id = None
            if medidor != "NONE":
                meter_id = det_uuid("meter", n)
                mstatus = {"INSTALLED": "INSTALLED", "MAINTENANCE": "IN_MAINTENANCE", "WRITTEN_OFF": "WRITTEN_OFF"}[medidor]
                meters.append([meter_id, TENANT_ID, f"HID-FICT-{n:06d}", 5, mstatus])
                inst_id = det_uuid("installation", n)
                if medidor == "WRITTEN_OFF":
                    installations.append([inst_id, TENANT_ID, meter_id, unit_id, "WATER", "2025-02-01", 100, "2026-08-01", 900, "f"])
                else:
                    installations.append([inst_id, TENANT_ID, meter_id, unit_id, "WATER", "2025-02-01", 100, None, None, "f"])

            reading_id = None
            consumo = rng.randint(5, 30)
            if leitura == "NORMAL":
                reading_id = det_uuid("reading", n)
                readings.append([reading_id, TENANT_ID, unit_id, inst_id, "WATER", "2026-10-01", "2026-10-15",
                                 1000 + n, "REALIZED", None, "MOBILE", "2026-10-15 09:00:00+00"])
                consumptions.append([det_uuid("consumption", n), TENANT_ID, unit_id, "WATER", "2026-10-01",
                                     consumo, consumo, "t", "f", "REAL", None, reading_id, 0])
            elif leitura == "ANOMALIA":
                reading_id = det_uuid("reading", n)
                readings.append([reading_id, TENANT_ID, unit_id, inst_id, "WATER", "2026-10-01", "2026-10-15",
                                 1000 + n, "REALIZED", ANOM_CONSUMO_ALTO, "MOBILE", "2026-10-15 09:00:00+00"])
                consumptions.append([det_uuid("consumption", n), TENANT_ID, unit_id, "WATER", "2026-10-01",
                                     45, 45, "t", "f", "REAL", CONS_ANOM_ALTO, reading_id, 0])
            elif leitura == "ESTIMADA":
                consumptions.append([det_uuid("consumption", n), TENANT_ID, unit_id, "WATER", "2026-10-01",
                                     12, 12, "t", "f", "ESTIMATED", None, None, 0])

            os_id = None
            if os_estado != "SEM_OS" or conta == "PARCELADA":
                os_id = det_uuid("service_order", n)
                os_inserts.append(
                    f"INSERT INTO service_orders (id, tenant_id, service_type_id, origin, execution_mode, priority, status, created_by) "
                    f"VALUES ('{os_id}', '{TENANT_ID}', '{STYPE_ID}', 'INTERNAL', 'FIELD', 'NORMAL', 'DRAFT', '{OPERADOR_ID}');\n")
                os_targets.append([det_uuid("os_target", n), TENANT_ID, os_id, "CONSUMER_UNIT", unit_id])
                if os_estado == "OS_FECHADA":
                    os_updates.append(f"UPDATE service_orders SET status='OPEN' WHERE id='{os_id}';\n")
                    os_updates.append(f"UPDATE service_orders SET status='EXECUTED', executed_at='2026-10-20 10:00:00+00' WHERE id='{os_id}';\n")
                    os_updates.append(f"UPDATE service_orders SET status='CLOSED_EXECUTED', closed_at='2026-10-21 10:00:00+00', close_reason_code='{CLOSE_REASON_CODE}' WHERE id='{os_id}';\n")
                else:
                    os_updates.append(f"UPDATE service_orders SET status='OPEN' WHERE id='{os_id}';\n")

            if conta != "SEM_CONTA":
                bill_id = det_uuid("bill", n)
                status_map = {"ABERTA": "NORMAL", "VENCIDA": "NORMAL", "PAGA": "PAID",
                              "CANCELADA": "CANCELLED", "PARCELADA": "INSTALLMENT"}
                due = {"ABERTA": "2026-11-20", "PAGA": "2026-10-20", "CANCELADA": "2026-10-20",
                       "VENCIDA": "2026-09-10", "PARCELADA": "2026-11-20"}[conta]
                valor = f"{rng.randint(35, 180)}.00"
                cons_bill = 45 if leitura == "ANOMALIA" else (12 if leitura == "ESTIMADA" else consumo)
                bills.append([bill_id, TENANT_ID, unit_id, "2026-10-01", status_map[conta], cons_bill, 0,
                              valor, "0.00", "0.00", "0.00", "0.00", valor, due, "2026-10-05", "t"])
                bill_items.append([det_uuid("bill_item", n), TENANT_ID, bill_id, "WATER", CAT[categoria], None,
                                   TVERSION_ID, "2026-09-16", "2026-10-15", "1.0000", "MINIMUM", 1,
                                   1 if social else 0, 0, None, None, cons_bill, valor, "0.00", valor])
                if conta == "PARCELADA":
                    agr_id = det_uuid("agreement", n)
                    agreements.append([agr_id, TENANT_ID, unit_id, cust_id, det_uuid("policy_version", "fic"),
                                       det_uuid("protocol", n), os_id, "600.00", "60.00", "2026-11-15",
                                       "NONE", "0", "ACTIVE", "STANDARD"])
                    for k, venc in ((1, "2026-12-15"), (2, "2027-01-15"), (3, "2027-02-15")):
                        schedules.append([det_uuid("schedule", f"{n}-{k}"), TENANT_ID, agr_id, k, venc, "180.00"])

            if debito:
                debits.append([det_uuid("debit", n), TENANT_ID, unit_id, "DEBITO_FICTICIO",
                               "Debito ficticio de testes", "120.00", 3, 0, "NORMAL", "t"])

            if n % 400 == 0:
                flush_lotes()
    flush_lotes()
    return w


def main():
    saida = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
    saida.mkdir(parents=True, exist_ok=True)
    w = gerar()
    arquivos = w.finalizar()

    # limpa cargas antigas do diretório de saída (regeneração idempotente)
    for antigo in saida.glob("carga-*.sql"):
        antigo.unlink()

    manifesto = {"semente": SEED, "tenant_id": TENANT_ID, "total_ucs": TOTAL,
                 "tenant_nome": TENANT_NOME,
                 "municipios_ficticios": [
                     {"nome": m["nome"], "ibge_ficticio": m["ibge"], "ucs": m["ucs"],
                      "papel": "cidade costeira turistica maior" if m is MUN_MAIOR else "cidade vizinha menor"}
                     for m in MUNICIPIOS],
                 "reparticao_por_situacao": {s[0]: {"municipio_maior": s[10] - REPARTICAO_MENOR[s[0]],
                                                    "municipio_menor": REPARTICAO_MENOR[s[0]]}
                                             for s in SITUACOES},
                 "contagens_por_tabela": dict(sorted(w.contagens.items())), "arquivos": {}}
    for nome, conteudo in arquivos:
        (saida / nome).write_text(conteudo, encoding="utf-8")
        manifesto["arquivos"][nome] = {"bytes": len(conteudo.encode("utf-8")),
                                      "sha256": sha256_hex(conteudo)}

    with (saida / "matriz.csv").open("w", newline="", encoding="utf-8") as f:
        csv_w = csv.writer(f)
        csv_w.writerow(["situacao", "status_uc", "categoria", "situacao_agua", "tarifa_social",
                        "hidrometro", "leitura", "conta", "debito", "ordem_servico", "contagem",
                        "ucs_municipio_maior", "ucs_municipio_menor"])
        for s in SITUACOES:
            csv_w.writerow([s[0], s[1], s[2], s[3], "SIM" if s[4] else "NAO", s[5], s[6], s[7],
                            "SIM" if s[8] else "NAO", s[9], s[10],
                            s[10] - REPARTICAO_MENOR[s[0]], REPARTICAO_MENOR[s[0]]])
    manifesto["arquivos"]["matriz.csv"] = {
        "bytes": (saida / "matriz.csv").stat().st_size,
        "sha256": hashlib.sha256((saida / "matriz.csv").read_bytes()).hexdigest()}
    (saida / "manifesto.json").write_text(json.dumps(manifesto, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"OK: {len(arquivos)} arquivos de carga, {TOTAL} UCs, tabelas={len(w.contagens)}")


if __name__ == "__main__":
    main()
