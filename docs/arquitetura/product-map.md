# Mapa do produto — pacote sanitation (gerado, P0.47)

> Gerado por `scripts/generate-product-map.py` a partir de
> `packages/sanitation/vertical.json`. Não edite à mão: regenere.
> Status: `active` = rota e menu no produto; `declared` = módulo existe,
> sem rota/menu até esta onda; `skeleton` = esqueleto (fiscal, P0.32).
> Permissão `null` = sem permissão catalogada na P0.13 (não inventada).

Pacote: **sanitation** — versão 0.1.0 (plataforma mínima 1.0.0-SNAPSHOT)

| Módulo | Nome | Status | Módulo Maven | Permissão | Tarefa de origem |
|---|---|---|---|---|---|
| registry | Cadastro comercial | active | `commercial/ziipsan-registry` | `registry:read` | P0.13/P0.16 |
| service-order | Ordens de serviço | declared | `shared/ziipsan-core` | `— (sem permissão catalogada)` | P1.31 |
| metering | Medição | declared | `operations/ziipsan-metering` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| billing | Faturamento | declared | `commercial/ziipsan-billing` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| collection | Arrecadação | declared | `commercial/ziipsan-collection` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| fiscal | Fiscal | skeleton | `commercial/ziipsan-fiscal` | `— (sem permissão catalogada)` | P0.32 |
| gis | Cadastro técnico / GIS | declared | `intelligence/ziipsan-gis` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| quality | Qualidade da água | declared | `intelligence/ziipsan-quality` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| losses | Perdas | declared | `intelligence/ziipsan-losses` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| mtr | Resíduos / MTR | declared | `grs/ziipsan-mtr` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| lgpd | Privacidade / LGPD | declared | `compliance/ziipsan-lgpd` | `— (sem permissão catalogada)` | P0 (módulo existente, sem rota HTTP nem menu até esta onda) |
| certification | Certificações de qualidade | declared | `core (motor, P1.32) + packages/sanitation (catálogo, P1.34)` | `cert:read` | P1.32 (motor no núcleo, conteúdo de saneamento no pacote — ADR-0041) |
| support | Suporte | active | `shared/ziipsan-core` | `support:read` | P1.23 |

## Cadastro comercial (`registry`) <!-- module: registry -->

- Status: `active` · módulo Maven: `commercial/ziipsan-registry`
- Permissão: `registry:read`
- Tarefa de origem: P0.13/P0.16
- Itens de menu:
  - Imóveis — rota `/cadastro/imoveis`, ícone `building`, permissão `registry:read`
  - Clientes — rota `/cadastro/clientes`, ícone `users`, permissão `registry:read`
  - Hierarquia territorial — rota `/cadastro/territorio`, ícone `map`, permissão `registry:read`
  - Catálogos — rota `/cadastro/catalogos`, ícone `list-checks`, permissão `registry:read`
- Rotas de API:
  - `/api/v1/registry/consumer-units`
  - `/api/v1/registry/customers`
  - `/api/v1/registry/categories`
  - `/api/v1/registry/subcategories`
  - `/api/v1/registry/water-statuses`
  - `/api/v1/registry/sewer-statuses`
- Abas da ficha da unidade: nenhuma.

## Ordens de serviço (`service-order`) <!-- module: service-order -->

- Status: `declared` · módulo Maven: `shared/ziipsan-core`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P1.31
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: `service-orders`

## Medição (`metering`) <!-- module: metering -->

- Status: `declared` · módulo Maven: `operations/ziipsan-metering`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: nenhuma.

## Faturamento (`billing`) <!-- module: billing -->

- Status: `declared` · módulo Maven: `commercial/ziipsan-billing`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: `bills`

## Arrecadação (`collection`) <!-- module: collection -->

- Status: `declared` · módulo Maven: `commercial/ziipsan-collection`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: `debts`

## Fiscal (`fiscal`) <!-- module: fiscal -->

- Status: `skeleton` · módulo Maven: `commercial/ziipsan-fiscal`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0.32
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: nenhuma.

## Cadastro técnico / GIS (`gis`) <!-- module: gis -->

- Status: `declared` · módulo Maven: `intelligence/ziipsan-gis`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu:
  - Catálogo GIS — rota `/gis/catalogo`, ícone `map`, permissão `gis:catalog-read`
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: nenhuma.

## Qualidade da água (`quality`) <!-- module: quality -->

- Status: `declared` · módulo Maven: `intelligence/ziipsan-quality`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: `quality`

## Perdas (`losses`) <!-- module: losses -->

- Status: `declared` · módulo Maven: `intelligence/ziipsan-losses`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: nenhuma.

## Resíduos / MTR (`mtr`) <!-- module: mtr -->

- Status: `declared` · módulo Maven: `grs/ziipsan-mtr`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: `documents`

## Privacidade / LGPD (`lgpd`) <!-- module: lgpd -->

- Status: `declared` · módulo Maven: `compliance/ziipsan-lgpd`
- Permissão: `— (sem permissão catalogada)`
- Tarefa de origem: P0 (módulo existente, sem rota HTTP nem menu até esta onda)
- Itens de menu: nenhum até esta onda.
- Rotas de API: nenhuma até esta onda.
- Abas da ficha da unidade: nenhuma.

## Certificações de qualidade (`certification`) <!-- module: certification -->

- Status: `declared` · módulo Maven: `core (motor, P1.32) + packages/sanitation (catálogo, P1.34)`
- Permissão: `cert:read`
- Tarefa de origem: P1.32 (motor no núcleo, conteúdo de saneamento no pacote — ADR-0041)
- Itens de menu: nenhum até esta onda.
- Rotas de API:
  - `/api/v1/certification/standards`
  - `/api/v1/certification/activations`
  - `/api/v1/certification/clause-map`
- Abas da ficha da unidade: nenhuma.

## Suporte (`support`) <!-- module: support -->

- Status: `active` · módulo Maven: `shared/ziipsan-core`
- Permissão: `support:read`
- Tarefa de origem: P1.23
- Itens de menu: nenhum até esta onda.
- Rotas de API:
  - `/api/v1/support/tickets`
  - `/api/v1/platform/support/queue`
- Abas da ficha da unidade: nenhuma.

## Domínios do GIS (declarados; carga na P1.26, V13_1)

- `WATER` — Água, cor `map-water`, ordem 1
- `SEWER` — Esgoto, cor `map-sewer`, ordem 2
- `STORM` — Drenagem, cor `map-storm`, ordem 3
- `WASTE` — Resíduos, cor `map-waste`, ordem 4
- `CONTEXT` — Contexto, cor `map-context`, ordem 5

## Regra das abas da ficha da unidade (P0.47)

Cada tarefa que cria uma fonte da ficha da unidade (contas, débitos, OS, atendimentos, documentos, qualidade) acrescenta a aba correspondente ao manifesto do pacote (campo unitSheetTabs do módulo). A aplicação da regra no frontend é da P1.12, não desta onda. Abas já refletidas aqui: contas (billing), débitos (collection), OS (service-order, P1.31), documentos (mtr), qualidade (quality). O registry fornece a própria ficha, não uma aba. Atendimentos ainda não têm módulo nem aba — entram com a tarefa que os criar (P1.36).
