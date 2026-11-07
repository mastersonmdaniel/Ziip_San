# RB-09 — Falha provocada na Central completa (P3.15)

Procedimento do EXERCÍCIO de falha provocada. A regra que dispara é a
EXISTENTE `central.collector.stale` (core V9_1: métrica
`platform.collector.last_run_age_s`, comparador `>`, limiar 900 s,
severidade CRITICAL, responsável: platform), catalogada pela P3.11
com o runbook **RB-02**. Nenhuma regra nova é criada pela P3.15
(uma segunda regra idêntica geraria `alert_event` duplicado); o RB-09
é só o roteiro do exercício sobre a regra única — a resposta ao
alerta em si segue o RB-02.

## Quando dispara

- O exercício para a coleta/sonda (ambiente local/homologação, no
  programa de exercícios da P3.11) e a idade da última coleta passa
  de 900 s — a medição simulada (idade velha e/ou sucesso falso)
  alimenta a avaliação única da regra `central.collector.stale`.
- Sucesso falso com coleta FRESCA não dispara: sem a condição da
  regra (idade > 900 s) não há sinal — sinal e regra descrevem a
  mesma condição.

## O que fazer

1. Confirmar se a falha é o exercício programado (registro de
   exercícios da P3.11) ou falha real da coleta.
2. Exercício: registrar o disparo do alerta como resultado do
   exercício (a prova do aceite "falha provocada alerta" é esse
   disparo) e restaurar a coleta.
3. Falha real: seguir o RB-02 (fila parada) e, conforme o componente,
   o RB-01 (API indisponível); o alerta fecha quando a coleta volta e
   a idade cai abaixo do limiar.
4. Não silenciar a regra para "parar o barulho": snooze só com
   incidente aberto e prazo.
