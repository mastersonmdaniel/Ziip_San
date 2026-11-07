#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P2.9 -- Gerador deterministico da massa sintetica de carga.

Gera o CSV canonico das ligacoes sinteticas (padrao: 300.000) e um
manifesto com contagens e SHA-256. A massa e SINTETICA DECLARADA: nenhum
dado real, nenhum nome/documento de pessoa -- so sequencia, UUIDs
derivados da semente, rota, categoria, leituras e consumo calculados por
formula fechada.

Determinismo: a mesma (semente, N) gera byte a byte o mesmo CSV. Os UUIDs
seguem a MESMA derivacao dos seeders SQL (sql/30-seed-medicao.sql):
md5("<semente>:<tipo>:<seq>") com o nibble de versao forcado para 5 e a
variante para 10xx -- assim CSV e banco enxergam os mesmos ids sem troca
de arquivos de mapeamento.

Formulas (fonte unica; os seeders SQL repetem exatamente isto):
  categoria : seq % 5 == 0 -> COM, senao RES          (80% RES / 20% COM)
  consumo   : RES -> 12 + ((seq * 7)  % 25)           (12..36 m3)
              COM -> 30 + ((seq * 11) % 120)          (30..149 m3)
  leitura anterior : (seq * 37) % 90000
  leitura atual    : anterior + consumo   (mostrador de 5 digitos, sem virada)
  rota      : ROTA-001 .. ROTA-100, ((seq - 1) % 100) + 1 -- seq 1 = ROTA-001,
              alinhada ao route_code do grupo CARGA-001..100 do seeder
  faturavel : seq <= --faturar (padrao 100.000): o subconjunto do aceite;
              o MESMO corte e materializado no banco (billable em
              consumption_records, ver sql/30-seed-medicao.sql)

Uso:
  python3 gerar-massa.py [--ligacoes 300000] [--faturar 100000]
                         [--semente ziipsan-carga-p29-v1] [--saida DIR]
"""
import argparse
import csv
import hashlib
import json
import os
import sys

SEMENTE_PADRAO = "ziipsan-carga-p29-v1"


def uuid_deterministico(semente: str, tipo: str, seq: int) -> str:
    """UUID derivado de md5(semente:tipo:seq); identico ao SQL (substr/md5)."""
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
    """Rota da ligacao: seq 1 -> ROTA-001 ... seq 100 -> ROTA-100, ciclando.
    E o route_code do grupo de faturamento correspondente (CARGA-NNN)."""
    return f"ROTA-{((seq - 1) % 100) + 1:03d}"


def gerar(saida: str, ligacoes: int, faturar: int, semente: str) -> dict:
    os.makedirs(saida, exist_ok=True)
    caminho = os.path.join(saida, "ligacoes.csv")
    sha = hashlib.sha256()
    cont = {"RES": 0, "COM": 0, "faturaveis": 0, "soma_consumo": 0}
    with open(caminho, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["seq", "unit_id", "meter_id", "installation_id", "rota",
                    "categoria", "leitura_anterior", "leitura_atual", "consumo_m3",
                    "faturavel"])
        for seq in range(1, ligacoes + 1):
            c = consumo(seq)
            ant = leitura_anterior(seq)
            fat = 1 if seq <= faturar else 0
            linha = [seq,
                     uuid_deterministico(semente, "unit", seq),
                     uuid_deterministico(semente, "meter", seq),
                     uuid_deterministico(semente, "installation", seq),
                     rota(seq),
                     categoria(seq), ant, ant + c, c, fat]
            w.writerow(linha)
            cont[categoria(seq)] += 1
            cont["faturaveis"] += fat
            cont["soma_consumo"] += c
    with open(caminho, "rb") as f:
        for bloco in iter(lambda: f.read(1 << 20), b""):
            sha.update(bloco)
    manifesto = {
        "tarefa": "P2.9",
        "massa": "SINTETICA DECLARADA -- nenhum dado real",
        "semente": semente,
        "ligacoes": ligacoes,
        "faturaveis": cont["faturaveis"],
        "por_categoria": {"RES": cont["RES"], "COM": cont["COM"]},
        "soma_consumo_m3": cont["soma_consumo"],
        "arquivo": "ligacoes.csv",
        "sha256": sha.hexdigest(),
    }
    with open(os.path.join(saida, "manifesto.json"), "w", encoding="utf-8") as f:
        json.dump(manifesto, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return manifesto


def main() -> int:
    ap = argparse.ArgumentParser(description="Massa sintetica deterministica da P2.9")
    ap.add_argument("--ligacoes", type=int, default=300_000)
    ap.add_argument("--faturar", type=int, default=100_000)
    ap.add_argument("--semente", default=SEMENTE_PADRAO)
    ap.add_argument("--saida", default=os.path.join(os.getcwd(), "massa"))
    a = ap.parse_args()
    if a.faturar > a.ligacoes:
        print("ERRO: --faturar maior que --ligacoes", file=sys.stderr)
        return 2
    m = gerar(a.saida, a.ligacoes, a.faturar, a.semente)
    print(json.dumps(m, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
