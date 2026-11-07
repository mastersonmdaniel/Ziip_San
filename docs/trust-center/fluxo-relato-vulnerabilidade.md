# Fluxo interno de relato de vulnerabilidade — P3.38

Fluxo **registrado** de recebimento, triagem e resposta, operado sobre
`support.vulnerability_report` e sua trilha
`support.vulnerability_report_event` (core `V10_6`) e pelas transições
de `br.com.ziipsan.platform.trustcenter.VulnerabilityReportFlow`. Cada
transição grava estado e evento na mesma transação; transição fora de
ordem é recusada pelo serviço e pelos `CHECK`s da migration.

## Etapas

| # | Etapa | Transição | O que é registrado | Resposta ao pesquisador |
|---|-------|-----------|--------------------|-------------------------|
| 1 | Recebimento | — → `RECEIVED` | referência `VULN-AAAA-NNNN`, relato, ativo declarado, `received_at`; evento `RECEIVED` | — (a confirmação é a etapa 2) |
| 2 | Confirmação | `RECEIVED` → `ACKNOWLEDGED` | `acknowledged_at`; evento `ACKNOWLEDGED` | Confirmação com a referência, em até **[prazo pendente de decisão do dono]** — medido por `acknowledgementState` (`WITHIN_TARGET`/`OVERDUE`; sem prazo definido, `NO_TARGET_DEFINED`) |
| 3 | Triagem (aceite) | `ACKNOWLEDGED` → `TRIAGED` | severidade classificada (nunca `UNASSESSED`), nota de triagem, `triaged_at`; evento `TRIAGED` | Classificação e próximos passos |
| 3' | Triagem (rejeição) | `ACKNOWLEDGED` → `REJECTED` (terminal) | motivo na nota de triagem; evento `REJECTED`; sem `triaged_at`/`resolved_at` | Rejeição com o motivo (fora de escopo, duplicado, não reproduzível) |
| 4 | Correção | `TRIAGED` → `IN_PROGRESS` → `RESOLVED` | eventos `RESOLUTION_STARTED`/`RESOLVED`, nota de resolução, `resolved_at` | Atualização ao fim da correção |
| 5 | Divulgação coordenada | `RESOLVED` → `DISCLOSED` (terminal) | resumo do publicado, `disclosed_at`; evento `DISCLOSED` | Divulgação combinada; crédito ao pesquisador se ele desejar (política, §7) |

## Regras operacionais

- **Nada de relato solto:** relato sem registro não existe para o
  fluxo. Contato do pesquisador é dado interno de atendimento e nunca é
  publicado (o Trust Center não lista relatos).
- **Dado pessoal envolvido:** o relato é ligado à ocorrência da P2.25 e
  ao procedimento de resposta a incidente da P3.10 (operador → tenant
  controlador no prazo contratual `lgpd.operator_notice_hours`; relógio
  e alertas da P3.10), em paralelo ao fluxo daqui — um não espera o
  outro para responder ao pesquisador.
- **Incidente de segurança declarado:** se a triagem confirmar
  exploração ativa, o relato também alimenta o registro de incidente
  (P3.10) e, resolvido, o resumo publicável da revisão pós-incidente
  pode ir à página de status (P3.18) — sem detalhe explorável.
- **Atraso de confirmação:** relato aberto com `acknowledgementState =
  OVERDUE` é pendência visível para a operação (a consulta é sobre o
  estado e a política configurada; a fiação do alerta na Central é da
  integração, como na P3.18).

## Exercício do fluxo

O teste de ponta a ponta com um **relato fictício** está em
`VulnerabilityDisclosureFlowTest` (platform): recebimento → confirmação
dentro do prazo de teste → triagem aceita com severidade → correção →
resolução → divulgação coordenada, com a trilha conferida evento a
evento; mais os caminhos de rejeição na triagem, de transições fora de
ordem recusadas e da medição de prazo (`OVERDUE` e `NO_TARGET_DEFINED`).
Rodar na CI total do PC do dono, junto da aplicação da `V10_6`.
