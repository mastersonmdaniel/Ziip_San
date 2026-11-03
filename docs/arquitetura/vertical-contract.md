# Contrato do pacote de vertical (P0.47)

Contrato executável: `packages/sanitation/vertical.json`, validado por
`packages/sanitation/vertical.schema.json` e, no build, por
`VerticalManifestLoader`/`VerticalManifestValidator` (módulo `ziipsan-platform`) e pelo
teste de fronteira `VerticalBoundaryTest`. O mapa do produto é
`docs/arquitetura/product-map.md`, gerado por `scripts/generate-product-map.py` e
conferido no CI por `VerticalManifestTest` (marcadores `<!-- module: <id> -->`).

## 1. Regra das abas da ficha da unidade

Cada tarefa que cria uma fonte da ficha da unidade (contas, débitos, OS, atendimentos,
documentos, qualidade) acrescenta a aba correspondente ao manifesto do pacote, no campo
`unitSheetTabs` do módulo. A aplicação da regra no frontend é da P1.12, não desta onda.

Abas refletidas no manifesto nesta onda (fontes que já existem como módulo): contas
(`billing`), débitos (`collection`), documentos (`mtr`), qualidade (`quality`). O
`registry` fornece a própria ficha, não uma aba. OS e atendimentos ainda não têm módulo
nem aba: entram com as tarefas que os criarem, junto com a aba.

## 2. Renomeação para `platform/*` e `packages/sanitation/*` — NÃO APLICADA (trabalho restante)

A P0.47 deveria aplicar os nomes definitivos de módulo conforme "a tabela única de
módulos de PLATAFORMA-E-VERTICAIS". Verificação feita nesta onda: **essa tabela não
existe no documento** — `PLATAFORMA-E-VERTICAIS.md` só fixa os padrões `platform/*` e
`packages/sanitation/*`, sem mapear nenhum módulo atual para o nome definitivo. Sem a
tabela, renomear seria adivinhar o destino de 13 módulos Maven, dos pacotes Java, dos
artefatos e das migrations — e a instrução da onda é explícita: não quebrar o build
pela renomeação.

Decisão da frente (registrada, não inventada como decisão do dono): entregar
`vertical.json` + registro + teste de fronteira + mapa **sem renomear os módulos
existentes**, e deixar a renomeação como trabalho restante, condicionado a existir a
tabela única. Proposta de classificação **para revisão do coordenador/dono, não
aplicada**:

| Classificação proposta | Módulos atuais |
|---|---|
| Núcleo → `platform/*` | `shared/ziipsan-core`, `shared/ziipsan-security`, `platform/ziipsan-platform`, `platform/ziipsan-app` (tenant, autenticação, BFF, parâmetros, auditoria — fronteira §2) |
| Pacote → `packages/sanitation/*` | `commercial/ziipsan-registry`, `commercial/ziipsan-billing`, `commercial/ziipsan-collection`, `commercial/ziipsan-fiscal`, `operations/ziipsan-metering`, `intelligence/ziipsan-gis`, `intelligence/ziipsan-quality`, `intelligence/ziipsan-losses`, `grs/ziipsan-mtr`, `compliance/ziipsan-lgpd` |

Pontos que a tabela única precisa resolver antes da renomeação (não resolvidos aqui):
o destino do `ziipsan-app` (raiz de composição, não só plataforma); o nome definitivo
do `ziipsan-core` dentro de `platform/*`; e se `packages/sanitation/*` carrega um
artefato agregador ou só os módulos. Até lá, o campo `mavenModule` do manifesto usa o
caminho atual, e o validador não exige o padrão novo.

## 3. Lista de exceções da varredura de termos (versionada)

O teste de fronteira recusa estes termos no código do núcleo
(`platform/ziipsan-platform`, `shared/ziipsan-core`, `shared/ziipsan-security`):
`hidrômetro`, `esgoto`, `fatura`, `saneamento`, `água`, `ligação de água`.
Exceções justificadas (hoje: nenhuma — varredura limpa nesta onda):

| Termo | Ocorrência | Justificativa |
|---|---|---|
| — | — | — |

Nota de falso positivo, registrada para não virar exceção futura: a palavra isolada
`ligação` NÃO é termo proibido. Ela ocorre no núcleo com o sentido de vínculo (ex.:
`JwtTenantFilter`, "sem ligação com o usuário autenticado"). Só a expressão
`ligação de água` é termo de pacote.

## 4. Pacote de exemplo

Só em teste (PLATAFORMA-E-VERTICAIS §6): o contrato de manifesto é exercitado por
manifestos sintéticos montados em `VerticalManifestTest` (validador rejeita rota
duplicada e manifesto sem `certification`). Não há pacote `example-vertical`
versionado no repositório — o teste sintético cumpre o papel sem carregar conteúdo
regulatório fictício.

## 5. P1.11 e P1.13 (frente plat-web): escrita condicional, idempotência e fronteiras de módulos

P1.11, superfície v0 — só o recurso de usuários (`/api/v1/users`, o único PUT
da app): GET único devolve `ETag: "v<version>"` (o `@Version` do `BaseEntity`);
PUT sem `If-Match` é 428 (`precondition-required`), com versão diferente da
atual é 412 (`precondition-failed`); `If-Match: *` aceita qualquer versão
existente. POST de criação com `Idempotency-Key` devolve a resposta gravada
(201, mesmo corpo) sem reexecutar, com escopo por tenant; sem a chave, o
comportamento é o de antes, e erro nunca é gravado. Componentes reutilizáveis
por outras frentes: `IdempotencyStore` e `ConditionalWrite` (módulo
`ziipsan-platform`, pacote `web`), e os tipos `ApiException.PreconditionRequired`
/ `PreconditionFailed`. Limites da v0: o store é em memória, por processo
(entre réplicas a mesma chave pode executar duas vezes); a generalização do
PUT condicional para os recursos do cadastro é das frentes donas de cada
controller, não desta. CORS: `Idempotency-Key`/`If-Match` liberados e `ETag`
exposto, sem os quais o navegador não usaria o contrato.

P1.13, suíte `ModuleBoundaryTest` (ArchUnit da P0.3, Surefire): núcleo só
depende de si; `platform` só do núcleo; domínio só do núcleo, de si e da
própria família (`commercial`, `intelligence`, `operations`, `grs`,
`compliance`); ninguém depende da `app`; nenhum nome de classe simples se
repete entre `billing` e `metering` (as duplicatas citadas na P1.13, ver
P0.25, não existem hoje — o teste falha se voltarem). A duplicata conhecida
`DmaDistrictRepository` (gis × losses) fica fora desta suíte de propósito:
ela é da P1.26, que a remove. A prova de violação proposital aplica a mesma
forma de regra ao par real gis → losses e exige `AssertionError`.

## 6. P1.38 — catálogo único de eventos de negócio

A seção `events` do `vertical.json` é o catálogo único: cada evento tem nome,
versão, esquema da carga (só identificadores e códigos, sem dado pessoal),
permissão exigida de quem recebe (dentro do catálogo da P0.13) e consumidores
declarados. Os consumidores declarados do pacote são `P3.36` (central de
avisos), `P3.5` e `ED-41` (notificações ao consumidor), `P2.26` (webhooks de
saída), `P2.28` (minhas tarefas) e `ED-07` (alertas do tenant) — só declarados,
não construídos nesta onda. O documento `docs/arquitetura/business-event-catalog.md`
é gerado por `scripts/generate-business-event-catalog.py` e conferido no CI por
`BusinessEventCatalogDocTest` (marcadores por evento, consumidor e lacuna).

Publicação: toda tarefa que publica eventos usa `BusinessEventPublisher`
(pacote `platform.infra.events`), que valida catálogo, versão e carga antes
de escrever na outbox da P1.9, com o tipo versionado (`nome@vN`); a
`PostgresMessageQueue` construída com catálogo aplica a mesma validação a
qualquer mensagem de evento, e recusa nome catalogado sem versão. Tipos que
não são eventos passam como transporte. Mudança incompatível de carga exige
nova versão do evento; a carga da versão nova é recusada na versão antiga.

Estado nesta onda: nenhum evento de negócio em produção (a outbox da P1.9 não
tem publicador em código de produção). O único tipo que aparece no código,
`billing.invoice.issued`, existe só nos testes da P1.9 e está registrado como
lacuna (`eventGaps`), não como evento — a tarefa que criar o publicador deve
declará-lo primeiro.
