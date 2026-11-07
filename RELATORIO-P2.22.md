# P2.22 — GIS-04: pacotes de edição, revisão por outra pessoa, ciclo de vida, travas por área e "rede na data"

**Pacote de entrega** (caminhos do repo `mastersonmdaniel/Ziip_San`, base main HEAD `0fbcd22` — que já inclui a P2.21; nenhum push feito).

## Decisão de numeração da migration (evidência)

A spec reservava `V13_4`, mas o slot está ocupado. Conferência feita via MCP em 07/11/2026 sobre
`intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/` na main (HEAD `0fbcd22`): existem
`V13_1` (classification_catalog), `V13_2` (features_history_migration), `V13_3` (responsible_party_redaction),
`V13_4` (catalog_approval), `V13_5`/`V13_6`/`V13_7` (fixes da integração P1c) e `V13_8` (topology_validation,
da P2.21) — exatamente o alerta da tarefa e o §3 do plano. Seguindo o precedente do projeto (slot ocupado →
próxima versão livre da série, sem editar migration aplicada — o mesmo que levou a P2.21 à V13_8), a P2.22
entrega **uma única migration nova: `V13_9__gis_change_sets.sql`**. Consequência registrada: os slots nominais
seguintes do plano (P3.25→V13_6, P3.26→V13_7, P3.27→V13_8) também estão ocupados e terão de seguir o precedente.

## Arquivos (13; eram 12 na entrega original — a correção A3 acrescentou um teste)

**Banco — `intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/`**
- `V13_9__gis_change_sets.sql` (novo, 54 comandos; parse OK no parser real do PostgreSQL via pglast):
  tabelas `gis_change_set`, `gis_change_set_item`, `gis_change_set_review`, `gis_edit_lock` (RLS via
  `register_tenant_table`, FKs `(tenant_id, …)`); FK `change_set_id` das 6 tabelas de feição para o pacote
  (a V13_2 a adiara para cá); guarda `gis_package_guard` nas 6 feições; máquinas de estado do pacote e da
  revisão; guarda de item (com exceção do rebase) e de trava; `gis_apply_change_set` +
  `gis_apply_change_set_item` (aplicação genérica por item); `gis_network_at_recorded`,
  `gis_network_at_business` e `gis_feature_business_snapshot` (sobre o `gis_feature_at` da V13_2);
  view `gis_change_set_pending_review` (superfície da P2.28).

**Java — `intelligence/ziipsan-gis`, pacote `changeset` (novo)**
- `GisChangeSetLifecycle` (regras puras: transições do pacote e da feição, auto-revisão, tabelas válidas),
  `GisChangeSetDtos`, `GisChangeSetService` (JdbcTemplate; JSON só via `Json` do core; tenant/usuário do
  contexto; traduz recusas do banco para 403/409), `GisChangeSetContext`.
- `web/GisChangeSetController` (`/api/v1/gis/change-sets`: CRUD, itens, submit, review, approve, return,
  reject, cancel, rebase, locks, pending-review), `web/GisNetworkAtController`
  (`GET /api/v1/gis/network-at`), `web/GisChangeSetRoles` (`gis:change-set-read/edit/review`, correspondentes
  a `gis.view`/`gis.edit`/`gis.review` do §6.6 no catálogo recurso:ação do projeto).

**Frontend — `frontend/src/`**
- `services/gis/changeSets.ts` (cliente dos endpoints) e `pages/gis/ChangeSetReview.tsx` (tela de revisão:
  antes/depois por feição em atributos e geometria WKT, validação do pacote, decisões, aprovar/devolver/
  rejeitar com justificativa). Sem mapa: o visualizador é da GIS-06 (P3.25) e ainda não existe no produto.

**Testes**
- `Gis04ChangeSetIT` (app, padrão `Gis03TopologyIT`, migrations até a V13_9): 20 testes cobrindo todos os
  itens do aceite no banco (12 na entrega original + 8 das correções pós-revisão — ver a seção no fim;
  a contagem "11" da primeira versão deste relatório estava errada, correção B1).
- `GisChangeSetLifecycleTest` (unitário, 6 testes): tabelas completas de transição e auto-revisão.
- `GisChangeSetServiceTest` (unitário, 5 testes; acrescentado pela correção A3): o 403 da api na
  auto-revisão provado no nível do serviço (aprovar/devolver/rejeitar com a identidade do autor).

Verificações estáticas feitas aqui: V13_9 parseia no pglast (parser real do PostgreSQL); balanceamento de
chaves/parênteses de todos os Java conferido. **Sem JDK/Maven/PostgreSQL nesta VM: nada compilou nem rodou —
a prova é a CI total no PC do dono, por camada, como no restante do projeto.**

## Cobertura do acceptance (spec + GIS-CADASTRO-TECNICO §13.2/GIS-04 e §6)

| Item | Estado |
|---|---|
| Gravação em feição em operação fora de pacote aplicado recusada pelo banco, inclusive por SQL direto | ✅ `gis_package_guard`: INSERT nascendo `IN_SERVICE`/`UNDER_CONSTRUCTION` e qualquer UPDATE de feição operacional (ou que entra em operação) sem a GUC transacional `app.gis_applying_change_set` do pacote sendo aplicado são recusados; DELETE só em `PLANNED` de pacote não aprovado. Testes IT por SQL direto nos três casos |
| Autor não aprova o próprio pacote (403 e erro no banco) | ✅ api: `GisChangeSetLifecycle.ensureReviewerIsNotAuthor` → `ApiException.Forbidden` (403) em approve/return/reject; banco: guardas de `gis_change_set_review`, da transição do pacote e de `gis_apply_change_set` recusam revisor = autor. IT prova os dois caminhos de banco |
| Aprovação numa transação, com falha desfazendo tudo | ✅ `gis_apply_change_set` roda na transação de quem chama (a api chama dentro da sua transação Spring); qualquer item que falhe aborta decisão, itens já aplicados e histórico. IT: pacote com um item bom e um com base 999 — nada muda, pacote segue `IN_REVIEW`, nenhuma revisão gravada |
| Rebase exigido em base desatualizada | ✅ item guarda `base_version` (coluna `version` da feição) e `base_snapshot`; na aplicação, divergência aborta com "rebase exigido"; `POST …/items/{id}/rebase` e a exceção da guarda de item permitem refazer só a base em pacote `IN_REVIEW`. IT cobre recusa → rebase → aplicação |
| Trava por área | ✅ `gis_edit_lock` com expiração; locks ativos sobrepostos de pacotes distintos não coexistem; a guarda de feição recusa gravação operacional de outro pacote na área (inclusive durante a aplicação dele). IT: bloqueia, expira, aplica |
| Feição projetada fora do traçado e dos indicadores | ✅ por construção + teste: `gis_network_at_*` só devolvem snapshot `IN_SERVICE`; PLANNED/UNDER_CONSTRUCTION nunca entram (a exclusão do traçado/EPANET em si é da GIS-03 e não foi alterada). IT: nó PLANNED não aparece na data de registro |
| `GET /api/v1/gis/network-at` correto por data de negócio e por data de registro | ✅ controlador + funções: negócio = `installed_on ≤ data < abandoned_on` sobre o snapshot (com fallback documentado para carga retroativa: janela pela versão registrada mais recente, atributos da versão em operação mais recente); registro = `gis_feature_at` (V13_2). IT com massa datada nos dois modos, incluindo troca de nome entre instantes |
| Tela de revisão com antes e depois | ⚠️ **parcial** — página entregue com antes/depois por feição (atributos + geometria WKT), validação e relatório do pacote; **falta** (a) o antes/depois **no mapa** — depende do visualizador da GIS-06 (P3.25), inexistente; (b) a **fiação da rota** no frontend (o espelho local não traz o roteador da aplicação) e teste E2E da tela |
| Pacote aguardando revisão aparece como item de minhas tarefas pela P2.28 | 🔗 **lado da P2.22 entregue; integração pendente por desenho** — estados `SUBMITTED`/`IN_REVIEW`, view `gis_change_set_pending_review` e endpoint `GET /change-sets/pending-review` prontos e testados na IT; a fila/tela "minhas tarefas" (PLT-13) é da P2.28, ainda pendente — nada dela foi inventado aqui |
| Transições de pacote e de ciclo de vida do §3.7/§6.1 testadas | ✅ guardas no banco + tabelas completas no teste unitário + IT de saltos recusados (`DRAFT → APPLIED`, aplicar pacote em `DRAFT`) |
| Pacote com erro de validação não aprova; aviso exige justificativa | ✅ (§6.2) portão em `gis_apply_change_set` sobre `gis_validation_issue` do pacote; IT cobre os dois casos |
| Tipos/canais de pacote do §6.1 | ✅ CHECKs com os 8 tipos e os canais; relatórios técnico/de qualidade no pacote (qualidade em JSONB) |

## Pendências e riscos (ordenados)

1. **Fiação da rota da tela de revisão** no frontend (arquivo de rotas não estava em nenhum espelho local) e E2E da tela — mesmo tipo de pendência de integração das frentes anteriores.
2. **Antes/depois no mapa** depende da GIS-06 (tiles + visualizador, P3.25).
3. **Interação com a GIS-03:** inserir nó sobre arco de legado (`change_set_id` nulo) por SQL direto passa a ser recusado quando a divisão geraria arco em operação fora de pacote — comportamento coerente com a decisão 3 (toda mudança na rede em operação é por pacote), mas muda o caminho que a `Gis03TopologyIT` exercia até a V13_8 (ela não aplica a V13_9; a CI por camada precisa confirmar que nenhum outro teste antigo aplica a V13_9 esperando escrita direta em operação).
4. **Riscos de CI (revisão estática apenas):** (a) ~~`gis_apply_change_set_item` monta o UPDATE a partir de `information_schema` do schema corrente — depende do `search_path` da sessão de aplicação ser o schema do tenant, como nas demais funções GIS~~ — **resolvido nas correções A1/M1 abaixo** (schema explícito resolvido do tenant + `search_path` fixado em todas as funções); (b) a ordem de disparo dos gatilhos BEFORE em `gis_arc`/`gis_connection` (snap da V13_8 × guarda nova) — a guarda roda depois do snap por ordem alfabética do nome do gatilho e a aplicação liga a GUC antes, mas o primeiro INSERT de arco PLANNED com snap criando JUNCTION dentro de pacote aberto merece o olhar da IT real; (c) `Map.of()` nos issues da revisão quebra se `rule_code` vier nulo do banco (a coluna é NOT NULL — risco teórico apenas); (d) no frontend, `useCan('gis:change-set-review')` segue o padrão das demais telas, mas a permissão nova não está em perfil de fábrica (P1.37), como as demais permissões GIS novas.
5. **QGIS e campo:** os canais `QGIS`/`FIELD` existem como tipo/canal do pacote, mas a escrita pelas views do QGIS é da GIS-10 e a sincronização de campo da GIS-15 — fora desta tarefa, sem nada inventado.
6. A P2.28 (minhas tarefas, PLT-13) consome `gis_change_set_pending_review`/`pending-review`: quando ela for implementada, conferir se o contrato (autor, tipo, contagem de itens, `submitted_at`) basta para o item de tarefa ou se pede o revisor designado — o modelo atual não tem revisor designado (qualquer `gis.review` revisa), decisão a registrar lá se mudar.

## Correções pós-revisão (REVISAO-P2.22.md — APROVADO COM CORREÇÕES: 0 críticos · 3 altos · 6 médios · 3 baixos)

A V13_9 ainda não foi pushada nem aplicada em nenhum banco, então todas as correções de banco foram
feitas editando a própria V13_9 (nenhuma migration aplicada — V13_1–V13_8 — foi tocada). Nenhuma
asserção existente foi afrouxada: os testes antigos só ganharam companhia; a única alteração em
código de teste preexistente é o comentário da classe da IT (A1). Verificação estática repetida:
a V13_9 corrigida parseia no pglast (57 comandos; eram 54 — +1 função, +1 DROP, +1 gatilho da guarda
de DELETE de revisão). Sem JDK/Maven/PostgreSQL nesta VM, a prova continua sendo a CI total no PC
do dono — inclusive para os testes novos.

### ALTOS

- **A1 — `search_path` em todas as funções da V13_9** → `V13_9__gis_change_sets.sql`: as 6 funções
  de gatilho (`gis_package_guard`, `gis_change_set_guard`, `gis_change_set_review_guard`,
  `gis_change_set_item_guard`, `gis_change_set_review_delete_guard` [nova], `gis_edit_lock_guard`)
  abrem com `PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true)`, o
  padrão literal da V13_6/V13_8. As funções chamadas pela api (`gis_apply_change_set`,
  `gis_network_at_recorded`, `gis_feature_business_snapshot`, `gis_network_at_business`) resolvem o
  schema do tenant em `public.tenant_catalog` pela GUC `app.current_tenant` (fallback: schema
  corrente, o caminho do migrador), fixam o `search_path` da transação nele e — no caso da
  aplicação — passam o schema explicitamente adiante (ver M1). As três funções de rede perderam o
  `STABLE` (a promessa não combina com o `set_config`). Na IT, novo teste
  `functionsResolveWithoutTenantSearchPath`: volta o `search_path` para `public` antes de cada
  exercício e chama guarda, `gis_apply_change_set` e `gis_network_at_business` qualificados pelo
  schema — têm de se resolver sozinhos. O `SET LOCAL` do setup da IT **fica**, com o motivo
  declarado no javadoc da classe: ele simula o TenantMigrator ao aplicar as migrations (paridade
  com a `Gis03TopologyIT`); quem não pode depender dele é a V13_9, e é isso que o teste novo prova.
- **A2 — guarda endurecida contra GUC forjada** → `V13_9__gis_change_sets.sql` (`gis_package_guard`
  + cabeçalho (2) reescrito): a exceção da gravação em operação agora exige, além da GUC, pacote
  existente no tenant da feição, em estado aplicável (`IN_REVIEW`/`APPROVED`, os mesmos que
  `gis_apply_change_set` aceita) e — na gravação raiz (profundidade de gatilho 1) — `change_set_id`
  gravado igual ao do pacote aplicante **e** a feição como item daquele pacote. Em profundidade
  > 1 (junção que o snap da V13_8 cria, pontas que o arrasto move) basta o pacote aplicante válido:
  são efeitos colaterais da gravação raiz já conferida, e exigi-los como itens quebraria a
  topologia. O comentário falso ("só a função liga") foi removido e a **limitação residual** está
  registrada no cabeçalho da migration: quem tem escrita SQL direta e monta um pacote legítimo em
  `IN_REVIEW` com a feição como item consegue ligar a GUC e gravar sem os portões da função de
  aplicação — a GUC é sinal cooperativo, não credencial; fechar isso exigiria `SECURITY DEFINER`
  dono da escrita, fora desta migration. Novo teste IT `forgedGucCannotWriteOperational`: GUC com
  pacote `DRAFT`, com uuid inexistente, com pacote `IN_REVIEW` cuja feição não é item e INSERT
  nascendo em operação com GUC de pacote em revisão — os quatro recusados por SQL direto.
- **A3 — teste do 403 da api** → novo arquivo
  `intelligence/ziipsan-gis/src/test/java/br/com/ziipsan/intelligence/gis/changeset/GisChangeSetServiceTest.java`
  (5 testes): dublê em memória do `JdbcTemplate` devolve um pacote `IN_REVIEW` de autor fixo e
  registra as escritas; com a identidade do autor no `TenantContextHolder`, `approve`,
  `returnToAuthor` e `reject` lançam `ApiException.Forbidden` (status 403) **antes** de qualquer
  escrita (a função de aplicação nem é chamada; nenhuma revisão é gravada); com outra identidade,
  aprovar chega à função do banco e devolver grava a decisão `RETURNED` (controle positivo de que
  o 403 é da identidade, não do caminho).

### MÉDIOS

- **M1 — aplicação de item sem no-op silencioso** → `V13_9__gis_change_sets.sql`
  (`gis_apply_change_set_item(p_item, p_schema)`): o schema vem explícito de
  `gis_apply_change_set` (resolvido uma única vez, como no A1) e qualifica o `UPDATE` e a leitura
  do catálogo (`pg_attribute`/`pg_class`/`pg_namespace`, não mais `information_schema` +
  `current_schema()`); chave de `proposed_state` que não é coluna aplicável da feição (desconhecida
  ou da lista de exclusões) **recusa** a aplicação com `gis: atributos propostos desconhecidos ou
  nao aplicaveis em <tabela>: <chaves>`; o cast usa `format_type(atttypid, atttypmod)` exato
  (domínio, `varchar(n)`, `numeric(p,s)`), não o `udt_name`.
- **M2 — rede na data de negócio: caminho primário testado e fallback justificado** → mantido o
  fallback retroativo, agora com a justificativa escrita contra o §3.7 no comentário de
  `gis_feature_business_snapshot` (a existência na data continua decidida só pela janela
  `installed_on ≤ data < abandoned_on`; o fallback só aproxima os atributos, porque o histórico do
  modelo é por tempo do sistema e feição de carga retroativa não tem versão vigente na data de
  negócio) e registrada aqui como **decisão**: virar ADR ou nota formal no GIS-CADASTRO-TECNICO
  fica como pendência de integração (esta entrega não edita o documento de arquitetura). Novo
  teste IT `networkAtBusinessPrimaryPath`: grava uma versão de histórico com `valid_period`
  cobrindo 2024 e nome distinto do atual; a consulta de 2024-03-01 devolve o nome da versão de
  2024 — prova de que quem respondeu foi o caminho primário (`gis_feature_at`), não o fallback
  (que devolveria os atributos atuais).
- **M3 — trava: ramos provados e autoria no serviço** → IT: novo `lockOverlapRefused` (segunda
  trava sobreposta de outro pacote recusada com `area ja travada`; a do mesmo pacote e a distante
  passam) e `lockOnStructurePointBlocks` (trava sobre o `geom_point` de `gis_structure` bloqueia a
  aplicação de outro pacote). Java (`GisChangeSetService.createLock`/`releaseLock`): `ensureAuthor`
  nos dois, como nas demais escritas do pacote.
- **M4 — recusas do banco mapeadas, domínios validados** → `GisChangeSetService`: `create` valida
  `type`/`channel` contra os domínios do §6.1 (novos conjuntos `CHANGE_SET_TYPES`/
  `CHANGE_SET_CHANNELS` em `GisChangeSetLifecycle`, espelho dos CHECKs) com 400 nomeado;
  `createLock` recusa expiração não futura com 400 antes do banco; `translateApplyError` virou
  `translateDbError` e cobre todas as mensagens nomeadas `gis: ...` da V13_9 (pacote não
  aprovável, pacote que não aceita revisão, itens de pacote fechado, trava em pacote fechado,
  registro imutável, atributos propostos inválidos, item inaplicável, não encontrado → 404) com
  fallback por SQLState (23514 → 400, 23503/23505 → 409); `addItem` e `rebaseItem` também passam
  pela tradução (antes vazavam 500). Com isso, `decide()` sobre pacote em `DRAFT` devolve 409
  (`pacote-nao-aceita-revisao`), não 500.
- **M5 — exceção de rebase estreita e DELETE guardado** → `V13_9__gis_change_sets.sql`: a exceção
  de rebase de `gis_change_set_item_guard` agora compara também `change_set_id`, `tenant_id` e
  `validation_result` (o "rebase" não move mais o item de pacote nem reescreve a validação); a
  mesma guarda ganhou ramo de `DELETE` (item só se apaga em pacote `DRAFT`/`RETURNED`) e a revisão
  ganhou a guarda `gis_change_set_review_delete_guard` (revisão nunca se apaga: é o registro de
  auditoria). A trava ficou de fora de propósito: `DELETE` é o caminho legítimo de liberação dela.
  Novo teste IT `itemAndReviewDeleteRefused` prova os três pontos (apagar item em `DRAFT` segue
  permitido; em `IN_REVIEW`, não; mover item via "rebase", não; apagar revisão de pacote aplicado,
  não).
- **M6 — frontend** → `frontend/src/pages/gis/ChangeSetReview.tsx`: (a) **Rejeitar** virou
  `type="button"` com handler próprio — não é mais submit do formulário de devolução com
  `preventDefault` no clique (fim da dupla fiação); (b) as ações seguem as máquinas de estado:
  Aprovar e aplicar só em `IN_REVIEW`/`APPROVED`, Devolver em `SUBMITTED`/`IN_REVIEW`, Rejeitar só
  em `IN_REVIEW` (em `SUBMITTED` não há mais botão de aprovar para clicar e errar); (c) a linha de
  geometria mostra o **antes** de verdade — a geometria do snapshot da base (`geom`/`geom_point`
  do `base_snapshot`, em GeoJSON) — e o depois em WKT proposto; `geom`/`geom_point` saíram da
  união de atributos para não duplicar a linha.

### BAIXOS

- **B1 — contagem do relatório** → corrigida nesta seção e na seção de arquivos: a
  `Gis04ChangeSetIT` tinha 12 testes (não 11); com as correções são 20, mais 6 do
  `GisChangeSetLifecycleTest` e 5 do novo `GisChangeSetServiceTest`.
- **B2 — cobertura da IT reforçada** → novo teste `guardCoverageAcrossTablesAndLifecycle`:
  INSERT direto `UNDER_CONSTRUCTION` em `gis_node` recusado; INSERT direto `IN_SERVICE` em
  `gis_structure` (segunda tabela) recusado; `DELETE` de `PLANNED` de pacote `APPROVED` recusado;
  salto de ciclo `ABANDONED → IN_SERVICE` via pacote recusado. Somado ao M3 (trava em
  `gis_structure`), a guarda passa a ser provada em 2 das 6 tabelas e nos dois sentidos do ciclo.
  **Declarado pendente** (sem esconder): as outras 4 tabelas (`gis_arc`, `gis_connection`,
  `gis_zone`, `gis_poi`) seguem provadas só por construção (a mesma função de guarda nas 6, com
  gatilhos idênticos) — exercitar uma a uma exige massa própria por tabela (snap de arco, ramal,
  zona sem tipo de fábrica semeado) e fica para o reforço com a CI verde, como a revisão sugeriu.
  Na mesma leva, o teste `underConstructionOutOfNetworkAt` fecha a lacuna apontada no item 7 da
  revisão: `UNDER_CONSTRUCTION` fora das duas consultas de rede na data.
- **B3 — cliente frontend no padrão de erro do projeto** → `frontend/src/services/gis/changeSets.ts`:
  `request()` agora converte a falha com `toApiError` de `@services/registry/errors`, como o
  cliente do registry (`registry/client.ts`), em vez de devolver o erro cru do axios para a página.
