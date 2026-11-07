#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P3.23 -- Avaliador do ensaio cronometrado de POC.

Le o CSV de resultados POR ITEM (uma linha por item de roteiro executado,
no formato de docs/demo/resultados-ensaio.template.csv) e aplica, sem
inventar numero nenhum, as regras do aceite da P3.23:

  * criterio de ensaio do P3: pelo menos 50 de 162 pontos no roteiro da
    VISAN -- e a estrutura do roteiro precisa somar 162 pontos maximos;
    se somar outro total, o veredito e INCONCLUSIVO_ESTRUTURA (o arquivo
    nao e o roteiro da P1.25), nunca uma aprovacao por aproximacao;
  * secoes 2.4 e 2.5 do SAMAE SEM FALHA: qualquer item dessas secoes com
    resultado FALHOU reprova; item NAO_EXECUTADO ou BLOQUEADO nessas
    secoes deixa o criterio NAO_COMPROVADO (nao se declara "sem falha"
    o que nao foi executado);
  * ensaio cronometrado contra o tempo de preparo dos editais de
    referencia (5 e 7 dias uteis): o tempo decorrido entre --inicio e
    --fim e convertido em dias uteis decorridos e comparado com os DOIS
    orcamentos; o veredito global exige caber no maior (7);
  * cobertura: cada item executado precisa ligar requisito, tarefa e
    teste (as colunas do relatorio de cobertura do aceite); item sem a
    ligacao completa torna a cobertura INCOMPLETA e o global nao aprova.

As mesmas regras existem em Java (PocEnsaioCriterion, pacote
br.com.ziipsan.platform.poc) com os mesmos numeros conferidos a mao nos
dois conjuntos de testes -- divergencia entre eles e defeito.

Dias uteis: segunda a sexta, menos os feriados listados em --feriados
(um AAAA-MM-DD por linha). NENHUM feriado e embutido: o calendario de
feriados do ensaio real e dado de entrada de quem ensaia.

Definicao de "dias uteis decorridos" entre duas datas d1 <= d2: numero
de dias uteis d com d1 < d <= d2, MAIS a fracao do primeiro dia. Para o
uso do ensaio (inicio e fim com hora), a fracao conta horas corridas do
primeiro dia util / 24 -- ver dias_uteis_decorridos() e os testes com
contas feitas a mao. Ensaio que comeca e termina no mesmo dia util tem
decorrido < 1 (so a fracao), o que e o esperado de um cronometro.

Uso:
  python3 avaliar-ensaio.py --resultados resultados.csv \
      --inicio 2026-11-02T08:00:00 --fim 2026-11-06T17:00:00 \
      [--feriados feriados.txt] --saida DIR
Saida: resultado-ensaio.json + cobertura-poc.csv no diretorio de saida.
Codigo de saida: 0 = APROVADO, 1 = REPROVADO_OU_INCOMPLETO, 2 = uso.
"""
import argparse
import csv
import json
import os
import sys
from datetime import date, datetime, time, timedelta

VISAN_PONTOS_TOTAIS = 162   # roteiro da VISAN (P1.25): 81 itens, 162 pontos
VISAN_MINIMO_P3 = 50        # criterio de ensaio do P3 (aceite da P3.23)
SAMAE_SECOES_CRITERIO = ("2.4", "2.5")
ORCAMENTOS_DIAS_UTEIS = (5, 7)  # tempos de preparo dos editais de referencia

RESULTADOS_VALIDOS = ("APROVADO", "FALHOU", "NAO_EXECUTADO", "BLOQUEADO")


def dia_util(d: date, feriados: set) -> bool:
    return d.weekday() < 5 and d not in feriados


def dias_uteis_decorridos(inicio: datetime, fim: datetime, feriados: set) -> float:
    """Dias uteis decorridos entre inicio e fim (ver docstring do modulo).

    Conta: dias uteis inteiros ESTRITAMENTE entre as duas datas, mais a
    fracao do dia do inicio (do instante do inicio ate o fim daquele dia,
    se dia util) e a fracao do dia do fim (do comeco do dia ate o
    instante do fim, se dia util). Com inicio e fim a meia-noite, o
    resultado e o numero inteiro de dias uteis em (inicio, fim].
    """
    if fim < inicio:
        raise ValueError("fim anterior ao inicio")
    if inicio.date() == fim.date():
        if not dia_util(inicio.date(), feriados):
            return 0.0
        return (fim - inicio).total_seconds() / 86400.0
    total = 0.0
    # fracao do primeiro dia (revisao cruzada P3.23: a meia-noite seguinte,
    # nao time.max -- com time.max a semana a meia-noite dava
    # 4,999999999988 em vez de 5,0 exatos)
    if dia_util(inicio.date(), feriados):
        fim_do_dia = datetime.combine(inicio.date() + timedelta(days=1), time.min)
        total += (fim_do_dia - inicio).total_seconds() / 86400.0
    # dias inteiros estritamente entre
    d = inicio.date() + timedelta(days=1)
    while d < fim.date():
        if dia_util(d, feriados):
            total += 1.0
        d += timedelta(days=1)
    # fracao do ultimo dia
    if dia_util(fim.date(), feriados):
        comeco = datetime.combine(fim.date(), time.min)
        total += (fim - comeco).total_seconds() / 86400.0
    return total


def carregar_resultados(caminho: str) -> list:
    with open(caminho, newline="", encoding="utf-8") as f:
        linhas = list(csv.DictReader(f))
    for i, r in enumerate(linhas, start=2):
        if r.get("resultado") not in RESULTADOS_VALIDOS:
            raise ValueError(
                f"linha {i}: resultado invalido {r.get('resultado')!r} "
                f"(validos: {', '.join(RESULTADOS_VALIDOS)})")
        for campo in ("pontos_maximos", "pontos_obtidos"):
            try:
                r[campo] = int(r[campo])
            except (TypeError, ValueError):
                raise ValueError(f"linha {i}: {campo} nao e inteiro: {r[campo]!r}")
        if r["pontos_obtidos"] < 0 or r["pontos_obtidos"] > r["pontos_maximos"]:
            raise ValueError(
                f"linha {i}: pontos_obtidos fora de [0, pontos_maximos]")
        if r["resultado"] == "APROVADO" and r["pontos_obtidos"] != r["pontos_maximos"]:
            raise ValueError(
                f"linha {i}: item APROVADO precisa de pontos_obtidos == "
                f"pontos_maximos (pontuacao parcial nao e aprovacao)")
        if r["resultado"] == "FALHOU" and r["pontos_obtidos"] != 0:
            raise ValueError(
                f"linha {i}: item FALHOU precisa de pontos_obtidos == 0")
    return linhas


def avaliar(linhas: list, inicio: datetime, fim: datetime, feriados: set) -> dict:
    visan = [r for r in linhas if r["roteiro"] == "VISAN"]
    samae_criterio = [r for r in linhas
                      if r["roteiro"] == "SAMAE" and r["secao"] in SAMAE_SECOES_CRITERIO]

    visan_max = sum(r["pontos_maximos"] for r in visan)
    visan_obt = sum(r["pontos_obtidos"] for r in visan
                    if r["resultado"] in ("APROVADO",))
    if visan_max != VISAN_PONTOS_TOTAIS:
        veredito_visan = "INCONCLUSIVO_ESTRUTURA"
    elif visan_obt >= VISAN_MINIMO_P3:
        veredito_visan = "APROVADO"
    else:
        veredito_visan = "REPROVADO"

    if any(r["resultado"] == "FALHOU" for r in samae_criterio):
        veredito_samae = "REPROVADO"
    elif not samae_criterio or any(
            r["resultado"] != "APROVADO" for r in samae_criterio):
        veredito_samae = "NAO_COMPROVADO"
    else:
        veredito_samae = "APROVADO"

    decorrido = dias_uteis_decorridos(inicio, fim, feriados)
    orcamentos = {str(o): ("DENTRO" if decorrido <= o else "FORA")
                  for o in ORCAMENTOS_DIAS_UTEIS}
    dentro_do_maior = decorrido <= max(ORCAMENTOS_DIAS_UTEIS)

    executados = [r for r in linhas if r["resultado"] in ("APROVADO", "FALHOU")]
    sem_cobertura = [r["item"] for r in executados
                     if not (r.get("requisito") and r.get("tarefa") and r.get("teste"))]
    cobertura = "COMPLETA" if executados and not sem_cobertura else "INCOMPLETA"

    aprovado = (veredito_visan == "APROVADO" and veredito_samae == "APROVADO"
                and dentro_do_maior and cobertura == "COMPLETA")
    return {
        "tarefa": "P3.23",
        "veredito_global": "APROVADO" if aprovado else "REPROVADO_OU_INCOMPLETO",
        "visan": {
            "pontos_maximos_no_arquivo": visan_max,
            "pontos_totais_do_roteiro": VISAN_PONTOS_TOTAIS,
            "pontos_obtidos": visan_obt,
            "minimo_do_criterio_p3": VISAN_MINIMO_P3,
            "veredito": veredito_visan,
        },
        "samae_secoes_2_4_e_2_5": {
            "itens": len(samae_criterio),
            "falhas": sum(1 for r in samae_criterio if r["resultado"] == "FALHOU"),
            "nao_executados_ou_bloqueados": sum(
                1 for r in samae_criterio
                if r["resultado"] in ("NAO_EXECUTADO", "BLOQUEADO")),
            "veredito": veredito_samae,
        },
        "tempo_de_preparo": {
            "inicio": inicio.isoformat(),
            "fim": fim.isoformat(),
            "dias_uteis_decorridos": round(decorrido, 4),
            "orcamentos_dias_uteis": orcamentos,
        },
        "cobertura": {
            "itens_executados": len(executados),
            "itens_sem_ligacao_requisito_tarefa_teste": sem_cobertura,
            "veredito": cobertura,
        },
        "itens_registrados": len(linhas),
    }


def escrever_cobertura(linhas: list, destino: str) -> None:
    with open(destino, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["requisito", "tarefa", "teste", "item_roteiro", "roteiro",
                    "secao", "resultado", "pontos_obtidos", "pontos_maximos",
                    "evidencia"])
        for r in linhas:
            w.writerow([r.get("requisito", ""), r.get("tarefa", ""),
                        r.get("teste", ""), r.get("item", ""),
                        r.get("roteiro", ""), r.get("secao", ""),
                        r.get("resultado", ""), r.get("pontos_obtidos", ""),
                        r.get("pontos_maximos", ""), r.get("evidencia", "")])


def main() -> int:
    ap = argparse.ArgumentParser(description="Avaliador do ensaio de POC (P3.23)")
    ap.add_argument("--resultados", required=True)
    ap.add_argument("--inicio", required=True, help="ISO, ex.: 2026-11-02T08:00:00")
    ap.add_argument("--fim", required=True, help="ISO, ex.: 2026-11-06T17:00:00")
    ap.add_argument("--feriados", help="arquivo com um AAAA-MM-DD por linha")
    ap.add_argument("--saida", default=os.path.join(os.getcwd(), "ensaio"))
    a = ap.parse_args()
    feriados = set()
    if a.feriados:
        with open(a.feriados, encoding="utf-8") as f:
            for linha in f:
                linha = linha.strip()
                if linha:
                    feriados.add(date.fromisoformat(linha))
    try:
        linhas = carregar_resultados(a.resultados)
        resultado = avaliar(linhas, datetime.fromisoformat(a.inicio),
                            datetime.fromisoformat(a.fim), feriados)
    except (ValueError, OSError) as e:
        print(f"ERRO: {e}", file=sys.stderr)
        return 2
    os.makedirs(a.saida, exist_ok=True)
    with open(os.path.join(a.saida, "resultado-ensaio.json"), "w",
              encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2)
        f.write("\n")
    escrever_cobertura(linhas, os.path.join(a.saida, "cobertura-poc.csv"))
    print(json.dumps(resultado, ensure_ascii=False, indent=2))
    return 0 if resultado["veredito_global"] == "APROVADO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
