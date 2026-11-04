# Catálogo de permissões `recurso:ação` (P0.13, ADR-0040; perfis de fábrica P1.37)

Fonte: `shared/ziipsan-security/.../security/auth/PermissionCatalog.java`. Este documento é
conferido contra o código no CI: toda permissão do código aparece aqui, e nenhuma linha aqui
existe sem a permissão correspondente no código.

Formato único: `recurso:ação`, sem ponto (`registry:reveal-document`, nunca
`registry.reveal-document`). Negação por padrão: papel ou permissão desconhecida não concede
nada. O Keycloak autentica e entrega identidade, tenant e perfis; a permissão fina é resolvida
na aplicação, a partir do catálogo.

Perfis de fábrica (P1.37, decisão do dono de 28/10/2026, JOR-01), versão `0.1.0` — a versão
do pacote `sanitation`, declarada também em `packages/sanitation/vertical.json` (o teste de
manifesto confere código e manifesto). `*` = perfil desligado no nascimento: o conjunto
está declarado, mas não concede nada até a liberação da ED-75.

## Permissões

| Permissão | Módulo | Recurso | Ação | Perfis de fábrica | Tarefa de origem |
|---|---|---|---|---|---|
| `registry:read` | cadastro | registry | read | ADMIN, MANAGER, OPERATOR, VIEWER, ATENDENTE, SUPERVISOR_COMERCIAL, FATURISTA, ANALISTA_ARRECADACAO, LEITURISTA, PROGRAMADOR_OS, EXECUTOR_CAMPO, ANALISTA_PERDAS, GESTOR, AUDITOR_INTERNO, OUVIDOR, ENCARREGADO_DADOS, PROCURADOR, CONTADOR, AUDITOR_EXTERNO*, REGULADOR* | P0.13 |
| `registry:write` | cadastro | registry | write | ADMIN, MANAGER, OPERATOR, ATENDENTE, SUPERVISOR_COMERCIAL | P0.13 |
| `registry:reveal-document` | cadastro | registry | reveal-document | (nenhum — só concessão direta) | P0.13 |
| `registry:search-document` | cadastro | registry | search-document | (nenhum — só concessão direta) | P0.11 |
| `registry:export` | cadastro | registry | export | (nenhum — só concessão direta) | P1.12 |
| `user:read` | plataforma | user | read | ADMIN, MANAGER, SUPERVISOR_COMERCIAL, GESTOR, AUDITOR_INTERNO, ENCARREGADO_DADOS, AUDITOR_EXTERNO* | P0.13 |
| `user:write` | plataforma | user | write | ADMIN | P0.13 |
| `deployment:read` | plataforma | deployment | read | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_OPS) | P0.43 |
| `gis:catalog-read` | gis | gis | catalog-read | ADMIN, EDITOR_CAD_TECNICO, REVISOR_CAD_TECNICO, AUDITOR_INTERNO, AUDITOR_EXTERNO*, REGULADOR* | P1.27 |
| `gis:catalog-manage` | gis | gis | catalog-manage | ADMIN, EDITOR_CAD_TECNICO | P1.27 |
| `console:read` | console | console | read | (nenhum perfil de tenant — só papéis de plataforma: os 4 papéis) | P1.6 |
| `console:manage` | console | console | manage | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_OPS) | P1.6 |
| `console:suspend` | console | console | suspend | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_FINANCE) | P1.6 |
| `console:delete` | console | console | delete | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER) | P1.6 |
| `central:read` | central | central | read | (nenhum perfil de tenant — só papéis de plataforma: os 4 papéis) | P1.17 |
| `central:manage` | central | central | manage | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_OPS) | P1.17 |
| `contract:read` | contratos | contract | read | (nenhum perfil de tenant — só papéis de plataforma: os 4 papéis) | P2.12 |
| `contract:manage` | contratos | contract | manage | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_FINANCE) | P2.12 |
| `contract:restrict` | contratos | contract | restrict | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_FINANCE) | P2.12 |
| `parameters:manage` | console | parameters | manage | ADMIN, GESTOR | P1.6 |
| `protocol:read` | atendimento | protocol | read | ATENDENTE, SUPERVISOR_COMERCIAL, OUVIDOR | P1.36 |
| `protocol:write` | atendimento | protocol | write | ATENDENTE | P1.36 |
| `brand:manage` | console | brand | manage | ADMIN | P1.19 |
| `cert:read` | certificacao | cert | read | RESP_TEC_QUALIDADE, LABORATORISTA | P1.32 |
| `cert:write` | certificacao | cert | write | RESP_TEC_QUALIDADE | P1.32 |
| `cert:approve` | certificacao | cert | approve | RESP_TEC_QUALIDADE | P1.32 |
| `cert:audit` | certificacao | cert | audit | RESP_TEC_QUALIDADE, AUDITOR_INTERNO | P1.32 |
| `support:read` | suporte | support | read | ADMIN, ATENDENTE, GESTOR, OUVIDOR | P1.23 |
| `support:write` | suporte | support | write | ADMIN, ATENDENTE, GESTOR | P1.23 |
| `support:manage` | suporte | support | manage | ADMIN | P1.23 |
| `support:queue` | suporte | support | queue | (nenhum perfil de tenant — só papéis de plataforma: PLATFORM_OWNER, PLATFORM_OPS, PLATFORM_SUPPORT) | P1.23 |
| `search:read` | busca | search | read | (nenhum — só concessão direta, decisão P1.33 confirmada na integração P1-C) | P1.33 |
| `service-order:read` | ordem de servico | service-order | read | (nenhum — só concessão direta) | P1.33 |
| `billing:read` | cobrança | billing | read | (nenhum — só concessão direta) | P1.33 |
| `document:read` | documentos | document | read | (nenhum — só concessão direta) | P1.33 |
| `asset:read` | ativos | asset | read | (nenhum — só concessão direta, designada pela frente P1.35) | P1.35 |
| `asset:manage` | ativos | asset | manage | (nenhum — só concessão direta, designada pela frente P1.35) | P1.35 |
| `template:read` | documentos | template | read | ADMIN, GESTOR, AUDITOR_INTERNO | P2.30 |
| `template:write` | documentos | template | write | ADMIN, GESTOR | P2.30 |
| `template:approve` | documentos | template | approve | ADMIN, GESTOR | P2.30 |

## Perfis de fábrica (P1.37)

Página inicial de todos: `/inicio`, atribuída aqui e construída pela P3.39 (que a compõe
por perfil). Menu de cada perfil: só os itens do mapa do produto (P0.47) cuja permissão o
perfil tem — o frontend filtra por permissão (P1.12) e o servidor nega a rota sem ela.
Perfis com conjunto vazio ou só `registry:read` aguardam o catálogo do seu domínio:
nenhuma permissão foi inventada para preenchê-los; a tarefa que catalogar o domínio
acrescenta as permissões aos perfis pertinentes, e o teste de cobertura a obriga.
As permissões das frentes paralelas da camada C foram reconciliadas na integração P1-C
(a tabela abaixo já reflete a reconciliação): protocolo no atendimento, marca e suporte
na administração do tenant, certificações na qualidade. A busca global e suas leituras
(P1.33) ficam fora de qualquer perfil, por decisão explícita da frente P1.33
(concessão direta, como `registry:export`).

| Código | Função | Ativo | Permissões |
|---|---|---|---|
| `ADMIN` | Administrador do tenant | sim | `registry:read`, `registry:write`, `user:read`, `user:write`, `gis:catalog-read`, `gis:catalog-manage`, `parameters:manage`, `brand:manage`, `support:read`, `support:write`, `support:manage`, `template:read`, `template:write`, `template:approve` |
| `MANAGER` | Gerente | sim | `registry:read`, `registry:write`, `user:read` |
| `OPERATOR` | Operador | sim | `registry:read`, `registry:write` |
| `VIEWER` | Observador | sim | `registry:read` |
| `ATENDENTE` | Atendente | sim | `registry:read`, `registry:write`, `protocol:read`, `protocol:write`, `support:read`, `support:write` |
| `SUPERVISOR_COMERCIAL` | Supervisor comercial | sim | `registry:read`, `registry:write`, `user:read`, `protocol:read` |
| `FATURISTA` | Faturista | sim | `registry:read` |
| `ANALISTA_ARRECADACAO` | Analista de arrecadação | sim | `registry:read` |
| `LEITURISTA` | Leiturista | sim | `registry:read` |
| `PROGRAMADOR_OS` | Programador de OS | sim | `registry:read` |
| `EXECUTOR_CAMPO` | Executor de campo | sim | `registry:read` |
| `ALMOXARIFE` | Almoxarife | sim | (nenhuma catalogada) |
| `OPERADOR_ESTACAO` | Operador de estação | sim | (nenhuma catalogada) |
| `ANALISTA_PERDAS` | Analista de perdas | sim | `registry:read` |
| `LABORATORISTA` | Laboratorista | sim | `cert:read` |
| `RESP_TEC_QUALIDADE` | Responsável técnico da qualidade | sim | `cert:read`, `cert:write`, `cert:approve`, `cert:audit` |
| `EDITOR_CAD_TECNICO` | Editor do cadastro técnico | sim | `gis:catalog-read`, `gis:catalog-manage` |
| `REVISOR_CAD_TECNICO` | Revisor do cadastro técnico | sim | `gis:catalog-read` |
| `GESTOR` | Gestor | sim | `registry:read`, `user:read`, `parameters:manage`, `support:read`, `support:write`, `template:read`, `template:write`, `template:approve` |
| `AUDITOR_INTERNO` | Auditor interno | sim | `registry:read`, `gis:catalog-read`, `user:read`, `cert:audit`, `template:read` |
| `OUVIDOR` | Ouvidor | sim | `registry:read`, `protocol:read`, `support:read` |
| `ENCARREGADO_DADOS` | Encarregado de dados | sim | `registry:read`, `user:read` |
| `PROCURADOR` | Procurador | sim | `registry:read` |
| `CONTADOR` | Contador | sim | `registry:read` |
| `AUDITOR_EXTERNO` | Auditor externo | não (ED-75) | `registry:read`, `gis:catalog-read`, `user:read` (previsto, sem efeito até a liberação) |
| `REGULADOR` | Regulador | não (ED-75) | `registry:read`, `gis:catalog-read` (previsto, sem efeito até a liberação) |

## Regras

- `registry:reveal-document` não está em nenhum perfil de fábrica, nem no ADMIN: revelar o
  documento em claro exige a permissão direta (papel do token com exatamente esse nome). Quem
  cadastra não precisa, em regra, ver o documento depois de gravado; o acesso fica na auditoria.
- `registry:search-document` (P0.11, ADR-0008) também não está em nenhum perfil de fábrica:
  buscar por documento completo exige a permissão direta, separada de leitura e de revelação.
  Sem ela, a busca por documento é 403; com ela, a busca é registrada em
  `personal_data_access_log` e conta na cota por usuário por hora (429 ao exceder).
- `registry:export` (P1.12) também não está em nenhum perfil de fábrica: exportar uma
  lista exige a permissão direta, separada de leitura. Sem ela, a exportação é 403 e a
  negação fica registrada em `personal_data_access_log` (resultado DENIED); com ela, cada
  exportação — síncrona ou enfileirada — é registrada ali (tipo EXPORT), com o recurso
  e a quantidade de linhas, e o arquivo traz a mesma máscara da tela (ADR-0008).
- Permissão nova entra no catálogo junto da rota que a usa; rota sem permissão declarada é
  recusada no CI. As únicas rotas de negócio sem permissão declarada são `GET /api/v1/me` (dados do
  próprio usuário autenticado, sem acesso a dado de terceiros) e `GET /api/v1/about` (P0.42:
  só metadados da implantação — versão, commit, ambiente e data — sem dado de tenant ou de usuário).
- Regra da P1.37, conferida no CI: toda permissão do catálogo está em pelo menos um perfil
  de fábrica ou na lista explícita de exceções do código (`coverageExemptions`), com motivo.
  As exceções são só as três concessões diretas de dado pessoal acima e as cinco de
  plataforma abaixo — permissão nova esquecida fora de todo perfil falha o build. As
  permissões criadas por outras frentes da camada C em paralelo não estão nesta branch: a
  integração as reconcilia, atribuindo cada uma aos perfis pertinentes (o teste de
  cobertura a obriga a fazê-lo, e o ADMIN só ganha a permissão nova quando ela for de
  administração do tenant — a integração decide caso a caso).
- `deployment:read` não pertence a nenhum perfil de fábrica de tenant: ela só existe via
  papel de plataforma (PLATFORM_OWNER e PLATFORM_OPS, ver `PlatformRole`). Um token de tenant
  nunca a recebe, e um token de plataforma nunca recebe permissão de tenant.
- `console:read/manage/suspend/delete` (P1.6) seguem a mesma regra: só existem via papel de
  plataforma (ver `PlatformRole` — OWNER tem as 4, OPS tem read/manage, SUPPORT tem read,
  FINANCE tem read/suspend). A matriz fina (FINANCE só com motivo inadimplência, eliminação
  só OWNER) continua no serviço, não no catálogo.
- `central:read/manage` (P1.17, registradas na integração P1-C pelo mesmo precedente)
  seguem a mesma regra: só existem via papel de plataforma (OWNER e OPS têm as 2,
  SUPPORT e FINANCE têm só `central:read`); nenhum perfil de fábrica de tenant as inclui.
- `contract:read/manage/restrict` (P2.12, ADR-0019) seguem a mesma regra: só existem
  via papel de plataforma (os 4 papéis leem; OWNER e FINANCE gerenciam e restringem);
  nenhum perfil de fábrica de tenant as inclui. A matriz fina (FINANCE só por
  inadimplência, com fatura vencida e aviso registrados) continua no serviço.
- `parameters:manage` (P1.6, P1.37) cobre toda a tela de mudança de parâmetro do tenant
  (`/api/v1/tenant/parameters/*`: propor, aprovar, aceitar, ler vigente e painel). MUDANÇA DE
  COMPORTAMENTO NA P1.37: ela deixa de ser só concessão direta e entra nos perfis de
  fábrica ADMIN e GESTOR — o ADMIN (e o GESTOR) passam a propor parâmetro sem a concessão
  direta separada, e quem propõe sem ela continua levando 403. A separação
  proponente ≠ aprovador continua no serviço, por ator, não por papel. A concessão direta
  continua valendo por si só para qualquer outro ator.
- `brand:manage` (P1.19) cobre toda a administração da marca do tenant
  (`/api/v1/tenant/brand/*`: ler, salvar, subir e remover logo): é concessão
  direta, como `parameters:manage` — nenhum perfil de fábrica a inclui. A
  marca pré-login (`/api/v1/public/tenant-brand`) é pública e não exige
  permissão: devolve só nome, logos, cor e contato.
- `cert:read/write/approve/audit` (P1.32) cobrem o núcleo de Certificações de qualidade
  (`/api/v1/certification/*`): leitura do catálogo, das ativações e dos mapas (`read`);
  propor ativação, desligar e vincular evidência (`write`); aprovar e aceitar a ativação
  (`approve`); e a visão de auditoria do vínculo muitos-para-muitos, pelo lado da evidência
  (`audit`). Também são concessão direta, sem perfil de fábrica — perfis por função na P1.37.
  A separação proponente ≠ aprovador continua no serviço, por ator, não por papel, e o
  desligamento nunca apaga registro (o mapa congelado e os vínculos continuam consultáveis).
- `search:read` (P1.33) é o portão de entrada da busca global e das visões salvas
  (`/api/v1/search/*`): concessão direta, nenhum perfil de fábrica a inclui. O portão
  NÃO amplia a leitura de nenhum recurso: cada seção da busca global exige ainda a
  permissão do próprio recurso (`registry:read` para cliente e unidade,
  `service-order:read` para OS, `billing:read` para conta, `document:read` para
  documento) e a seção sem permissão volta em `deniedTypes`, sem dado. A busca
  global por documento completo passa pelo mesmo gate da P0.11 (`registry:search-document`,
  registro em `personal_data_access_log` e cota por usuário por hora, 429 ao exceder):
  sem a permissão própria, a busca global por CPF/CNPJ é 403 e não contorna o controle
  de CPF por nenhuma outra seção (nenhuma seção casa por documento parcial, máscara ou
  prefixo). Escopo por área (P3.30) e compartilhamento de visões por unidade
  organizacional ainda não existem: sem `org_units`, a busca global vale para o tenant
  inteiro (RLS) e a visão com `visibility=ORG_UNIT` só é listada ao próprio dono até a
  P3.30 — o compartilhamento é armazenado, não fingido.
- Auditor externo e regulador nascem desligados (P1.37): existem no catálogo, no manifesto
  e em `GET /api/v1/profiles` com `enabled=false`, mas um token com esse papel não ganha
  permissão nenhuma e a atribuição pelo administrador do tenant é recusada com erro de
  validação. O mecanismo de liberação é da ED-75, que ativa o conjunto já declarado sem
  redefinir permissões.
- Perfis de fábrica versionados com o produto (versão do pacote) e atribuídos pelo
  administrador do tenant (`POST/PUT /api/v1/users`, com `user:write`), com a atribuição
  auditada pelo trigger da V1_2 sobre `user_profiles` e com a regra de que o ator só
  concede o que possui. A V1_18 (integração P1-C) alargou o CHECK da V1_9 para os 26
  códigos, então a gravação de um perfil novo em `user_profiles` funciona (provada em
  FactoryProfileMeTest com GESTOR atribuído pelo ADMIN). A resolução por token (papel do
  Keycloak → permissões, menu e ações) já funcionava para os 26 perfis e não depende
  dessa tabela.
  Perfis compostos pelo ADMIN do tenant e restrição negativa ficam na ED-06, sobre este catálogo.
