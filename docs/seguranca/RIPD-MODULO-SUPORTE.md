# RIPD — Módulo de suporte (chamados) — atualização P3.19

> Relatório de Impacto à Proteção de Dados do módulo de chamados de
> suporte (`support.*`, core `V9_4`/P1.23, SLA na `V10_2`/P2.15),
> atualizado pela P3.19 (retenção, expurgo e exportação; ADR-0020;
> RAS-18). Segue o modelo de RIPD por módulo da P3.10. Papéis: o Ziip
> SAN é operador dos dados que o tenant (controlador) registra nos
> chamados; nos chamados, o tenant é quem pede suporte ao Ziip.

## Tratamento coberto

| Item | Descrição |
|---|---|
| Dados | Assunto e conversa do chamado (podem conter dado pessoal — marcados em `contains_personal_data` quando CPF, CNPJ, e-mail ou telefone são detectados, P1.23), anexos registrados pelo pipeline da P1.20, eventos do chamado, identificação do solicitante (`requester_id`) |
| Tabelas | `support.ticket`, `support.ticket_message`, `support.ticket_attachment`, `support.ticket_event`, `support.ticket_number_counter` |
| Finalidade | Prestação do suporte contratado (ADR-0020); fila da Central Ziip |
| Base legal | Execução do contrato SaaS (P2.12/P3.9); o DPA prevê a retenção de chamados pela vigência mais 5 anos e a exceção dos chamados no certificado de descarte |

## Retenção e expurgo (o que a P3.19 muda)

- `support.ticket.purge_at` (coluna criada na `V9_4`, NULL até aqui) é
  fixado pelo `TicketRetentionService` como **fim da vigência do
  contrato (`platform.saas_contract.ends_at`) + 5 anos**. Sem contrato
  registrado, a fixação é recusada — nenhuma data é inventada.
- **Ensaio de expurgo** (`dryRun`): só conta (chamados, mensagens,
  anexos e eventos com `purge_at` vencido); não apaga nada.
- **Expurgo** (`purge`): apaga anexos, mensagens, eventos e chamados
  vencidos, na ordem das FKs, na transação com o contexto RLS do
  tenant. Bytes de anexo no armazenamento de objetos e o certificado
  de descarte são da eliminação de tenant (P3.16).
- **Tenant de natureza pública** (`reg.legal_nature` =
  `platform.tenant_profile.legal_nature` em `AUTARCHY`,
  `PUBLIC_COMPANY` ou `SEM`): expurgo e eliminação de fim de contrato
  só com **tabela de temporalidade e listagem de eliminação
  registradas e aprovadas pelo tenant** (Lei 8.159/1991, gestão e
  eliminação de documentos públicos). Sem qualquer um dos registros, a
  execução é recusada com o motivo nomeado; natureza ausente ou fora
  do domínio também recusa (fail-closed).
- **Exportação de fim de contrato**: os chamados entram na exportação
  do tenant (`platform.tenant_export`, P1.6) como a seção
  `support_tickets` (chamados + mensagens públicas + metadados de
  anexo, sem bytes e sem notas internas), antes de qualquer expurgo.

## Riscos e medidas

| Risco | Medida |
|---|---|
| Dado pessoal colado no corpo do chamado | Marcação `contains_personal_data` e máscara nas listas (P1.23); orientação no formulário para não anexar dado pessoal |
| Eliminação prematura de documento público | Portão arquivístico da Lei 8.159/1991 acima; recusa explícita, sem default |
| Retenção infinita por `purge_at` nunca fixado | Fixação a partir do contrato no encerramento da vigência (procedimento no manual de atendimento) |
| Vazamento entre tenants no expurgo/exportação | Contexto RLS `app.current_tenant` por transação e filtro `tenant_id` em todo SQL, como no `TicketService` |
| Nota interna exposta ao tenant | Exportação e API só incluem mensagens `PUBLIC` |

## Pendências ligadas

- Persistência própria dos registros de temporalidade/listagem
  (tabela) depende de migration que o plano **não reservou** para a
  P3.19; até lá o serviço recebe os registros lidos da fonte do tenant
  (`ArchivalApproval`) e recusa na ausência deles.
- Job agendado de expurgo (varredura periódica) não faz parte desta
  entrega; o serviço é invocável pela Central/agendador futuro.
- **Grant de DELETE (achado da revisão cruzada):** a `V9_4` não
  concede `DELETE` a `ziipsan_app` em nenhuma das quatro tabelas
  `support.*`; no espelho conferido não há grant posterior. O expurgo
  em produção depende de migration de grant com número reservado no
  plano (nenhum inventado) ou de prova na integração de grant
  equivalente fora do espelho — sem isso o banco recusa o `DELETE`.
