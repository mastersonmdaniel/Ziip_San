#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P3.23 -- Gerador deterministico da massa sintetica do TENANT DE DEMONSTRACAO.

Aceite atendido por este arquivo:
  * tenant de demonstracao com PELO MENOS 10 mil unidades ficticias geradas
    pela massa sintetica (o gerador RECUSA menos de 10.000 -- falha fechada);
  * sem dado pessoal real: titular, documento e endereco sao FORMULAS sobre
    a sequencia e a semente -- nenhum nome, documento ou endereco de pessoa
    ou empresa real entra aqui;
  * documentos gerados MARCADOS como sinteticos: toda linha carrega
    documento_sintetico=SIM e marca=SINTETICO, o titular se chama
    "TITULAR SINTETICO NNNNN" e o endereco e "RUA SINTETICA ..." -- a marca
    viaja COM o dado, no mesmo registro (a varredura da P0.45 procura
    documento formatado sem a marca SINTETICO; aqui ela nunca falta).

Diferenca para o gerador da P2.9 (scripts/carga/gerar-massa.py): a carga
nao precisava de titular -- bastavam ligacao, leitura e consumo. A
DEMONSTRACAO mostra telas de cadastro, por isso esta massa tem titular e
documento (CPF para RES, CNPJ para COM). As formulas de categoria,
consumo, leitura, rota e UUID sao AS MESMAS da P2.9, para a massa de
demonstracao e a de carga enxergarem os mesmos ids com a mesma semente
de familia (a semente padrao e outra, declarada abaixo: demo nao e carga).

Documento: os digitos-base derivam de md5(semente:tipo:seq) e os digitos
verificadores sao CALCULADOS pelo algoritmo publico de CPF/CNPJ, para o
cadastro de demonstracao passar nas validacoes do produto. Numero com DV
valido NAO e documento de pessoa real: e uma combinacao sintetica,
sempre acompanhada da marca SINTETICO e de titular ficticio declarado.
Sequencias de digitos todos iguais (000..., 111...), que o algoritmo
aceitaria, sao rejeitadas e re-derivadas (ver digitos_base).

Determinismo: a mesma (semente, N) gera byte a byte o mesmo CSV.

Uso:
  python3 gerar-massa-demo.py [--unidades 12000] [--semente ...] [--saida DIR]
"""
import argparse
import csv
import hashlib
import json
import os
import sys

SEMENTE_PADRAO = "ziipsan-demo-p323-v1"
MINIMO_UNIDADES = 10_000  # piso do aceite da P3.23 ("pelo menos 10 mil")
PADRAO_UNIDADES = 12_000  # padrao com folga sobre o piso; configuravel


def uuid_deterministico(semente: str, tipo: str, seq: int) -> str:
    """UUID derivado de md5(semente:tipo:seq); identico ao SQL e a P2.9."""
    h = hashlib.md5(f"{semente}:{tipo}:{seq}".encode("utf-8")).hexdigest()
    variante = "89ab"[int(h[16], 16) % 4]
    return f"{h[0:8]}-{h[8:12]}-5{h[13:16]}-{variante}{h[17:20]}-{h[20:32]}"


def categoria(seq: int) -> str:
    return "COM" if seq % 5 == 0 else "RES"


def consumo(seq: int) -> int:
    if categoria(seq) == "COM":
        return 30 + ((seq * 11) % 120)
    return 12 + ((seq * 7) % 25)


def leitura_anterior(seq: int) -> int:
    return (seq * 37) % 90000


def rota(seq: int) -> str:
    return f"ROTA-{((seq - 1) % 100) + 1:03d}"


def _digitos(semente: str, tipo: str, seq: int, quantidade: int) -> list:
    """Digitos-base deterministicos a partir do md5; nunca todos iguais."""
    h = hashlib.md5(f"{semente}:{tipo}:{seq}".encode("utf-8")).hexdigest()
    digitos = [int(c, 16) % 10 for c in h[:quantidade]]
    if len(set(digitos)) == 1:  # sequencia proibida (000..., 111...)
        digitos[-1] = (digitos[-1] + 1) % 10
        if len(set(digitos)) == 1:
            digitos[0] = (digitos[0] + 1) % 10
    return digitos


def _dv(digitos: list, pesos: list) -> int:
    resto = sum(d * p for d, p in zip(digitos, pesos)) % 11
    return 0 if resto < 2 else 11 - resto


def cpf_sintetico(semente: str, seq: int) -> str:
    """CPF sintetico com DV calculado. SEMPRE usado com a marca SINTETICO."""
    base = _digitos(semente, "cpf", seq, 9)
    d1 = _dv(base, list(range(10, 1, -1)))
    d2 = _dv(base + [d1], list(range(11, 1, -1)))
    n = "".join(map(str, base + [d1, d2]))
    return f"{n[0:3]}.{n[3:6]}.{n[6:9]}-{n[9:11]}"


def cnpj_sintetico(semente: str, seq: int) -> str:
    """CNPJ sintetico com DV calculado. SEMPRE usado com a marca SINTETICO."""
    base = _digitos(semente, "cnpj", seq, 12)
    d1 = _dv(base, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    d2 = _dv(base + [d1], [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    n = "".join(map(str, base + [d1, d2]))
    return f"{n[0:2]}.{n[2:5]}.{n[5:8]}/{n[8:12]}-{n[12:14]}"


def documento(semente: str, seq: int) -> tuple:
    """(tipo, numero formatado) do titular sintetico da unidade seq."""
    if categoria(seq) == "COM":
        return "CNPJ", cnpj_sintetico(semente, seq)
    return "CPF", cpf_sintetico(semente, seq)


def titular(seq: int) -> str:
    return f"TITULAR SINTETICO {seq:05d}"


def endereco(seq: int) -> str:
    return (f"RUA SINTETICA {((seq - 1) % 200) + 1:03d}, {100 + (seq % 900)}"
            f" - BAIRRO SINTETICO - MUNICIPIO SINTETICO")


def gerar(saida: str, unidades: int, semente: str) -> dict:
    if unidades < MINIMO_UNIDADES:
        raise ValueError(
            f"demonstracao exige pelo menos {MINIMO_UNIDADES} unidades "
            f"(aceite da P3.23); recebido {unidades}")
    os.makedirs(saida, exist_ok=True)
    caminho = os.path.join(saida, "unidades.csv")
    sha = hashlib.sha256()
    cont = {"RES": 0, "COM": 0, "soma_consumo": 0}
    with open(caminho, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["seq", "unit_id", "meter_id", "installation_id", "rota",
                    "categoria", "titular", "tipo_documento", "documento",
                    "documento_sintetico", "marca", "endereco",
                    "leitura_anterior", "leitura_atual", "consumo_m3"])
        for seq in range(1, unidades + 1):
            c = consumo(seq)
            ant = leitura_anterior(seq)
            tipo, doc = documento(semente, seq)
            w.writerow([seq,
                        uuid_deterministico(semente, "unit", seq),
                        uuid_deterministico(semente, "meter", seq),
                        uuid_deterministico(semente, "installation", seq),
                        rota(seq), categoria(seq), titular(seq), tipo, doc,
                        "SIM", "SINTETICO", endereco(seq), ant, ant + c, c])
            cont[categoria(seq)] += 1
            cont["soma_consumo"] += c
    with open(caminho, "rb") as f:
        for bloco in iter(lambda: f.read(1 << 20), b""):
            sha.update(bloco)
    manifesto = {
        "tarefa": "P3.23",
        "massa": "SINTETICA DECLARADA -- nenhum dado pessoal real; "
                 "todo documento marcado como sintetico",
        "semente": semente,
        "unidades": unidades,
        "minimo_do_aceite": MINIMO_UNIDADES,
        "por_categoria": {"RES": cont["RES"], "COM": cont["COM"]},
        "soma_consumo_m3": cont["soma_consumo"],
        "arquivo": "unidades.csv",
        "sha256": sha.hexdigest(),
    }
    with open(os.path.join(saida, "manifesto.json"), "w", encoding="utf-8") as f:
        json.dump(manifesto, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return manifesto


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Massa sintetica deterministica do tenant de demonstracao (P3.23)")
    ap.add_argument("--unidades", type=int, default=PADRAO_UNIDADES)
    ap.add_argument("--semente", default=SEMENTE_PADRAO)
    ap.add_argument("--saida", default=os.path.join(os.getcwd(), "massa-demo"))
    a = ap.parse_args()
    try:
        m = gerar(a.saida, a.unidades, a.semente)
    except ValueError as e:
        print(f"ERRO: {e}", file=sys.stderr)
        return 2
    print(json.dumps(m, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
