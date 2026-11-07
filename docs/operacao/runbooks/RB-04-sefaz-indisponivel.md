# Indisponibilidade da SEFAZ

- Regras ligadas: **nenhuma executável no catálogo vigente** — `sefaz.unavailable` é PROPOSTA PENDENTE de métrica própria da SEFAZ (`docs/observability/alert-rules-pendentes.md` §3); este runbook é mantido e pode ser exercitado, mas nenhum alerta distinguível dispara hoje
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Confirmar `tpAmb` e status da SEFAZ de homologação; registrar horário.
2. Emissão fiscal entra em contingência prevista (P3.7/P2.6) — não emitir em outro ambiente nem com certificado diverso.
3. Fila de emissão fica pendente e auditável; nada é marcado como autorizado sem retorno.
4. Na volta, transmitir pendentes em ordem e conferir autorizações.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
