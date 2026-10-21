# Plataforma Ziip e pacotes de vertical

Desenho da separação entre o núcleo da plataforma e os pacotes de vertical (saneamento primeiro; outras verticais no
futuro). Decisão em [ADR-0037](adr/ADR-0037-plataforma-com-pacotes-de-vertical.md). Estado em 14/10/2026: nada
implementado. Só o saneamento tem escopo; saúde e segurança pública entram por decisão futura.

## 1. Objetivos e não objetivos

**Objetivos:** que uma segunda vertical entre como pacote, sem reescrever tenant, autenticação, Central, marca, suporte
nem o GIS; que a fronteira entre núcleo e pacote seja testada; que o custo de preparar agora seja pequeno.

**Não objetivos:** definir conteúdo, normas ou módulos de saúde ou de segurança pública; generalizar faturamento,
medição e arrecadação, que são de saneamento; loja de pacotes de terceiros; pacote alterável pelo cliente.

## 2. Fronteira

| Núcleo da plataforma | Pacote de vertical (`sanitation` hoje) |
|---|---|
| Tenant, isolamento, papéis, permissões, parâmetros (ADR-0002), auditoria, lote e fila, notificações | Definições de parâmetros próprias (`billing.*`, `metering.*`, `gis.hydraulic.*`) |
| Autenticação, BFF, portais (estrutura), Central Ziip, cobrança do SaaS | Módulos: cadastro comercial, medição, faturamento, arrecadação, OS e almoxarifado de saneamento, fiscal |
| Marca, temas, ícones genéricos, Ajuda, Suporte, Sobre e changelog | Conteúdo de ajuda, ícones de entidade, símbolos de mapa |
| GIS: modelo de feições, papéis topológicos, catálogo extensível, camadas, estilos, pacotes de edição, histórico, importação, OGC | Domínios (`WATER`, `SEWER`, `STORM`, `WASTE`), catálogo de fábrica, regras de conectividade, EPANET e SWMM |
| Relatórios (motor), exportação | Relatórios e camadas regulatórias (SINISA, ANA, vigilância da água) |

Módulos Maven e pastas seguem a fronteira: `platform/*` e `packages/sanitation/*` (nomes definitivos na P0.47). O pacote
depende do núcleo; o inverso é recusado pelo teste.

## 3. Manifesto

`packages/<id>/vertical.json`, validado por schema no CI (mesmo mecanismo do `brand.schema.json`, MARCA-E-TEMAS §2.2):

| Campo | Conteúdo |
|---|---|
| `id`, `version`, `minPlatformVersion` | identificação (`sanitation`), versão semântica, versão mínima do núcleo |
| `nameKey`, `descriptionKey` | chaves i18n do nome e da descrição da vertical |
| `gis.domains` | domínios (`key`, rótulo, cor `map-*`, ordem) para `gis_domain` |
| `gis.catalog` | arquivo do catálogo de fábrica (migration repetível, `R__`) |
| `icons` | pasta de ícones de interface e de símbolos de mapa (ADR-0036) |
| `modules` | módulos de negócio habilitáveis, com rotas, itens de menu, ícone, permissão exigida; `certification` (Certificações de qualidade, ADR-0041) tem motor no núcleo e catálogo de fábrica do saneamento no pacote |
| `parameters` | definições de parâmetros do tenant (classe a, b, c ou d) |
| `roles`, `permissions` | papéis e permissões do pacote |
| `help`, `releaseNotes` | conteúdo de ajuda e entrada no changelog |
| `migrations` | schemas e faixa de versões Flyway do pacote |

O núcleo lê todos os manifestos presentes na imagem ao subir; manifesto inválido ou versão do núcleo abaixo do mínimo
impede a subida (mesmo critério da marca, MARCA-E-TEMAS §2.4).

## 4. Tenant e ativação

`platform.vertical` (id, versão, estado) e `platform.tenant_vertical` (tenant, vertical, ativado em, ativado por,
estado `ACTIVE` ou `ARCHIVED`), em core `V9_7`. Regras:

- A ativação é do console da plataforma (Central Ziip, ADR-0018), registrada na auditoria e no contrato (ADR-0019).
- Menu, parâmetros, permissões e domínios do GIS resolvem pelos pacotes ativos do tenant.
- Pacote com dados no tenant só se arquiva, nunca se apaga.
- Todo tenant existente nasce com `sanitation` ativo.

## 5. GIS e domínios

O `gis_feature_class.domain` deixa de ser lista fixa: a `V13_1` cria `gis_domain` (chave, pacote, rótulo, cor, ordem) e
`domain` passa a referenciá-la. Os domínios `WATER`, `SEWER`, `STORM`, `WASTE` e `CONTEXT` são carregados pelo pacote de
saneamento. O catálogo de fábrica, os estilos padrão e os símbolos pertencem ao pacote, e o tenant continua podendo
estender o catálogo (ADR-0034). O papel topológico fixo (NODE, ARC, CONNECTION, STRUCTURE, ZONE, POI) é do núcleo e
serve a qualquer vertical; hidráulica (EPANET, SWMM) é do pacote.

## 6. Teste de fronteira

- Verificação de dependência entre módulos no build (ArchUnit e regra do Maven): núcleo não depende de pacote.
- Varredura de termos: `hidrômetro`, `ligação de água`, `esgoto`, `fatura de água` e semelhantes não aparecem em
  `platform/*` nem em textos do núcleo; lista de exceções versionada e justificada.
- O pacote `sanitation` mínimo da P0.47 carrega o manifesto e os domínios; a suíte existente passa sem alteração.
- Teste de contrato do manifesto com um pacote de exemplo (`example-vertical`, só em teste, sem conteúdo regulatório).

## 7. Marca e nome

A marca única do produto é resolvida pelo `BrandProvider` (ADR-0016). O pacote fornece o nome da vertical (por exemplo
"Saneamento") e textos próprios. O nome comercial de cada vertical e a relação com "Ziip SAN" não estão decididos
(plano §2.4); a renomeação `ziipsan` da P0.33 não muda.

## 8. Cuidados de dados

Saúde e segurança pública envolvem dado pessoal sensível ou regime legal próprio. Antes de qualquer requisito: ADR da
vertical, avaliação jurídica, classe de dados nova, retenção, base legal e trilha de acesso. Nenhum campo, tabela ou
importação dessas verticais é criado sem isso.

## 9. Tarefas

| Tarefa | Fase | Plano |
|---|---|---|
| Contrato do manifesto, registro de pacotes, `platform.vertical`, pacote `sanitation` mínimo, teste de fronteira | P0 | P0.47 |
| `gis_domain` na `V13_1` e aceite da GIS-01 ajustado | P1 | P1.26 |
| Nome comercial das verticais | decisão do dono | §2.4 |
