# Manual de atendimento — retenção, expurgo e exportação de chamados (P3.19)

> Complemento operacional do atendimento de suporte (chamados
> `SUP-AAAA-NNNNNN`, P1.23/ADR-0020). Cobre só o ciclo de vida do
> chamado depois do encerramento do contrato do tenant; o atendimento
> em si (fila, estados, SLA/CSAT da P2.15) segue o material da P1.23 e
> da P2.15.

## 1. Retenção: quanto tempo o chamado fica guardado

- Todo chamado fica retido até **5 anos depois do fim da vigência do
  contrato SaaS** do tenant. Essa data é o `purge_at` do chamado.
- Ao fim da vigência (contrato `EXPIRED`/`TERMINATED` na Central),
  rodar a fixação do `purge_at` (`TicketRetentionService.stampPurgeAtFromContract`).
  Se o tenant não tiver contrato registrado, a operação recusa e o
  caso vai para a Central — nunca se digita uma data à mão.
- Chamado com `purge_at` NULL não é expurgável: ele ainda está dentro
  da retenção (ou a fixação ainda não rodou — conferir).

## 2. Ensaio de expurgo (dry-run)

1. Rodar o ensaio (`dryRun`) do tenant: ele devolve a contagem de
   chamados, mensagens, anexos e eventos com `purge_at` vencido.
2. Conferir a contagem contra o esperado (contrato encerrado há mais
   de 5 anos). Divergência = parar e investigar; o ensaio não apaga
   nada, pode ser repetido.

## 3. Expurgo

1. Conferir a natureza jurídica do tenant (`reg.legal_nature`):
   - **Privada (`PRIVATE`)**: expurgo liberado após o ensaio.
   - **Pública (`AUTARCHY`, `PUBLIC_COMPANY`, `SEM`)**: só executar
     com a **tabela de temporalidade do tenant** e a **listagem de
     eliminação** registradas **e aprovadas pelo tenant** (Lei
     8.159/1991). Faltando qualquer uma, o sistema recusa e nomeia o
     que falta — não contornar, não reclassificar o tenant para
     destravar.
2. Executar o expurgo (`purge`) e registrar o resultado (contagens)
   na auditoria da plataforma (`platform.audit_event`, P1.6).
3. Anexos: o expurgo apaga o registro; os bytes no armazenamento de
   objetos seguem o fluxo de eliminação e o certificado de descarte
   da P3.16, que declara a exceção dos chamados retidos.

## 4. Exportação de fim de contrato

- Os chamados entram na exportação de fim de contrato do tenant
  (P1.6) na seção `support_tickets`: chamados, mensagens públicas e
  metadados de anexo (sem bytes, sem notas internas).
- A exportação vem **antes** de qualquer expurgo/eliminação: tenant
  que sai recebe os chamados; o que fica retido além do contrato é só
  o que o `purge_at` ainda cobre.
- Para tenant público, a eliminação pós-exportação obedece ao mesmo
  portão do item 3 (temporalidade + listagem aprovadas).

## 5. O que nunca fazer

- Não apagar chamado por SQL avulso fora do serviço de retenção.
- Não tratar ausência de tabela de temporalidade como aprovação.
- Não exportar nota interna (`INTERNAL`) ao tenant.
