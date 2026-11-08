# Pacote ISO 14001 (ED-70) — módulo `certification`

Pacote da ISO 14001 no módulo `certification` (ADR-0041), subpacote
`br.com.ziipsan.core.certification.env14001` sobre o núcleo da P1.32,
ao lado dos pacotes irmãos `quality9001` (ED-69), `infosec27001`
(ED-73) e `health45001` (ED-71). O catálogo de fábrica de aspectos de
ETA, ETE e rede vive no pacote `br.com.ziipsan.core.sanitation`
(`SanitationAspectCatalog`), como o aceite fixa.

## Ativação

Ligada por `cert.iso14001.enabled` (core V3, desligado por padrão),
com escopo declarado (`cert.scope_statement`) e edição
(`cert.iso14001.edition`, ALLOWED_VALUES `["2015", "2026"]` no core
V3): **2026 é a edição-alvo; 2015 só em transição** declarada. A
régua `Iso14001Activation` é componente de todos os registros da
norma: sem ativação ligada, nenhum registro nasce (construtor
canônico incluído). Desligar a norma **não apaga registro**
(`cert.deactivation_keeps_records` travado em `true`; o SQL pendente
não tem DELETE).

## O que nasce só com a norma ligada

- **Aspectos e impactos** (`EnvironmentalAspect`): área ETA/ETE/rede,
  descrição do aspecto e do impacto, situação normal/anormal/
  emergência; nascem do catálogo de fábrica `sanitation` ou de
  declaração livre do tenant.
- **Significância** (`SignificanceCriteria` + `SignificanceEvaluation`):
  critério **configurável pelo tenant** (fatores e limiar declarados;
  nenhum valor de fábrica), total calculado e veredito pelo limiar.
  A avaliação carrega ativação e critério como componentes e o
  construtor canônico confere total = soma dos escores, total ≥ 0 e
  veredito coerente com o limiar — construção direta com total ou
  veredito declarados em divergência é recusada (correção D1 da
  revisão da ED-70).
- **Licenças e condicionantes** (`EnvironmentalLicense`): validade,
  responsável, evidências e condicionantes com prazo/evidência;
  **alerta de vencimento calculado** (`expiryStatus`) com a janela em
  dias declarada pelo chamador — nenhum dia de fábrica.
- **Emergência ambiental por cenário**
  (`EnvironmentalEmergencyScenario`): cenário declarado pelo tenant,
  com ações de resposta, responsável e aspectos ligados.
- **Mapa de cláusulas como autoavaliação** (`EmsClauseAssessment`):
  referência, resumo em palavras próprias, situação e evidências;
  o título literal é sempre `[A DEFINIR]` até a decisão do dono
  sobre licença × reescrita.

## O lodo é da ED-105 (não duplicar)

A geração e a destinação do lodo são registradas pela ED-105
(JOR-16, OPE-25, obrigação REGULATORY) e valem para todo tenant com
estação, **com a norma ligada ou desligada**. A ED-70 apenas lê:
`SludgeAspectView.read(SludgeAspectSummary)` consome o resumo real
da ED-105, sem ativação e sem tabela própria de resíduo. Os aspectos
declarativos de lodo usam os templates `WTP-SLUDGE`/`WWTP-SLUDGE` do
catálogo — que não registram geração nem destinação.

## Numeração

O SQL nasce em `db/pendente/ED-70__certification_env_iso14001.sql`,
sem número Flyway. Na integração: próximo número livre da sequência
global da P3.12 (V100+), atualização da lista de publicadas no mesmo
pouso. **V102/V103 são reservados a outro lote e não são usados.**
