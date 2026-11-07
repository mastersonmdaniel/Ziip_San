# Programa de exercícios de falha — P3.11

Periodicidade proposta: um exercício por mês na homologação local,
rodando os cinco cenários em rotação; depois do lançamento, os mesmos
cenários em produção controlada entram no rito da LC.7.

> **Correção pós-revisão (P-2):** quatro dos cinco cenários da spec
> **não têm regra executável nem alerta esperado distinguível** — as
> regras que usavam a métrica genérica auto-referente
> `platform.alerts.open` foram retiradas do catálogo executável para
> `docs/observability/alert-rules-pendentes.md`, porque não existe
> métrica real de PSP, SEFAZ, certificado ou réplica na V9_1/P0.8.
> Nesses cenários o exercício pode exercitar o **runbook**, mas **não
> comprova disparo de alerta** cenário a cenário até a modelagem da
> métrica própria existir. O cenário 6 também não dispara alerta hoje:
> a regra de burn é proposta pendente e o endpoint de caos é pendência
> de integração com a P0.27 (ver script). Só o cenário 4 tem alerta
> executável comprovável.

| # | Cenário | Como provocar (local) | Alerta esperado | Runbook |
|---|---|---|---|---|
| 1 | Derrubada de réplica | parar o serviço de réplica do Compose local | **SEM REGRA EXECUTÁVEL** — proposta pendente de métrica própria (`alert-rules-pendentes.md` §5) | RB-06 |
| 2 | Perda do PSP | apontar o PSP para endpoint local morto (mock desligado) | **SEM REGRA EXECUTÁVEL** — proposta pendente de métrica própria (`alert-rules-pendentes.md` §2) | RB-03 |
| 3 | Indisponibilidade da SEFAZ | bloquear a saída para a SEFAZ de homologação no ambiente local | **SEM REGRA EXECUTÁVEL** — proposta pendente de métrica própria (`alert-rules-pendentes.md` §3) | RB-04 |
| 4 | Fila parada | parar o coletor/worker local por > 15 min | `central.collector.stale` (executável; também `platform.outbox.oldest_pending_age_s.high` se a outbox envelhecer) | RB-02 |
| 5 | Certificado vencido | instalar certificado de teste já vencido no cofre local | **SEM REGRA EXECUTÁVEL** — proposta pendente de métrica própria (`alert-rules-pendentes.md` §4) | RB-05 |
| 6 | Falha genérica provocada | `scripts/operacao/provocar-falha-local.sh` | **SEM REGRA EXECUTÁVEL** — burn é proposta pendente (`alert-rules-pendentes.md` §1) e o endpoint de caos é pendência de integração com a P0.27 | RB-01 |

Regras: exercício sem registro não aconteceu; resultado (tempo até o
alerta, tempo até a mitigação, o que o runbook não cobriu) vai para
`registro-exercicios-de-falha.md`; lacuna de runbook vira correção do
runbook no mesmo ciclo, não anotação solta. Nos cenários sem regra
executável, o registro deve dizer explicitamente "sem alerta esperado —
só runbook exercitado", nunca registrar disparo que a instrumentação
não pode distinguir.
