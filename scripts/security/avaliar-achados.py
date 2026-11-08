#!/usr/bin/env python3
"""Portao de achados da P3.8 sobre o relatorio JSON do OWASP ZAP.

Uso: avaliar-achados.py <zap-report.json>
Sai 1 se houver alerta High ou Critical (achado alto aberto bloqueia a
rodada); 0 se nao houver; 2 se o relatorio nao puder ser lido/interpretado
(falha fechada: relatorio ilegivel NAO vira rodada verde).
Espelha FindingGate.fromZapReport + evaluate (Java) — mesma regra,
inclusive a correcao D4 da revisao cruzada: relatorio ESTRUTURALMENTE
VAZIO falha fechado (exit 2) — sem a chave "site", "site" vazio ou
nao-lista, site que nao e objeto, site sem "alerts", "alerts" nao-lista,
alerta que nao e objeto, ou ZERO alertas no total. Relatorio truncado
ou gerado errado nunca vira "OK, nenhum achado alto".
"""
import json
import sys

def main() -> int:
    if len(sys.argv) != 2:
        print("uso: avaliar-achados.py <zap-report.json>", file=sys.stderr)
        return 2
    try:
        with open(sys.argv[1], encoding="utf-8") as fh:
            report = json.load(fh)
    except Exception as exc:  # falha fechada
        print(f"BLOQUEADO: relatorio ilegivel ({exc.__class__.__name__})")
        return 2
    if not isinstance(report, dict):
        print("BLOQUEADO: relatorio nao e um objeto JSON (falha fechada)")
        return 2
    if "site" not in report:
        print("BLOQUEADO: relatorio sem a estrutura 'site' — vazio/incompleto nao vira rodada verde (falha fechada)")
        return 2
    sites = report.get("site")
    if not isinstance(sites, list):
        print("BLOQUEADO: relatorio com 'site' fora do formato de lista esperado (falha fechada)")
        return 2
    if not sites:
        print("BLOQUEADO: relatorio com 'site' vazio — nenhum site varrido nao e evidencia (falha fechada)")
        return 2
    blocking = []
    total = 0
    for site in sites:
        if not isinstance(site, dict):
            print("BLOQUEADO: item de 'site' fora do formato de objeto esperado (falha fechada)")
            return 2
        if "alerts" not in site:
            print("BLOQUEADO: site sem 'alerts' — relatorio incompleto (falha fechada)")
            return 2
        alerts = site.get("alerts")
        if not isinstance(alerts, list):
            print("BLOQUEADO: 'alerts' fora do formato de lista esperado (falha fechada)")
            return 2
        for alert in alerts:
            if not isinstance(alert, dict):
                print("BLOQUEADO: alerta fora do formato de objeto esperado (falha fechada)")
                return 2
            total += 1
            risk = str(alert.get("riskdesc", "")).strip().lower()
            if risk.startswith("high") or risk.startswith("critical"):
                blocking.append(f"ZAP-{alert.get('pluginid', '?')}: {alert.get('name', '?')} ({alert.get('riskdesc')})")
    if total == 0:
        print("BLOQUEADO: relatorio sem nenhum alerta — vazio nao e evidencia de varredura (falha fechada)")
        return 2
    for item in blocking:
        print(f"ACHADO ALTO ABERTO: {item}")
    print(f"total de alertas: {total}; bloqueantes: {len(blocking)}")
    if blocking:
        print("RESULTADO: BLOQUEADO — nenhum achado alto aberto (P3.8)")
        return 1
    print("RESULTADO: OK — nenhum achado alto aberto")
    return 0

if __name__ == "__main__":
    sys.exit(main())
