#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ED-01 — Conferência mecânica da prontidão PD-01 (somente leitura, local).

Recorte (fontes):
- Onda A: ids do JSON `tarefas-onda-A.json` (34 tarefas).
- Grupo B1 da onda B: lista das TABELAS do plano v3.0 (PLANO-v3.0.md,
  seção "Grupo B1: canais digitais, BI, qualidade e GIS" = 20 tarefas,
  com ED-88 e ED-92 no B1) confirmada por CHECAGEM-MONTAGEM.md
  ("ED-88 e ED-92 movidas para o grupo B1").
  DIVERGÊNCIA DECLARADA: a string do aceite da ED-01 enumera o B2 por
  intervalos (ED-83 a ED-94), o que, lido literalmente, poria ED-88 e
  ED-92 no B2 e daria B1 = 18. Este script adota as tabelas do plano
  (B1 = 20) e imprime os dois placares.
- P4: tarefas P4.* do JSON `tarefas-GL-P4-LC-PEND-C.json`, EXCETO P4.6
  (parada por PD-05 e excluída expressamente pelo aceite da LC.1 —
  "não entra no critério nem bloqueia o início").

Evidência por item (pasta local em ziipsan-producao/):
- pasta do pacote (ed-XX / p4.x);
- RELATORIO*.md em qualquer nível da pasta (produção);
- REVISAO*/CONFIRMACAO*.md com veredito aprovatório em LINHA DE
  VEREDITO formal (método corrigido — ver abaixo).

MÉTODO DE VEREDITO (CORRIGIDO na correção do estágio 3, 09/11/2026 —
defeitos D-REV-01/D-REV-02 da REVISAO-ED-01):
A versão original procurava a palavra-chave aprovatória
(APROVAD[OA]|CONFIRMAD[OA]|LIBERA) em QUALQUER ponto do arquivo de
revisão e sobredeclarou 2 itens: em ED-04 e ED-08 a palavra aparece em
texto corrido ("contrapartida confirmada", "teto 100 confirmado")
enquanto o veredito formal das respetivas revisões é PARCIAL.
Critério mecânico corrigido, MAIS estrito:
- LINHA DE VEREDITO: linha que contém a palavra "veredito"
  (case-insensitive) — é a linha que declara o veredito do artefato.
- BLOCO DE VEREDITO: a linha de veredito mais as 4 linhas seguintes,
  ignorando linhas de tabela Markdown (linhas iniciadas por "|") no
  corpo — cobre o cabeçalho de veredito seguido do corpo na(s)
  linha(s) seguinte(s) e vereditos partidos em duas linhas, sem
  varrer o texto corrido do documento.
- Um item só tem veredito aprovatório se ALGUM bloco de veredito de
  algum artefato de revisão/confirmação contiver palavra aprovatória
  (APROVAD[OA]|CONFIRMAD[OA]|LIBERA), depois de removidas as negações
  expressas ("NÃO APROVADO"/"NÃO CONFIRMADO" não contam).
- Consequência: "PARCIAL" como veredito formal classifica PARCIAL
  mesmo que "confirmado" apareça noutro ponto do arquivo fora dos
  blocos de veredito (casos ED-04 e ED-08).
Isto continua a ser leitura MECÂNICA de pacotes locais — não é
re-revisão semântica; cadeias de reprovação→correção são lidas no
documento de verificação (§6).

FONTE EXTERNA DECLARADA (fechamento fora da pasta do pacote):
- P4.8: a confirmação que fecha a cadeia está em
  `correcoes-m3-p4.8/REVISAO-CONFIRMACAO-P4.8.md` ("CONFIRMADO —
  P4.8 APROVADA"), fora da pasta `p4.8/` — facto registado no
  documento de verificação (§6.1) desde a produção. O script lê esse
  artefato nomeado, com o mesmo critério de blocos de veredito; sem
  ele, os blocos dentro da pasta só contêm REPROVADO.

TABELA DE EXCEÇÕES EXPLÍCITAS (juízo manual declarado, nunca ajuste
silencioso de saída — cada exceção cita o veredito real):
- ED-13: o veredito final formal da REVISAO-ED-13.md ("Veredito final
  da tarefa") é a fórmula de fecho "**PRONTA para integração, com as
  pendências declaradas**", sem palavra aprovatória padrão — o
  critério mecânico dá PARCIAL_LOCAL. A classificação COMPROVADO_LOCAL
  é a aceite na revisão independente da ED-01 (REVISAO-ED-01 §§3.1 e
  O-2: conta como comprovada com a ressalva viva do SAMAE 2.8,
  nomeada no documento de verificação §6.1). Exceção: força
  COMPROVADO_LOCAL quando há relatório e a fórmula PRONTA é
  localizada na linha do veredito final; a nota do CSV regista a
  exceção.

Classes:
- COMPROVADO_LOCAL: pasta + relatório + veredito aprovatório em bloco
  de veredito (ou exceção explícita acima).
- PARCIAL_LOCAL: pasta + relatório, sem veredito aprovatório em bloco
  de veredito.
- SEM_EVIDENCIA_LOCAL: sem pasta ou sem relatório.

Saídas: verificacao-pd01.csv e o placar no stdout.
NÃO acessa rede nem GitHub. NÃO prova o estado da main.
"""
import csv
import json
import pathlib
import re
import sys

HOME = pathlib.Path.home()
FONTES = HOME / "workspace/ziipsan-retomada/fontes"
PRODUCAO = HOME / "workspace/ziipsan-producao"
SAIDA = pathlib.Path(__file__).resolve().parent / "verificacao-pd01.csv"

B1_TABELAS_PLANO = ["ED-04", "ED-28", "ED-39", "ED-40", "ED-42", "ED-43",
                    "ED-47", "ED-48", "ED-49", "ED-50", "ED-51", "ED-54",
                    "ED-55", "ED-92", "ED-88", "ED-64", "ED-63", "ED-65",
                    "ED-66", "ED-67"]
B2_TABELAS_PLANO = ["ED-56", "ED-76", "ED-69", "ED-71", "ED-72", "ED-73",
                    "ED-74", "ED-84", "ED-87", "ED-105", "ED-70", "ED-75",
                    "ED-83", "ED-89", "ED-79", "ED-91", "ED-96", "ED-77",
                    "ED-85", "ED-78", "ED-86", "ED-90", "ED-93", "ED-94",
                    "ED-97", "ED-98", "ED-99"]
APROVATORIO = re.compile(r"APROVAD[OA]|CONFIRMAD[OA]|LIBERA", re.IGNORECASE)
NEGACAO = re.compile(r"N[ÃA]O\s+(?:APROVAD[OA]|CONFIRMAD[OA])", re.IGNORECASE)
TOKEN_VEREDITO = re.compile(
    r"APROVAD[OA]|CONFIRMAD[OA]|LIBERA|REPROVAD[OA]|PARCIAL|PENDENTE",
    re.IGNORECASE)

# Fechamento de cadeia fora da pasta do pacote (declarado no docstring).
FONTES_EXTERNAS = {
    "P4.8": PRODUCAO / "correcoes-m3-p4.8" / "REVISAO-CONFIRMACAO-P4.8.md",
}

# Exceções explícitas e justificadas (declaradas no docstring).
EXCECOES = {
    "ED-13": {
        "padrao": re.compile(r"PRONTA\s+para integra", re.IGNORECASE),
        "classe": "COMPROVADO_LOCAL",
        "nota": ("EXCEÇÃO EXPLÍCITA (tabela no docstring do script): "
                 "veredito final formal é 'PRONTA para integração, com as "
                 "pendências declaradas' (REVISAO-ED-13.md), fórmula sem "
                 "palavra aprovatória padrão; classificação aceite na "
                 "REVISAO-ED-01 §§3.1/O-2, com a ressalva do SAMAE 2.8 "
                 "nomeada no documento de verificação §6.1"),
    },
}


def carregar(nome):
    return json.loads((FONTES / nome).read_text(encoding="utf-8"))


def pasta_de(tid):
    if tid.startswith("ED-"):
        n = int(tid.split("-")[1])
        return PRODUCAO / (f"ed-{n:02d}" if n < 100 else f"ed-{n}")
    return PRODUCAO / tid.lower()


def blocos_de_veredito(texto):
    """Devolve os blocos de veredito do artefato (critério do docstring).

    Cada bloco é (linha_inicial, [linhas do bloco]): a linha que contém
    "veredito" mais as 4 linhas seguintes, excluídas as linhas de
    tabela Markdown do corpo do bloco.
    """
    linhas = texto.splitlines()
    blocos = []
    for i, linha in enumerate(linhas):
        if "veredito" in linha.lower():
            corpo = [x for x in linhas[i + 1:i + 5]
                     if not x.strip().startswith("|")]
            blocos.append((linha.strip(), [linha] + corpo))
    return blocos


def bloco_tem_aprovatorio(bloco_linhas):
    texto = NEGACAO.sub("", "\n".join(bloco_linhas))
    return bool(APROVATORIO.search(texto))


def resumo_do_bloco(linha_inicial, bloco_linhas):
    """Linha de veredito legível para o CSV: a linha inicial e, se outra
    linha do bloco trouxer o token do veredito, também essa linha."""
    resumo = linha_inicial
    for extra in bloco_linhas[1:]:
        if TOKEN_VEREDITO.search(NEGACAO.sub("", extra)):
            resumo = (resumo + " ⏐ " + extra.strip())[:200]
            break
    return resumo[:200]


def conferir(tid, titulo):
    pasta = pasta_de(tid)
    if not pasta.is_dir():
        return [tid, titulo, pasta.name, "", "", "", "SEM_EVIDENCIA_LOCAL",
                "pasta local ausente"]
    arquivos = [p for p in pasta.rglob("*.md") if p.is_file()]
    relatorios = sorted(p.name for p in arquivos
                        if p.name.startswith("RELATORIO"))
    revisoes = sorted(p for p in arquivos
                      if p.name.startswith(("REVISAO", "CONFIRMACAO",
                                            "REREVISAO")))
    nomes_revisoes = [p.name for p in revisoes]
    if tid in FONTES_EXTERNAS and FONTES_EXTERNAS[tid].is_file():
        revisoes = revisoes + [FONTES_EXTERNAS[tid]]
        nomes_revisoes.append(
            "correcoes-m3-p4.8/REVISAO-CONFIRMACAO-P4.8.md "
            "(fonte externa declarada)")
    aprov = []          # (prioridade, ordem, nome, resumo do bloco)
    tem_reprov = False  # algum bloco com REPROVAD (cadeia de correção)
    n_blocos = 0
    veredito_parcial = ""  # primeiro bloco com PARCIAL/PENDENTE, p/ o CSV
    for ordem, rev in enumerate(revisoes):
        try:
            texto = rev.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        # Para o veredito exibido, blocos de CONFIRMACAO (e a fonte
        # externa declarada) têm prioridade sobre os de REVISAO: são o
        # fecho da cadeia. A classe só depende da existência de bloco
        # aprovatório — a prioridade é só de exibição no CSV.
        prioridade = 0 if ("CONFIRMACAO" in rev.name
                           or rev in FONTES_EXTERNAS.values()) else 1
        for linha_inicial, bloco in blocos_de_veredito(texto):
            n_blocos += 1
            limpo = NEGACAO.sub("", "\n".join(bloco))
            if re.search(r"REPROVAD", limpo, re.IGNORECASE):
                tem_reprov = True
            if bloco_tem_aprovatorio(bloco):
                aprov.append((prioridade, ordem, rev.name,
                              resumo_do_bloco(linha_inicial, bloco)))
            elif not veredito_parcial and re.search(
                    r"PARCIAL|PENDENTE", limpo, re.IGNORECASE):
                veredito_parcial = resumo_do_bloco(linha_inicial, bloco)
    if relatorios and aprov:
        classe = "COMPROVADO_LOCAL"
        veredito = sorted(aprov)[0][3]
    elif relatorios:
        classe = "PARCIAL_LOCAL"
        veredito = veredito_parcial
    else:
        classe = "SEM_EVIDENCIA_LOCAL"
        veredito = ""
    nota = ""
    # Exceção explícita (tabela no docstring): só eleva PARCIAL com
    # relatório quando a fórmula do veredito real é localizada.
    if (tid in EXCECOES and classe == "PARCIAL_LOCAL" and relatorios):
        exc = EXCECOES[tid]
        achou = False
        for rev in revisoes:
            try:
                texto = rev.read_text(encoding="utf-8", errors="ignore")
            except OSError:
                continue
            for linha_inicial, bloco in blocos_de_veredito(texto):
                if exc["padrao"].search("\n".join(bloco)):
                    achou = True
                    veredito = resumo_do_bloco(linha_inicial, bloco)
                    break
            if achou:
                break
        if achou:
            classe = exc["classe"]
            nota = exc["nota"]
    if not nota:
        if classe == "COMPROVADO_LOCAL" and tem_reprov:
            nota = ("cadeia com reprovação inicial localizada em bloco de "
                    "veredito — ler a cadeia completa (correção/conferência) "
                    "no documento de verificação (§6)")
        elif classe == "PARCIAL_LOCAL" and revisoes and n_blocos == 0:
            nota = ("relatório sem nenhuma linha de veredito formal "
                    "localizável nos artefatos de revisão/confirmação")
        elif classe == "PARCIAL_LOCAL" and revisoes:
            nota = ("relatório sem veredito aprovatório em linha de veredito "
                    "formal (blocos de veredito sem APROVADO/CONFIRMADO/"
                    "LIBERA)")
        elif classe == "PARCIAL_LOCAL":
            nota = "relatório sem artefato de revisão/confirmação"
        elif classe == "SEM_EVIDENCIA_LOCAL":
            nota = "sem RELATORIO na pasta"
    return [tid, titulo, pasta.name, ";".join(relatorios),
            ";".join(nomes_revisoes), veredito, classe, nota]


def main():
    onda_a = carregar("docs__plano-v3__redacao__tarefas-onda-A.json")
    onda_b = carregar("docs__plano-v3__redacao__tarefas-onda-B.json")
    glp4 = carregar("docs__plano-v3__redacao__tarefas-GL-P4-LC-PEND-C.json")
    titulos = {}
    for rec in onda_a + onda_b + glp4:
        titulos[rec["id"]] = rec.get("title", "")

    grupos = []
    grupos.append(("ONDA_A", [r["id"] for r in onda_a]))
    grupos.append(("B1_TABELAS_PLANO", B1_TABELAS_PLANO))
    p4_criterio = [r["id"] for r in glp4
                   if r["id"].startswith("P4.") and r["id"] != "P4.6"]
    grupos.append(("P4_SEM_P4.6", sorted(p4_criterio)))

    linhas = []
    placar = {}
    for grupo, ids in grupos:
        cont = {"COMPROVADO_LOCAL": 0, "PARCIAL_LOCAL": 0,
                "SEM_EVIDENCIA_LOCAL": 0}
        for tid in ids:
            row = conferir(tid, titulos.get(tid, ""))
            linhas.append([grupo] + row)
            cont[row[6]] += 1
        placar[grupo] = (len(ids), cont)

    with SAIDA.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["grupo", "tarefa", "titulo", "pasta_local",
                    "relatorios", "revisoes_confirmacoes", "veredito_lido",
                    "classe", "nota"])
        w.writerows(linhas)

    total = sum(v[0] for v in placar.values())
    comp = sum(v[1]["COMPROVADO_LOCAL"] for v in placar.values())
    parc = sum(v[1]["PARCIAL_LOCAL"] for v in placar.values())
    sem = sum(v[1]["SEM_EVIDENCIA_LOCAL"] for v in placar.values())
    print("PLACAR PD-01 (evidência LOCAL de pacotes; main não verificada)")
    for grupo, (n, cont) in placar.items():
        print(f"  {grupo}: conferidos={n} comprovados={cont['COMPROVADO_LOCAL']} "
              f"parciais={cont['PARCIAL_LOCAL']} sem_evidencia={cont['SEM_EVIDENCIA_LOCAL']}")
    print(f"  TOTAL: conferidos={total} comprovados={comp} parciais={parc} sem_evidencia={sem}")
    print(f"  Fora do critério (declarado, não conferido como pendência): "
          f"P4.6 (PD-05/LC.1); B2 tabelas do plano = {len(B2_TABELAS_PLANO)} tarefas (só LC.1)")
    b1_literal = [r["id"] for r in onda_b if r["id"] not in
                  (["ED-56"] + [f"ED-{n}" for n in range(69, 80)]
                   + [f"ED-{n}" for n in range(83, 95)]
                   + [f"ED-{n}" for n in range(96, 100)] + ["ED-105", "ED-01"])]
    print(f"  Divergência B1: tabelas do plano = {len(B1_TABELAS_PLANO)} tarefas; "
          f"leitura literal dos intervalos do aceite = {len(b1_literal)} tarefas "
          f"(diferença: ED-88 e ED-92)")
    print(f"  CSV: {SAIDA}")
    faltantes = [r[1] for r in linhas if r[7] != "COMPROVADO_LOCAL"]
    print(f"  Itens não comprovados localmente: {faltantes}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
