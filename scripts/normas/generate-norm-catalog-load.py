#!/usr/bin/env python3
"""Gera a migration repetivel R__norm_catalog.sql (P1.34) a partir de
docs/normas/catalogo-normas.json.

Uso (a partir da raiz do repositorio):
    python3 scripts/normas/generate-norm-catalog-load.py

Regras (plano v3.0 §3, core R__norm_catalog.sql; header da V9_8):
- A P1.34 NAO cria migration versionada nova: a carga vive SOMENTE na
  repetivel R__norm_catalog.sql, sobre as tabelas da V9_8 (P1.32).
- Fonte: docs/normas/catalogo-normas.json, schema_version 1, 1.676 itens.
  O arquivo declara: metadados e resumos em palavras proprias; o texto
  das normas NAO e reproduzido. Este gerador copia SOMENTE os campos
  abaixo — nunca inventa texto de norma, sucessor, data ou edicao.
- Idempotencia: upsert por catalog_key (o id estavel do JSON) e upsert
  de edicao por (standard_id, edition). Reexecutar nao duplica. Linhas
  retiradas de uma versao futura do catalogo NAO sao apagadas aqui:
  apagar romperia ativacoes de tenants que referenciam o codigo; a
  retirada e decisao de curadoria, em nova versao desta carga.
- code = catalog_key = id do JSON. O JSON tem itens com o mesmo numero
  e ano (ex.: NBR 12212 duas vezes, NBR 8890 duas vezes), logo derivar
  o codigo de numero/ano colidiria no UNIQUE de code; o id e unico
  (1.676 distintos), estavel e seguro para URL (/standards/{code}).

Mapeamento de situacao (o CHECK da V9_8 aceita so 4 valores; o JSON
tem 10 — a nota original vai para o resumo, apos "Nota de situacao:",
para nada se perder):
    VIGENTE, CONFIRMADA            -> VIGENTE
    VIGENCIA_FUTURA                -> VIGENCIA_FUTURA
    REVOGADA, CANCELADA,
    NAO_E_NORMA_EM_VIGOR           -> REVOGADA
    SUBSTITUIDA                    -> SUBSTITUIDA
    PARCIALMENTE_REVOGADA          -> VIGENTE (segue parcialmente em
                                      vigor; a nota preservada adverte
                                      da revogacao parcial)
    EM_REVISAO                     -> VIGENTE (em vigor, em revisao)
    A_CONFIRMAR                    -> VIGENTE com selo NAO_CONFIRMADA
                                      (o selo, nao a situacao, carrega
                                      a incerteza; plano: item nao
                                      confirmado exibe o selo de revisao)
Selo de verificacao:
    needs_review = true (exatamente os 13 A_CONFIRMAR) -> NAO_CONFIRMADA
    verified = OFFICIAL_SOURCE   -> OFICIAL
    verified = SECONDARY_SOURCE  -> SECUNDARIA
Edicao (cert_standard_edition, uma por norma — o JSON nao traz mapa de
clausulas; cert_clause so recebe linhas na ED-76, a partir dos textos
comprados):
    edition_year, senao year, senao 's/e' (sem edicao declarada no
    catalogo; 5 itens). Nunca um ano inventado.
Datas de efeito (effective_from): o JSON nao tem campo estruturado de
data; so os 14 VIGENCIA_FUTURA ganham effective_from, curado da
status_note de cada um (tabela EFFECTIVE_FROM abaixo, com o motivo).
Os demais ficam NULL: a data de revogacao/substituicao vive na nota
de situacao, em texto livre, e inventar data estruturada a partir
dela seria fabricar dado regulatorio. O item nt-2026-001-rtc-nfag nao
tem data fixada na fonte ("datas serao fixadas por CGIBS e RFB") e
fica com effective_from NULL: a transicao so dispara quando uma
versao futura do catalogo trouxer a data.
superseded_by_id: NULL para todas as SUBSTITUIDA. O JSON nao traz
sucessor estruturado (so mencao em texto livre na status_note, sem
forma confiavel de ligar ao id do sucessor); ligar por palpite seria
inventar vinculo regulatorio. A coluna existe para curadoria futura.

EXCLUSAO — COMPRA ABNT PENDENTE (nao reabrir, nao fingir):
    A lista fechada de 33 normas da P1.28
    (docs/arquitetura/P1-28-LISTA-FECHADA-NORMAS-ABNT.md) esta com
    COMPRA PENDENTE — NAO COMPRADA, sem aprovacao do dono. Os itens do
    JSON correspondentes a essas 33 normas NAO entram na carga: 36
    linhas do JSON (duplicatas de catalogo e o item combinado
    10004-1/10004-2 contam aqui, porque sao a mesma norma a comprar).
    As demais 228 referencias ABNT do JSON entram SOMENTE como
    metadados (numero/edicao) + resumo em palavras proprias — o JSON
    nao contem texto de norma algum. Quando (e se) a compra for
    aprovada e feita, a curadoria re-inclui os 36 itens retirando-os
    da tabela EXCLUDED_ABNT abaixo, em nova versao desta carga.
"""

import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SOURCE = ROOT / "docs" / "normas" / "catalogo-normas.json"
TARGET = (
    ROOT
    / "shared"
    / "ziipsan-core"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "core"
    / "R__norm_catalog.sql"
)

# numero -> edicao a comprar (P1.28, lista fechada de 33 itens).
P128 = {
    "NBR 12586": "1992",
    "NBR 12587": "1992",
    "NBR 12218": "2017",
    "NBR 9649": "1986",
    "NBR 5647-1": "2025",
    "NBR 7665": "2025",
    "NBR 7675": "2026",
    "NBR 15561": "2024",
    "NBR 17287": "2026",
    "NBR 15750": "2024",
    "NBR 15802": "2010",
    "NBR 5667-1": "2006",
    "NBR 5667-2": "2006",
    "NBR 5667-3": "2006",
    "NBR 12208": "2020",
    "NBR 12209": "2011",
    "NBR 12212": "2017",
    "NBR 12213": "1992",
    "NBR 12214": "2020",
    "NBR 12216": "1992",
    "NBR 12217": "1994",
    "NBR 12244": "2006",
    "NBR 7362": "2025",
    "NBR 7367": "1988",
    "NBR 14486": "2000",
    "NBR ISO 21138-1": "2021",
    "NBR ISO 21138-3": "2021",
    "NBR 8890": "2020",
    "NBR 9648": "1986",
    "NBR 10160": "2026",
    "NBR 17076": "2024",
    "NBR 10004-1": "2024",
    "NBR 10004-2": "2024",
}
# Item combinado do catalogo que cobre as duas partes 10004-1 e 10004-2.
COMBINED_10004 = "abnt-nbr-10004-2024"

STATUS_MAP = {
    "VIGENTE": "VIGENTE",
    "CONFIRMADA": "VIGENTE",
    "VIGENCIA_FUTURA": "VIGENCIA_FUTURA",
    "REVOGADA": "REVOGADA",
    "CANCELADA": "REVOGADA",
    "NAO_E_NORMA_EM_VIGOR": "REVOGADA",
    "SUBSTITUIDA": "SUBSTITUIDA",
    "PARCIALMENTE_REVOGADA": "VIGENTE",
    "EM_REVISAO": "VIGENTE",
    "A_CONFIRMAR": "VIGENTE",
}

SEAL_MAP = {"OFFICIAL_SOURCE": "OFICIAL", "SECONDARY_SOURCE": "SECUNDARIA"}

# id -> (effective_from, motivo). Curado da status_note de cada item.
EFFECTIVE_FROM = {
    "decreto-13135-2026": ("2026-11-02", "20 dias apos DOU 13/10/2026, art. 25"),
    "mte-nr-10": ("2027-06-01", "5a revisao, Portaria MTE 737/2026, vigor 1 ano apos publicacao"),
    "bcb-in-777-2026": ("2027-02-01", "art. 3o: vigor em 01/02/2027, nao na publicacao"),
    "bcb-res-565-2026": ("2027-07-01", "produz efeitos em 01/07/2027"),
    # Split: 01/02/2027 (parte) e 01/07/2027 (parte); usa-se a primeira
    # data; o desdobramento permanece na nota de situacao da linha.
    "bcb-res-587-2026": ("2027-02-01", "primeira data do desdobramento (01/02/2027 e 01/07/2027)"),
    # Em vigor desde a publicacao, com efeitos desde 03/08/2026; a
    # obrigatoriedade foi fixada para 01/01/2027.
    "confaz-ajuste-sinief-49-2025": ("2027-01-01", "obrigatoriedade fixada para 01/01/2027"),
    "moc-nfag-modelo-75": ("2026-12-01", "obrigatoriedade de emissao em 01/12/2026"),
    "nt-2026-001-rtc-nfag": (None, "SEM DATA FIXADA na fonte (datas serao fixadas por CGIBS e RFB)"),
    "nt-2026-004-rtc-nfag": ("2026-11-16", "producao em 16/11/2026"),
    "stn-mf-portaria-2520-2026": ("2027-01-01", "exercicio financeiro de 2027"),
    "svrs-nfag-e-modelo-75": ("2026-12-01", "fatos geradores a partir de 01/12/2026"),
    "cisam-meio-oeste-rn-2026-05": ("2027-05-20", "art. 163, par. 2o: vigor em 20/05/2027"),
    # Data ja passada na carga (23/10/2026): a transicao de carga (o
    # UPDATE no fim deste arquivo) ja a vira para VIGENTE no provisionamento.
    "arsesp-deliberacao-1836-2026": ("2026-10-23", "vigor no dia seguinte ao 60o dia apos DOE-SP 24/08/2026"),
    "portaria-stn-mf-2520-2026": ("2027-01-01", "exercicio de 2027 (entrada duplicada da mesma portaria)"),
}


def sql_str(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def sql_date(value):
    if value is None:
        return "NULL"
    return f"DATE '{value}'"


def main():
    with open(SOURCE, encoding="utf-8") as fh:
        catalog = json.load(fh)
    assert catalog["schema_version"] == 1, "schema_version do JSON mudou; revisar o mapeamento"
    items = catalog["items"]
    assert len(items) == 1676, f"total do JSON mudou: {len(items)}"
    ids = [it["id"] for it in items]
    assert len(set(ids)) == len(ids), "ids duplicados no JSON"

    excluded = []
    loaded = []
    for it in items:
        edition = (it.get("edition_year") or it.get("year") or "").strip()
        if it["kind"] == "ABNT_NBR" and (
            (it["number"] in P128 and edition == P128[it["number"]])
            or it["id"] == COMBINED_10004
        ):
            excluded.append(it)
        else:
            loaded.append(it)

    matched_numbers = {it["number"] for it in excluded if it["id"] != COMBINED_10004}
    missing = set(P128) - matched_numbers
    assert not missing, f"itens da P1.28 sem correspondencia no JSON: {sorted(missing)}"
    assert len(excluded) == 36, f"excluidos esperados 36, obtidos {len(excluded)}"
    assert len(loaded) == 1640, f"carregados esperados 1640, obtidos {len(loaded)}"

    future = [it for it in items if it["status"] == "VIGENCIA_FUTURA"]
    assert set(EFFECTIVE_FROM) == {it["id"] for it in future}, (
        "tabela EFFECTIVE_FROM divergente dos VIGENCIA_FUTURA do JSON: "
        f"faltando={sorted(set(EFFECTIVE_FROM) - {it['id'] for it in future})}, "
        f"sobrando={sorted({it['id'] for it in future} - set(EFFECTIVE_FROM))}"
    )
    for it in loaded:
        assert it["status"] in STATUS_MAP, f"status desconhecido: {it['status']}"
        assert it["verified"] in SEAL_MAP, f"verified desconhecido: {it['verified']}"
        assert (it.get("title_pt") or "").strip(), f"titulo vazio: {it['id']}"

    rows = []
    for it in sorted(loaded, key=lambda x: x["id"]):
        status = STATUS_MAP[it["status"]]
        seal = "NAO_CONFIRMADA" if it.get("needs_review") else SEAL_MAP[it["verified"]]
        summary = it["scope_summary"]
        if (it.get("status_note") or "").strip():
            summary += "\n\nNota de situação: " + it["status_note"]
        edition = (it.get("edition_year") or it.get("year") or "s/e").strip()
        eff = EFFECTIVE_FROM.get(it["id"], (None, None))[0] if it["status"] == "VIGENCIA_FUTURA" else None
        rows.append(
            {
                "key": it["id"],
                "title": it["title_pt"],
                "summary": summary,
                "cls": it["norm_class"],
                "area": it["area_name"],
                "module": ",".join(it.get("modules_affected") or []) or None,
                "agency": it["issuer"],
                "seal": seal,
                "status": status,
                "url": it.get("url") or None,
                "eff": eff,
                "edition": edition,
            }
        )

    by_class = Counter(r["cls"] for r in rows)
    by_status = Counter(r["status"] for r in rows)
    by_seal = Counter(r["seal"] for r in rows)
    future_dated = sum(1 for r in rows if r["eff"] is not None)
    future_undated = [r["key"] for r in rows if r["status"] == "VIGENCIA_FUTURA" and r["eff"] is None]

    out = []
    out.append("-- Core Migration — Repeatable: R__norm_catalog.sql")
    out.append("-- Description: P1.34 — carga do catalogo global de normas do modulo")
    out.append("-- Certificacoes de qualidade (ADR-0041; plano v3.0 §3, core R__norm_catalog).")
    out.append("--")
    out.append("-- FONTE: docs/normas/catalogo-normas.json (schema_version 1, gerado em")
    out.append("-- 2026-10-22, 1.676 itens). Metadados e resumos em palavras proprias;")
    out.append("-- o texto das normas NAO e reproduzido — nenhuma linha abaixo contem")
    out.append("-- texto de norma. Gerado por scripts/normas/generate-norm-catalog-load.py;")
    out.append("-- NAO edite a mao: ajuste o gerador e regenere (a saida e deterministica).")
    out.append("--")
    out.append("-- DESTINO: platform.cert_standard / cert_standard_edition (V9_8, P1.32).")
    out.append("-- code = catalog_key = id estavel do JSON (o JSON tem itens com o mesmo")
    out.append("-- numero e ano, logo derivar o codigo de numero/ano colidiria no UNIQUE")
    out.append("-- de code). summary = scope_summary + 'Nota de situacao: ' + status_note,")
    out.append("-- quando houver nota — a situacao original do JSON (10 valores) e mapeada")
    out.append("-- para os 4 do CHECK da V9_8 (ver o gerador) e a nota preserva o detalhe.")
    out.append("-- module = modules_affected unidos por virgula (vazio -> NULL).")
    out.append("-- cert_clause NAO recebe linhas aqui: o JSON nao traz mapa de clausulas;")
    out.append("-- o mapa por edicao, em palavras proprias, e da ED-76, a partir dos")
    out.append("-- textos comprados — nunca antes da compra.")
    out.append("--")
    out.append(f"-- CARGA: {len(rows)} linhas (JSON 1.676 menos 36 excluidas, abaixo).")
    out.append("--   por classe: " + ", ".join(f"{k}={v}" for k, v in sorted(by_class.items())) + ".")
    out.append("--   por situacao (mapeada): " + ", ".join(f"{k}={v}" for k, v in sorted(by_status.items())) + ".")
    out.append("--   por selo: " + ", ".join(f"{k}={v}" for k, v in sorted(by_seal.items())) + ".")
    out.append(f"--   VIGENCIA_FUTURA com data de efeito curada: {future_dated}; sem data: "
               + ", ".join(future_undated) + " (a transicao aguarda a data).")
    out.append("--")
    out.append("-- EXCLUIDAS (36 linhas do JSON, 33 normas da P1.28 — COMPRA PENDENTE,")
    out.append("-- NAO COMPRADA, sem aprovacao do dono; docs/arquitetura/P1-28-LISTA-")
    out.append("-- FECHADA-NORMAS-ABNT.md): estas normas NAO aparecem no catalogo do")
    out.append("-- produto enquanto a compra nao for decidida. As demais referencias")
    out.append("-- ABNT entram SOMENTE como metadados (numero/edicao) + resumo proprio.")
    for it in sorted(excluded, key=lambda x: x["id"]):
        out.append(f"--   - {it['id']} ({it['number']}, ed. "
                   f"{(it.get('edition_year') or it.get('year') or '?')})")
    out.append("--")
    out.append("-- IDEMPOTENCIA: upsert por catalog_key e por (standard_id, edition);")
    out.append("-- reexecutar nao duplica. Linhas retiradas de versao futura do catalogo")
    out.append("-- NAO sao apagadas (ativacoes de tenants referenciam o codigo).")
    out.append("--")
    out.append("-- TRANSICAO DE CARGA: o UPDATE no fim aplica a mesma regra do servico")
    out.append("-- NormEffectivenessService (VIGENCIA_FUTURA -> VIGENTE quando")
    out.append("-- effective_from <= CURRENT_DATE), para o provisionamento ja nascer")
    out.append("-- correto; o scheduler diario (ziipsan.norm.effectiveness.enabled)")
    out.append("-- aplica a regra as datas que chegarem depois.")
    out.append("--")
    out.append("-- GRANT: a escrita do catalogo e do migrador, mas a transicao diaria")
    out.append("-- roda como ziipsan_app — por isso o UPDATE de coluna abaixo, restrito")
    out.append("-- a (status, updated_at). Sem ele, o job falharia por falta de privilegio")
    out.append("-- (a V9_8 concede so SELECT a ziipsan_app).")
    out.append("")
    out.append("GRANT UPDATE (status, updated_at) ON platform.cert_standard TO ziipsan_app;")
    out.append("")

    batch = 200
    for start in range(0, len(rows), batch):
        chunk = rows[start:start + batch]
        out.append("INSERT INTO platform.cert_standard (catalog_key, code, title, summary,")
        out.append("    standard_class, area, module, agency, verification_seal, status,")
        out.append("    official_source_url, effective_from, effective_to, superseded_by_id)")
        out.append("VALUES")
        vals = []
        for r in chunk:
            vals.append(
                "    ({key}, {key}, {title}, {summary}, {cls}, {area}, {module}, {agency}, "
                "{seal}, {status}, {url}, {eff}, NULL, NULL)".format(
                    key=sql_str(r["key"]),
                    title=sql_str(r["title"]),
                    summary=sql_str(r["summary"]),
                    cls=sql_str(r["cls"]),
                    area=sql_str(r["area"]),
                    module=sql_str(r["module"]),
                    agency=sql_str(r["agency"]),
                    seal=sql_str(r["seal"]),
                    status=sql_str(r["status"]),
                    url=sql_str(r["url"]),
                    eff=sql_date(r["eff"]),
                )
            )
        out.append(",\n".join(vals))
        out.append("ON CONFLICT (catalog_key) DO UPDATE SET")
        out.append("    code = EXCLUDED.code,")
        out.append("    title = EXCLUDED.title,")
        out.append("    summary = EXCLUDED.summary,")
        out.append("    standard_class = EXCLUDED.standard_class,")
        out.append("    area = EXCLUDED.area,")
        out.append("    module = EXCLUDED.module,")
        out.append("    agency = EXCLUDED.agency,")
        out.append("    verification_seal = EXCLUDED.verification_seal,")
        out.append("    status = EXCLUDED.status,")
        out.append("    official_source_url = EXCLUDED.official_source_url,")
        out.append("    effective_from = EXCLUDED.effective_from,")
        out.append("    effective_to = EXCLUDED.effective_to,")
        out.append("    updated_at = NOW();")
        out.append("")

    out.append("-- Uma edicao por norma (edition_year, senao year, senao 's/e'); a")
    out.append("-- vigencia da edicao acompanha a da norma (futura ou nula). O cast")
    out.append("-- ::date e obrigatorio: uma coluna VALUES so com NULL resolve para")
    out.append("-- text, e o INSERT ... SELECT nao coage text para date (falha")
    out.append("-- 'column is of type date but expression is of type text'); com")
    out.append("-- DATE literal a coluna ja e date e o cast e neutro.")
    for start in range(0, len(rows), batch):
        chunk = rows[start:start + batch]
        out.append("INSERT INTO platform.cert_standard_edition (standard_id, edition,")
        out.append("    effective_from, effective_to)")
        out.append("SELECT s.id, v.edition, v.effective_from::date, v.effective_to::date")
        out.append("FROM (VALUES")
        vals = [
            "    ({key}, {edition}, {eff}, NULL)".format(
                key=sql_str(r["key"]), edition=sql_str(r["edition"]), eff=sql_date(r["eff"])
            )
            for r in chunk
        ]
        out.append(",\n".join(vals))
        out.append(") AS v(catalog_key, edition, effective_from, effective_to)")
        out.append("JOIN platform.cert_standard s ON s.catalog_key = v.catalog_key")
        out.append("ON CONFLICT (standard_id, edition) DO UPDATE SET")
        out.append("    effective_from = EXCLUDED.effective_from,")
        out.append("    effective_to = EXCLUDED.effective_to,")
        out.append("    updated_at = NOW();")
        out.append("")

    out.append("-- Transicao de carga (mesma regra do NormEffectivenessService): norma")
    out.append("-- com vigencia futura cuja data de efeito ja chegou nasce VIGENTE.")
    out.append("UPDATE platform.cert_standard")
    out.append("   SET status = 'VIGENTE', updated_at = NOW()")
    out.append(" WHERE status = 'VIGENCIA_FUTURA'")
    out.append("   AND effective_from IS NOT NULL")
    out.append("   AND effective_from <= CURRENT_DATE;")
    out.append("")
    out.append("-- Guarda de carga: o catalogo de fabrica tem exatamente as linhas")
    out.append("-- desta versao. Linhas 'syn-' sao de teste (P1.32) e nao contam.")
    out.append("-- Se uma versao futura do catalogo mudar o total, o gerador atualiza")
    out.append("-- o numero junto — nunca ajuste o numero a mao sem regenerar.")
    out.append("DO $$")
    out.append("DECLARE v_count INT;")
    out.append("BEGIN")
    out.append("    SELECT COUNT(*) INTO v_count FROM platform.cert_standard")
    out.append("     WHERE catalog_key NOT LIKE 'syn-%';")
    out.append(f"    IF v_count <> {len(rows)} THEN")
    out.append("        RAISE EXCEPTION 'R__norm_catalog: esperado % linhas de fabrica, encontrado %', "
               f"{len(rows)}, v_count;")
    out.append("    END IF;")
    out.append("END $$;")
    out.append("")

    TARGET.write_text("\n".join(out), encoding="utf-8")
    print(f"fonte: {SOURCE} (schema_version 1, {len(items)} itens)")
    print(f"excluidas (P1.28, compra pendente): {len(excluded)}")
    print(f"carregadas: {len(rows)}")
    print("por classe: " + ", ".join(f"{k}={v}" for k, v in sorted(by_class.items())))
    print("por situacao: " + ", ".join(f"{k}={v}" for k, v in sorted(by_status.items())))
    print("por selo: " + ", ".join(f"{k}={v}" for k, v in sorted(by_seal.items())))
    print(f"vigencia futura com data: {future_dated}; sem data: {', '.join(future_undated)}")
    print(f"escrito: {TARGET} ({TARGET.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
