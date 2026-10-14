# Ícones das telas e dos itens e símbolos do mapa

Desenho do conjunto de ícones da interface e dos símbolos do mapa. Decisões em [ADR-0036](adr/ADR-0036-icones-e-simbologia-do-mapa.md);
verticais em [ADR-0037](adr/ADR-0037-plataforma-com-pacotes-de-vertical.md). Estado em 14/10/2026: nada implementado; o
frontend usa 25 ícones do `lucide-react` 0.303 e nenhum símbolo próprio.

## 1. Escopo

| Conjunto | Onde aparece | Base | Pacote |
|---|---|---|---|
| Ícones de interface | menu, cabeçalho, botões, tabelas, estados vazios, alertas, chips de status | Lucide e próprios | núcleo e pacote de vertical |
| Ícones de entidade | cliente, imóvel, ligação, hidrômetro, fatura, ordem de serviço, almoxarifado, chamado | próprios, no estilo do Lucide | pacote de vertical |
| Símbolos de mapa | mapa web, projeto QGIS, WMS do GeoServer, PDFs, legenda | próprios | pacote de vertical |
| Ícones de marca | logo, favicon, ícones de aplicativo | pacote de marca (ADR-0016) | fora deste documento |

## 2. Guia de estilo

### 2.1 Ícones de interface

- Grade 24 × 24, área útil de 20 × 20, traço de 2 px, pontas e junções arredondadas, sem preenchimento, exceto um
  detalhe de ênfase.
- Cor só por `currentColor`; os tokens `icon-*` (ADR-0017) definem a cor por contexto e por tema.
- Nome em inglês, minúsculas com hífen, com o pacote como prefixo quando não for do núcleo: `core-search`,
  `sanitation-water-meter`.
- Uma metáfora por ícone; sem texto dentro do ícone; sem referência a marca de terceiros.
- Tamanhos: 16, 20 e 24 px, conferidos em cada um.

### 2.2 Símbolos de mapa

- Grade 32 × 32, glifo monocromático legível a 12 px, com contorno de contraste (halo) aplicado pelo renderizador, não
  desenhado no SVG.
- Forma própria por **classe** de peça (válvula, hidrante, ventosa, poço de visita, boca de lobo, contêiner), de modo
  que o tipo seja reconhecido sem legenda; variação de **tipo** dentro da classe por marca interna ou sobreposição
  (por exemplo, registro de gaveta, borboleta e esfera na mesma forma de válvula, com marca diferente).
- Cor do **domínio** (água, esgoto, drenagem, resíduos) vinda dos tokens `map-*`, aplicada pelo renderizador; o SVG
  não carrega cor própria.
- **Estado por forma e traço, nunca só por cor**: em operação (contínuo), projetado (tracejado), em obra (traço e
  ponto), abandonado (cinza, fino e tracejado), fora da meta de precisão (halo ou hachura), aproximado (marcador na
  legenda). Cada estado tem sobreposição própria no mesmo símbolo.
- Rotação: peças direcionais (válvula de retenção, redução, caixa de passagem com sentido) giram com o arco.
- Linhas (arcos) são estilo, não símbolo: cor do domínio, espessura por classe de DN e padrão de traço por estado
  (GIS-CADASTRO-TECNICO §10.2).

## 3. Fonte única e geração

```
packages/<vertical>/icons/ui/*.svg          ícones de interface
packages/<vertical>/icons/map/<class>/*.svg  símbolos de mapa (glifo + variações de tipo)
packages/<vertical>/icons/map/states.json    sobreposições por estado
```

O build gera, de uma só origem:

| Saída | Consumidor | Observação |
|---|---|---|
| `IconRegistry` tipado (TypeScript) e componente `<Icon name>` | frontend | nome desconhecido é erro de compilação |
| *Sprite* MapLibre (SDF, recolorido por `icon-color`) | mapa web (GIS-06) | claro, escuro e padrões de cor sem novo sprite |
| SVG parametrizado de preenchimento | projeto QGIS gerado (GIS-10) | |
| SLD com `ExternalGraphic` | WMS do GeoServer (GIS-17) | símbolo igual ao da web |
| SVG para PDF e legenda | exportação de mapa (§10.2) | |

Prova de conceito na P1.30: o mesmo SVG dá o mesmo símbolo nos quatro canais, com teste de regressão visual. Se o SDF
não reproduzir um símbolo com detalhe interno, o símbolo usa a variante de duas camadas (glifo e marca), documentada.

## 4. Inventário inicial

### 4.1 Interface (núcleo da plataforma)

Navegação (início, cadastro, faturamento, medição, arrecadação, ordens de serviço, almoxarifado, GIS, relatórios,
chamados, ajuda, configurações, Central Ziip, Sobre); ações (criar, editar, excluir, salvar, cancelar, pesquisar,
filtrar, exportar, importar, baixar, imprimir, anexar, aprovar, recusar, reabrir); estados (sucesso, aviso, erro,
informação, carregando, bloqueado, pendente); sistema (tema claro, escuro, sistema, idioma, usuário, sair, notificações,
ajuda contextual). Cobertura: toda rota e todo item de menu têm ícone (teste de CI). O ponto de partida são os 25
ícones do Lucide hoje em uso, mantidos.

### 4.2 Interface do pacote de saneamento

Cliente, imóvel, ligação de água, ligação de esgoto, hidrômetro, leitura, rota de leitura, fatura, conta, pagamento,
débito automático, nota fiscal (PDF e XML), ordem de serviço, material, requisição de material, reserva, baixa de
estoque, inventário, veículo, equipe de campo, vazamento, manobra, qualidade da água, amostra, resíduo (MTR), chamado,
contrato, portal do consumidor. Os que o Lucide cobre usam o Lucide; os demais são próprios.

### 4.3 Símbolos de mapa do pacote de saneamento

Por domínio, uma forma por classe e uma marca por tipo, na mesma lista do catálogo de fábrica
(GIS-CADASTRO-TECNICO §4):

| Domínio | Famílias de símbolo | Entrega |
|---|---|---|
| Água | registros (gaveta, borboleta, esfera, retenção, redutora de pressão, descarga, ventosa), hidrante, macromedidor, hidrômetro, reservatório, estação elevatória, estação de tratamento, poço, captação, nascente, conexões (curva por ângulo, tê, cruzeta, redução, cap, flange, luva), ponto de monitoramento, ponto de proteção catódica | P1.30 |
| Esgoto | poço de visita, tubo de inspeção e limpeza, terminal de limpeza, caixa de gordura, estação elevatória, estação de tratamento, sifão, vertedor, ligação | P1.30 |
| Drenagem | boca de lobo, poço de visita pluvial, caixa de passagem, bueiro, dissipador, bacia de detenção, exutório | ED-64 |
| Resíduos | contêiner, ponto de entrega voluntária, ecoponto, lixeira, ponto de coleta, descarte irregular, unidade de triagem, aterro | ED-65 |
| Contexto e comercial | imóvel, logradouro, quadra, marco geodésico, ponto de interesse, problema de validação, ordem de serviço no mapa | P3.25 |

Cinco estados por símbolo (em operação, projetado, em obra, abandonado, fora da meta). O total de arquivos é o produto
das famílias pelos tipos ativos do catálogo; o teste de cobertura do CI é o inventário oficial.

## 5. Personalização pelo tenant

O tenant ajusta, por tipo e estado, cor, espessura, traço e escolhe o símbolo entre os da biblioteca do pacote
(`gis_style`, V13_6). Não envia SVG. O padrão Ziip é restaurável a qualquer momento. A cor do tenant na marca
(ADR-0016) não afeta o mapa.

## 6. Validação pelo dono do produto

Cada lote (interface P1.29, água e esgoto P1.30, drenagem e resíduos nas ED-64 e ED-65) é entregue como folha de
contato, uma imagem por tema (claro e escuro, Azul Água e Verde Água) com todos os símbolos nos estados e em 12, 16 e 24
px, e legenda gerada. O aceite do dono é critério de saída da tarefa; ajuste de forma depois do aceite exige novo
aceite.

## 7. Verticais

Cada pacote traz o seu `icons/`. O núcleo não contém ícone de entidade de vertical. Para uma vertical nova, o guia de
estilo do §2 vale sem mudança, o inventário é refeito e o dono valida o primeiro lote. Símbolos de saúde ou de
segurança pública não têm inventário nem prazo (ADR-0037).

## 8. Tarefas

| Tarefa | Fase | Plano |
|---|---|---|
| Guia de estilo, `IconRegistry`, componente `<Icon>`, verificações do CI, Lucide fixado | P0 | P0.46 |
| Conjunto inicial de ícones de telas e itens, folha de contato e aceite | P1 | P1.29 |
| Símbolos de água e esgoto, pipeline para sprite, QGIS, SLD e PDF, prova de conceito | P1, com a P1.27 | P1.30 |
| Sprite e estilos publicados com os símbolos, tema claro e escuro | P3 | GIS-06 (P3.25) |
| Símbolos de drenagem e de resíduos | onda B | ED-64, ED-65 |
