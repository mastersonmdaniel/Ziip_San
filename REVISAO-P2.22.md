# REVISÃO CRUZADA — P2.22 (GIS-04: pacotes de edição, revisão, travas e "rede na data")

**Revisor:** agente revisor independente (somente leitura no GitHub/MCP e nos espelhos; nenhum push, nenhuma correção de código).
**Entrega revisada:** `~/workspace/ziipsan-producao/p2.22/entrega/` — 12 arquivos + `RELATORIO-P2.22.md` (contagem conferida: 1 migration, 7 Java main, 1 teste unitário, 1 IT, 2 frontend = 12 ✓).
**Referências:** espelhos `p2.22/espelho/` e `p2.21/` (espelho + entrega), `GIS-CADASTRO-TECNICO.md` §3.7/§6.1–§6.6/§13.2 (linha GIS-04), acceptance literal de `tarefas-P2.json`, listagem MCP da `main` (HEAD `0fbcd22`) feita nesta revisão.

## Veredito geral: **APROVADO COM CORREÇÕES**

O desenho está correto e o acceptance literal está, em código, quase todo atendido. Há **0 problemas críticos, 3 altos, 6 médios e 3 baixos**. Os 3 altos não derrubam o desenho, mas precisam ser resolvidos (ou provados falsos na CI) antes de considerar a camada fechada. Nada foi executado nesta revisão: esta VM não tem JDK/Maven/PostgreSQL (o próprio `RELATORIO-P2.22.md` declara o mesmo) — **toda afirmação de comportamento em runtime é NÃO VERIFICÁVEL aqui e depende da CI total no PC do dono**, como no restante do projeto.

---

## Veredito por item do acceptance

### 1. Migration `V13_9` (spec reservava `V13_4`) — **OK**
- Listagem MCP desta revisão em `intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/` na `main` (HEAD `0fbcd22`): `R__gis_factory_catalog.sql`, `V13_1`, `V13_2`, `V13_3`, `V13_4__gis_catalog_approval`, `V13_5`, `V13_6`, `V13_7`, `V13_8__gis_topology_validation`, `V2_3` — **V13_9 é de fato a primeira livre** e o precedente da P2.21 (slot ocupado → próxima livre, sem editar aplicada) foi respeitado.
- A entrega contém **somente** `V13_9__gis_change_sets.sql` na pasta de migrations: **não altera `R__` nem `V13_8`** nem nenhuma migration aplicada ✓.
- Coerência com o schema real (V13_2/V13_8): as 6 tabelas de feição (`gis_node`, `gis_arc`, `gis_connection`, `gis_structure`, `gis_zone`, `gis_poi`), colunas `lifecycle_status`/`installed_on`/`abandoned_on`/`change_set_id`/`geom` (+ `geom_point` em `gis_structure`, tratado à parte na guarda), estados `PLANNED/UNDER_CONSTRUCTION/IN_SERVICE/ABANDONED` e a coluna `version` (acrescentada por `register_tenant_table`, como a V2_3 registra) — tudo confere. As FKs `(tenant_id, change_set_id)` usam a `UNIQUE (tenant_id, id)` criada na própria migration ✓. A FK de `change_set_id` é exatamente a que a V13_2 adiou ("chega na GIS-04") ✓.
- Ressalvas dentro deste item: ver problemas **A1** (search_path) e **M1** (aplicação genérica de item).

### 2. Gravação em feição em operação fora de pacote aplicado recusada pelo banco, inclusive por SQL direto — **OK COM RESSALVA (A2)**
- `gis_package_guard` (V13_9:208–311): INSERT nascendo `IN_SERVICE`/`UNDER_CONSTRUCTION` recusado; UPDATE com `OLD` ou `NEW` em operação recusado; DELETE só em `PLANNED` de pacote não aprovado — nas 6 tabelas. A IT prova por SQL direto INSERT/UPDATE/DELETE em `gis_node` (`Gis04ChangeSetIT.directInsertInServiceRefused`, `directWriteOnOperationalRefused`).
- A exceção é a GUC **transacional** `app.gis_applying_change_set` ligada com `set_config(..., true)` em `gis_apply_change_set` (V13_9:527): **não vaza entre transações/sessões** ✓ (escopo local de transação, some no commit/rollback).
- Ressalva séria: ver **A2** — a GUC é forjável por qualquer sessão SQL e a guarda não valida o pacote aplicante.

### 3. Autor não aprova o próprio pacote (403 na API **e** erro no banco) — **PROBLEMA (A3): código OK nos dois caminhos, teste só do banco**
- **API (código ✓):** `GisChangeSetService.approve/returnToAuthor/reject` chamam `GisChangeSetLifecycle.ensureReviewerIsNotAuthor(set.authorId(), userId do contexto)` → `ApiException.Forbidden` (403) — `GisChangeSetService.java:286–303, 314–345`. A identidade é a real: autor vem da linha do banco (`author_id`, gravado do contexto na criação) e o revisor de `TenantContextHolder.getUserId()` via `GisChangeSetContext` — nada vem do corpo da requisição ✓. `decide()` idem.
- **Banco (código ✓):** três recusas independentes — `gis_apply_change_set` (revisor = autor), `gis_change_set_review_guard` (INSERT de revisão) e `gis_change_set_guard` (transição para `APPROVED`/`APPLIED` com `reviewed_by = author_id`).
- **Teste (✗):** a IT prova só o banco (`selfReviewRefusedInDatabase`). **Não existe nenhum teste do 403 da API** — nem IT HTTP, nem teste de controller/service (a unidade `GisChangeSetLifecycleTest` testa só a função pura). O aceite da GIS-04 no §13.2 exige "403 na api e erro no banco (**teste**)" — a metade da API está sem prova.

### 4. Aprovação numa transação, com falha desfazendo tudo — **OK**
- `gis_apply_change_set` roda na transação de quem chama (sem `BEGIN/COMMIT` interno, sem bloco de exceção que engula erro): qualquer item que falhe aborta itens já aplicados, o INSERT em `gis_change_set_review` e o UPDATE final do pacote. O serviço chama dentro da sua transação Spring (`@Transactional` em `approve`) ✓.
- A IT `applyIsAtomicOnStaleItem` é bem montada: item bom criado **antes** do item com base 999 (ordem de aplicação `created_at, id` garante o bom primeiro), e as asserções conferem nome intacto, pacote ainda `IN_REVIEW` e **zero** linhas de revisão ✓. (Poderia conferir também `version`/histórico da feição boa — reforço opcional, não defeito.)

### 5. Rebase exigido em base desatualizada — **OK COM RESSALVA (M5)**
- A comparação é a certa: `version` atual da feição (lida com `FOR UPDATE`) × `base_version` do item, dentro de `gis_apply_change_set`; divergência aborta com "rebase exigido" antes de aplicar aquele item — e, pela atomicidade, tudo.
- O endpoint `POST /{id}/items/{itemId}/rebase` **não burla a guarda**: ele só atualiza `base_version`/`base_snapshot` do item via `gis_change_set_item_guard`, cuja exceção exige pacote `SUBMITTED`/`IN_REVIEW` e congela operação/tabela/feição/estado proposto. O serviço exige autor (`ensureAuthor`) e papel `EDIT` ✓. IT `rebaseThenApply` cobre recusa → rebase → aplicação ✓.
- Ressalva: a exceção da guarda não compara `change_set_id` (e libera `validation_result`) — ver **M5**.

### 6. Trava por área — **OK COM RESSALVAS (M3)**
- É **geométrica real (PostGIS)**, não por id: `ST_Intersects(l.geom, geom da feição)` na guarda de feição (V13_9:283) e `ST_Intersects` na guarda da própria trava (V13_9:426). Expiração tratada nos dois lugares com `expires_at > clock_timestamp()` ✓. Dois pacotes na mesma área: a segunda trava ativa sobreposta é recusada na criação **e** a gravação em operação de outro pacote dentro da área é recusada — inclusive durante a aplicação do pacote dele (a guarda compara `l.change_set_id IS DISTINCT FROM NEW.change_set_id`, e a aplicação grava `change_set_id` = pacote aplicante, então a trava do próprio pacote não o bloqueia ✓).
- A IT `areaLockBlocksOtherPackage` prova bloqueio de gravação + liberação após expirar a trava. **Não prova** a recusa de criar a segunda trava sobreposta (o ramo de overlap de `gis_edit_lock_guard` nunca é exercitado) — ver **M3**.

### 7. Feição projetada fora do traçado e dos indicadores — **OK**
- Por construção: `gis_network_at_recorded`/`gis_network_at_business` só devolvem snapshots com `lifecycle_status = 'IN_SERVICE'` — `PLANNED` e `UNDER_CONSTRUCTION` nunca entram; a exclusão do traçado/EPANET em si é da GIS-03 (V13_8) e não foi alterada ✓.
- Teste: a IT prova `PLANNED` fora na data de registro (`networkAtRecorded`, antes da aplicação). Não há teste direto de `UNDER_CONSTRUCTION` fora das duas consultas — lacuna menor, somada em **M7**.

### 8. `GET /api/v1/gis/network-at` por data de negócio e por data de registro — **OK COM RESSALVA (M2)**
- **Registro:** `gis_network_at_recorded` delega ao `gis_feature_at` da V13_2 (histórico por `valid_period` × linha atual por `created_at`) — semântica fiel ao §3.7. IT prova antes/depois de alteração com troca de nome entre instantes ✓.
- **Negócio:** janela `installed_on ≤ data < abandoned_on` aplicada sobre o snapshot, conforme o §3.7 literal. IT prova instalada entra / abandonada sai / antes da instalação não existe ✓.
- Ressalva (**M2**): (a) o *fallback* de carga retroativa de `gis_feature_business_snapshot` (V13_9:612+) é uma extrapolação **além da spec literal** — razoável e documentada no código e no relatório, mas é decisão nova e deveria virar registro (ADR/nota no GIS-CADASTRO-TECNICO) na integração; (b) os "atributos vigentes na data" do §3.7 saem aproximados pelo tempo do sistema (caminho primário) ou pela versão em operação mais recente (fallback), não por uma vigência de negócio que o modelo não tem; (c) a IT monta toda a massa "agora" (tempo do sistema posterior a todas as datas de negócio consultadas), logo **só exercita o fallback** — o caminho primário (`gis_feature_at` no fim da data) nunca é testado no modo negócio.

### 9. Tela de revisão com antes e depois — **PARCIAL (declarado) + PROBLEMAS NÃO DECLARADOS (M6)**
- `ChangeSetReview.tsx` + `changeSets.ts` seguem o padrão do frontend existente (React Query, `useCan('gis:change-set-review')`, `errorMessage` de `@services/registry/errors`, componentes `@components/ui/*`, textos pt-BR) e os tipos TS batem com os DTOs Java (`before`/`after`, `baseVersion`, decisões) ✓.
- **Declarado honestamente** no relatório e no cabeçalho da página: sem antes/depois **no mapa** (GIS-06/P3.25 inexistente) e **sem fiação de rota** (o roteador não estava em nenhum espelho) ✓ — não está escondido.
- Não declarados: ver **M6** (botão Rejeitar com dupla fiação, estado `SUBMITTED` oferecendo Aprovar que o banco sempre recusa, geometria "antes" sempre "—" na linha de WKT).

### 10. Fronteira P2.28 — **OK**
- O lado da P2.22 está completo e sem inventar a P2.28: estados `SUBMITTED`/`IN_REVIEW`, view `gis_change_set_pending_review` (autor, tipo, canal, `submitted_at`, contagem de itens) e `GET /change-sets/pending-review` com papel `REVIEW`. Nenhuma fila/tela de "minhas tarefas" foi criada. IT `pendingReviewSurface` prova aparecer ao submeter e sumir ao aplicar ✓. A observação do relatório sobre revisor designado (hoje qualquer `gis.review` revisa) é a fronteira certa a registrar na P2.28.

### Itens transversais (pergunta 6 da tarefa)
- **Pacotes/nomenclatura Java:** `br.com.ziipsan.intelligence.gis.changeset` (+ `.web`), nomes no padrão do módulo (espelha `catalog/application` + `catalog/web` e a frente `validation` da P2.21) ✓.
- **JSON só via `Json` do core:** grep em toda a entrega por Jackson/Gson/`ObjectMapper`/serialização manual = **zero ocorrências**; todos os usos são `Json.write/orDefault/readMap` de `br.com.ziipsan.core.json.Json` ✓ (métodos conferidos contra a classe real do core nos espelhos).
- **Erros no padrão `ApiException`:** `NotFound`, `BadRequest(código, msg)`, `Conflict(código, msg)`, `Forbidden(msg)` — mesmo uso de `GisCatalogService`/`GisQualityService` ✓, com a ressalva **M4** para recusas do banco fora do mapa de tradução.
- **Controller/rotas/papéis:** rotas completas (`/api/v1/gis/change-sets`: CRUD, itens, submit, review, approve, return, reject, cancel, rebase, locks, pending-review; `/api/v1/gis/network-at`) e `GisChangeSetRoles` no mesmo desenho de `GisCatalogRoles`/`GisValidationRoles` (`recurso:ação`, correspondência com `gis.view/edit/review` do §6.6 documentada) ✓.
- **Serviço × banco sem conflito:** o serviço falha cedo (403, transições via `GisChangeSetLifecycle`, que é espelho fiel das guardas — tabelas de transição do pacote e da feição conferidas linha a linha contra a V13_9 e contra o §3.7/§6.1, idênticas) e traduz as recusas do banco; nenhuma regra duplicada diverge da do banco. A máquina Java admite `IN_REVIEW → APPLIED` direto, como o banco — coerente com o §6.2 (aprovação aplica numa só transação; `APPROVED` é estado transitório quando há endpoint de aprovação separada, que não existe aqui — observação, não defeito).

---

## Correções necessárias (ordenadas por severidade)

### ALTO

- **A1 — Nenhuma função da V13_9 fixa o `search_path`, violando a convenção que a própria série V13 criou.** `V13_9__gis_change_sets.sql` inteiro (funções nas linhas 208, 318, 349, 375, 409, 445, 491, 579, 612, 655): nenhum `PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true)`. A V13_6 existe só porque corpo plpgsql resolve nomes no `search_path` da conexão em execução e o caminho da *app* não o fixa; a V13_8 declara no cabeçalho "toda função de gatilho começa fixando o search_path". As guardas de revisão/item/trava, `gis_apply_change_set` (lê `gis_validation_issue`, chama a função de item) e as funções `network-at` (chamam `gis_feature_at` sem qualificação) referenciam tabelas/funções sem qualificar — no caminho da app podem falhar com "relation does not exist" ou resolver no schema errado. A IT **mascara** o problema: `Gis04ChangeSetIT.legacyScratch` faz `SET LOCAL search_path = <schema>, public`. O relatório da entrega levanta o risco irmão (`current_schema()` em `gis_apply_change_set_item`, risco 4a) mas não este. **Correção:** fixar o `search_path` local no início de cada função de gatilho (padrão V13_6/V13_8) e qualificar/resolver com o schema do tenant nas demais funções, ou provar na CI, com um teste sem `search_path` de tenant na sessão, que o caminho da app resolve — um dos dois, registrado.
- **A2 — A exceção da guarda é forjável e a guarda não valida o pacote aplicante.** `gis_package_guard` (V13_9:216, 251) confia em `current_setting('app.gis_applying_change_set')` — GUC que **qualquer sessão SQL pode ligar** com `set_config` (o comentário "só a função `gis_apply_change_set` liga" é falso). Pior: a guarda só compara a GUC com `NEW.change_set_id`; não confere se o pacote existe, está em `IN_REVIEW`/`APPROVED`, nem se a feição é item dele. Quem tem escrita SQL direta (ex.: role do QGIS da GIS-10) monta um pacote `DRAFT` qualquer, liga a GUC com o id dele e grava feição em operação sem revisão — exatamente o caminho que o aceite manda o banco recusar. O aceite literal (SQL direto *sem* GUC) passa; a garantia arquitetural da decisão 3 do ADR-0034, não. **Correção:** na guarda, exigir que o pacote da GUC exista, pertença ao tenant da feição e esteja em estado aplicável (e, para `UPDATE`/`RETIRE`, que exista item daquele pacote para a feição); registrar a limitação residual da GUC se ela for mantida.
- **A3 — O 403 da API não tem nenhum teste** (detalhe no item 3 acima). **Correção:** acrescentar teste que exercite `approve`/`return`/`reject` pelo serviço ou pela API com autor = revisor esperando 403 (a IT atual só prova o banco; a unidade só a função pura).

### MÉDIO

- **M1 — `gis_apply_change_set_item` pode aplicar atributos em silêncio pela metade.** (V13_9:445–489) O `UPDATE` é montado de `information_schema.columns` filtrado por `current_schema()`: se o `search_path` da sessão de aplicação divergir (ver A1), `v_sets` sai vazio e a aplicação "passa" gravando só `version + 1` e geometria — sem erro. Chaves de `proposed_state` que não são colunas são ignoradas em silêncio (erro de digitação vira no-op). Os casts `($1 ->> col)::udt_name` são frágeis para tipos fora do trivial (ex.: domínios/`varchar(n)`). **Correção:** falhar se alguma chave proposta não casar com coluna aplicável; resolver o schema pelo schema das tabelas do tenant, não por `current_schema()`.
- **M2 — `network-at` por data de negócio: caminho primário sem teste e fallback além da spec.** (detalhe no item 8) **Correção:** IT com massa cujo tempo de registro anteceda a data consultada (exercitar `gis_feature_at` no modo negócio) e registro formal do fallback retroativo como decisão (ADR ou nota no documento GIS), já que ele define atributos que a spec não define.
- **M3 — Trava por área: ramos sem prova e criação sem autor.** A IT não exercita a recusa de **segunda trava sobreposta** (`gis_edit_lock_guard`, V13_9:420–429) nem trava sobre feição de `gis_structure` por `geom_point`. No serviço, `createLock` (`GisChangeSetService.java:357`) só confere que o pacote existe — qualquer usuário com papel `EDIT` trava a área de pacote alheio (os demais métodos de escrita usam `ensureAuthor`). **Correção:** teste de overlap de travas; `ensureAuthor` (ou justificativa registrada) em `createLock`/`releaseLock`.
- **M4 — Recusas do banco fora do mapa de tradução viram 500.** `translateApplyError` (`GisChangeSetService.java:481`) só reconhece 6 padrões de mensagem; fora deles devolve a `DataAccessException` crua. Casos concretos: `decide()` sobre pacote em `DRAFT` (a guarda de revisão recusa → 500 em vez de 409); `create()` com `type`/`channel` fora dos CHECKs (→ 500 em vez de 400 — o serviço nem valida os domínios); `createLock` com `expires_at` no passado (CHECK `expires_at > created_at` → 500). **Correção:** validar domínios no serviço e mapear as demais recusas nomeadas (`gis: ...`) para 409/400.
- **M5 — Exceção de rebase da guarda de item é mais larga que o rebase.** `gis_change_set_item_guard` (V13_9:375–403) não compara `change_set_id` na exceção: um `UPDATE` em pacote `SUBMITTED`/`IN_REVIEW` pode **mover o item para outro pacote** (e alterar `validation_result`) mantendo os campos comparados. Além disso, item e revisão não têm guarda de `DELETE`: por SQL direto apaga-se item de pacote `APPLIED` e apaga-se revisão, furando a "imutabilidade do pacote aplicado" que o comentário da guarda promete (a trava é a exceção legítima: `DELETE` é o caminho de liberação). **Correção:** incluir `change_set_id` (e `validation_result`) na comparação da exceção; triggers de `DELETE` recusando item/revisão fora de pacote aberto, se a imutabilidade for a regra.
- **M6 — Frontend: três defeitos não declarados.** `ChangeSetReview.tsx`: (a) o botão **Rejeitar** é `type="submit"` dentro do formulário de devolução e ainda tem `onClick={onReject}` (linhas 169–173) — dupla fiação frágil: depende de `preventDefault` no clique para não submeter como devolução; (b) `reviewable` inclui `SUBMITTED` (linha 75), mas `gis_apply_change_set` só aceita `IN_REVIEW`/`APPROVED` — nesse estado o botão "Aprovar e aplicar" sempre falha (e cai no 500 de M4); (c) na tabela antes/depois, a linha "geometria (WKT)" mostra **sempre "—" no Antes** (linhas ~215–221), embora o `base_snapshot` traga a geometria — o antes/depois tabular de geometria está incompleto e isso não está nas pendências declaradas. **Correção:** separar os formulários/botões; alinhar `reviewable` aos estados que cada ação aceita; exibir a geometria do `before`.

### BAIXO

- **B1 — Contagem errada no relatório da entrega:** `RELATORIO-P2.22.md` diz "11 testes" na `Gis04ChangeSetIT`; o arquivo tem **12** `@Test`. Só documentação.
- **B2 — Lacunas de cobertura da IT (sem defeito de código apontado):** guarda provada só em `gis_node` (INSERT `UNDER_CONSTRUCTION`, as outras 5 tabelas, transição de ciclo inválida da feição — ex.: `ABANDONED → IN_SERVICE` via pacote — e DELETE de `PLANNED` de pacote **aprovado** não são exercitados; a máquina de ciclo só é provada na unidade Java e, no banco, via salto de pacote). Reforçar quando a CI estiver verde.
- **B3 — Cliente frontend diverge do padrão de erro do projeto:** `changeSets.ts:request()` devolve `response.data` sem converter a falha com `toApiError` (o cliente do registry, padrão das demais telas, converte); a página depende de `errorMessage()` tolerar o erro cru do axios. Uniformizar com o padrão.

---

## O que está correto e **não deve ser mexido**

1. **A numeração V13_9** e a justificativa documentada (MCP + precedente P2.21) — não renumerar, não "voltar para V13_4".
2. **A atomicidade de `gis_apply_change_set`** — função na transação do chamador, sem commit interno nem captura de exceção; o desenho e a IT de rollback estão certos.
3. **A comparação de rebase** (`version FOR UPDATE` × `base_version`) e a exceção estreita de propósito no serviço (só base muda; proposta só em `DRAFT`/`RETURNED`).
4. **As três recusas independentes de auto-revisão no banco** (aplicação, INSERT de revisão, transição do pacote) somadas ao 403 cedo na API com identidade do contexto — o desenho em camadas está certo; falta só o teste da camada HTTP (A3).
5. **A trava geométrica com `ST_Intersects` + expiração por `clock_timestamp()`** nos dois pontos (criação da trava e gravação da feição) — não trocar por trava por id nem por `now()`.
6. **As máquinas de estado** do pacote e do ciclo de vida, idênticas entre V13_9, `GisChangeSetLifecycle` e §3.7/§6.1 — incluindo `IN_REVIEW → APPLIED` direto, coerente com a aprovação-que-aplica do §6.2.
7. **O portão de validação na aplicação** (ERROR aberto bloqueia; WARNING aberto exige justificativa) dentro da função do banco, valendo para qualquer canal.
8. **A fronteira com a P2.28**: view + endpoint de leitura, nenhum pedaço da PLT-13 inventado.
9. **A honestidade das pendências declaradas** (rota não fiada, mapa da GIS-06, risco do `current_schema()`): estão declaradas nos lugares certos — as correções acima são **adicionais** a elas, não substitutas.
10. **JSON exclusivamente via `Json` do core** e o padrão `ApiException`/`Roles`/`Context` herdado das frentes anteriores.

## Não verificável nesta revisão (e por quê)
- **Execução real de qualquer item** (migration aplicada, gatilhos disparando, IT verde, ordem alfabética dos gatilhos BEFORE em `gis_arc`/`gis_connection` interagindo com o snap da V13_8 — risco já levantado pelo relatório da entrega): esta VM não tem JDK/Maven/PostgreSQL. A prova é a CI total por camada no PC do dono, ainda pendente — inclusive para confirmar/refutar A1 no caminho real da app.
- **Padrão fino do frontend** (existência/assinatura de todos os componentes `@components/ui/*` citados): o espelho local da P2.22 não traz o frontend da aplicação; a conferência foi contra o padrão visível nos espelhos de outras frentes (registry) e contra os DTOs Java.
