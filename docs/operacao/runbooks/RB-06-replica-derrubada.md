# Réplica do banco derrubada

- Regras ligadas: **nenhuma executável no catálogo vigente** — `db.replica.down` é PROPOSTA PENDENTE de métrica própria de réplica (`docs/observability/alert-rules-pendentes.md` §5); este runbook é mantido e pode ser exercitado, mas nenhum alerta distinguível dispara hoje
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Confirmar qual réplica caiu e que o primário segue íntegro (sem promoção manual impulsiva).
2. Leituras que dependiam da réplica degradam para o primário conforme configuração; registrar impacto.
3. Recriar a réplica pelo Compose/IaC local e aguardar reaplicação do WAL até o atraso zerar.
4. Confirmar atraso ~0, resolver o alerta e registrar tempos no exercício de falha.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
