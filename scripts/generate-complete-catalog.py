#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador do catalogo completo de relatorios da ED-46.

Fontes (espelho local em ../../espelho/fontes/, copias dos docs do repo
docs/levantamento/editais/ no HEAD 0fbcd22):
  * 18g secao 2.4  - uma linha por relatorio das secoes 19 (VISAN) e
                     2.28 (SAMAE): TR-664..TR-835 e SG-890..SG-1062.
  * 18d (E8)       - relatorios de leitura TR-191..TR-199 / SG-193..SG-200.
  * plano v3.0 (matriz viva, linha da ED-46) - escopo e exclusoes:
    fora os da ED-45/P3.22 (tratados como entregues e referenciados), os
    de qualidade (ED-50) e os de fonte propria (ED-16, ED-31, ED-32).

Saidas (NAO editar a mao; regenerar):
  * CompleteReportCatalog.java  (registro normativo em codigo)
  * catalog-seed.frag.sql       (INSERTs da tabela report_catalog_entries,
                                 incluidos no corpo da migration da ED-46
                                 pelo marcador @@CATALOG_SEED@@)

Classificacao (regras documentadas no RELATORIO-ED-46):
  1. mapa manual TR -> relatorio da P3.22 (DELIVERED_P322);
  2. TR-665 e TR-199/TR-795 -> relatorios novos executaveis (EXECUTABLE);
  3. exclusoes de fonte propria por tema (ED-16/ED-31/ED-32/ED-48/ED-49/ED-50);
  4. demais -> DEPENDENT_SOURCE, com a dependencia textual extraida da
     coluna "Plano" do 18g (a tarefa que produz a fonte).
Entradas novas da 23d §5.2, dos POC e das fronteiras (P3.40/ED-87/ED-89
ficam FORA por mandado do acceptance) sao declaradas em NEW_ENTRIES.
"""
import re, sys, pathlib

BASE = pathlib.Path(__file__).resolve().parents[2]  # ed-46/
FONTES = BASE / "espelho" / "fontes"
OUT_JAVA = BASE / "entrega/intelligence/ziipsan-reporting/src/main/java/br/com/ziipsan/reporting/catalog/CompleteReportCatalog.java"
OUT_FRAG = BASE / "entrega/intelligence/ziipsan-reporting/src/main/resources/db/migration/tenant/catalog-seed.frag.sql"

def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"')

def parse_18g():
    lines = (FONTES / "18g-aderencia-operacao-qualidade-bi.md").read_text(encoding="utf-8").splitlines()
    start = next(i for i, l in enumerate(lines) if l.startswith("### 2.4"))
    end = next(i for i, l in enumerate(lines) if l.startswith("### 2.5"))
    rows = []
    for l in lines[start:end]:
        if not (l.startswith("| TR-") or l.startswith("| SG-")):
            continue
        cells = [c.strip() for c in l.strip().strip("|").split("|")]
        if len(cells) < 6:
            continue
        codes, title, plano = cells[0], cells[1], cells[4]
        trs = [int(x) for x in re.findall(r"TR-(\d+)", codes)]
        sgs = [int(x) for x in re.findall(r"SG-(\d+)", codes)]
        if not trs and not sgs:
            continue
        rows.append(dict(trs=trs, sgs=sgs, codes=codes, title=title, plano=plano))
    return rows

def parse_18d():
    lines = (FONTES / "18d-aderencia-cadastro-medicao-gis.md").read_text(encoding="utf-8").splitlines()
    rows = []
    for l in lines:
        m = re.match(r"\| \d+ \| (TR-19\d[^|]*)\| ([^|]+)\|", l)
        if not m:
            continue
        codes, title = m.group(1).strip(), m.group(2).strip()
        trs = [int(x) for x in re.findall(r"TR-(\d+)", codes)]
        sgs = [int(x) for x in re.findall(r"SG-(\d+)", codes)]
        if trs and 191 <= trs[0] <= 199:
            rows.append(dict(trs=trs, sgs=sgs, codes=codes, title=title, plano="18d (E8)"))
    return rows

# --- regra 1: mapa manual TR -> RPT da P3.22 (titulo conferido um a um) ---
P322_MAP = {
    667: ("RPT-005", "Detalhado e resumido sao a mesma view por arquivo (RPT-005)."),
    668: ("RPT-005", "Detalhado e resumido sao a mesma view por arquivo (RPT-005)."),
    671: ("RPT-002", None),
    672: ("RPT-004", "A RPT-004 agrupa por data de remessa/pagamento e banco (POC-60)."),
    674: ("RPT-001", "POC-58."),
    678: ("RPT-002", None),
    679: ("RPT-003", "POC-59."),
    680: ("RPT-001", None),
    683: ("RPT-022", "Parcial: a RPT-022 cobre protocolo, tempos medio/total e filtro por periodo; prazo x fora do prazo por equipe e satisfacao sao da ED-35/ED-36."),
    694: ("RPT-023", None),
    696: ("RPT-023", None),
    707: ("RPT-018", "POC-65."),
    712: ("RPT-019", None),
    729: ("RPT-007", None),
    742: ("RPT-010", None),
    743: ("RPT-006", "Servicos entram em debitos/creditos da conta (V4_1); nao ha quebra propria de servicos na RPT-006."),
    744: ("RPT-007", None),
    745: ("RPT-006", "Sucinto liquido: a RPT-006 traz agua, esgoto, debitos e creditos."),
    748: ("RPT-014", "POC-56."),
    758: ("RPT-024", None),
    770: ("RPT-004", "POC-60."),
    773: ("RPT-015", "Sintetico por situacao (RPT-015) e por categoria (RPT-016)."),
    775: ("RPT-016", "Economias por categoria (RPT-016) e por situacao (RPT-015)."),
    791: ("RPT-016", None),
    832: ("RPT-017", "POC-57."),
}
P322_18D = {191: ("RPT-013", None)}

# --- regra 3: exclusoes de fonte propria (matriz viva, linha ED-46) ---
def own_source(title, tr):
    t = title.lower()
    if "recadastramento" in t or "cadastramento já vencido" in t or "cadastramento ja vencido" in t:
        return "ED-16"
    if "dívida ativa" in t or "divida ativa" in t:
        return "ED-31"
    if any(k in t for k in ["contábeis", "contábil", "rubrica", "saldo por conta", "razão",
                            "lançamentos por processos contábeis", "faturamento por conta contábil",
                            "parcelamentos contábeis", "abatimentos feitos por fechamento",
                            "devolução de adiantamentos", "status das perdas obtidas",
                            "faturamento com a visão contábil", "diário da contabilidade",
                            "realocação de documentos"]):
        return "ED-32"
    if "qualidade de água" in t or "qualidade da água" in t or "análises laboratoriais" in t \
            or "analises laboratoriais" in t or "coleta de material" in t or "recoleta" in t:
        return "ED-50"
    if 780 <= tr <= 789:
        # Bloco inteiro de qualidade da agua/laboratorio do §2.4 (a matriz
        # remete os relatorios de qualidade a ED-50); inclui os paineis
        # TR-788/789, cujo titulo nao traz a palavra "qualidade".
        return "ED-50"
    if tr == 749:
        return "ED-48"
    if 762 <= tr <= 767:
        return "ED-49"
    return None

def source_ref(plano):
    codes = re.findall(r"(?:P\d+\.\d+|ED-\d+|P4\.1|P4\.3)", plano)
    codes = list(dict.fromkeys(codes))
    if codes:
        ref = "Fonte: " + "/".join(codes) + " (18g, coluna Plano)"
    else:
        ref = "Sem tarefa-fonte nomeada no 18g (coluna Plano: " + plano[:70].strip() + ")"
    if "falta a fonte" in plano or "falta o motor" in plano or "falta a fonte de dados" in plano:
        ref += "; o 18g registra fonte de dados faltante"
    return ref

def classify(row):
    tr = row["trs"][0] if row["trs"] else None
    title = row["title"]
    if tr == 664:
        return ("EXECUTABLE", None, None, None,
                "Caput da lista: atendido pelo nucleo da P3.22 + este catalogo completo (sem view propria).")
    if tr == 665:
        return ("EXECUTABLE", "RPT-103", "rpt_compensacoes_usuario", None,
                "Item da 23d §5.2: le o calculo persistido da P3.4 (cutoff_compensations, V9_2).")
    if tr in (199, 795):
        return ("EXECUTABLE", "RPT-104", "rpt_tempo_entre_leituras_leiturista", "POC-62", None)
    if tr == 835:
        return ("DEPENDENT_SOURCE", None, None, None,
                "O nucleo declarativo da P3.22 (entregue) e o gerador nativo parametrizavel; "
                "o gerador livre/BI desta linha e da ED-47. Sem codigo RPT: apontar uma view "
                "qualquer seria declaracao falsa.")
    if tr in P322_MAP:
        code, note = P322_MAP[tr]
        poc = {748: "POC-56", 832: "POC-57", 674: "POC-58", 679: "POC-59",
               770: "POC-60", 683: "POC-64", 707: "POC-65"}.get(tr)
        return ("DELIVERED_P322", code, None, poc, note)
    owner = own_source(title, tr or -1)
    if owner:
        return ("EXCLUDED_OWN_SOURCE", None, None, None,
                "Fonte propria: a matriz viva remete este relatorio a tarefa " + owner + ".")
    poc = {772: "POC-61", 798: "POC-63"}.get(tr or -1)
    note = None
    if tr == 798:
        note = ("Distribuicao, impressoes e bobinas vivem no coletor/app: o 18g cita P2.7/P4.4, "
                "mas a P4.4 foi extinta como duplicata da ED-18 (plano v3.0) - a fonte executavel e a ED-18.")
    return ("DEPENDENT_SOURCE", None, None, poc, note)

def classify_18d(row):
    tr = row["trs"][0]
    if tr in P322_18D:
        code, note = P322_18D[tr]
        return ("DELIVERED_P322", code, None, None, note)
    if tr == 199:
        return ("EXECUTABLE", "RPT-104", "rpt_tempo_entre_leituras_leiturista", "POC-62", None)
    notes = {
        192: "Boletim + OS automatica de verificacao: a OS e da P3.4/ED-23.",
        193: "Painel em tempo real (E7): app de campo da ED-18.",
        194: "Fonte existe (P2.5), relatorio nao entregue: dependencia textual registrada.",
        195: "Deteccao existe no metering (P2.5); o comunicado e da P3.5/ED-41.",
        196: "Nao e relatorio: presenca e mensagens no app (ED-18).",
        197: "Conjunto: a parte por leiturista esta na RPT-104; qualidade da leitura sem fonte unica.",
        198: "Por leiturista esta na RPT-104; por grupo/rota nao ha fonte confirmada em meter_readings (V5_1).",
    }
    src = {192: "P3.4/ED-23", 193: "ED-18", 194: "P2.5", 195: "P2.5/P3.5",
           196: "ED-18", 197: "P2.7", 198: "P2.7"}.get(tr, "P2.7")
    return ("DEPENDENT_SOURCE", None, None, None, notes.get(tr))

NEW_ENTRIES = [
    # id, refs, titulo, fonte, status, rpt, view, poc, nota
    ("S52-PRODUTIVIDADE-ATENDENTE", "23d §5.2; JOR-39",
     "Produtividade por atendente (protocolos, tempo médio, reabertura)",
     "service_protocols (V8_1, P1.36)", "EXECUTABLE", "RPT-101", "rpt_produtividade_atendente", None,
     "Reabertura SEM fonte na V8_1 (sem coluna de reabertura/atribuicao): metrica declarada pendente da ED-35; a view entrega protocolos e tempo medio por criador do protocolo."),
    ("S52-RECLAMACOES-ORIGEM-EXTERNA", "23d §5.2; ED-35",
     "Reclamações por origem externa do protocolo",
     "service_protocols (V8_1, P1.36); taxonomia de origem externa da ED-35", "EXECUTABLE",
     "RPT-102", "rpt_reclamacoes_origem_externa", None,
     "Origem = canal da V8_1 (REGULATOR = agencia reguladora; OMBUDSMAN = ouvidoria). As origens Consumidor.gov.br, Procon/SINDEC, Ministerio Publico e Defensoria sao da ED-35 (sac.external_channels) e ainda nao sao coluna: entram nesta view quando a ED-35 entregar, sem troca de contrato."),
    ("S52-ACESSOS-ATIVIDADE", "23d §5.2",
     "Acessos: atividade de acesso a dados pessoais por usuário (exportações, negados, cotas)",
     "personal_data_access_log (V1_6, P0.11/P1.12)", "EXECUTABLE", "RPT-105", "rpt_acessos_dados_usuario", None,
     "Uso de cotas aqui = QUOTA_EXCEEDED do log de acesso (P1.12). Cotas de contrato ficam na entrada S52-ACESSOS-COTAS."),
    ("S52-ACESSOS-INATIVOS", "23d §5.2",
     "Acessos: usuários inativos",
     "Identidade (Keycloak) - sem cadastro de usuarios no schema do tenant", "DEPENDENT_SOURCE", None, None, None,
     "Ausencia em log nao prova inatividade: a lista de usuarios vive na identidade (P0.13/ED-06). Sem essa fonte, nenhum relatorio honesto e possivel."),
    ("S52-ACESSOS-PERFIS-SENSIVEIS", "23d §5.2",
     "Acessos: perfis com permissão sensível",
     "Papeis/permissoes na identidade (Keycloak) e no ReportUserContext", "DEPENDENT_SOURCE", None, None, None,
     "Nao ha tabela de papeis no schema do tenant nos pacotes conferidos; a fonte e a identidade (P0.13/ED-06)."),
    ("S52-ACESSOS-COTAS", "23d §5.2",
     "Acessos: uso de cotas (contrato SaaS)",
     "platform.usage_ledger (V10_1, P2.11) - schema platform, escopo da Central (P3.15)", "DEPENDENT_SOURCE", None, None, None,
     "Tabela de plataforma, nao de tenant: a leitura e da Central Ziip (P3.15), nao de view rpt_* do tenant."),
    ("S52-ACESSOS-MENSAGENS", "23d §5.2",
     "Acessos: uso de mensagens",
     "Registro de disparos da P3.5/ED-41", "DEPENDENT_SOURCE", None, None, None,
     "Nos pacotes locais da P3.5 nao ha tabela persistida de disparos conferida; sem ela, o uso de mensagens nao e mensuravel sem inventar fonte."),
    ("S52-ALMOX-CURVA-ABC", "23d §5.2; ED-58",
     "Almoxarifado analítico: curva ABC", "ED-58 (almoxarifado)", "DEPENDENT_SOURCE", None, None, None, None),
    ("S52-ALMOX-GIRO", "23d §5.2; ED-58",
     "Almoxarifado analítico: giro de estoque", "ED-58 (almoxarifado)", "DEPENDENT_SOURCE", None, None, None, None),
    ("S52-ALMOX-SEM-MOVIMENTO", "23d §5.2; ED-58",
     "Almoxarifado analítico: itens sem movimento", "ED-58 (almoxarifado)", "DEPENDENT_SOURCE", None, None, None, None),
    ("S52-ALMOX-DIVERGENCIA", "23d §5.2; ED-58",
     "Almoxarifado analítico: divergência de inventário", "ED-58 (almoxarifado)", "DEPENDENT_SOURCE", None, None, None, None),
    ("S52-ALMOX-CONSUMO-POR-OS", "23d §5.2; ED-58",
     "Almoxarifado analítico: consumo por tipo de OS", "ED-58 (almoxarifado) + service_orders (V9_1)", "DEPENDENT_SOURCE", None, None, None, None),
    ("POC-66-SALDO-POR-CONTA", "POC-66 (roteiro VISAN 19)",
     "Acompanhamento de saldo por conta (contábil)",
     "ED-32 (fonte propria)", "DELIVERED_BY_SOURCE_TASK", None, None, "POC-66",
     "A acceptance da ED-32 reivindica POC-66..68 e a matriz exclui fonte propria da ED-46: a entrega e da ED-32 (TrialBalance/ReceivablesMonthlyBalance)."),
    ("POC-67-CONTABILIDADE-DIARIA", "POC-67 (roteiro VISAN 19)",
     "Acompanhamento diário da contabilidade",
     "ED-32 (fonte propria)", "DELIVERED_BY_SOURCE_TASK", None, None, "POC-67",
     "Entrega da ED-32 (DailyAccountingExport)."),
    ("POC-68-PENDENCIAS-RUBRICAS", "POC-68 (roteiro VISAN 19)",
     "Pendências por rubricas contábeis",
     "ED-32 (fonte propria)", "DELIVERED_BY_SOURCE_TASK", None, None, "POC-68",
     "Entrega da ED-32 (ReceivablesMonthlyBalance por rubrica/conta)."),
]

def entry_id(row):
    if row["trs"]:
        return "TR-%d" % row["trs"][0]
    return "SG-%d" % row["sgs"][0]

def main():
    entries = []
    for row in parse_18g():
        status, rpt, view, poc, note = classify(row)
        tr0 = row["trs"][0] if row["trs"] else -1
        if status == "DELIVERED_P322":
            src = "P3.22 (nucleo e views da V15_1)"
        elif tr0 == 665:
            src = "cutoff_compensations (V9_2, P3.4)"
        elif tr0 in (199, 795):
            src = "meter_readings (V5_1, P2.7)"
        elif tr0 == 664:
            src = "Nucleo da P3.22 + este catalogo (ED-46)"
        elif status == "EXCLUDED_OWN_SOURCE":
            src = "Tarefa de fonte propria " + str(own_source(row["title"], tr0))
        elif tr0 == 835:
            src = "Nucleo declarativo da P3.22 (entregue) + gerador livre da ED-47"
        else:
            src = source_ref(row["plano"])
        entries.append(dict(id=entry_id(row), refs=row["codes"], title=row["title"],
                            source=src, status=status, rpt=rpt, view=view, poc=poc, note=note))
    for row in parse_18d():
        if any(e["id"] == entry_id(row) for e in entries):
            continue
        status, rpt, view, poc, note = classify_18d(row)
        src = ("P3.22 (nucleo e views da V15_1)" if status == "DELIVERED_P322"
               else "meter_readings (V5_1, P2.7)" if status == "EXECUTABLE"
               else "Fonte: " + {192: "P3.4/ED-23", 193: "ED-18", 194: "P2.5", 195: "P2.5/P3.5",
                                  196: "ED-18", 197: "P2.7", 198: "P2.7"}.get(row["trs"][0], "P2.7") + " (18d, E8)")
        entries.append(dict(id=entry_id(row), refs=row["codes"], title=row["title"],
                            source=src, status=status, rpt=rpt, view=view, poc=poc, note=note))
    for (i, refs, title, src, status, rpt, view, poc, note) in NEW_ENTRIES:
        entries.append(dict(id=i, refs=refs, title=title, source=src, status=status,
                            rpt=rpt, view=view, poc=poc, note=note))

    # ---- Java ----
    j = []
    j.append("package br.com.ziipsan.reporting.catalog;")
    j.append("")
    j.append("import java.util.List;")
    j.append("")
    j.append("/**")
    j.append(" * Catalogo COMPLETO de relatorios da ED-46 (secoes 19 da VISAN e")
    j.append(" * 2.28 do SAMAE + itens ausentes da 23d §5.2 + POC-56..POC-68).")
    j.append(" *")
    j.append(" * <p>ARQUIVO GERADO por {@code scripts/generate-complete-catalog.py}")
    j.append(" * a partir do levantamento de editais (18g §2.4, 18d E8) - NAO editar")
    j.append(" * a mao. Cada entrada declara a tarefa que produz a sua fonte")
    j.append(" * (dependencia textual do acceptance): relatorio cuja fonte nao foi")
    j.append(" * entregue NAO ganha view - ganha a dependencia nomeada.")
    j.append(" *")
    j.append(" * <p>Fora deste catalogo por mandado do acceptance: relatorios de")
    j.append(" * privacidade (P3.40) e de operacao de estacoes (ED-87/ED-89), que")
    j.append(" * entram pela tarefa do modulo; e o pacote regulatorio por agencia")
    j.append(" * (ED-97), que e camada de exportacao sobre este catalogo, nao item dele.")
    j.append(" */")
    j.append("public final class CompleteReportCatalog {")
    j.append("")
    j.append("    private CompleteReportCatalog() {")
    j.append("    }")
    j.append("")
    j.append("    public static List<CatalogEntry> all() {")
    j.append("        return List.of(")
    rendered = []
    for e in entries:
        def q(v):
            return "null" if v is None else '"' + esc(str(v)) + '"'
        rendered.append("            new CatalogEntry(%s, %s, %s, %s, CatalogStatus.%s, %s, %s, %s, %s)"
                 % (q(e["id"]), q(e["refs"]), q(e["title"]), q(e["source"]), e["status"],
                    q(e["rpt"]), q(e["view"]), q(e["poc"]), q(e["note"])))
    j.append(",\n".join(rendered))
    j.append("        );")
    j.append("    }")
    j.append("}")
    OUT_JAVA.write_text("\n".join(j) + "\n", encoding="utf-8")

    # ---- SQL fragment ----
    s = ["-- GERADO por scripts/generate-complete-catalog.py - nao editar a mao.",
         "-- Uma linha por item do catalogo completo da ED-46 (dado de produto, sem tenant)."]
    for e in entries:
        def sq(v):
            if v is None:
                return "NULL"
            return "'" + str(v).replace("'", "''") + "'"
        s.append("INSERT INTO report_catalog_entries (id, edital_refs, title, source_ref, status, report_code, view_name, poc_ref, note) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) ON CONFLICT (id) DO NOTHING;"
                 % (sq(e["id"]), sq(e["refs"]), sq(e["title"]), sq(e["source"]), sq(e["status"]),
                    sq(e["rpt"]), sq(e["view"]), sq(e["poc"]), sq(e["note"])))
    OUT_FRAG.write_text("\n".join(s) + "\n", encoding="utf-8")
    from collections import Counter
    print("entradas:", len(entries), Counter(e["status"] for e in entries))

if __name__ == "__main__":
    main()
