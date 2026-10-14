# ADR-0036 — Ícones das telas e dos itens e símbolos do mapa

- **Status:** Aceito, 14/10/2026
- **Decisor:** dono do produto (cartões de 14/10/2026)
- **Afeta:** ADR-0016 (pacote de marca: ícones ficam fora do pacote de marca e dentro do pacote de vertical, ADR-0037);
  ADR-0017 (tokens `map-*` e `icon-*`, contraste); ADR-0034 (decisão 6, simbologia, e `gis_style`); ADR-0035 (SLD do
  GeoServer); ADR-0037 (verticais); `docs/arquitetura/ICONES-E-SIMBOLOGIA.md`; `GIS-CADASTRO-TECNICO.md` §10.2; plano
  v2.13 (P0.46, P1.29, P1.30, GIS-06, ED-64, ED-65)

## Contexto

O frontend usa 25 ícones do `lucide-react` (versão 0.303, importados um a um nos componentes) e nenhum símbolo próprio.
O catálogo de fábrica do GIS (ADR-0034) prevê dezenas de tipos de peça e estrutura (curvas, tês, registros, ventosas,
hidrantes, poços de visita, bocas de lobo, contêineres, estações), cada um com estados (em operação, projetado, em obra,
abandonado, fora da meta de precisão). Nada disso tem representação gráfica definida, e o mesmo símbolo precisa
aparecer, igual, no mapa web, no projeto QGIS, no WMS do GeoServer e nos PDFs. O dono do produto também pediu que o
produto possa ter outras verticais (ADR-0037), cada uma com os seus ícones e símbolos.

## Decisão

1. **Ícones de interface (telas, menus, ações, estados): Lucide mais ícones próprios.** O Lucide continua como base
   (licença ISC, versão fixada, atualização deliberada). O que ele não cobre (saneamento, GIS, fiscal, cobrança) é
   desenhado como ícone próprio, no mesmo estilo (grade 24 px, traço de 2 px, pontas e junções arredondadas, `currentColor`).
2. **Símbolos do mapa: conjunto SVG próprio, de fonte única.** Um diretório de SVG por pacote de vertical gera, no build,
   o *sprite* do MapLibre, os símbolos do projeto QGIS, o SLD do GeoServer e a legenda dos PDFs. Cada tipo do catálogo
   de fábrica referencia o símbolo por `style_key`; o estado se expressa por forma, traço e sobreposição, nunca só por
   cor (ADR-0034, decisão 6).
3. **Criação:** os SVG são gerados no desenvolvimento, seguindo o guia de estilo de `ICONES-E-SIMBOLOGIA.md`, e o dono
   do produto valida o conjunto antes de cada fase que o consome. Não há designer externo na linha do plano.
4. **Pacote de ícones e símbolos por vertical:** o núcleo da plataforma traz ícones genéricos (navegação, ação, estado,
   documentos); cada pacote de vertical traz os seus ícones de entidade e os símbolos de mapa (ADR-0037).
5. **Verificação no CI:** SVG sanitizado (sem `script`, `foreignObject`, `on*` nem referência externa, a mesma regra do
   ADR-0016); nome único e tipado; ícone de interface sem cor literal (só `currentColor` e tokens); símbolo de mapa só
   com os tokens `map-*`; cobertura: todo tipo ativo do catálogo de fábrica tem símbolo para todos os estados, e toda
   rota e item de menu tem ícone; sprite e SLD gerados a partir do mesmo SVG têm o mesmo hash de origem.
6. **Personalização pelo tenant:** o tenant escolhe, para cada tipo e estado, cor, espessura, padrão de traço e um
   símbolo **da biblioteca do pacote**; não envia SVG próprio (a mesma razão do logo: ADR-0016, §3.2 de MARCA-E-TEMAS).
   Upload de símbolo próprio só entra com sanitização por lista permitida, em decisão futura.
7. **Acessibilidade:** ícone sozinho tem nome acessível vindo do dicionário i18n; ícone decorativo é `aria-hidden`; o
   significado de estado nunca depende só de cor; tamanho mínimo legível de 16 px para ícone de interface e de 12 px
   para símbolo de mapa.
8. **Atribuição:** a licença do Lucide entra na lista de componentes de terceiros da tela Sobre (ADR-0021).

## Consequências

### Positivas

- Um símbolo, quatro canais: o mapa não diverge entre web, QGIS, WMS e PDF.
- Nova vertical traz o seu conjunto sem alterar o núcleo.
- Contraste, cobertura e sanitização conferidos por máquina.

### Negativas

- O conjunto próprio dá trabalho: dezenas de símbolos com cinco estados cada, mais os de drenagem e resíduos na onda B.
- O pipeline de geração (SVG para sprite SDF, SLD e QGIS) é uma peça nova de build, com prova de conceito antes de
  se firmar (P1.30).
- O dono precisa validar visualmente cada lote.

## Alternativas consideradas

- Só Lucide: não cobre saneamento e o cadastro técnico.
- Conjunto 100% próprio para a interface: custo sem ganho; o Lucide já cobre ações e navegação.
- Conjunto aberto adaptado (Maki, OSM) para o mapa: cobre pouco do cadastro técnico e mistura estilos.
- Designer externo: preterido; fica registrado como possibilidade se o dono preferir ao validar o primeiro lote.

## Tarefas derivadas

- P0.46: guia de estilo, pacote de ícones da interface e verificações do CI.
- P1.29: conjunto inicial de ícones de telas e itens, validado pelo dono.
- P1.30: símbolos de mapa de água e esgoto e pipeline de geração para os quatro canais.
- GIS-06 (P3.25): sprite e estilos publicados pela `api` com os símbolos.
- ED-64 e ED-65 (onda B): símbolos de drenagem e de resíduos.
