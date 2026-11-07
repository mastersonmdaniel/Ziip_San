# P2.23 — GIS-05: base da ligação e vínculo feição ↔ imóvel ↔ ligação ↔ ramal; ramais automáticos

**Pacote de entrega** (caminhos do repo `mastersonmdaniel/Ziip_San`, base main HEAD `0fbcd22`; nenhum push feito).
Este relatório NÃO entra no push; fica na raiz da entrega, como nos pacotes anteriores.

## Decisão de numeração das migrations (evidência)

A spec reservava `V3_7` (registry) e `V13_5` (GIS). **Os dois slots nominais estão ocupados**, conferido por
listagem MCP dos diretórios reais em 07/11/2026 (HEAD `0fbcd22`):

- `commercial/ziipsan-registry/src/main/resources/db/migration/tenant/`: existem `V3_1` a `V3_13` —
  `V3_7` é `catalog_frontend_fields`, e a série vai até `V3_13__vulnerable_proof_redaction.sql`.
  → A migration do registry desta tarefa é a primeira livre da série: **`V3_14__service_connections.sql`**.
- `intelligence/ziipsan-gis/src/main/resources/db/migration/tenant/`: no GitHub existem `V13_1`–`V13_8`
  (mais `V2_3`); `V13_5` é fix da integração P1c, `V13_8` é a GIS-03 (P2.21) e `V13_9` é a GIS-04 (P2.22,
  pacote local sobre o mesmo HEAD-base, ainda não pushado). Primeira livre depois da V13_9:
  **`V13_10__gis_service_connection_link.sql`**.

É o precedente do projeto (P2.21→V13_8, P2.22→V13_9): slot ocupado → próxima versão livre da série, sem
editar migration aplicada. Nenhuma migration existente foi tocada.

**Consequência de ordem registrada:** a V3_14 roda na cadeia ANTES de V5_1 (metering), V9_1 (OS), V13_x
(GIS) e V14_1 (staging). Por isso (i) `connection_status_history.service_order_id` nasce NOT NULL sem FK —
a FK para `service_orders` é criada na V13_10, guardada, quando a V9_1 já existe em qualquer tenant (o
mesmo desenho da V9_3); (ii) a leitura do staging para `connected_on` é guardada por `to_regclass` — em
upgrade o staging já existe e é lido; a IT prova esse caminho com a V14_1 aplicada antes da V3_14.

## Espelho e limite de leitura (HTTP 429)

O espelho via MCP baixou 204 arquivos (registry e GIS quase completos) e então o GitHub aplicou
**HTTP 429** (45 arquivos falharam, entre eles `V3_1`, `V3_6`, `GisValidationModel.java` do repo e testes
menores). Conforme a regra do projeto, **as chamadas pararam** e o restante foi suprido pelos pacotes
locais já revisados do mesmo HEAD-base: P2.21/P2.22 (validação, change sets, migrations GIS) e P2.5
(metering V5_1, core `Json`, `parameter_version_ids` JSONB). `GisValidationEngine/Service` do espelho
P2.23 foram conferidos idênticos aos da entrega P2.21 (`diff` limpo) antes de alterar as cópias.

**Sem JDK/Maven/PostgreSQL nesta VM: nada compilou nem rodou — a prova é a CI total no PC do dono, por
camada, como no restante do projeto.** Verificação estática feita aqui: V3_14 (23 comandos) e V13_10
(5 comandos) parseiam no parser real do PostgreSQL (pglast); balanceamento de chaves/parênteses de todos
os Java conferido; diffs das cópias alteradas conferidos arquivo a arquivo.

## Arquivos (27)

**Banco**
- `commercial/ziipsan-registry/.../tenant/V3_14__service_connections.sql` (novo): catálogo único
  `connection_statuses` (cópia de `water_statuses`/`sewer_statuses` preservando o id, com `kind`,
  `system_role` derivado por regra documentada e `days_to_cut` nulo — o legado não tem o dado);
  `service_connections` (âncora do imóvel, `connected_on` NOT NULL com `connected_on_source`);
  `service_connection_units`; `connection_status_history`; carga inicial das ligações e dos vínculos;
  migração do histórico com **conferência por contagem dentro da migration** (divergência aborta) e
  `DROP TABLE unit_status_history` só depois de conferir.
- `intelligence/ziipsan-gis/.../tenant/V13_10__gis_service_connection_link.sql` (novo): FK
  `gis_connection.service_connection_id → service_connections`; FK do histórico para `service_orders`;
  `gis_service_line_max_distance_m(tenant)` (espelho de `gis_snap_tolerance_m`: parâmetro APPROVED →
  factory_default → 100); `gis_generate_auto_service_lines` (ramal PLANNED em pacote, ≤ parâmetro,
  `capture_method = AUTO_SERVICE_LINE`, retorno com motivo de cada não-geração);
  `gis_active_connections_without_service_line` (leitura da validação).

**Java — registry (`commercial/ziipsan-registry`)**
- Novos: entidades `ConnectionStatus`, `ServiceConnection`, `ServiceConnectionUnit`,
  `ConnectionStatusHistory` (pacote `domain`); 4 repositórios; `application/ServiceConnectionRules`
  (âncora e `system_role`, puros), `ServiceConnectionDtos`, `ServiceConnectionService` (criar com 1:1,
  mudar situação só por OS com histórico e `parameter_version_ids` via `Json`, encerrar, ficha completa
  × restrita por `gis:view-personal`) e `web/ServiceConnectionController`
  (`/api/v1/registry/service-connections`, ficha em `/{id}/card`).
- Alterado: `application/ConsumerUnitService` (cópia do espelho com 3 trechos trocados): `recordStatus`
  grava no histórico da ligação (criando a ligação na âncora quando falta) e atualiza
  `service_connections.status_id`; a linha do tempo lê `connection_status_history`; `statusName` lê o
  catálogo único primeiro. Construtor e demais fluxos intactos (nenhum teste existente muda de forma).

**Java — GIS (`intelligence/ziipsan-gis`)**
- Alterados (cópias da P2.21, diff conferido): `validation/GisValidationModel` (+`ServiceConnectionFeature`
  e a mensagem da regra nova), `validation/GisValidationEngine` (+regra
  `ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE`, WARNING; `Snapshot` ganha o 7º componente com construtor
  de compatibilidade de 6 argumentos — testes existentes não mudam), `validation/GisValidationService`
  (carrega as ligações da área, reconcilia problemas delas e grava o problema com o envelope da área).
- Novos: pacote `connection` — `AutoServiceLinePlanner` (decisão pura + motivos, espelho da função SQL),
  `AutoServiceLineDtos`, `AutoServiceLineService` (pacote `AUTO_SERVICE_LINE`/`AUTO` via
  `GisChangeSetService`, itens INSERT propondo `IN_SERVICE`, submissão à revisão; rodada vazia cancela
  o pacote) e `connection/web/AutoServiceLineController` (`/api/v1/gis/service-lines`).

**Testes**
- `platform/ziipsan-app/.../Gis05ServiceConnectionIT.java` (novo, padrão Gis04): 9 testes — catálogo
  (ids/roles), carga 1:1 + condomínio, histórico por contagem + tabela antiga removida, grupo só de
  histórico, unicidade enquanto ativa, `connected_on` do staging, FKs da V13_10, consulta de ligação
  ativa sem ramal, ramal automático (gera a ~55 m, pula a ~1,1 km com `BEYOND_MAX_DISTANCE`, aprovação
  por outra pessoa põe o ramal em operação e a validação some).
- Unitários: `ServiceConnectionRulesTest` (9), `AutoServiceLinePlannerTest` (7),
  `GisValidationEngineServiceConnectionTest` (6). Nenhuma asserção existente foi afrouxada ou alterada.

## Cobertura do acceptance, item a item

| Item | Estado |
|---|---|
| `service_connections` 1:1 com o imóvel físico (lote) por tipo enquanto ativa | ✅ índice parcial único `(tenant, property_unit_id, kind) WHERE ended_on IS NULL`; âncora = unidade-mãe em `CONDOMINIUM_MACRO`, senão a própria unidade (glossário §8: imóvel ≠ unidade; no modelo vigente o lote é ancorado assim — não há tabela de lotes no repo). Teste IT dos dois sentidos (recusa e nova após encerramento) |
| Várias unidades consumidoras por ligação em condomínio | ✅ `service_connection_units`; carga inicial e serviço ligam âncora + filhas MACRO; teste IT (2 unidades na mesma ligação) |
| Sem mudança no ciclo da P2.5 (faturamento verde) | ✅ nada de billing é tocado; as colunas `water_status_id`/`sewer_status_id` da unidade **permanecem** como projeção (o §3.11 manda o faturamento ler o imóvel até a ED-13) e são mantidas pelo fluxo de mudança de situação — a fonte da escrita passa a ser a ligação |
| Situação movida para a ligação; catálogo único `connection_statuses` com `kind`, `system_role`, `days_to_cut` | ✅ ligação carrega `status_id`; catálogo funde os dois antigos **preservando ids** (histórico e projeção continuam válidos sem tradução); `system_role` por regra determinística das flags (ACTIVE/CUT/SUSPENDED/DISCONNECTED/NO_CONNECTION/INACTIVE — tabela no cabeçalho da V3_14 e espelhada em `ServiceConnectionRules`, testada dos dois lados); `days_to_cut` nasce NULL (sem dado no legado; preenchimento é do tenant) |
| `connection_status_history` (anterior/nova, momento, `service_order_id` obrigatório, `parameter_version_ids`) substitui `unit_status_history`, migração conferida por contagem (DAD-09) | ✅ tabela nova com os campos da spec (`parameter_version_ids` JSONB array, como na conta da P2.5); migração linha a linha com proveniência (`consumer_unit_id`) e `DO` block que **aborta a migration se a contagem divergir**; grupo que só existe no histórico ganha ligação pela última situação (nenhuma linha órfã); `DROP` da antiga só após conferir; `ConsumerUnitService` reroteado. FK da OS chega na V13_10 (ordem da cadeia) — teste IT prova existência e enforcement |
| `connected_on` obrigatório, preenchido pela carga do legado que a P1.2 leva ao staging (DAD-10) | ✅ NOT NULL + `connected_on_source` (proveniência nunca silenciosa): staging `APPLIED` da âncora (`LEGACY_STAGING`, guardado — a coluna já existe na V14_1 justamente para isto) → 1ª mudança do histórico (`STATUS_HISTORY`) → criação da unidade (`UNIT_REGISTRATION`); api exige a data na criação. Testes IT dos caminhos staging e histórico |
| Ramal sem ligação e ligação ativa sem ramal viram problema de validação | ✅ ramal sem ligação **já era** a regra `CONNECTION_WITHOUT_LINK` da GIS-03 (confirmada no motor real) — nada duplicado; nova regra `ACTIVE_CONNECTION_WITHOUT_SERVICE_LINE` (WARNING, §5.4) no motor + carga no serviço por área suja + função SQL de varredura; testes unitários do motor e IT da consulta |
| Ramal automático até `gis.service_line_max_distance_m` (padrão 100 m) em pacote revisado, `capture_method = AUTO_SERVICE_LINE` | ✅ função PostGIS (arco em operação do domínio/função certos: `WATER_DISTRIBUTION`/`SEWER_NETWORK`; ponto do imóvel = coordenadas da unidade ou do endereço normalizado) + serviço que cria o pacote `AUTO_SERVICE_LINE`, itens INSERT e **submete à revisão** (aprovação é a da GIS-04, por outra pessoa); fora da distância/sem ponto/sem arco volta com motivo, nunca some. Teste IT de ponta a ponta com aplicação do pacote |
| Ficha com ligação, imóvel e hidrômetro vigente; sem `gis.view-personal`, só identificador e situação | ✅ `GET /{id}/card`: completa = ligação + imóvel (matrícula) + endereço normalizado/geocodificação da P1.3 (`addresses` + `geocode_precision` + ponto) + unidades atendidas + hidrômetro vigente (`meter_installations` com `removed_on` nulo, via unidades) + ramais; a decisão é pelas autoridades do token (`gis:view-personal` → `gis:view-personal` recurso:ação, padrão do `PersonalDataAccessService`), nunca por parâmetro; restrita = id, tipo, código/nome e papel da situação |
| Usa endereço normalizado e geocodificação da P1.3 (CAD-83) | ✅ ficha e geração de ramal leem `consumer_units.address_id → addresses` (campos normalizados, `municipality_ibge`, `geocode_precision`, `coordinates`) e `consumer_units.coordinates` |
| ED-13 completa sobre esta base; ligação obrigatória e cobrança por disponibilidade são da ED-88 | 🔗 fronteiras respeitadas: nada de ED-13/ED-88 foi antecipado (sem catálogos técnicos novos, sem troca da leitura do ciclo, sem cobrança por disponibilidade) |

## Pendências e riscos (ordenados)

1. **CI no PC do dono é a prova.** Riscos estáticos principais: (a) a IT assume os nomes exatos das
   migrations do registry (`V3_1`, `V3_6` entre elas) e que `register_tenant_table` dá default às
   colunas de auditoria/`version` nas inserções diretas — mesmo padrão das ITs GIS anteriores, mas a
   cadeia V3 completa nunca rodou numa IT deste pacote; (b) a V3_14 lê `tenant_schema`/`version` de
   `unit_status_history` e dos catálogos (mapeados pelas entidades JPA; a DDL da V3_1 não foi lida —
   429); (c) o operador KNN `<->` sobre `geography` na geração (PostGIS 3.x) e o snap da V13_8 sobre o
   ramal PLANNED inserido pela função merecem o olhar da IT real.
2. **Limpeza posterior (não feita de propósito):** `UnitStatusHistory`/`UnitStatusHistoryRepository` e os
   catálogos `water_statuses`/`sewer_statuses` (entidades/repositórios) ficam no repo sem uso novo — a
   tabela do histórico não existe mais e o campo `statusHistory` do `ConsumerUnitService` ficou sem uso
   (construtor preservado para não quebrar os testes que o montam). Remover as classes é uma limpeza
   mecânica segura depois da CI verde; as tabelas antigas de situação ficam até a ED-13 trocar a leitura.
3. **Cargas futuras do legado:** a V3_14 preenche `connected_on` do staging para unidades já aplicadas;
   o aplicador do legado (P1.2 v0/P2.10) não cria ligações ao aplicar unidades novas — o ponto de
   integração é chamar a criação da ligação (serviço/`recordStatus`) na aplicação, com o
   `connected_on` que o staging já carrega. Registrado, não inventado.
4. **Validação por área:** ligação sem ponto na unidade nem no endereço não entra no retrato da área
   suja (não há envelope que a contenha); a varredura completa é a função
   `gis_active_connections_without_service_line` da V13_10, para o painel/rotina — ligar essa varredura
   a um agendamento é integração (worker), como a pendência do publicador da GIS-03.
5. **Ficha — hidrômetro de esgoto:** `meter_installations.kind` só tem WATER/WELL no modelo vigente;
   a ficha devolve hidrômetro vigente só para ligação de água (esgoto volta a lista vazia, sem inventar).
   O vínculo direto instalação ↔ ligação (coluna em `meter_installations`) é da ED-13 (§3.11: "o
   hidrômetro se liga à ligação" — aqui a ficha resolve pelas unidades atendidas).
6. **Permissão da ficha:** `gis:view-personal` é autoridade nova no formato recurso:ação; como as demais
   permissões GIS novas, nenhum perfil de fábrica a inclui ainda (perfis são da P1.37) — hoje é
   concessão direta, e sem ela todos veem a forma restrita (fail-safe a favor da LGPD).
