#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
P2.9 -- Sonda de latencia da API, executada EM PARALELO a carga de faturamento.

Somente biblioteca padrao (o PC do dono nao precisa de k6 instalado; o
equivalente em k6 esta em api-probe.js, para paridade com a ferramenta
citada na P0.23). Dispara requisicoes de leitura contra a API durante
toda a janela da carga e grava cada amostra (instante, endpoint, status,
ms) num CSV. Ao fim, calcula p50/p95/p99 e a taxa de erro.

Veredito do p95 (aceite da P2.9: "sem estourar o p95 da API"):
  - P95_MAX_MS definido  -> REPROVA se p95 > P95_MAX_MS;
  - P95_BASELINE_MS definido (p95 da carga de referencia da P0.23) ->
    REPROVA se p95 > baseline * 1.15 (regra de regressao RNF-70 da P0.23);
  - nenhum dos dois -> apenas MEDE e reporta (o teto numerico do RNF nao
    consta dos espelhos consultados; nao inventamos um).

Uso (o executar-carga.sh sobe isto em segundo plano):
  API_BASE_URL=http://127.0.0.1:8090 P95_MAX_MS=... \
  python3 medir-api.py --duracao-seg 7200 --concorrencia 8 --saida DIR
Endpoints: API_PROBE_PATHS (CSV de caminhos GET), padrao "/actuator/health".
Token (se a API exigir): API_BEARER_TOKEN, lido do ambiente, nunca de arquivo.
"""
import argparse
import csv
import json
import os
import signal
import statistics
import sys
import threading
import time
import urllib.request
import urllib.error


def percentil(amostras_ordenadas, p):
    """Percentil NEAREST-RANK (a mesma regua do k6): o menor valor tal que
    pelo menos p% das amostras sao <= ele; indice ceil(p/100 * n) - 1."""
    if not amostras_ordenadas:
        return None
    import math
    k = max(1, math.ceil(p / 100.0 * len(amostras_ordenadas)))
    return amostras_ordenadas[min(k, len(amostras_ordenadas)) - 1]


class Sonda:
    def __init__(self, base, caminhos, token, saida_csv):
        self.base = base.rstrip("/")
        self.caminhos = caminhos
        self.token = token
        self.amostras = []
        self.lock = threading.Lock()
        self.parar = threading.Event()
        self.csv_f = open(saida_csv, "w", newline="", encoding="utf-8")
        self.csv_w = csv.writer(self.csv_f, lineterminator="\n")
        self.csv_w.writerow(["epoch_ms", "caminho", "status", "ms", "erro"])

    def uma(self, idx):
        caminho = self.caminhos[idx % len(self.caminhos)]
        req = urllib.request.Request(self.base + caminho, method="GET")
        if self.token:
            req.add_header("Authorization", f"Bearer {self.token}")
        ini = time.time()
        status, erro = 0, ""
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                resp.read()
                status = resp.status
        except urllib.error.HTTPError as e:
            status = e.code
        except Exception as e:  # erro de rede conta como erro, nunca some
            erro = type(e).__name__
        ms = (time.time() - ini) * 1000.0
        with self.lock:
            self.csv_w.writerow([int(ini * 1000), caminho, status, f"{ms:.1f}", erro])
            self.csv_f.flush()  # amostra em disco na hora: kill nao perde a janela
            self.amostras.append((ms, status, erro))

    def trabalhador(self, idx):
        n = idx
        while not self.parar.is_set():
            self.uma(n)
            n += 1

    def fechar(self):
        self.csv_f.flush()
        self.csv_f.close()


def main() -> int:
    ap = argparse.ArgumentParser(description="Sonda de p95 em paralelo a carga (P2.9)")
    ap.add_argument("--duracao-seg", type=int, required=True)
    ap.add_argument("--concorrencia", type=int, default=8)
    ap.add_argument("--saida", required=True)
    a = ap.parse_args()
    base = os.environ.get("API_BASE_URL", "")
    if not base:
        print("ERRO: defina API_BASE_URL", file=sys.stderr)
        return 2
    caminhos = [c.strip() for c in os.environ.get(
        "API_PROBE_PATHS", "/actuator/health").split(",") if c.strip()]
    os.makedirs(a.saida, exist_ok=True)
    sonda = Sonda(base, caminhos, os.environ.get("API_BEARER_TOKEN", ""),
                  os.path.join(a.saida, "amostras-api.csv"))
    threads = [threading.Thread(target=sonda.trabalhador, args=(i,), daemon=True)
               for i in range(a.concorrencia)]
    ini = time.time()
    for t in threads:
        t.start()
    # O orquestrador encerra a sonda com kill (SIGTERM) ao fechar a janela:
    # o handler apenas sinaliza a parada; o fluxo normal abaixo fecha o CSV
    # e grava o resumo com as amostras da janela real (nada se perde: cada
    # amostra ja foi gravada com flush em Sonda.uma).
    signal.signal(signal.SIGTERM, lambda *_: sonda.parar.set())
    try:
        while time.time() - ini < a.duracao_seg and not sonda.parar.is_set():
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    sonda.parar.set()
    for t in threads:
        t.join(timeout=35)
    sonda.fechar()
    ms = sorted(m for m, _, _ in sonda.amostras)
    erros = sum(1 for _, s, e in sonda.amostras if e or s >= 500 or s == 0)
    resumo = {
        "amostras": len(ms),
        "erros": erros,
        "taxa_erro": (erros / len(ms)) if ms else None,
        "p50_ms": percentil(ms, 50),
        "p95_ms": percentil(ms, 95),
        "p99_ms": percentil(ms, 99),
        "max_ms": ms[-1] if ms else None,
        "media_ms": statistics.fmean(ms) if ms else None,
        "duracao_seg": round(time.time() - ini, 1),
    }
    teto = os.environ.get("P95_MAX_MS", "")
    base_ms = os.environ.get("P95_BASELINE_MS", "")
    veredito = "MEDIDO_SEM_TETO_DEFINIDO"
    if teto and resumo["p95_ms"] is not None:
        veredito = "APROVADO" if resumo["p95_ms"] <= float(teto) else "REPROVADO"
        resumo["teto_ms"] = float(teto)
    if base_ms and resumo["p95_ms"] is not None:
        limite = float(base_ms) * 1.15
        resumo["baseline_ms"] = float(base_ms)
        resumo["limite_regressao_15pct_ms"] = limite
        reg = "APROVADO" if resumo["p95_ms"] <= limite else "REPROVADO"
        veredito = reg if veredito in ("MEDIDO_SEM_TETO_DEFINIDO", "APROVADO") else veredito
    resumo["veredito_p95"] = veredito
    with open(os.path.join(a.saida, "resumo-api.json"), "w", encoding="utf-8") as f:
        json.dump(resumo, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(json.dumps(resumo, ensure_ascii=False, indent=2))
    return 1 if veredito == "REPROVADO" else 0


if __name__ == "__main__":
    raise SystemExit(main())
