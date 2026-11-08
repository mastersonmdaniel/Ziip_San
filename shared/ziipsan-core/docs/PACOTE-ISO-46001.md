# Pacote ISO 46001 — uso proprio de agua (ED-81)

Modulo `certification` (core, `shared/ziipsan-core`), subpacote
`br.com.ziipsan.core.certification.water46001`. Faixa Flyway do
plano SS3: `V21_x` (pacotes por norma, ED-69 a ED-77, ED-80 a
ED-82); SQL desta entrega em `db/pendente/` com prefixo `ED-81__`,
sem numero — o numero sai da sequencia global na integracao.

## O que faz
- **Ativacao** por tenant com escopo declarado e escopo de
  INSTALACAO (`Iso46001Activation`), sobre os parametros reais do
  core V3 `cert.iso46001.enabled` (classe d, padrao desligado),
  `cert.iso46001.edition` (classe d, sem padrao — edicoes no
  catalogo da P1.32) e `cert.scope_statement`; proposta e aceite
  por pessoas diferentes (ADR-0041); desligar nao apaga registro.
- **Catalogo de edicoes** (`Iso46001Catalog`): ISO 46001:2019
  publicada, vigente e EM REVISAO (adotavel); projeto da edicao 2
  apenas ACOMPANHADO — nao adotavel e sem nenhum requisito
  prescrito (edicao nao publicada nao tem requisito inventado).
- **Unidades de uso operacional** (`OperationalUseUnit` +
  `OwnUseService`): ficha sobre a unidade consumidora da P1.3
  (`consumer_units`), com hidrometro instalado nela pela
  hidrometria da ED-14 — existencia e instalacao conferidas por
  portas, fail-closed. Natureza OPERATIONAL/EMERGENCY; finalidade
  por codigo do catalogo do tenant.
- **Fonte do uso proprio** (`OwnUseSource`): leitura da ED-85
  (OPE-05) SEM RECALCULO. **Verdade da fonte (correcao D1 da
  revisao):** o snapshot `loss_monthly_balances` NAO tem o split
  operacional/emergencial — ele guarda `exported_m3` e um
  `authorized_unbilled_m3` unico, agregado, que INCLUI o uso
  social. Os componentes chegam a esta fonte JA DECOMPOSTOS pelo
  adapter (contrato na pendencia P-ED81-01): operacional = soma
  das categorias `NETWORK_FLUSHING`/`RESERVOIR_CLEANING` e
  emergencial = soma de `FIREFIGHTING`, ambos de
  `loss_authorized_unbilled_volumes` por DMC/mes de
  `occurred_on`, com `SOCIAL_TANKER` (social) excluido; exportada
  = `exported_m3` do snapshot. Decompor por categoria nao e
  recalcular o balanco. Componente ausente recusa; valor nao
  finito (infinito) recusa (correcao D5).
- **Revisao do uso e linha de base** (`WaterUseReview`): periodo,
  instalacao do escopo, usos significativos pelo criterio de
  significancia DECLARADO pelo tenant, linha de base declarada
  (rotulo do periodo-base + valor) — sem base, a revisao nao fecha
  e a variacao fica ausente ([A DEFINIR], nunca numero presumido).
- **Plano de acao** (`WaterActionPlan`): ligado a objetivo
  existente do registro da P3.31 (`cert_objectives`), conferido
  por porta; acoes com responsavel e prazo; economia esperada e
  estimativa do tenant (ausente = [A DEFINIR]).

## Fronteiras
P1.3 dona do cadastro da unidade; ED-14 dona do hidrometro e da
instalacao; ED-85 dona do balanco (aqui so se le, com a
decomposicao por categoria feita pelo adapter — ver acima);
P3.31 dona dos objetivos; P1.32 dona de ligar/desligar a norma.
Fiacao (JDBC/Spring das portas, resolucao da referencia de
instalacao, tela) e pendencia nomeada de integracao no relatorio
da ED-81.

## Amarras pos-revisao (correcao da REVISAO-ED-81)
- **Periodo x fonte (D4):** `WaterUseReview` recusa fonte cujo
  ano/mes nao caia dentro do periodo da revisao.
- **DMC x instalacao (D4, pendencia P-ED81-05):** nao ha ligacao
  entre o `dmcProfileRef` da fonte e a `installationRef` do
  escopo — nem na ED-85 nem neste pacote. Modo de falha ate a
  fiacao resolver o mapa DMC→instalacao: uma fonte de DMC que nao
  atende a instalacao pode ser anexada a revisao dela sem
  deteccao neste pacote.
- **Revisao fechada unica por escopo (D3):** indice unico
  parcial `uq_w46001_reviews_one_closed_per_scope` no SQL — uma
  so CLOSED por (tenant, instalacao, inicio, fim); rascunhos
  livres.
