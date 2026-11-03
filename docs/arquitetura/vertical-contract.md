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
