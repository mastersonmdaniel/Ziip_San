# ADR-0020 — Ajuda e Suporte com protocolo, SLA por contrato e acesso assistido com consentimento

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/arquitetura/AJUDA-SUPORTE-SOBRE.md`; ADR-0010, ADR-0014, ADR-0018, ADR-0019; RNF-26, RNF-40, RNF-54
- **Ajuste de 12/10/2026:** item 4 e retenção alinhados às decisões do dono do produto (ClamAV como exceção do
  ADR-0010; chamados e anexos pela vigência do contrato mais 5 anos), antes de qualquer implementação.

## Contexto

O Ziip SAN não tem ajuda, canal de suporte nem página de status. No Ziip Cloud, o suporte é um formulário que envia
e-mail e recebe resposta única, sem protocolo nem prazo; o `PACOTE-SUPORTE.md` recomendou construção própria, sem GLPI.
O cliente de saneamento exige prazo contratual medido, e chamados podem trazer dados de consumidores.

## Decisão

1. Módulo de ajuda e suporte próprio, no monólito, com dados no schema `suporte` do banco do core, o mesmo do schema
   `platform` (`tenant_id` e RLS), para a fila da Central Ziip (ADR-0018) funcionar entre clusters.
2. Conteúdo (artigos, tours, dicas, erros explicados) em arquivos versionados no repositório, editados por pull
   request e publicados por pipeline próprio. Tours apontam para atributos `data-tour`; o catálogo de erros usa o
   código exato do problem+json e da rejeição fiscal.
3. Chamado com protocolo `SUP-AAAA-NNNNNN`, conversa encadeada com notas internas, estados com pausa do relógio,
   prioridade P1–P4 e prazos pela política de SLA referenciada no contrato (ADR-0019), escalonamento e CSAT.
4. Anexos validados pelo conteúdo, mantidos em quarentena até a varredura pelo ClamAV, que roda junto do `worker`, e
   guardados no armazenamento de objetos sob prefixo e chave do tenant. ClamAV é exceção registrada no ADR-0010
   (seção Exceções), com o mesmo pipeline para os logos de tenant (ADR-0016); serviço equivalente da hospedagem
   (ADR-0011) o substitui atrás da mesma interface.
5. Notificações pela outbox (ADR-0014). O e-mail leva protocolo, estado e link, sem conteúdo nem anexos.
6. Acesso assistido em vez de impersonação: o agente usa a própria identidade com MFA, só com consentimento do
   administrador do tenant vinculado a um chamado, com escopo, prazo máximo de 72 horas, revogação a qualquer momento
   e auditoria de cada requisição.
7. Página pública de status em hospedagem separada, alimentada pelas sondas dos SLOs e pelos incidentes da Central.
8. O suporte do Ziip ao tenant e o atendimento do tenant ao consumidor (R7) não compartilham tabelas, numeração nem
   filas. Compartilham componentes: estados, gerador de protocolo, pipeline de anexos, prazos em dias úteis, conversa
   e CSAT.

## Consequências

- O SLA do contrato passa a ser medido e reportado; o contrato precisa definir política e calendário.
- O time de conteúdo trabalha em Git; o CI impede tour quebrado e erro sem explicação.
- ClamAV exige atualização de assinaturas e monitoramento; anexo fica indisponível até a varredura.
- Resposta por e-mail fica para o P4; até lá, toda interação passa pela aplicação.
- Retenção de chamados e de anexos (decisão do dono do produto, 12/10/2026): **vigência do contrato mais 5 anos**,
  registrada na tabela do RNF-40. Efeito LGPD: o prazo é longo, então vale a minimização (o formulário orienta a não
  anexar dado pessoal desnecessário e a citar a ligação em vez do consumidor; texto com CPF, CNPJ, e-mail ou telefone
  marca `contem_dado_pessoal`); acesso restrito ao solicitante, ao administrador do tenant e aos agentes, com registro
  (RNF-41); eliminação no fim do prazo, com contagem. A eliminação de fim de contrato do tenant (ADR-0015) não alcança
  os chamados; o certificado de descarte registra essa exceção e a data prevista de eliminação, e o DPA precisa
  prevê-la.

## Alternativas consideradas

- **GLPI customizado.** Rejeitada: segunda pilha (PHP e MySQL) e dívida herdada.
- **Ferramenta SaaS de atendimento.** Rejeitada: novo operador no DPA, dado possivelmente fora do Brasil (RNF-44) e
  sem contexto de tenant, versão e `correlation_id`.
- **Modelo do Ziip Cloud.** Rejeitada: sem protocolo, histórico, prazo nem medição.
- **Base de conhecimento editada no banco.** Rejeitada: sem revisão nem versão atrelada à do software.
- **Mesmas tabelas do R7.** Rejeitada: mistura controlador e operador, prazos legais e contratuais.
- **Impersonação pelo Keycloak.** Rejeitada: a ação fica em nome do usuário e não limita o escopo.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Estrutura `conteudo/ajuda`, validação no CI e pipeline de publicação | P1 |
| Catálogo para todos os códigos `urn:...:problema:*` existentes | P1 |
| Chamados com protocolo, conversa, estados, anexos com varredura e notificações; fila v0 na Central | P1 |
| Hook de ajuda contextual, tour e dica no frontend | P1 |
| SLA por contrato com calendário útil, escalonamento e CSAT | P2 |
| Rejeições da NFAg-e e da NFS-e no catálogo | P2 |
| Página pública de status; acesso assistido; retenção (vigência + 5 anos), expurgo e exportação de chamados; RIPD | P3 |
| Resposta por e-mail, assistente de deflexão, assinatura de status, notas do tenant | P4 |
