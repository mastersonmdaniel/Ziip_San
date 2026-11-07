# API indisponível / burn rate alto

- Regras ligadas: **nenhuma executável no catálogo vigente** — `api.availability.burn_fast, api.availability.burn_slow` são PROPOSTA PENDENTE de modelagem (`docs/observability/alert-rules-pendentes.md` §1); este runbook é mantido para quando a derivação do SLI a partir de `http.server.requests` for decidida
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Confirmar no painel local: contadores da métrica real `http.server.requests` (P0.8, actuator autenticado) e últimos `alert_event` abertos na Central.
2. Provocar/confirmar a falha: `scripts/operacao/provocar-falha-local.sh` (ambiente local apenas).
3. Verificar saúde dos contêineres do Compose local e logs JSON por `X-Request-ID`.
4. Se banco: seguir RB-06. Se fila: RB-02.
5. Restabelecer, confirmar alerta resolvido e registrar no exercício/revisão pós-incidente.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
