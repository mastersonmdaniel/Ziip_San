#!/usr/bin/env python3
"""P0.8: verifica o orcamento de series ativas por servico.

Le docs/observability/metrics-budget.yml, soma as estimativas por servico e falha
(exit 1) quando a soma passa do orcamento. Uso no CI (job observability-budget) e local:

    python3 scripts/check-metrics-budget.py [--budget-file PATH]

Nao mede series reais: compara estimativas DECLARADAS com o teto DECLARADO. A honestidade
do mecanismo esta em que (a) todo medidor novo exige atualizar o arquivo e (b) o CI barra
o estouro — nao em fingir que a soma e uma contagem observada (ver o cabecalho do yml).
"""
import argparse
import sys
from pathlib import Path

import yaml


def check(budget_file: Path):
    data = yaml.safe_load(budget_file.read_text(encoding="utf-8"))
    services = data.get("services") or {}
    if not services:
        return ["budget file sem 'services'"]
    failures = []
    for name, svc in sorted(services.items()):
        budget = svc.get("budget")
        components = svc.get("components") or []
        if budget is None:
            failures.append(f"{name}: sem 'budget'")
            continue
        if not components:
            failures.append(f"{name}: sem 'components'")
            continue
        total = 0
        for comp in components:
            est = comp.get("estimated_series")
            if not isinstance(est, int) or est < 0:
                failures.append(f"{name}/{comp.get('name')}: estimated_series invalido: {est!r}")
                continue
            total += est
        print(f"{name}: estimado={total} orcamento={budget}")
        if total > budget:
            failures.append(f"{name}: estimado {total} > orcamento {budget} (excesso {total - budget})")
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--budget-file", default=None,
                    help="caminho do metrics-budget.yml (padrao: docs/observability/metrics-budget.yml na raiz do repo)")
    args = ap.parse_args()
    root = Path(__file__).resolve().parent.parent
    budget_file = Path(args.budget_file) if args.budget_file else root / "docs/observability/metrics-budget.yml"
    if not budget_file.is_file():
        print(f"ERRO: budget file nao encontrado: {budget_file}", file=sys.stderr)
        return 2
    failures = check(budget_file)
    if failures:
        for f in failures:
            print(f"FALHOU: {f}", file=sys.stderr)
        return 1
    print("OK: orcamento de series dentro do teto")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
