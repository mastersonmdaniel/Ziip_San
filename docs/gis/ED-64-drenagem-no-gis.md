# ED-64 — Drenagem no GIS (GIS-13): notas de integração e ensaio

Spec: JSON do plano v3.0, id `ED-64` — "aceite da GIS-13: catálogo
de drenagem carregado; regras de conectividade, declividade e
sentido; estado de conservação, obstrução, foto e inspeção;
sub-bacias com atributos do SWMM; temático de conservação;
relatório do cadastro de drenagem; TR-1048; símbolos de drenagem
(§4.3) em cinco estados no mesmo pipeline da P1.30, com aceite do
dono; plano de O&M da drenagem por classe (NR 12/ANA, Res. ANA
245/2025, arts. 11 e 12) gerando OS recorrentes de limpeza
(`drain.cleaning_interval_by_class`); alagamento ligado à chuva do
período, tipo alagamento no catálogo de causas da ED-92;
monitoramento pluviométrico por fonte configurável
(`drain.rain_source`: Cemaden por IMPORTAÇÃO do download mensal com
fonte citada, ou pluviômetro próprio — sem API do Cemaden);
relatório de indicadores operacionais (OPE-32); inspeção por
vídeo/desobstrução da ED-56 reaproveitável quando existir".
Dependências: P1.27 (catálogo), P2.21 (GIS-03) e ED-53 (GIS-09,
pacote local existente).

## Numeração

A série tenant do GIS na main vai até V13_12 e nenhum número
seguinte está confirmado livre (ED-53/54/63 disputam os slots; a
P3.27 cita uma V13_13 de temáticos não integrada). Os dois SQL
entram em `db/pendente/` sem número:

- `intelligence/ziipsan-gis/.../db/pendente/ED-64__gis_drainage.sql`
- `shared/ziipsan-core/.../db/pendente/ED-64__core_drain_parameters.sql`

Na integração, cada um recebe o próximo número da sua série
(tenant GIS e core, respectivamente) e muda de pasta; o conteúdo
não muda.

## Decisões de desenho registradas

1. **ENERGY_DISSIPATOR = NODE sempre** (o §4.4 admite NODE ou
   STRUCTURE): singularidade pontual da rede; um só caminho de
   gravação (precedente: ECOPOINT da ED-65).
2. **OUTFALL de drenagem** na classe nova STORM_NODE com code
   `OUTFALL` (o do §4.4): a classe OUTFALL da V13_2 é SEWER e a
   unicidade do tipo é por (tenant, classe, code).
3. **Declividade**: a coluna gerada `slope` da V13_2 é
   (jusante − montante)/comprimento — negativa no escoamento
   correto. As regras da GIS-13 trabalham com a declividade do
   escoamento (montante − jusante)/comprimento: < 0 erro
   STORM_SLOPE_ADVERSE, = 0 erro STORM_SLOPE_FLAT, abaixo da mínima
   do tenant aviso STORM_SLOPE_BELOW_MIN. A mínima NÃO tem valor de
   norma (§4.4: não há NBR de microdrenagem): é o parâmetro
   `drain.gravity_min_slope`, sem padrão de fábrica.
   **Enforcement na gravação (correção D1 da revisão)**: o
   `createFeature` calcula o comprimento geodésico da geometria de
   entrada, aplica declividade E sentido (`checkFlowDirection`,
   com as cotas das pontas derivadas do `flow_direction`
   declarado) e RECUSA a gravação em qualquer erro do §5.4; com as
   duas cotas e geometria sem comprimento determinável, também
   recusa. O endpoint `validate-arc` aplica as mesmas regras sem
   gravar. A leitura do parâmetro de mínima pelo mecanismo da
   P1.6 segue fiação de integração (abaixo) — sem parâmetro, os
   erros de adversa/nula/sentido já recusam; só o AVISO de mínima
   depende dele.
4. **Nenhum intervalo de limpeza de fábrica**: a NR 12/ANA exige o
   plano com periodicidade por classe; os meses são do tenant em
   `drain.cleaning_interval_by_class` (factory_default NULL no
   catálogo do core). Classe sem intervalo = NOT_SCHEDULED, e o
   gerador registra o motivo do pulo POR FEIÇÃO quando existe
   linha de plano ativa sem intervalo (precisão D8 da revisão);
   sem nenhuma linha de plano, a geração devolve (0, []) sem
   registros — nada é gerado em nenhum dos dois casos.
5. **Chuva sem fonte não entra**: cada leitura grava o `source_ref`
   do lote (citação do download mensal do Cemaden ou identificação
   do pluviômetro). Acumulado de período sem leitura = NULL
   (desconhecido), nunca zero.
6. **Causa ALAGAMENTO é marcador proposto**: a ED-92 ainda não
   existe; `network_cause_code='ALAGAMENTO'` e
   `network_occurrence_id` nulo até a integração reconciliar com o
   catálogo de causas dela. Nenhum tipo de ocorrência foi inventado
   no núcleo.
7. **Vídeo é da ED-56**: método CCTV_EXTERNAL_REF + `video_ref`
   obrigatório (CHECK no banco e regra Java espelho). A
   desobstrução programada da ED-56, quando existir, lê os mesmos
   trechos por reincidência de obstrução (inspeções desta entrega).
8. **Cinco estados dos símbolos** = 4 de ciclo de vida (§3.7) +
   OUT_OF_TARGET (§10.2), idêntico à leitura declarada da ED-65; se
   a §4.3 de ICONES-E-SIMBOLOGIA nomear outro quinto estado, a troca
   é de dado na lista `SYMBOL_STATES`.
9. **STORM_BASIN não é do §4.4 (correção D2 da revisão)**: a
   tabela do §4.4 tem 19 códigos. O tipo STORM_BASIN (ZONE) tem
   fonte real na lista de `zone_type` da V13_2 (§3.6.5 do
   GIS-CADASTRO-TECNICO). O catálogo é declarado em todo o pacote
   como "19 do §4.4 + STORM_BASIN da V13_2/§3.6.5" — decisão
   registrada, sem superestimar o §4.4.

## Fiação de integração (não faz parte desta entrega)

- Leitura de `drain.cleaning_interval_by_class` e
  `drain.rain_source` pelo mecanismo de parâmetros (P1.6) e
  materialização das linhas em `gis_drain_cleaning_plan`.
- Abertura da OS de limpeza no núcleo (ED-36) a partir de
  `gis_drain_cleaning_request` (status ORDERED + service_order_id;
  baixa da OS → DONE + completed_on, que alimenta o próximo ciclo).
- Ligação da ocorrência de alagamento com a ocorrência de rede da
  ED-92 (network_occurrence_id) e reconciliação do código da causa.
- Registro dos relatórios GIS-003/GIS-004 no núcleo da P3.22
  (ExternalReportRegistrar) e da fonte DRAIN_CONDITION no registro
  de temáticos da GIS-08 (P3.27) — as definições estão em
  `GisDrainReports`; as views já existem no SQL pendente.
- Regras de conectividade/declividade desta entrega plugadas no
  worker de validação por área suja da GIS-03 (gis_validation_issue
  com os rule_codes STORM_*).
- Arte dos símbolos pela P1.30 a partir das 100 chaves do manifesto
  + **aceite do dono** (decisão dele).
- Layout do CSV do download mensal do Cemaden: conferir no primeiro
  arquivo real; rótulos novos entram nas listas de cabeçalho de
  `GisDrainRain` como dado.

## Ensaio

- TR-1048: roteiro externo (não está no repo nem nesta VM) — o
  ensaio roda com o roteiro real, com a linha GIS-13 do §13.2 como
  referência interna.
- IT de plataforma `Gis13DrainIT` (3 testes): roda na CI do dono
  (PostgreSQL 16 + PostGIS); nesta VM não há servidor PostgreSQL.
