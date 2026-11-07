# Certificado vencido ou a vencer

- Regras ligadas: **nenhuma executável no catálogo vigente** — `certificate.expiring` é PROPOSTA PENDENTE de métrica própria de validade de certificado (`docs/observability/alert-rules-pendentes.md` §4); este runbook é mantido e pode ser exercitado, mas nenhum alerta distinguível dispara hoje
- Responsável: `platform`
- Ambiente do exercício: homologação local (P0.27). Produção controlada só depois do lançamento (LC.7).

## Passos

1. Identificar o certificado (fiscal/PSP/TLS) e a validade real no cofre local.
2. Não contornar validação nem desligar verificação de cadeia.
3. Renovar/substituir pelo fluxo do cofre e reiniciar só o serviço afetado.
4. Emitir documento de teste na homologação e registrar o novo vencimento.

## Encerramento

Preencher o registro do exercício (`docs/operacao/registro-exercicios-de-falha.md`) e, se houve impacto, a revisão pós-incidente (`docs/operacao/modelo-revisao-pos-incidente.md`).
