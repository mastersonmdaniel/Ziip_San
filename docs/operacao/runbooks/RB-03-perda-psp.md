# Perda do PSP (Pix/boleto)

- Regras ligadas: **nenhuma executável no catálogo vigente** — `psp.unavailable` é PROPOSTA PENDENTE de métrica própria do PSP (`docs/observability/alert-rules-pendentes.md` §2); este runbook é mantido e pode ser exercitado, mas nenhum alerta distinguível dispara hoje
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Confirmar indisponibilidade no PSP (não presumir): status do provedor e últimos callbacks.
2. Suspender novas cobranças pelo PSP afetado conforme parâmetro do tenant; pagamentos já liquidados não são refeitos.
3. Comunicar pelo modelo de status (P3.18) se o exercício for em janela real.
4. Na volta, reconciliar pelo `endToEndId`/varredura antes de baixar qualquer conta.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
