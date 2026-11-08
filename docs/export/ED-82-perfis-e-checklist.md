# ED-82 - Perfis de exportacao ISO 37120 e PNQS e checklist de cadastro das NBR

Pacote `br.com.ziipsan.reporting.exportprofile` (modulo
`intelligence/ziipsan-reporting`), sobre as fichas de indicador da ED-77
e os fechamentos congelados do registro unico da P3.41. Nucleo puro
Java, sem registro proprio de indicador.

## Perfil ISO 37120

Documento com indicadores e serie historica sob a referencia
**ABNT NBR ISO 37120:2021** (nomeada no acceptance). Cada indicador so
entra com um `Iso37120Mapping` DECLARADO por quem integra (codigo do
produto, referencia na norma, quem declarou): esta entrega nao traz
nenhum mapeamento de fabrica, porque o texto da ISO 37120 nao esta nas
fontes desta VM. Indicador sem mapeamento recusa o documento inteiro
(`EXPORT_ISO_MAPPING_REQUIRED`).

## Perfil PNQS (sem leiaute proprio)

Documento com indicadores e serie historica para preenchimento na
plataforma da ABES, na **estrutura neutra do produto**. Verificado em
22/10/2026 (acceptance): o regulamento do PNQS 2026 (versao 1.2) nao
define leiaute nem formato de troca de dados. O documento carrega os
avisos literais: o PNQS e **premio e selo de reconhecimento, nao norma
tecnica nem certificacao acreditada**, e a estrutura e neutra, nao um
"leiaute PNQS". Nenhuma linha tem referencia externa.

## Serie historica

Pontos nascem so de `IndicatorClosing` com status `CLOSED` da P3.41
(fechamento reaberto e recusado), ordenados por periodo, sem periodo
repetido e nunca vazios.

## Checklist de completude do cadastro (NBR)

16 itens de verificacao nomeados: 4 topicos que as fontes nomeiam
(completude do cadastro; vinculo a rede de referencia cadastral
municipal; precisao do levantamento - P3.27; relatorios de extensao do
cadastro - P3.27) contra cada uma das NBR 12586, 12587, 13133 e 14166.
O texto das normas nao esta nesta VM (a P1.28 guarda so a referencia no
produto e mantem os textos fora do repositorio, pela licenca da ABNT),
logo todo item carrega o requisito como `[SEM FONTE]` - nenhum
requisito de norma foi fabricado. `isComplete` so e verdadeiro com
todos os itens VERIFIED/NOT_APPLICABLE avaliados por alguem nomeado e
os itens de vinculo com a rede de referencia ligada. Item
NOT_APPLICABLE exige justificativa (note nao-vazia) de quem avaliou -
sem ela a avaliacao e recusada na construcao (correcao D2 da revisao).

## Paridade Java x SQL (correcao D1/D2 da revisao)

O SQL espelha as regras do Java: as linhas desnormalizam o
`profile_kind` do documento (FK composta por kind garante que e o kind
do pai) e o CHECK exige referencia externa presente e nao-branca no
ISO 37120 e NULL no PNQS; documento tem coerencia kind x referencia
normativa literal; avaliacao NOT_APPLICABLE exige note nao-vazia;
item `%-REDE-REFERENCIA` so conclui (VERIFIED/NOT_APPLICABLE) com
`reference_network_linked`; e o prefixo NBR do `item_code` tem de ser
a `norm_reference` gravada. Mapeamento ISO duplicado recusa com
`EXPORT_ISO_MAPPING_DUPLICATED` (correcao D3).

## O que NAO esta nesta entrega (honesto)

- Mapeamentos ISO 37120 concretos, categorias/campos do PNQS e
  requisitos literais das 4 NBR: dependem dos textos, fora desta VM.
- Fiacao (persistencia dos documentos, endpoints, agendamento): portas
  do nucleo puras; ligacao e pendencia nomeada de integracao (P-ED82-*).
