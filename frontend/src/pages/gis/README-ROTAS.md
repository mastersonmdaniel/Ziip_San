# GIS-06 — fiação de rotas (integração)

As telas desta entrega são `GisMap` (rota `/gis`, §10.3) e
`GisStyles` (rota `/gis/styles`). O `App.tsx` do repo usa lazy por
rota (padrão da P1.12); a integração adiciona, junto das demais
rotas GIS:

```tsx
const GisMap = lazy(() =>
  import('@pages/gis/GisMap').then((m) => ({ default: m.GisMap })))
const GisStyles = lazy(() =>
  import('@pages/gis/GisStyles').then((m) => ({ default: m.GisStyles })))
```

e, dentro de `<Routes>` (área autenticada, como as demais telas GIS):

```tsx
<Route path="/gis" element={<GisMap theme={theme} />} />
<Route path="/gis/styles" element={<GisStyles />} />
```

Notas honestas de integração:

- `GisMap` recebe o tema por prop (`'light' | 'dark'`). O app tem
  `ThemeProvider`/`useTheme` em `@hooks/useTheme` (visto no
  `main.tsx`), mas a assinatura do hook não pôde ser conferida
  nesta tarefa (MCP em HTTP 429 após as primeiras leituras): quem
  integrar liga a prop ao hook real em vez de deixar o padrão
  `'light'`.
- A troca de tema precisa refletir nas variáveis CSS `--map-*` da
  paleta (ADR-0017): `readMapTokens` lê as variáveis do tema
  corrente no `documentElement`. Se a paleta nomear as variáveis de
  outro jeito, o ajuste é só na lista `MAP_TOKENS` de
  `services/gis/mapStyle.ts`.
- O documento de estilo gerado pelo backend NÃO declara
  `sprite` nem `glyphs` (correção pós-revisão A3): declarar
  apontava para rotas inexistentes e formato inválido. Fontes de
  glifos são ativo do design system (P0.36) e o sprite de símbolos
  é arte da P1.30: na integração, quem ligar os rótulos define
  `style.glyphs` para a fonte de glifos do app antes de criar o
  mapa, e, quando a P1.30 entregar a arte, o backend volta a
  declarar o `sprite` na rota que o controller já serve
  (`/api/v1/gis/map/symbols/sprite`, hoje um manifesto-inventário).
  Sem `glyphs`, os rótulos (camadas `-labels`) não renderizam
  texto.
- Identificação (correção pós-revisão B3): as camadas `context`
  (contexto importado) e `properties` (imóveis) não têm ficha GIS
  própria — `LAYER_TABLE` não as mapeia de propósito. O clique
  nelas mostra o aviso na tela (não é silêncio): imóvel se
  consulta no cadastro comercial e contexto é referência de
  fundo. Se um dia houver ficha de contexto, o caminho é incluir
  `gis_context_feature` na lista fechada do
  `GisMapSearchService` e no `LAYER_TABLE`.
- O centro inicial do mapa é neutro (Brasil): centrar na sede do
  tenant é fiação da integração (extensão das camadas ou parâmetro
  do tenant), não coordenada fixa nesta tela.

# Frente 5 (decisão 65) — rotas do editor e dos temáticos + rato

A integração adiciona, junto das demais rotas GIS (lazy por rota,
padrão P1.12):

```tsx
const GisWebEditor = lazy(() =>
  import('@pages/gis/GisWebEditor').then((m) => ({ default: m.GisWebEditor })))
const GisThematics = lazy(() => import('@pages/gis/GisThematics'))
```

e, dentro de `<Routes>` (área autenticada):

```tsx
<Route path="gis/editor" element={<GisWebEditor />} />
<Route path="gis/tematicos" element={<GisThematics />} />
```

Notas honestas desta frente:

- Nenhuma fonte declarava os caminhos destas duas telas: este
  README só cobria `/gis` e `/gis/styles`, e os serviços
  (`services/gis/thematic.ts`, `services/gis/webEdit.ts`) declaram
  bases de API, não rotas de página. `/gis/editor` e
  `/gis/tematicos` são **decisão de executor** pela convenção das
  rotas GIS irmãs (`/gis`, `/gis/styles`, `/gis/catalogo`,
  `/gis/change-sets/:id`), registada no `RELATORIO-GIS-ROTAS.md`
  do pacote `conclusao-front/gis-rotas`.
- `GisWebEditor` tem export nomeado (e padrão); `GisThematics`
  só tem export padrão — daí as duas formas de `lazy` acima.
- Interacção por rato (nota do dono, 10/11/2026 03:37): no
  editor, as ferramentas seleccionam o modo e a geometria faz-se
  com o rato sobre a área de desenho local da página (arrastar
  move feição/vértice, clicar define vértices, arrastar radial
  roda/escala, clicar sobre o arco divide/insere nó). O desenho
  no mapa MapLibre da GIS-06 e a gravação em pacote da GIS-04
  continuam esqueleto fora desta página (pendências nomeadas no
  relatório da frente).
