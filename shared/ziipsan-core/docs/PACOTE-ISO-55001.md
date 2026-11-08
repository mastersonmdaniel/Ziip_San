# Pacote ISO 55001 — gestão de ativos (ED-72)

Módulo `certification` (core, `shared/ziipsan-core`), subpacote
`br.com.ziipsan.core.certification.assetmgmt55001`, ao lado dos pacotes
irmãos `quality9001` (ED-69) e `infosec27001` (ED-73). Origem: ADR-0041.

## Ativação

A ativação é do núcleo de certificações da P1.32 (V21_1), parametrizada
no core V3: `cert.iso55001.enabled` (BOOLEAN, classe d, padrão `false`),
`cert.iso55001.edition` (ENUM, ALLOWED_VALUES `["2024"]`, padrão
`"2024"` — edição-alvo ISO 55001:2024) e escopo declarado
`cert.scope_statement`, com proposta e aceite por pessoas diferentes.
`Iso55001Activation` é a régua em memória que os registros do pacote
recebem: com a norma desligada, nenhum registro nasce; desligar NÃO
apaga registro (`cert.deactivation_keeps_records` travado em `true`; o
SQL pendente não tem DELETE).

## O que o pacote guarda

- **Inventário e ciclo de vida sob a norma** (`AssetLifecycleRecord`):
  visão sobre o ativo da P1.35 (COE-05) e o inventário regulatório do GIS
  da ED-56. Nos campos coincidentes, prevalece a definição da NR/ANA;
  divergências ficam registradas. Ciclo de vida com os nomes da ED-56.
- **Risco LIDO da ED-56** (`RiskSnapshot`): cópia do `RiskView` com P×C
  decomposto (probabilidade, consequência, score, band, exclusão). Não
  há régua de risco paralela: leitura inconsistente (score ≠ P×C) é
  recusada, não corrigida.
- **Plano estratégico** (`StrategicAssetPlan`): objetivos e critérios de
  decisão declarados pelo tenant para RENOVAR, REPARAR, MANTER e
  DESCARTAR (cobertura dos quatro é obrigatória), e `DecisionRecord` —
  registro das decisões, sempre citando um critério do plano e do mesmo
  desfecho. Nenhum limiar de fábrica.
- **Plano de investimento/manutenção por ativo**
  (`AssetInvestmentPlan`): linhas com o custo apropriado LIDO — total da
  OS da ED-36 e quantidade × custo médio dos materiais da ED-58. Linha
  sem custo apropriado exige motivo declarado; horizonte é o declarado
  pelo tenant.
- **Ação preditiva** (`PredictiveActionRule`): regra estatística simples
  — média/desvio-padrão de janela histórica; sinal quando a observação
  mais recente se afasta mais de k desvios na direção configurada.
  `minSamples` e `k` são parâmetros do tenant; série curta =
  `INSUFFICIENT_DATA`, nunca sinal presumido. O sinal é sugestão
  registrada, não OS.
- **Vínculos de manutenção** (`MaintenanceLinks`): referência à linha do
  plano preventivo de NÃO LINEARES da ED-56 (OPE-20) e ao gatilho de
  condição com peça sobressalente crítica (OPE-38), com o estado da peça
  como lido (`BELOW_MINIMUM` levanta `requiresRestock`).
- **Mapa de cláusulas como autoavaliação** (`AmsClauseAssessment`): só
  referência, título em palavras próprias ou `[A DEFINIR]`, situação
  declarada e evidências ligadas a registros do produto — com o aviso
  fixo de que autoavaliação não é certificação.

## Fronteiras

- **Frota FORA**: veículos são da ED-59 (que estende a manutenção da
  ED-56); `AssetKind.VEHICLE` é recusado no código e no SQL.
- **Sem texto de norma**: textos e títulos literais da ISO 55001 não são
  reproduzidos sem licença — títulos nascem `[A DEFINIR]`
  (`title_pending`) até a curadoria (ED-76/tenant) declará-los.
- **ED-53** (editor web do GIS) é dependência da fonte: a geometria e a
  edição dos ativos vivem lá/na ED-56; este pacote não duplica editor nem
  cadastro — consome o ativo e o inventário como eles existem.

## Persistência

SQL em `db/pendente/ED-72__certification_assetmgmt_iso55001.sql` (sem
número de migration — numeração global é da integração). 11 tabelas
`cert_i55001_*`, RLS via `register_tenant_table`, sem DELETE, e toda FK
composta `(tenant_id, X)` com o `UNIQUE (tenant_id, ...)` do pai
declarado antes (lição da cascata C3).
