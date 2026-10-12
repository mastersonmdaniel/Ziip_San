# ADR-0032 — Material da OS com alçada e baixa automática

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** ADR-0002 (parâmetros com aceite); ADR-0015 (classes de parâmetro); ADR-0026 (app de campo); ADR-0028
  (item i, almoxarifado); ADR-0030 (dispositivo registrado); ADR-0031 (idempotência pela chave da tarefa);
  `docs/arquitetura/PARAMETROS-DO-TENANT.md` (grupo de almoxarifado); `docs/arquitetura/CENTRAL-ZIIP.md`; plano §3
  (faixa `V20_x`), ED-20, ED-36, ED-58, ED-62
- **Base:** ADR-0028, item i (recorte "núcleo saneamento + integração", plano v2.5); `18g-aderencia-operacao-qualidade-bi.md`
  (E-ALMOX); `18f-aderencia-atendimento-os-portal.md` (ENT-03, ENT-07)
- **Ajuste de 12/10/2026 (editais, v2.6), antes de qualquer implementação:** a ED-58, que carrega o ciclo deste ADR,
  sai da onda C para a onda A da trilha de editais, junto da OS completa (ED-36), para que a requisição de material da
  OS afete o estoque desde a primeira onda; a ED-58 vem logo depois da ED-36 e depende dela, da ED-12 e da ED-14, e a
  baixa de campo (item 4) é feita depois da ED-20, na mesma onda; a ED-62 passa a depender da ED-58 para exportar os
  movimentos (item 6); o tamanho não muda (7,5 semanas-pessoa) e compras continua em 2029, sob demanda (ADR-0028, item
  i, ajuste v2.5); a linha "na onda C, depois da ED-36 e da ED-20" de "O que muda no roadmap" fica substituída por este
  ajuste; efeito na capacidade no ADR-0029 (ajuste v2.6); decisão do dono do produto.

## Contexto

O ADR-0028 (item i, ajustado na v2.5) decidiu construir o almoxarifado no recorte do saneamento: materiais e estoque
consumidos pela ordem de serviço. Os editais pedem OS encerrada com executores e materiais (VISAN, POC-43; SAMAE 2.17,
crítico, que inclui estoque), e a OS completa (ED-36) já registra materiais, sem efeito no estoque. Sem regra de
autorização, qualquer requisição de material de alto valor ou controlado (hidrômetros) sai do estoque sem controle;
sem baixa automática, estoque e custo da OS dependem de lançamento manual.

## Decisão

1. **Ciclo do material na OS.**
   - Ao selecionar o material na OS, gera-se uma **requisição de material** com os itens e as quantidades previstas.
   - Na **aprovação da alçada**, o material fica **reservado**: sai do saldo disponível e continua no saldo físico.
   - Na **saída para a equipe**, registra-se a entrega da requisição (quem retirou, quando, quantidade entregue).
   - No **encerramento da OS**, a **baixa é automática** pela quantidade realmente usada, e a **sobra volta
     automaticamente** ao estoque; a reserva é liberada.
   - O **custo do material é apropriado na OS** pelo método de custeio do almoxarifado (custo médio ponderado por
     padrão).
   - OS cancelada libera a reserva; material já entregue volta por devolução registrada.
2. **Alçada por valor e por item**, configurável por tenant como parâmetro de classe b (ADR-0015; PARAMETROS-DO-TENANT,
   grupo de almoxarifado):
   - faixas de valor da requisição, cada uma com o papel que aprova (encarregado, almoxarife, gerente);
   - abaixo do limite configurado, aprovação automática, registrada como tal;
   - **itens controlados** (por exemplo, hidrômetros e materiais de alto valor) sempre exigem aprovação, em qualquer
     valor;
   - **segregação de funções** travada (classe a): quem requisita não aprova a própria requisição;
   - toda requisição, decisão, reserva, entrega, baixa, devolução e ajuste vai para a auditoria, com usuário, data e
     regra de alçada aplicada.
3. **Saldo insuficiente: saldo negativo permitido.** A requisição pode ser aprovada sem saldo disponível. Nesse caso o
   sistema gera **alerta de reposição** ao almoxarifado e mostra o item no painel de estoque do tenant e na Central
   (contagem por tenant, sem dado pessoal).
   - **Risco:** estoque negativo distorce o custo médio e o inventário, e pode esconder perda ou desvio.
   - **Mitigação:** relatório diário de itens negativos; regularização obrigatória por entrada ou ajuste com
     justificativa, num prazo configurável; o custo da baixa sobre saldo negativo usa o último custo médio conhecido e
     é recalculado na regularização, com registro da diferença; inventário periódico com prazo máximo configurável;
     alerta na Central para item negativo além do prazo.
4. **Campo e offline** (app de campo, ADR-0026). O material usado é registrado no app, por tarefa. A baixa acontece na
   sincronização, com **idempotência pela chave da tarefa** (ADR-0031): reenvio não baixa duas vezes. Para itens
   controlados usados sem requisição aprovada, a alçada é aplicada **a posteriori**: a baixa entra pendente de
   aprovação, o saldo é afetado na sincronização e a reprovação gera ajuste com justificativa e ocorrência para o
   encarregado. O registro grava aparelho, usuário e hora (ADR-0030).
5. **Modelo de dados** em inglês, no módulo `erp` (`inventory`), faixa `V20_x` do plano §3 (pela sequência global a
   partir de `V100`, com o módulo no nome, se depois da P3.12): `approval_rule` (faixas, itens controlados, papel
   aprovador, vigência), `material_requisition` (OS, tarefa, requisitante, estado, regra aplicada, aprovador),
   `requisition_item` (material, quantidades prevista, entregue, usada e devolvida), `stock_reservation` (item, local,
   quantidade, estado), `stock_movement` (entrada, saída, reserva, liberação, baixa, devolução, ajuste; custo unitário;
   origem; chave de idempotência) e `approval_decision` (decisão, usuário, data, justificativa).
6. **Integração com o ERP do cliente** (ED-62): quando o cliente mantiver o estoque oficial no ERP, os movimentos da OS
   são exportados por API ou arquivo; o ciclo de reserva, alçada e baixa continua no produto.

## Consequências

### Positivas

- Estoque e custo da OS ficam corretos sem lançamento manual, inclusive para o trabalho feito offline.
- Material controlado não sai sem aprovação, e a trilha mostra quem pediu, quem aprovou e quanto foi usado.
- Atende a OS com materiais da POC da VISAN e a parte de estoque da seção 2.17 do SAMAE.

### Negativas

- Saldo negativo permitido troca bloqueio operacional por risco de custo e inventário errados; a regularização passa
  a ser rotina obrigatória do almoxarifado.
- Alçada a posteriori no campo deixa material controlado baixado antes da aprovação.
- Mais um conjunto de parâmetros por tenant a configurar na implantação.

### O que muda no código

- Módulo `inventory`: requisição, alçada, reserva, movimentos, custeio, alertas e relatórios de negativos e de
  consumo por OS.
- OS (ED-36): seleção de material gera requisição; encerramento dispara baixa e devolução.
- App de campo (ED-20): registro de material usado por tarefa, enviado na sincronização.
- Parâmetros do grupo de almoxarifado com aceite (ADR-0002) e regra de segregação travada.
- Painel de estoque do tenant e contador de itens negativos na Central.

### O que muda no roadmap

- ED-58 passa a incluir o ciclo deste ADR (+2,5 semanas, consolidado 18), na onda C, depois da ED-36 e da ED-20.

## Alternativas consideradas

- **Baixa na aprovação.** Rejeitada: o estoque físico cairia antes de o material sair, e a sobra exigiria lançamento
  manual.
- **Baixa manual no encerramento.** Rejeitada: depende de lançamento que o campo costuma esquecer; estoque e custo
  ficam errados.
- **Bloquear a requisição sem saldo.** Rejeitada pelo dono do produto: pararia a OS em serviço essencial por falta de
  lançamento de entrada.
- **Alçada única por valor, sem itens controlados.** Rejeitada: hidrômetro de baixo valor unitário passaria sem
  aprovação, e o controle do parque (ED-14) depende dele.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Parâmetros do grupo de almoxarifado em PARAMETROS-DO-TENANT, com classe e padrão de fábrica | P4 (ED-58) |
| Requisição, alçada, reserva, entrega, baixa automática, devolução e custeio | P4 (ED-58) |
| Saldo negativo com alerta de reposição, relatório diário, regularização e inventário periódico | P4 (ED-58) |
| Registro de material no app de campo e baixa na sincronização com alçada a posteriori | P4 (ED-58, sobre a ED-20) |
| Exportação dos movimentos ao ERP do cliente | P4 (ED-62) |
