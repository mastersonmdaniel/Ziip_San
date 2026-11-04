# Símbolos de mapa de água e esgoto (P1.30)

Fonte única e pipeline de geração dos símbolos do mapa (ADR-0036; ADR-0034,
decisão 6; `docs/arquitetura/ICONES-E-SIMBOLOGIA.md` §2.2, §3 e §6).

## Fonte única

| Arquivo | Papel |
|---|---|
| `source.json` | Um símbolo por classe e tipo do catálogo de fábrica de água e esgoto: forma por **classe** (`base`) + marca por **tipo** (`mark`), grade 32×32, traço 2, sem cor própria (`currentColor`). Tipos de linha (`PIPE`, `SERVICE_LINE`) e de zona (`BASIN`) não têm glifo de ponto: o símbolo deles é o estilo de linha/preenchimento. |
| `states.json` | Os cinco estados de exibição (ver abaixo). |
| `generated/` | Tudo que o pipeline gera. Nunca edite à mão: regenere. |

Inventário (conferido pelo teste de cobertura contra o SQL, não confiado
nesta lista): 87 tipos de fábrica — 41 da seed V13_2 (`gis01:`) + 46 do
catálogo R__ da P1.27 (`gis02:`) — sendo 73 de ponto, 12 de linha e 2 de
zona, em 20 classes, cada um nos cinco estados = 435 entradas por canal.

## Os cinco estados

| Estado | Renderização (nunca só por cor) |
|---|---|
| Em operação (`in-service`) | Traço contínuo, cor do domínio |
| Projetado (`planned`) | Tracejado `6 3` |
| Em obra (`under-construction`) | Traço e ponto `8 2 2 2` |
| Abandonado (`abandoned`) | Fino (×0,75) e tracejado `4 3`, cinza `#737373` nos canais com cor literal |
| Fora da meta de precisão (`out-of-target`) | Contínuo + halo: anel tracejado no ponto, capa (casing) na linha, hachura na zona |

O `accuracy_status` `APPROXIMATE` (cadastro aproximado, GIS-CADASTRO-TECNICO
§3.8) usa a mesma sobreposição de fora da meta e aparece na legenda como
"Fora da meta / aproximado": o plano pede cinco estados, não seis.

## Cor e contraste

A cor do domínio vem dos tokens `map-*` (`frontend/src/theme/common.json`):
água `map-water`, esgoto `map-sewer`. O SVG de base usa `currentColor`; o
renderizador aplica o token. As cópias em `generated/svg-colored/` (QGIS e
SLD, que exigem cor literal) usam os valores resolvidos dos mesmos tokens.
Contraste símbolo × mapa base claro e escuro ≥ 3:1 (WCAG, gráficos),
conferido por `mapSymbolContrast.test.ts`; o halo (branco no escuro, fundo
escuro no claro) é aplicado pelo renderizador (`icon-halo-color` no MapLibre)
e separa o símbolo do fundo quando o mapa base tem imagem.

## Canais gerados (do mesmo arquivo)

| Saída | Consumidor |
|---|---|
| `generated/sprite.json` + `sprite.png` (+ `@2x`) | MapLibre. Sprite SDF (`sdf:true`): a distância vai no canal alfa, RGB zero, mesma codificação do tiny-sdf/spritezero (raio 8 a 1x, corte 0,25, borda ≈191), para recolorir por `icon-color`/`icon-halo-color` sem novo sprite. 365 ícones (73 de ponto × 5 estados); `content` marca o glifo 32×32 dentro da célula 48×48. |
| `generated/qgis-style.xml` | Projeto QGIS (GIS-10): um `<symbol>` por tipo e estado. Os marcadores apontam para `svg-colored/light/` — copie esse diretório junto do estilo ou inclua-o nos caminhos de busca de SVG do QGIS. Cor no QGIS: como o SVG de base usa `currentColor`, a cor literal vai nas cópias; a geometria é idêntica (mesmo `originHash` no manifesto). |
| `generated/sld-light.xml`, `generated/sld-dark.xml` | WMS do GeoServer (GIS-17). A hachura de fora da meta em zona usa `shape://slash` (extensão do GeoServer). |
| `generated/style-fragment.json` | Fragmento de estilo MapLibre para o GIS-06: `iconMapping` tipo→ícone por estado, camadas de linha (com casing de halo) e de zona. Tema claro; o GIS-06 troca as cores pelos tokens `map-*` no tema escuro. A hachura de zona vira `fill-pattern` no GIS-06 (marcada como nota no fragmento). |
| `generated/legend.svg` | Legenda em SVG (tema claro, todos os tipos × estados). |
| `generated/contact-sheet.html` | Folha de contato §6: todos os símbolos nos cinco estados e em 12, 16 e 24 px, nos temas claro e escuro. É o artefato a validar. |
| `generated/manifest.json` | Índice com `originHash` por símbolo (hash da entrada da fonte), arquivos, estilos e hashes dos canais. A regressão entre canais é esta: sprite, QGIS, SLD e legenda referenciam o mesmo `originHash`; o teste confere que ele corresponde à fonte e que cada canal tem as 435 entradas. |

Regenerar: `node scripts/icons/generate-map-symbols.mjs` (só biblioteca
padrão do Node). Conferir sem escrever: o mesmo comando com `--check`
(recusa diferença — é o que o CI deve rodar após qualquer edição da fonte).

## Domínio por tipo: overrides e ambiguidades

Domínio do tipo = domínio da classe, exceto 6 overrides em que o próprio
nome do tipo afirma o esgoto (registrados em `source.json.domainOverrides`
e travados pelo teste): `GRAVITY_SEWER`, `FORCE_MAIN`, `INVERTED_SIPHON`,
`SEWAGE_LIFT_STATION`, `WWTP`, `SEWER_SERVICE_LINE` → `SEWER`. Três tipos
ficam no domínio da classe até a curadoria decidir, sem invenção:
`SLUDGE_PIPELINE` (lododuto pode vir de ETA ou ETE), `UTS` (tratamento
simplificado pode ser de água ou esgoto) e `PRECONDITIONING_STATION`
(pré-condicionamento idem). Mudar qualquer um é trocar um campo no
`source.json` e regenerar — sem código e sem migration.

## O que esta frente NÃO faz (fronteiras)

- **Nenhuma migration.** O plano da P1.30 não prevê migration, e nenhuma foi
  criada nem editada. Em particular, `gis_feature_type.style_key` continua
  nulo no banco: o `style_key` desta frente vive no manifesto
  (`sanitation:<classe>:<tipo>`, ex.: `sanitation:valve:gate-valve`). Persistir
  o `style_key` no catálogo é trabalho da integração/GIS-06 (P3.25), que
  escolhe o veículo (migration versionada ou R__); esta frente não escolheu
  versão nenhuma.
- **Nenhuma rota nova.** A publicação do sprite e dos estilos pela API é do
  GIS-06 (P3.25); a P1.30 entrega os arquivos e o fragmento de estilo.
- **Nenhum aceite concedido.** O aceite do dono é critério pendente do dono:
  está registrado como `PENDENTE` no manifesto, na legenda e na folha de
  contato. Ajuste de forma depois do aceite exige novo aceite (§6).
- **Tipo do tenant sem símbolo próprio** usa o glifo da classe (fallback
  documentado no manifesto); o teste de cobertura recusa tipo de **fábrica**
  ativo sem símbolo — tipo de tenant novo entra no inventário do teste
  quando for criado, e o fallback impede que ele fique sem representação.
- **Renderização ao vivo** no MapLibre, no QGIS e no GeoServer não foi
  provada aqui (não há mapa publicado nesta frente): o que está provado por
  máquina é geometria idêntica entre canais, SDF válido, XML bem-formado e
  cobertura total. A prova de renderização é do GIS-06/GIS-10/GIS-17.
