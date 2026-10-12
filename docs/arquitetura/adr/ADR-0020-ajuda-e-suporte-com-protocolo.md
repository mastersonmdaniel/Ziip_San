# ADR-0020 — Ajuda e Suporte com protocolo, SLA por contrato e acesso assistido com consentimento

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** `docs/arquitetura/AJUDA-SUPORTE-SOBRE.md`; `docs/arquitetura/CENTRAL-ZIIP.md` §7; ADR-0010, ADR-0014,
  ADR-0018, ADR-0019; RNF-26, RNF-40, RNF-54
- **Ajuste de 12/10/2026:** item 4 e retenção alinhados às decisões do dono do produto (ClamAV como exceção do
  ADR-0010; chamados e anexos pela vigência do contrato mais 5 anos), antes de qualquer implementação.
- **Segundo ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** seção "Exceção: acesso de emergência"
  incluída por decisão do dono do produto; nomes de schema, tabela, coluna e rota passam ao inglês (plano §8, Idioma).
- **Ajuste de 12/10/2026 (plano v2.3), antes de qualquer implementação:** o catálogo de erros explicados passa a usar
  o prefixo `urn:ziipsan:problem:` e os códigos de problema em inglês (plano P0.33); o protocolo `SUP-AAAA-NNNNNN`
  permanece, por ser visível ao cliente; decisão do dono do produto.
- **Ajuste de 12/10/2026 (editais), antes de qualquer implementação:** item 3 passa a admitir, além das prioridades
  P1–P4 do padrão, a política do nível Setor Público (ADR-0025) com cinco severidades (S1 a S5) classificadas por
  impacto × criticidade, prazo de solução por severidade (severidade 1 em 2 horas úteis como referência), meta de
  aderência e cálculo de multa no relatório mensal; o plantão 24x7 para a severidade 1 depende da decisão de equipe
  do P4 (ADR-0023); decisão do dono do produto.
- **Ajuste de 12/10/2026 (editais, v2.5), antes de qualquer implementação:** item 3 passa a classificar todo chamado
  pelas cinco severidades S1 a S5 do ADR-0025, no lugar das prioridades P1–P4, cujo nome colidia com o das fases do
  roadmap; no padrão do produto, os prazos de cada severidade seguem a política de SLA do contrato, sem multa; no
  nível Setor Público, com prazo de solução, aderência e multa (ADR-0025); o plantão da S1 passa a ter equipe pelo
  ADR-0029; decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.12):** referências a equipe e capacidade sem efeito (ADR-0029 revogado).

## Contexto

O Ziip SAN não tem ajuda, canal de suporte nem página de status. No Ziip Cloud, o suporte é um formulário que envia
e-mail e recebe resposta única, sem protocolo nem prazo; o `PACOTE-SUPORTE.md` recomendou construção própria, sem GLPI.
O cliente de saneamento exige prazo contratual medido, e chamados podem trazer dados de consumidores.

## Decisão

1. Módulo de ajuda e suporte próprio, no monólito, com dados no schema `support` do banco do core, o mesmo do schema
   `platform` (`tenant_id` e RLS), para a fila da Central Ziip (ADR-0018) funcionar entre clusters.
2. Conteúdo (artigos, tours, dicas, erros explicados) em arquivos versionados no repositório, editados por pull
   request e publicados por pipeline próprio. Tours apontam para atributos `data-tour`; o catálogo de erros usa o
   código exato do problem+json (`urn:ziipsan:problem:<código>`, em inglês; textos da explicação em português) e da
   rejeição fiscal.
3. Chamado com protocolo `SUP-AAAA-NNNNNN` (formato mantido em português por ser visível ao cliente; única exceção de
   idioma do plano §8), conversa encadeada com notas internas, estados com pausa do relógio,
   prioridade P1–P4 e prazos pela política de SLA referenciada no contrato (ADR-0019), escalonamento e CSAT.
4. Anexos validados pelo conteúdo, mantidos em quarentena até a varredura pelo ClamAV, que roda junto do `worker`, e
   guardados no armazenamento de objetos sob prefixo e chave do tenant. ClamAV é exceção registrada no ADR-0010
   (seção Exceções), com o mesmo pipeline para os logos de tenant (ADR-0016); serviço equivalente da hospedagem
   (ADR-0011) o substitui atrás da mesma interface.
5. Notificações pela outbox (ADR-0014). O e-mail leva protocolo, estado e link, sem conteúdo nem anexos.
6. Acesso assistido em vez de impersonação: o agente usa a própria identidade com MFA, só com consentimento do
   administrador do tenant vinculado a um chamado, com escopo, prazo máximo de 72 horas, revogação a qualquer momento
   e auditoria de cada requisição. A única exceção ao consentimento é o acesso de emergência (seção seguinte).
7. Página pública de status em hospedagem separada, alimentada pelas sondas dos SLOs e pelos incidentes da Central.
8. O suporte do Ziip ao tenant e o atendimento do tenant ao consumidor (R7) não compartilham tabelas, numeração nem
   filas. Compartilham componentes: estados, gerador de protocolo, pipeline de anexos, prazos em dias úteis, conversa
   e CSAT.

## Exceção: acesso de emergência

Decisão do dono do produto em 12/10/2026 (plano §2.4). A versão anterior deste ADR não admitia acesso sem
consentimento; passa a admitir uma única exceção, com estas condições cumulativas:

1. **Só em incidente de segurança declarado**, registrado conforme o plano de resposta a incidente (plano P3.10) antes
   da abertura da sessão. Sem incidente aberto, o pedido é negado e auditado.
2. **Só `PLATFORM_OWNER`**, com reautenticação e segundo fator no momento de abrir a sessão (step-up); o MFA do login
   anterior não basta.
3. **Somente leitura**, com as vedações do acesso assistido (revelar documento, exportar); qualquer escrita é recusada.
4. **Aviso ao tenant em até 24 horas** da abertura, ao administrador e ao encarregado, pela outbox, com motivo,
   período e escopo; a sessão aparece ao tenant na mesma lista do acesso assistido.
5. **Registro completo**: toda requisição no `audit_log` do tenant e em `platform.audit_event` com o id do incidente;
   a sessão, o motivo e o que foi consultado entram no registro do incidente. A sessão termina com o encerramento do
   incidente ou antes.

O acesso de emergência usa a mesma tabela de sessões (`support.assisted_access_session`, tipo `EMERGENCY`, com a
referência ao incidente no lugar do consentimento) e a mesma faixa de aviso na tela. Ele não autoriza a role da
Central a ler tabelas de tenant (ADR-0018, item 5). O DPA (plano P3.9) precisa prever a exceção.

## Consequências

- O SLA do contrato passa a ser medido e reportado; o contrato precisa definir política e calendário.
- O time de conteúdo trabalha em Git; o CI impede tour quebrado e erro sem explicação.
- ClamAV exige atualização de assinaturas e monitoramento; anexo fica indisponível até a varredura.
- Resposta por e-mail fica para o P4; até lá, toda interação passa pela aplicação.
- Retenção de chamados e de anexos (decisão do dono do produto, 12/10/2026): **vigência do contrato mais 5 anos**,
  registrada na tabela do RNF-40. Efeito LGPD: o prazo é longo, então vale a minimização (o formulário orienta a não
  anexar dado pessoal desnecessário e a citar a ligação em vez do consumidor; texto com CPF, CNPJ, e-mail ou telefone
  marca `contains_personal_data`); acesso restrito ao solicitante, ao administrador do tenant e aos agentes, com registro
  (RNF-41); eliminação no fim do prazo, com contagem. A eliminação de fim de contrato do tenant (ADR-0015) não alcança
  os chamados; o certificado de descarte registra essa exceção e a data prevista de eliminação, e o DPA precisa
  prevê-la.
- O acesso de emergência permite agir num incidente sem esperar o tenant, ao custo de um caminho de leitura sem
  consentimento. A mitigação é o conjunto de condições da exceção, o aviso em 24 horas e a revisão de cada uso na
  simulação e no pós-incidente.

## Alternativas consideradas

- **GLPI customizado.** Rejeitada: segunda pilha (PHP e MySQL) e dívida herdada.
- **Ferramenta SaaS de atendimento.** Rejeitada: novo operador no DPA, dado possivelmente fora do Brasil (RNF-44) e
  sem contexto de tenant, versão e `correlation_id`.
- **Modelo do Ziip Cloud.** Rejeitada: sem protocolo, histórico, prazo nem medição.
- **Base de conhecimento editada no banco.** Rejeitada: sem revisão nem versão atrelada à do software.
- **Mesmas tabelas do R7.** Rejeitada: mistura controlador e operador, prazos legais e contratuais.
- **Impersonação pelo Keycloak.** Rejeitada: a ação fica em nome do usuário e não limita o escopo.
- **Nenhuma exceção ao consentimento.** Rejeitada pelo dono do produto em 12/10/2026: num incidente de segurança, a
  espera pelo consentimento atrasa a contenção.
- **Exceção ampla (qualquer papel de plataforma, com escrita).** Rejeitada: ampliaria o acesso a dado pessoal sem
  necessidade para conter o incidente.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Estrutura `content/help`, validação no CI e pipeline de publicação | P1 |
| Catálogo para todos os códigos `urn:ziipsan:problem:*` existentes | P1 |
| Chamados com protocolo, conversa, estados, anexos com varredura e notificações; fila v0 na Central | P1 |
| Hook de ajuda contextual, tour e dica no frontend | P1 |
| SLA por contrato com calendário útil, escalonamento e CSAT | P2 |
| Rejeições da NFAg-e e da NFS-e no catálogo | P2 |
| Página pública de status; acesso assistido; acesso de emergência; retenção (vigência + 5 anos), expurgo e exportação de chamados; RIPD | P3 |
| Resposta por e-mail, assistente de deflexão, assinatura de status, notas do tenant | P4 |
