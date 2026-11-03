#!/usr/bin/env python3
"""Gate de regressão da carga (P0.23, RNF-70): falha se o p95 regredir mais de 15%.

Uso: python3 scripts/load/check-regression.py <k6-summary.json> [--baseline scripts/load/baseline.json]
- Sem baseline versionada (p95 null): imprime o p95 medido e sai 0 com aviso — a baseline
  só entra no arquivo por revisão humana, nunca automaticamente a partir de uma medição.
- Com baseline: falha (saída 1) se p95_medido > p95_base * 1.15.
O resumo do k6 vem de `k6 run --summary-export=<arquivo>`.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BASELINE = ROOT / "baseline.json"
LIMIT = 1.15


def main() -> int:
    if len(sys.argv) < 2:
        print("uso: check-regression.py <k6-summary.json> [--baseline <arquivo>]", file=sys.stderr)
        return 2
    summary = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    baseline_path = Path(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[2] == "--baseline" else BASELINE
    # k6 <1.0 aninha os percentis em "values"; o k6 1.x achata no próprio objeto.
    duration = summary["metrics"]["http_req_duration"]
    p95 = (duration.get("values") or duration)["p(95)"]
    print(f"p95 medido: {p95} ms")
    if not baseline_path.exists():
        print("AVISO: sem arquivo de baseline — registre o p95 por revisão humana antes de usar como gate.")
        return 0
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    base_p95 = baseline.get("api_read_p95_ms")
    if base_p95 is None:
        print("AVISO: baseline sem p95 (null) — esta medicao NAO vira baseline sozinha; revise e preencha.")
        return 0
    limit = base_p95 * LIMIT
    print(f"baseline: {base_p95} ms; limite (+15%): {limit:.2f} ms")
    if p95 > limit:
        print(f"FALHA (RNF-70): regressao acima de 15% no p95 ({p95} > {limit:.2f}).")
        return 1
    print("OK: p95 dentro do limite de regressao de 15%.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
