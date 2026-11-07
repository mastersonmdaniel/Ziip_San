# Fila parada (outbox/coletor)

- Regras ligadas: `central.collector.stale, platform.outbox.oldest_pending_age_s.high`
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Conferir `platform.config_item` `central.collector.last_run_at`: ausência = nunca coletou (não é zero).
2. Conferir idade da mensagem PENDING mais antiga e contagem DEAD.
3. Reiniciar o coletor/worker local; NÃO apagar mensagens PENDING/DEAD à mão.
4. Confirmar retomada da coleta e resolução do alerta; registrar causa.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
