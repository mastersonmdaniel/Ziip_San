# Ajuda, Suporte e Sobre

Decisões em ADR-0020 (ajuda e suporte) e ADR-0021 (Sobre, versão e changelog). Dependências: ADR-0010 (exceção do
ClamAV), ADR-0014 (outbox), ADR-0016 (pacote de marca), ADR-0018 (Central Ziip), ADR-0019 (cobrança por contrato),
RNF-10/11/26/40/52/54. O prefixo `urn:sigsan:problema:` passa a `urn:ziipsan:problema:` com a renomeação do P0
(plano P0.33). Todas as rotas seguem o prefixo `/api/v1` (plano P0.10); as do operador ficam sob `/api/v1/platform`,
como as demais da Central. Tabelas, colunas, rotas, chaves e identificadores estão em inglês, e os textos ao usuário
em português (plano §8, Idioma); o prefixo do URN e os códigos de problema seguem o contrato atual.

## 1. Objetivos

1. Resolver a dúvida antes do chamado: ajuda por tela, tour, dica por campo e erro explicado.
2. Chamado com protocolo, histórico, prazo contratual e medição.
3. Acesso do suporte a dados do tenant só com consentimento, prazo e auditoria; sem consentimento, só a exceção de
   emergência em incidente de segurança declarado (§5).
4. Versão gerada no build e changelog do cliente versionado.
5. Sobre como fonte única de identificação do produto, do fornecedor e dos documentos aceitos.

## 2. Fronteira com o atendimento ao consumidor (R7)

| | Suporte Ziip (este documento) | Atendimento do tenant (R7) |
|---|---|---|
| Relação | Ziip → tenant (B2B, contrato SaaS) | Tenant → consumidor final |
| Base | Contrato e SLA (ADR-0019) | NR 11 e regras de SAC |
| Dados | Schema `support` no banco do core | Schema do tenant |
| Protocolo | `SUP-AAAA-NNNNNN`, sequência da plataforma | Formato e sequência do tenant |
| Prazos | Contratuais, por prioridade | Legais, por tipo de manifestação |

Os dois não compartilham tabelas, filas, numeração nem atendentes. Compartilham componentes: máquina de estados,
gerador de protocolo parametrizado, pipeline de anexos (validação, varredura, armazenamento), notificação pela outbox,
cálculo de prazo em dias úteis com feriados por município, componente de conversa e CSAT. Um chamado ao Ziip pode citar
o número de um protocolo R7 como referência, sem copiar dados do consumidor.

## 3. Onde ficam os conteúdos

| Conteúdo | Local | Formato |
|---|---|---|
| Artigos | `content/help/articles/<module>/<slug>.md` | Markdown em português com cabeçalho: `id`, `title`, `module`, `routes`, `roles`, `audience` (`tenant` ou `internal`), `min_version`, `keywords` |
| Tours | `content/help/tours/<route>.yaml` | Passos com `target` em atributo `data-tour`, nunca seletor CSS |
| Dicas | `content/help/hints/<module>.yaml` | Chave estável do campo (`billing.reading.consumption`) → texto |
| Erros explicados | `content/help/errors/*.yaml` | Código exato → título, explicação, causas, ações, artigo, sugestão de chamado |
| Changelog do cliente | `content/release-notes/<version>.yaml` | ADR-0021 |
| Chamados, estado de leitura | Banco, schema `support` | Dados transacionais |
| Aceites de documentos, implantações | Banco, schema `platform` (`CENTRAL-ZIIP.md` §4) | Dados transacionais |

O time de conteúdo edita por pull request (editor web do GitHub), com revisão do produto. O CI valida cabeçalho,
links, rotas, alvos `data-tour` existentes no código e cobertura do catálogo de erros, e compila `help-bundle.json`.
Conteúdo tem pipeline próprio, sem nova imagem: o job `migrate` carrega o pacote nas tabelas de leitura, com busca em
português (`tsvector`, `unaccent`). No React, o hook `useContextualHelp()` lê a rota atual (`useLocation`) e busca
artigos, tour e dicas; `<Hint id>` exibe a dica do campo.

## 4. Modelo de dados

Schema `support` no banco do core (o mesmo do schema `platform`), com `tenant_id` e RLS em toda tabela. Fica fora do
schema do tenant para que a fila da Central Ziip funcione entre clusters (ADR-0005). Versões no plano §3: core `V9_3`
(conteúdo de ajuda), `V9_4` (chamados), `V9_5` (novidades vistas, com o aceite de documento), `V10_2` (SLA e CSAT),
`V10_3` (acesso assistido e de emergência) e `V10_4` (página de status).
`document_acceptance` e `deployment` ficam no schema `platform`, junto do contrato (`CENTRAL-ZIIP.md` §4); o aceite de
documento não se confunde com `parameter_acceptances`, que é o aceite do conjunto de parâmetros (ADR-0002).

| Tabela | Campos principais |
|---|---|
| `article`, `article_version` | id, slug, módulo, rotas[], papéis[], público, versão do conteúdo, hash, texto, `tsvector` |
| `article_feedback` | artigo, usuário, útil (sim/não), comentário, rota de origem |
| `tour_completion` | usuário, tour, versão do tour, data |
| `error_explanation` | código (`problem:<code>`, `nfage:<cStat>`, `nfse:<code>`), textos, artigo |
| `ticket` | id UUIDv7, protocolo, tenant, solicitante, assunto, categoria (dúvida, incidente, requisição, melhoria, segurança, privacidade), módulo, rota e versão de origem, `correlation_id`, prioridade P1–P4, estado, política de SLA, prazos de primeira resposta e resolução, pausa, violação, nível (N1, N2, engenharia), responsável, marcos de tempo, reaberturas, `contains_personal_data`, `purge_at` |
| `ticket_message` | chamado, autor (solicitante, agente, sistema), visibilidade (pública ou interna), corpo sanitizado, data |
| `ticket_attachment` | mensagem, nome, MIME detectado, tamanho, SHA-256, chave do objeto, estado da varredura |
| `ticket_event` | transições de estado, prioridade, atribuição e SLA |
| `sla_policy` | nome, calendário (24x7 ou útil), alvos por prioridade; referenciada pelo contrato (ADR-0019) |
| `csat_response` | chamado, nota 1–5, comentário, data |
| `access_consent` | tenant, chamado, concedente, escopo (leitura ou leitura e escrita), módulos, início, fim, revogação |
| `assisted_access_session` | tipo (`CONSENTED` ou `EMERGENCY`), consentimento (nulo na emergência), incidente (obrigatório na emergência), agente, fator da reautenticação, início, fim, aviso ao tenant enviado em |
| `release_note_view` | usuário, versão, data |
| `status_component`, `status_incident`, `status_update`, `maintenance_window` | página de status |

## 5. Fluxos

**Abrir.** O formulário sugere artigos enquanto o usuário digita. Ao enviar, grava rota, versão, módulo e, vindo de
uma tela de erro, o `correlation_id` e o código do problema, sem payload. Gera o protocolo, sugere a prioridade
(impacto × urgência), calcula os prazos pela política do contrato e publica as notificações na outbox. P1 fica para
faturamento ou portal parado e suspeita de incidente de segurança.

**Estados.** `NEW` → `IN_PROGRESS` → `WAITING_CUSTOMER` (relógio pausado) → `RESOLVED` → `CLOSED`, exibidos ao
usuário como novo, em atendimento, aguardando cliente, resolvido e fechado. Sete dias sem resposta em
`WAITING_CUSTOMER` levam a `RESOLVED`; `RESOLVED` fecha em 7 dias, e resposta nesse prazo reabre. Fechado, abre-se outro chamado vinculado. O solicitante pode cancelar antes do atendimento.

**Responder e escalar.** Resposta pública ou nota interna (nunca visível ao tenant). Escalar muda nível e responsável
sem reiniciar o relógio. A 80% do prazo, alerta ao responsável; no estouro, ao coordenador.

**Fechar e CSAT.** Ao resolver, o solicitante recebe pedido de avaliação por 7 dias; nota 1 ou 2 abre revisão.

**Acesso assistido.** O agente pede acesso no chamado com motivo, escopo e prazo; o administrador do tenant (ou quem
tiver `support.authorize-access`) aprova. O agente entra com a própria identidade e MFA (ADR-0007), com o
consentimento no token; não existe login como o usuário. Padrão: leitura, 2 horas, máximo 72; escrita só com escopo
explícito. Revelar documento (ADR-0008) e exportar ficam bloqueados. O tenant vê a faixa de acesso ativo, pode revogar
e recebe o relatório das ações. Toda requisição vai ao `audit_log` com o id do consentimento.

**Acesso de emergência** (exceção do ADR-0020, decisão do dono do produto de 12/10/2026). Sem consentimento, só com
incidente de segurança declarado e registrado: `PLATFORM_OWNER` abre a sessão referenciando o incidente, com
reautenticação e segundo fator no momento; somente leitura, com as vedações do acesso assistido; aviso ao tenant
(administrador e encarregado) em até 24 h, pela outbox; toda requisição no `audit_log` do tenant e em `audit_event` com
o id do incidente, e a sessão no registro do incidente. A sessão termina com o encerramento do incidente ou antes.
Detalhes em `CENTRAL-ZIIP.md` §7.

**Notificações.** Na aplicação e por e-mail, pela outbox. O e-mail leva protocolo, estado e link, sem conversa nem
anexos.

## 6. APIs principais

Lado do tenant (BFF, sessão do usuário):

- `GET /api/v1/help/context?route=` (artigos, tour e dicas da tela); `GET /api/v1/help/articles?q=&module=`;
  `GET /api/v1/help/articles/{slug}`; `POST .../feedback`; `GET /api/v1/help/errors/{code}`;
  `PUT /api/v1/help/tours/{id}/completion`.
- `POST|GET /api/v1/support/tickets`; `GET .../{protocol}`; `POST .../{protocol}/messages`, `/cancel`,
  `/reopen`, `/csat`.
- `POST /api/v1/support/attachments` (etapa própria; devolve id e estado da varredura);
  `GET /api/v1/support/attachments/{id}`.
- `POST /api/v1/support/consents`; `DELETE /api/v1/support/consents/{id}`; `GET /api/v1/support/access-sessions`
  (sessões assistidas e de emergência do tenant).
- `GET /api/v1/about`; `GET /api/v1/about/licenses`; `GET /api/v1/release-notes`;
  `POST /api/v1/release-notes/{version}/view`.

Telas do frontend: `/help`, `/support` e `/about`.

Lado do operador (Central Ziip, MFA obrigatório): `GET /api/v1/platform/support/queue`;
`PATCH /api/v1/platform/support/tickets/{protocol}` (estado, prioridade, nível, responsável); `POST .../messages`
(com visibilidade); `POST .../access-requests`; `POST /api/v1/platform/assisted-access/{consent}/sessions`;
`POST /api/v1/platform/emergency-access/{incident}/sessions` (só `PLATFORM_OWNER`, com reautenticação); incidentes e
janelas da página de status.

Público, sem autenticação e com cache: `GET /api/v1/public/status` (mesmo prefixo público de
`/api/v1/public/brand`, ADR-0016).

## 7. Integração com a Central Ziip (ADR-0018)

A Central é o lado do operador: fila por prioridade, prazo e tenant; visão do tenant (contrato, SLA, versão, módulos,
erros recentes pelo `correlation_id`); respostas-modelo; pedido de acesso assistido; incidentes da página de status;
painel de conteúdo (buscas sem resultado, artigos mal avaliados, erros mais exibidos); FRT, MTTR, SLA cumprido,
reabertura, CSAT e deflexão. A Central não lê o schema do tenant fora de uma sessão de acesso assistido ou de
emergência.

## 8. Página pública de status

Site estático em hospedagem separada da produção, para continuar no ar quando ela cair. Componentes: backoffice,
portal do consumidor (2ª via e Pix), autorizadores fiscais, PSP Pix e notificações. O estado vem das sondas dos SLOs
(RNF-10, RNF-11) e dos incidentes publicados na Central; manutenção aparece com 72 horas de antecedência (RNF-12).
Histórico de 90 dias e relato pós-incidente, sem nome de tenant. Na aplicação, uma faixa repete o incidente ativo.

## 9. Sobre e versionamento

Regras no ADR-0021. Pipeline:

1. O CI deriva a versão da tag (`v1.4.2`) ou, sem tag, `1.5.0-dev.N`, com build metadata `+<sha7>`.
2. Maven recebe `-Drevision`; `spring-boot-maven-plugin` gera `build-info.properties` e
   `git-commit-id-maven-plugin`, `git.properties`.
3. O Vite recebe versão, commit e data por variável de build e grava `version.json`.
4. CycloneDX (Maven e npm) gera o SBOM do RNF-35; dele sai `third-party-licenses.json` (componente, versão, licença
   SPDX), embalado na imagem e no SPA. Licença fora da lista permitida bloqueia o CI.
5. `content/release-notes/*.yaml` é validado e embalado como `release-notes.json`.
6. O job `migrate` grava a linha de `platform.deployment`; essa é a data exibida.

Toda resposta da API leva `Ziip-Version` e `Ziip-Min-Client-Version`. Servidor com versão diferente da do SPA gera
aviso de recarga; SPA abaixo da mínima gera aviso bloqueante, depois de preservar rascunhos.

O Sobre mostra produto e empresa desenvolvedora (razão social, CNPJ, contato, encarregado), vindos do pacote de marca
(ADR-0016); versão, commit, ambiente e data de implantação; Termos, Política, Contrato e DPA com a versão vigente e a
aceita, com data e autor do aceite; licenças de terceiros; relação de atualizações. "Novidades" abre uma vez por
versão por usuário, só com os itens que afetam o tenant.

## 10. O que se aproveita do Ziip Cloud

Aproveita-se: anexo validado no servidor com lista fixa sem SVG e HTML e baixado sempre como anexo; auditoria de cada
evento; tours por rota como dados (`ZIIP_TOURS`); dicas por campo com suporte a toque; estrutura e conteúdo do
`CatalogoErros` para a NFS-e (ADR-0003); a regra de registrar segurança, legislação e evidência legal; novidades uma
vez por versão; versão do documento legal gravada no aceite. Do `PACOTE-SUPORTE.md`: construção própria na mesma
pilha (rota B), estados com pausa de SLA, CSAT e avaliação de artigos.

Não se aproveita: anexo em `bytea` com tipo pela extensão; e-mail com conteúdo e anexos; resposta única sem protocolo
nem prazo; versão manual em três lugares; changelog em HTML no `app.js`; "visto" por instalação; implantação igual à
subida do processo; desenvolvedor e links fixos; catálogo por palavra-chave; tours por seletor CSS. O assistente de IA
do pacote vai para o P4.

## 11. Segurança e LGPD

- Anexos: até 5 por mensagem, 10 MB cada (RNF-26); tipo pelo conteúdo (PNG, JPEG, WEBP, PDF, TXT, CSV, XML), sem
  compactados; EXIF removido; anexo em quarentena até a varredura pelo ClamAV, que roda junto do `worker` (exceção do
  ADR-0010, substituível pelo serviço equivalente da hospedagem); infectado permanece bloqueado e gera alerta; objeto em
  `support/<tenant>/<ticket>/<attachment>` cifrado com a chave do tenant (RNF-32); download por URL pré-assinada curta,
  como anexo e com `nosniff`. Mensagens sanitizadas no servidor; cota e limite de taxa por tenant.
- Minimização: o formulário e o campo de anexo orientam a não incluir nem anexar dado pessoal desnecessário e a citar
  a ligação em vez do consumidor. CPF, CNPJ, e-mail ou telefone no texto geram alerta e marcam `contains_personal_data`;
  listas mostram o texto mascarado.
- O usuário vê os próprios chamados; o administrador do tenant, os do tenant; agentes, a fila, com registro de acesso
  (RNF-41). Logs sem dado pessoal (RNF-52); dados no Brasil (RNF-44).
- Ziip é controlador dos dados dos usuários do tenant na relação contratual e operador dos dados de consumidores que
  apareçam em chamado ou em acesso assistido ou de emergência, nos termos do DPA, que precisa prever a exceção de
  emergência.
- Retenção decidida pelo dono do produto em 12/10/2026 (substitui a proposta de 5 anos e 180 dias): chamados **e**
  anexos, pela vigência do contrato mais 5 anos; `purge_at` é fixado quando o contrato termina. Consentimentos e
  sessões de acesso assistido seguem o prazo do `audit_log`; sessões de emergência, o maior entre esse prazo e o do
  registro de incidente (`ret.incident_record_years`, 5 anos). Registro na tabela do RNF-40. Efeitos LGPD: minimização
  (item acima); acesso restrito ao solicitante, ao administrador do tenant e aos agentes, com registro (RNF-41);
  eliminação no fim do prazo, com contagem. Chamados entram na exportação de fim de contrato (RNF-42), mas não na
  eliminação do tenant: o certificado de descarte registra a exceção e a data prevista, e o DPA precisa prevê-la.
- A versão exata só aparece para usuário autenticado.

## 12. Fases e critérios de aceite

Tarefas correspondentes no plano: P0.39–P0.42; P1.20–P1.24; P2.15, P2.16; P3.17–P3.19; P4.8.

| Fase | Entregas | Critério de aceite |
|---|---|---|
| P0 | Versão pelo build, `build-info`, `version.json`, cabeçalhos de versão e aviso de recarga; licenças pelo SBOM; estrutura de `content/release-notes` com validação no CI; `GET /api/v1/about` mínimo | Nenhuma versão escrita à mão no repositório; dois builds do mesmo commit dão a mesma versão; SPA antigo mostra aviso após implantação em homologação |
| P1 | Estrutura de `content/help` e pipeline de publicação; busca e ajuda contextual; hook de tour e componente de dica; catálogo para todo `urn:...:problema:*`; chamados com protocolo, conversa, estados, anexos com varredura e notificações; fila v0 na Central; Sobre completo; `document_acceptance`; novidades por usuário | Teste falha se um código de problema não tiver explicação ou se um alvo de tour não existir; EICAR é bloqueado e um PDF limpo é liberado; usuário de outro tenant recebe 404 em chamado alheio; e-mail sem conteúdo do chamado |
| P2 | SLA por contrato com calendário útil, pausa, alertas e escalonamento; CSAT; rejeições da NFAg-e e da NFS-e no catálogo; notas de versão filtradas por tenant; métricas de conteúdo | Prazo calculado bate com casos de teste com feriado municipal e pausa; relatório mensal de FRT, MTTR, SLA e CSAT gerado na Central |
| P3 (antes do go-live) | Página pública de status com sondas e janelas; acesso assistido com consentimento; acesso de emergência em incidente declarado; retenção pela vigência do contrato mais 5 anos e expurgo; exportação no fim do contrato; pentest cobrindo o módulo; RIPD atualizado; plantão e manual de atendimento | Acesso sem consentimento e fora de incidente declarado é negado e auditado; emergência só com `PLATFORM_OWNER` reautenticado, somente leitura e aviso ao tenant em até 24 h; revogação encerra a sessão em até 1 minuto; status muda sozinho com a sonda fora; ensaio de expurgo com contagem |
| P4 | Resposta por e-mail; assistente de deflexão com citação e transbordo humano; assinatura de status; notas do tenant; editor visual; NPS | A definir no P4 |
