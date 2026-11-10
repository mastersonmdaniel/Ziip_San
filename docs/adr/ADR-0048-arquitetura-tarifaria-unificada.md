# ADR-0048 — Arquitetura Tarifária Unificada: catálogo único de cobranças, billing maestro, convênios em módulo separado e pacote de rota resolvido

<!-- Papel: Corretor — Camada 0 (ADR + desenho) · Data: 09/11/2026 · Pacote: proposta-tarifaria/camada-0-adr-desenho -->
<!-- Sessão corretora distinta das sessões produtora e revisora (PROTOCOLO-ESTEIRA.md, §2/§4(i)). Este ficheiro é NOVO; o ADR-0047 original NÃO foi tocado. -->

- **Status:** ACEITO — por decisão expressa do dono, item 43 do
  `DECISOES-DO-DONO.md` (09/11/2026, 03:13 −03): "EXECUTAR a
  proposta-alvo de arquitetura tarifária do item 32, com ADR próprio,
  em camadas (catálogo → billing maestro → convênios genérico →
  campo), sem reescrever as fórmulas provadas, com prova diferencial
  por camada"; **e** item 45 do mesmo ficheiro (09/11/2026, 03:28
  −03): multimunicípio no nível COMPLETO, agora, junto à
  reestruturação tarifária, com P-BF-06/07/08 DECIDIDAS e a dimensão
  município a nascer já nesta Camada 0 (ver §4 deste ADR, reescrito).
- **Decisor:** dono do produto.
- **Número:** ADR-0048. **Nota de proveniência e renumeração
  (obrigatória, R-00 da revisão independente):** este ADR nasceu
  como ADR-0047 por derivação das fontes plano/diário
  (`ziipsan-retomada/fontes/docs__PLANO-EXECUCAO-PDCA-R0-R9.md` e
  `docs__DIARIO-DE-BORDO.md`), onde as referências correm contíguas
  até ADR-0046. A revisão independente provou que essa derivação era
  insuficiente: as fontes plano/diário não cobrem os ADRs citados em
  código/README, e o **ADR-0047 já está atribuído à P1.16** (regra
  única de arredondamento e tributo da conta) — evidência:
  `p2.5/espelho/.../billing/README.md:112,118` ("P1.16, ADR-0047") e
  `BillRoundingVectorsTest.java:18` ("(P1.16, ADR-0047)"), além da
  migration `V4_2__social_tariff_enrollments.sql` e do ficheiro de
  vetores da P1.16 citados na `REVISAO-CAMADA-0.md` §1.1/R-00.
  **ADR-0048: zero referências locais** em toda a árvore conferida
  (`ziipsan-producao/` e `ziipsan-retomada/`) — é o número correcto.
  **Reverificar contra a `main` no pouso** (como qualquer número):
  conferir que nenhum ADR-0048 foi atribuído entretanto; divergindo,
  o pacote volta para renumeração por adendo — nunca sobrescreve.
  **Lição registada:** a numeração de ADR confere-se também contra
  as referências em código, migrations, testes e READMEs entregues,
  não só contra plano/diário. Convenção de arquivo:
  `docs/adr/ADR-NNNN-slug.md`.
- **Parte normativa integrante:** `ADENDO-DESENHO-MUNICIPIO.md`
  (nesta pasta do pacote) é **parte normativa deste ADR** no que diz
  respeito à dimensão município: onde o `DESENHO-CAMADAS.md` original
  conflitar com o adendo, vale o adendo (ver §4). O adendo fixa as
  decisões D-01…D-08 e as premissas PM-01… que o bloco
  `multimunicipio/` consome.
- **Afeta:** P2.5 (`TariffEngine`, `BillCalculator`, snapshot da
  conta), ED-25 (cofaturamento), ED-34 (catálogo de serviços), ED-32
  (contábil), P2.6 (fiscal/NFAg-e), P2.20 (manutenção de contas),
  ED-19 (campo), ED-38 (portal — explicação da conta), ED-22/ED-57
  (isenções/benefícios, só por gancho), ED-88 (só quando a agência
  definir — ver PD-TAR-04 no desenho).
- **Base:** `PROPOSTA-ARQUITETURA-TARIFARIA.md` (proposta do dono,
  item 32 do `DECISOES-DO-DONO.md`, 08/11/2026);
  `DECISOES-DO-DONO.md` itens 32, 34, 43 e 45;
  `ANALISE-MULTIMUNICIPIO.md` inteira, com o adendo pós-revisão dela
  (listas fechadas de tabelas; cenário A decomposto); código entregue
  da P2.5, da ED-25, da ED-34, da ED-32, da P2.6 e da ED-19 (caminhos
  na versão original deste ADR e no `RELATORIO-CAMADA-0.md`, com a
  precisão de caminhos da `ERRATA-DESENHO-R02-R05.md`).

## 1. Contexto

Hoje cada tipo de cobrança que entra numa conta vive no seu próprio
canal, com a sua própria regra de resolução:

- **Água e esgoto** são calculados pelo `TariffEngine` da P2.5
  (`calculate` / `calculateSewer`), com fatiamento por versão via
  `SliceCharge.tariffVersionId` (UUID) e caso-âncora provado em CI
  total (leitura 1000→1027, ocorrência E-01 = R$ 201,60; CI total
  rodada 9 verde no PC do dono, 08/11/2026).
- **A parcela fixa TFDI** (Res. ARESC 105/2018, via `FixedPartCharge`)
  entra por um canal avulso: o valor é pré-resolvido pelo chamador e
  ocupa o 10.º campo do `BillInput` (`fixedPartValue`, com construtor
  de compatibilidade de 9 campos — o precedente de extensão sem
  quebrar chamadores desta arquitetura).
- **Débitos** entram por `Installments` sobre `DebitReceivable`
  (o `BillCalculator` **exige** parcela selecionada: débito sem
  parcela a cobrar lança exceção) e **créditos** por `CreditApplier`
  (teto = cobranças; resíduo ao mês seguinte).
- **Serviços** têm catálogo próprio na ED-34 (`PriceTable` /
  `ServicePriceItem` com `RevenueNature` da Lei 11.445/2007,
  art. 10-A, II), mas o lançamento no encerramento da OS
  (`ServiceChargeOnClose`) cai direto em `debit_receivables`, fora de
  qualquer composição única.
- **Cofaturamento de terceiro** (resíduos sólidos) vive na ED-25,
  escrito à mão só para `SOLID_WASTE`: `CobillContract` (record, com
  `ServiceKind` de um único valor e a nota expressa "desenho aberto a
  outros serviços"; o valor **não** vive no contrato),
  `CobillNoticeRule` (aviso de 60 dias, NR 13, arts. 46–56, conforme
  o aceite), `CobillWithdrawalRule`, `CobillBookletPlan`,
  `CobillSettlement` (`OWN_FIRST`/`PRO_RATA`),
  `ThirdPartyDebtCutoffRule` (regra `cut.third_party_debt_blocks`) e
  `SolidWasteChargeCalculator`.
- **O snapshot da conta é parcial.** `bills.tariff_snapshot` (V4_1/V4_4)
  guarda a fotografia dos **valores**; `bills.parameter_version_ids`
  (V4_4) guarda os ids das `parameter_versions` lidas, gravados por
  `BillSnapshotService.captureParameterVersions` — serviço sem
  chamador de produção localizado nos pacotes (só teste o exercita).
  Nenhuma das duas peças registra **as versões de todas as cobranças**
  que compuseram a conta. Quem grava `tariff_snapshot` em produção
  também não foi localizado nos pacotes — furo de integração nomeado
  (PD-TAR-06 no desenho), não presumido.
- **A segregação contábil** da ED-32 é por evento
  (`AccountingEventKey`: `BILL_ISSUED`, `PAYMENT_RECEIVED`,
  `CREDIT_RECOVERY`, `BILL_CANCELLED`, `BILL_PARCELLED`,
  `PAYMENT_REFUNDED`, `TAX_WITHHELD`, `DOUBTFUL_PROVISION`,
  `DOUBTFUL_REVERSAL`) — **sem** chaves `COBILL_*` no enum vigente;
  a ED-25 nomeia os fatos localmente (`CobillSettlement.CobillAccountingEvent`)
  e declara a fiação no enum da ED-32 como pendência de integração.
- **A NFAg-e** (P2.6) é emitida por conta (`billId`,
  `NfageEmissionService`/`NfageTotalRule`): valor de terceiro
  cofaturado não é receita fiscal própria da autarquia, e hoje nada
  na composição da conta discrimina próprio × terceiro para o fiscal.
- **O campo** (ED-19) já recebe pacote mínimo e resolvido
  (`RoutePackage` + `FieldTariffSnapshot` por tarefa, hash do
  artefato via `ArtifactHash`), com prova diferencial histórica de
  10.000 casos na JVM e JUnit 53/53 — mas o retrato é montado a partir
  da tarifa da P2.5, não de um catálogo de cobranças.

O dono formulou a arquitetura-alvo em 08/11/2026 (item 32), decidiu
executá-la em 09/11/2026 (item 43) e decidiu a dimensão município no
nível COMPLETO, agora, em 09/11/2026 (item 45). Este ADR é o
instrumento dessas decisões.

## 2. Decisão

Adota-se a **Arquitetura Tarifária Unificada**, em cinco pilares:

### Pilar 1 — Registro único e versionado de cobranças (o catálogo)

Toda cobrança que pode entrar numa conta é um registro versionado de
um catálogo único (`billing.catalog`, Camada 1), com:

- **código** estável por tenant **e por escopo** (ver §4: a unicidade
  é `(tenant_id, municipality_ibge, code)` na semântica do adendo
  D-01 — o mesmo `code` pode existir em municípios diferentes do
  mesmo tenant);
- **dono**: `OWN` (própria) × `THIRD_PARTY` (terceiro) — campo
  obrigatório, sem default;
- **natureza da receita**: `RevenueNature` **reutilizada** da ED-34
  (`billing.servicecatalog.RevenueNature`, import — nunca duplicar o
  enum: TARIFF / COMPLEMENTARY / ANCILLARY / ASSOCIATED_PROJECT,
  Lei 11.445/2007, art. 10-A, II, com `requiresSharingPct`);
- **modelo de cálculo** (Pilar 2);
- **vigência/versão**: cada versão referencia as fontes vigentes por
  identificador — em particular, a cobrança de água/esgoto referencia
  a versão de tarifa por **UUID** (precedente
  `SliceCharge.tariffVersionId`); o catálogo **referencia**, nunca
  copia faixas, preços ou fórmulas;
- **escopo de município** (ver §4 e adendo D-01): a definição carrega
  `municipalityIbge` nullable; a versão herda o escopo da definição;
- **homologação** quando exigida e **regra de segregação contábil**
  por dono/natureza (de-para da ED-32, Camada 3).

O catálogo nasce **vazio**: nenhum seed, nenhum dado inventado
(precedente DAD-20, `debit_types`/`credit_origins`: "nenhum dado
inventado"). SQL novo em
`src/main/resources/db/pendente/CAMADA-1__charge_catalog.sql`;
numeração Flyway global só na integração. FK composta exige
`UNIQUE (tenant_id, id)` na tabela-pai (lição R3-G1).

### Pilar 2 — Uma calculadora por modelo de cálculo

Não há fórmula única. Há exatamente seis modelos (proposta §2.2),
cada um com a sua calculadora atrás da interface `ChargeCalculator`
(Camada 2):

| Modelo | Calculadora-alvo | Fonte vigente que ela envolve (sem reescrever) |
|---|---|---|
| Progressiva por consumo | água | `TariffEngine.calculate` |
| Derivada de volume/percentual | esgoto | `TariffEngine.calculateSewer` / `sewerVolume` |
| Fixa por economia/unidade | TFDI, taxas fixas | valor da `FixedPartCharge` (Res. ARESC 105/2018) |
| Preço de catálogo | serviços | `ServicePriceItem` da ED-34 |
| Percentual sobre base | percentuais | `Money.percentOf` com modo explícito |
| Valor externo informado | convênios | contrato/valor da ED-25 (Camada 3) |

Os regimes de arredondamento vigentes são parte do contrato
(`Money`, escala 2: HALF_UP tarifa/esgoto/rateio; DOWN parcelas de
débito/crédito e base de retenção; HALF_DOWN imposto retido) — cada
calculadora declara o seu e nenhum muda.

### Pilar 3 — Billing como maestro, com snapshot completo

O billing compõe a conta a partir do catálogo (Camada 2): para
unidade + competência, resolve as cobranças devidas **considerando o
município da unidade, derivado da cadeia territorial** (ver §4 e
adendo D-02), chama a calculadora de cada uma, soma as próprias,
discrimina as de terceiros, aplica créditos e retenção na **ordem
vigente** e grava o **snapshot completo** de todas as cobranças e
versões usadas — **com o município do escopo resolvido por linha e o
município da UC no cabeçalho** (adendo D-04).

Ordem de composição decidida (a vigente, com os dois acréscimos
marcados):

1. água → 2. esgoto → 3. parcela fixa TFDI — **que vira linha de
catálogo** (modelo fixa por unidade) e sai do canal avulso mantendo o
valor → 4. demais cobranças próprias por modelo → 5. débitos via
`Installments` (**intocado**; débitos seguem fora do catálogo —
ver §5) → 6. créditos via `CreditApplier` (teto = cobranças próprias
+ débitos) → 7. retenção sobre (próprias + débitos − créditos), com a
última linha absorvendo o resíduo → 8. **linhas de terceiro entram
depois do `CreditApplier` e fora da base de retenção** (`taxBase`).

Invariante central, verificável: **sem linhas além das clássicas, o
resultado reproduz o `BillResult` vigente campo a campo** —
`charges()`, `taxBase` e `total` idênticos, tolerância **zero** —
provado por prova diferencial por camada (decisão item 43). A fórmula
do total vigente permanece a do CHECK
`chk_bills_total_with_withholding_tax` para a parte própria:
total próprio = água + esgoto + fixa + débitos − créditos − impostos;
o total da conta = total próprio + linhas de terceiro discriminadas.

**Invariante de regressão do item 45 (INV-C2-11, adendo D-05):**
sobre a mesma massa, os **totais do tenant não mudam** com a dimensão
activa quando as tarifas municipais reproduzem os valores da
tarifa-empresa — a dimensão muda a atribuição por município, nunca o
total do tenant.

O snapshot completo é um documento novo (`ChargeSnapshotDocument`,
Camada 2) gravado **no ato da emissão, pela camada de aplicação** —
o desenho nomeia e fecha o furo do produtor não localizado do
`tariff_snapshot` vigente (PD-TAR-06), sem presumir código
inexistente e sem substituir as colunas vigentes
(`tariff_snapshot`, `parameter_version_ids`), com as quais convive.

O ponto de extensão do `BillInput` segue o precedente da própria
P2.5: campo novo ao fim + construtor de compatibilidade. Nenhum
chamador vigente quebra.

### Pilar 4 — Convênios em módulo separado, generalizando a ED-25

Contrato, adesão/consentimento, aviso, retirada a pedido, repasse e
segregação contábil vivem em módulo de convênios (Camada 3), que
**generaliza a ED-25 por parametrização**: nasce um enum aberto novo
(`CobillServiceKind`) com adaptador de/para para o
`CobillContract.ServiceKind` vigente; a calculadora de valor externo
reusa os valores e contratos da ED-25; **tabelas e regras da ED-25
são preservadas** — generalizar é parametrizar, nunca reescrever.
Na conta, convênio entra só como linha discriminada de terceiro
(Pilar 3, passo 8). O valor é do parceiro: a autarquia não o versiona
nem reajusta. Inadimplência de convênio não corta água
(`ThirdPartyDebtCutoffRule` vigente, intocada).

Desdobramentos nomeados da Camada 3: (a) ponto de extensão da ED-32 —
de-para contábil por dono/natureza e as chaves de evento necessárias
nomeadas no desenho (`COBILL_ISSUED` / `COBILL_RECEIVED` /
`COBILL_TRANSFERRED`, aditivas ao enum vigente, que hoje não as tem);
(b) fiscal P2.6 — discriminação nomeada que mantém o valor de
terceiro **fora** da receita fiscal própria na NFAg-e por conta.

### Pilar 5 — Pacote de rota mínimo e resolvido

O aparelho de campo recebe **apenas os itens resolvidos das unidades
da sua rota**: um resolvedor novo projeta o `FieldTariffSnapshot`
vigente a partir das cobranças marcadas como calculáveis em campo no
catálogo, **resolvidas pelo município de cada unidade** (adendo
D-02/D-06). O aparelho não recebe o catálogo, não recebe a tabela
inteira, não ganha motor de gestão. **Convênio nunca é calculado no
aparelho.** O hash do calculador da ED-19 é guarda de regressão
obrigatório no relatório da camada (protocolo da esteira, §2,
estágio 5). O contrato de campo vigente — o neutro da O7-6 — fica
**intocado**: esta arquitetura não o resolve nem o altera.
**Fronteira (adendo D-06):** a montagem de rotas
(`RoutePackageAssembler`) e a invariante rota↔localidade da P-BF-07
são do **bloco `multimunicipio/`**, não desta Camada 4; esta camada
entrega a política de calculabilidade, a resolução por UC e a
projecção do snapshot, que o outro bloco consome.

## 3. Portões (condições permanentes desta decisão)

1. **Fórmulas provadas intactas.** `TariffEngine`, `Installments`,
   `CreditApplier`, `TaxWithholding`, `Money` e o calculador da ED-19
   não mudam de aritmética. Divergência na prova diferencial é
   defeito da camada nova, nunca "diferença aceitável". Nenhuma
   asserção de teste vigente é afrouxada.
2. **Sem big bang.** Estrangulamento por camadas, nesta ordem:
   Camada 1 (catálogo) → Camada 2 (maestro) → Camada 3 (convênios) →
   Camada 4 (campo). Cada camada é pacote próprio sob a esteira
   (produção → revisão independente → confirmação independente →
   portão → pouso → verificação por hash → diário), com CI total
   verde no PC do dono como prova de camada.
3. **Regimes jurídicos nunca misturados.** Tarifa regulada
   (água/esgoto — Lei 11.445/2007), preço público de serviço, taxa e
   valor de convênio têm natureza e dono diferentes; o catálogo os
   registra juntos, mas cada registro carrega dono e natureza, e a
   segregação contábil (ED-32) e fiscal (P2.6) seguem por cobrança.
   Citações legais neste ADR são só as que as fontes locais
   carregam: Lei 11.445/2007, art. 10-A, II (via `RevenueNature` da
   ED-34); NR 13, arts. 46–56 (via aceite da ED-25); Res. ARESC
   105/2018 (via `FixedPartCharge`). **[Correção R-04 da revisão:]**
   a citação "Lei 11.445/2007, arts. 29/30" da versão original fica
   **[SEM FONTE local]** — nenhuma fonte local localizada na
   amostragem dirigida fixa esses dois artigos para água/esgoto — e
   por isso **não é repetida como fundamento** neste ADR; vale a
   referência à lei sem artigos nesse ponto.
4. **Nada inventado.** Catálogo e parâmetros nascem vazios/sem valor
   de fábrica; o que não tiver fonte fica `[A DEFINIR]` / `[SEM FONTE]`
   e entra na lista de pendências do desenho (PD-TAR-nn).

## 4. Dimensão município — DECIDIDA (item 45; secção reescrita)

> Esta secção **substitui integralmente** o §4 e o registo
> correspondente do §7 da versão original (ADR-0047), que declaravam
> a dimensão "fora desta decisão" e P-BF-06/07/08 abertas. Essa
> letra era verdadeira à produção e foi **supersedida** pela decisão
> do dono de 09/11/2026, 03:28 (item 45). A supersessão regista-se
> aqui, no `ADENDO-DESENHO-MUNICIPIO.md` e no `ADENDO-LANDING.md`;
> o ficheiro do ADR-0047 original **não é tocado**.

**Decisão (item 45):** implementar o multimunicípio no **nível
COMPLETO** no conjunto do sistema, **agora**, junto à reestruturação
tarifária, aproveitando a janela do cenário (A) da
`ANALISE-MULTIMUNICIPIO.md`: o âmbito tarifário é absorvido pelo
catálogo novo, que **nasce dimensional**. **P-BF-06** (tarifas por
município), **P-BF-07** (rotas por município) e **P-BF-08**
(relatórios/faturamento por município) ficam **DECIDIDAS**.

**Âmbito deste bloco (tarifário):** este bloco é dono do **catálogo**
e da **dimensão na composição do billing**. Concretamente, neste
bloco a dimensão é:

- **eixo de primeira classe derivado da cadeia territorial** —
  o município de uma UC deriva-se de
  `localities.municipality_ibge` / `ibge_municipalities` (registry
  `V3_12`), pela cadeia
  `localities → commercial_sectors → blocks → consumer_units`
  (registry `V3_1`); o município **nunca é digitado no billing**
  (adendo D-02). IBGE é o código de **7 dígitos** do cadastro
  territorial vigente — nenhum código concreto é inventado neste ADR;
- **escopo no catálogo** (adendo D-01): `ChargeDefinition` carrega
  `municipalityIbge` nullable; **NULL = escopo "empresa"** — o
  default que **preserva o comportamento vigente** (todas as
  definições/versões vigentes comportam-se como escopo empresa);
  `ChargeVersion` herda o escopo da definição, sem coluna própria;
- **precedência na resolução** (adendo D-02, INV-C1-04-v2): para a
  UC, a versão de definição com escopo municipal do município da UC
  **vence** a de escopo "empresa"; a coexistência empresa+municipal
  **não** é ambiguidade; ambiguidade dentro do mesmo escopo continua
  falha fechada;
- **coerência tarifa×cobrança** (adendo D-03): as 5 tabelas da lista
  fechada do nível MÍNIMO da análise (`tariffs`, `tariff_versions`,
  `tariff_category_rules`, `tariff_blocks`, `tariff_social_bands` —
  billing `V4_1`: `tariffs:19`, `tariff_versions:45`,
  `tariff_category_rules:71`, `tariff_blocks:102`,
  `tariff_social_bands:124`) ganham `municipality_ibge` nullable por
  SQL pendente aditivo novo da Camada 2; divergência municipal
  cruzada é recusada na resolução (falha fechada);
- **snapshot auditável** (adendo D-04): o snapshot completo carrega,
  por linha, o `municipalityIbge` do escopo resolvido e, no
  cabeçalho, o município da UC — sustenta, no billing, o recorte da
  P-BF-08 (o reporting em si é do bloco `multimunicipio/`).

**Fronteira com o bloco `multimunicipio/`:** perfil de município
como entidade, parâmetros por município, rotas/campo (incl. a
invariante rota↔localidade da P-BF-07 e o `RoutePackageAssembler`),
fiscal por município, agenda ED-98, GIS, portal contextual, perfil
integrado nativo e o recorte de reporting sobre
`bills`/`consumption_records` são do **bloco próprio
`multimunicipio/`**, que desenha **sobre** as decisões desta camada
(premissas PM-01… do adendo). Este bloco não os implementa nem os
desenha além desta fronteira.

**Limites mantidos pelo item 45 (registados, não são lacunas):**

- **Sem RLS por município:** município **não** vira fronteira de
  isolamento/RLS por este ADR; o isolamento continua sendo o tenant
  (`register_tenant_table`), como em todo o sistema vigente e na
  análise (§7.1 dela: fronteira de RLS é outro estudo, não
  precificado, não decidido).
- **Sem layouts de NFS-e por município concreto:** ficam fora —
  `[SEM FONTE]` (não há município concreto; análise §7.3).
- **Sem marca por município:** fora (análise §7.4).
- **Sem coluna desnormalizada em `bills`/`consumption_records`
  neste bloco:** a decisão §7.2 da análise não foi decidida neste
  bloco; o recorte por view/join e o reporting são do bloco
  `multimunicipio/` (adendo D-08).

**Invariante de regressão (item 45):** os **totais do tenant não
mudam** — formalizada como INV-C2-11 no adendo (D-05), com prova
diferencial sobre a base fictícia de 12k (dois municípios fictícios,
9.000/3.000 UCs, item 34).

## 5. O que esta decisão NÃO faz

- Não resolve a **O7-6** (contrato aritmético de campo × paridade
  integral com o `TariffEngine`): o neutro vigente mantém-se e é
  prévio ao go-live, como já era (PD-TAR-03).
- Não decide o nome das chaves `cobill.*` além do prefixo — decisão
  **O7-7**, "decidir junto", pendente: a Camada 3 usa as chaves
  vigentes (`cobill.enabled`, `cobill.notice_min_days`,
  `cobill.billing_mode`, `cobill.payment_allocation` e
  `cut.third_party_debt_blocks`), sem renomear nenhuma (PD-TAR-02).
- Não traz débito parcelado para dentro do catálogo: o garfo não está
  coberto pela proposta do item 32 e fica nomeado como pendência
  (PD-TAR-05); `Installments` e os catálogos vizinhos DAD-20
  (`debit_types`/`credit_origins`) ficam onde estão.
- Não decide a fonte da tarifa das soluções individuais de esgoto da
  ED-88 (`sewer.alt_solution_tariff_source` × O7-3): quando a agência
  definir, ela vira uma cobrança de catálogo pelo Pilar 1 — desenho
  registado, gatilho externo (PD-TAR-04).
- Não atribui números Flyway: os SQL novos nascem em `db/pendente/`
  e a numeração global é da integração (PD-TAR-08).
- Não estima esforço por si: o dimensionamento das Camadas 1–4 está
  no `DESENHO-CAMADAS.md` desta camada, com as correções da
  `ERRATA-DESENHO-R02-R05.md` e o redimensionamento do adendo (D-07),
  com estimativas marcadas como estimativas (a proposta, §6, declara
  que nenhuma fonte estima esforço).
- Não implementa, neste bloco, perfil-entidade, parâmetros por
  município, rotas, fiscal, agenda, GIS, portal nem perfil integrado
  — são do bloco `multimunicipio/` (ver §4, fronteira).

## 6. Consequências

**Positivas.** Uma só pergunta passa a ter uma só resposta — "o que
esta unidade deve neste mês?" — com dono, natureza, modelo, versão e
**município do escopo resolvido** de cada linha; a conta ganha
snapshot completo e auditável de todas as cobranças (hoje só há
fotografia de valores + ids de parâmetros sem produtor de produção
localizado); convênio deixa de ser um desenho de um serviço só sem
reescrever a ED-25; o campo continua mínimo, agora alimentado pelo
mesmo catálogo do servidor e resolvido por município da unidade; a
TFDI sai do canal avulso sem mudar um centavo; e a dimensão município
entra **sem retrofit**, porque o catálogo nasce dimensional (cenário
A da análise).

**Negativas / custos.** O núcleo `BillCalculator` é a peça de maior
impacto (ALTO, no mapa da proposta): a Camada 2 mexe no caminho mais
provado do billing e por isso carrega a prova diferencial mais
pesada — agora ampliada aos casos municipais do adendo (D-05). O
catálogo adiciona uma camada de resolução entre o chamador e as
fórmulas — custo de integração declarado, com o snapshot completo
como contrapartida. A generalização da ED-25 toca um pacote
aprovado: mitigada pela regra "parametrizar, nunca reescrever" e
pela suíte da ED-25 como rede (**32 testes em 7 classes** — contagem
mecânica de `@Test` sobre `ed-25/entrega`, correção R-05; a cifra
"27 testes" da versão original vinha do `RELATORIO-ED-25.md` e fica
supersedida por esta contagem, ver errata).

**Neutras / registos.** A P2.20 (correção por nova versão, com
`NfageDisposition`) passa a operar sobre linhas de catálogo — o
modelo de linhas reflete lá sem mudar a regra de retificação. A
ED-38 explica a conta a partir do cálculo gravado: com o snapshot
expandido, a explicação ganha as novas cobranças sem reescrever o
motor (conferir na integração da Camada 2). ED-22/ED-57 alimentam as
calculadoras por um gancho de isenção/benefício **por cobrança**
(Camada 1), sem mudar esses pacotes.

## 7. Alternativas consideradas

- **Big bang (trocar tudo de uma vez):** rejeitada pelo dono na
  própria formulação da execução (item 43: "em camadas") e pela
  proposta (§2): poria em risco, sem rede, as fórmulas provadas em
  CI total.
- **Fórmula única parametrizada:** rejeitada na proposta (§1.2):
  água progressiva, esgoto derivado, fixa, preço de catálogo,
  percentual e valor externo não são a mesma aritmética; uma
  calculadora por modelo preserva cada prova no seu lugar.
- **Duplicar `RevenueNature` no catálogo:** rejeitada — criaria duas
  fontes da mesma classificação legal (Lei 11.445/2007, art. 10-A);
  o desenho importa o enum da ED-34.
- **Resolver a O7-6 junto:** rejeitada — a proposta (§6) e o item 43
  mantêm o contrato de campo vigente; misturar as duas decisões
  contaminaria a prova diferencial das duas frentes.
- **Implementar a dimensão município junto:** **era** rejeitada por
  escopo na versão original (pré-item 45) e fica registada como
  alternativa **supersedida**: o item 45 decidiu-a (COMPLETO, agora)
  e o §4 deste ADR, reescrito, é a sua forma decidida. Não é
  contradição — é a cronologia das decisões do dono (43 às 03:13;
  45 às 03:28).
- **Escopo genérico/opaco (antigo Lado B do PD-TAR-01):** supersedido
  pelo item 45 enquanto semântica — ver adendo D-01: a mecânica
  (coluna nullable + construtor de compatibilidade) aproveita-se; a
  semântica opaca morre e nasce o escopo municipal tipado.

## 8. Referências

- `~/workspace/ziipsan-producao/PROPOSTA-ARQUITETURA-TARIFARIA.md`
  (proposta, itens 1–6).
- `~/workspace/ziipsan-producao/DECISOES-DO-DONO.md` — itens 32
  (proposta registada), 34 (base fictícia; P-BF-06/07/08), 43
  (execução decidida) e 45 (multimunicípio COMPLETO decidido, agora;
  P-BF-06/07/08 decididas; limites mantidos).
- `~/workspace/ziipsan-producao/ANALISE-MULTIMUNICIPIO.md` — análise
  de custo da dimensão município, com o adendo pós-revisão (listas
  fechadas de tabelas; cenário A decomposto), consumida (§4).
- `~/workspace/ziipsan-producao/PROTOCOLO-ESTEIRA.md` — §§2–3
  (papéis, portão) e §3.2 (falsos positivos).
- `DESENHO-CAMADAS.md` (nesta pasta) — desenho dimensionado das
  Camadas 1–4, invariantes INV-Cn-xx e registo de garfos PD-TAR-nn;
  **`ADENDO-DESENHO-MUNICIPIO.md`** (nesta pasta) — parte normativa
  deste ADR para a dimensão município (D-01…D-08, PM-01…);
  **`ERRATA-DESENHO-R02-R05.md`** (nesta pasta) — correções menores
  R-02…R-05 da revisão independente.
- `REVISAO-CAMADA-0.md` (nesta pasta) — revisão independente:
  veredito REPROVADO sobre a numeração original (R-00), achados
  R-01…R-05 e conflitos C45-01…C45-09 com o item 45.
- Código entregue conferido: P2.5 (`p2.5/entrega/` + núcleo em
  `p2.5/espelho/` — ver precisão de caminhos na errata R-03), ED-25
  (`ed-25/entrega/`), ED-34 (`ed-34/entrega/`), ED-32
  (`ed-32/entrega/`), P2.6 (`p2.6/entrega/`), ED-19
  (`ed-19/entrega/`). Nota de conferência: o `p2.5/espelho/` é
  fotografia anterior à TFDI no pacote `bill` (9 campos, sem
  `fixedPartValue`); a forma vigente do `bill` é a da `p2.5/entrega/`
  (10 campos) — divergência de espelho registada no
  `RELATORIO-CAMADA-0.md`, não divergência de facto.

---

*ADR-0048 produzido localmente em 09/11/2026 pela sessão corretora
da Camada 0, sob parada dura de HTTP 429 (zero chamadas ao GitHub),
como ficheiro NOVO de correção — o ADR-0047 original não foi tocado.
Aguarda reapreciação em âmbito limitado (renumeração + erratas +
adendo do item 45) e confirmação independente em sessões distintas
(esteira, estágios 2 e 4) antes de qualquer pouso.*
