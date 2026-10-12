# sigsan-billing — tarifa, conta e créditos/débitos

Motor de tarifa de água e esgoto, cálculo da conta e persistência de tarifa e conta, em **domínio
puro** (sem I/O) separado da persistência. As regras foram reescritas em código próprio a partir da
especificação do levantamento `docs/levantamento/02-gsan-leitura-consumo-faturamento.md` (seções 3.2,
3.8, 3.9, 4, 5 e 10), conferidas com os relatórios 08 e 11. Nenhum código, SQL ou nome do legado foi
copiado.

> **Nada neste módulo é parecer jurídico.** Os relatórios 08 e 11 avisam que exigem revisão por
> advogado, regulatório e contador; a seção "Depende de confirmação" abaixo lista o que fica pendente.

## Princípios

- **Sem dado inventado.** Nenhuma tarifa, faixa, alíquota, percentual de esgoto, percentual ou limite
  de tarifa social, multa ou juros existe no código ou na migration. Ausente = erro explícito
  (`TariffConfigurationException`, `IllegalArgumentException`) ou aviso no resultado. Os valores que
  aparecem nos testes são **fictícios** e rotulados como tal (`FictitiousTariffs`).
- **`BigDecimal` com `RoundingMode` explícito**, nunca `double`
  (`SourceHygieneTest` varre o código de produção).
- **Idempotência no banco**: uma conta "viva" por (unidade, referência).

## Implementado

Pacote base `br.com.sigsan.commercial.billing`.

| Pacote | O quê |
|---|---|
| `tariff` | `TariffEngine`: vigência por **data**; categoria/subcategoria (com reserva na categoria); consumo mínimo e tarifa mínima (franquia) **ou tarifa básica** sem franquia (mínimo 0); faixas progressivas; excedente por economia; consumo mínimo da ligação com fator de economias; **rateio por dias** com duas ou mais vigências no período; esgoto com `pccoleta` (reduz o volume) e `pcesgoto` (multiplica o valor), percentual alternativo para consumo baixo, fator em 2 casas **ou** exato (política explícita); **tarifa social** parametrizável (`SocialTariffRule`) |
| `bill` | `BillCalculator` (água, esgoto, débitos, créditos, impostos, total); `CreditApplier` (crédito nunca excede a conta, resíduo ao mês seguinte); `Installments` (parcela truncada, centavos na última); `TaxWithholding` (retenção configurável, o último absorve o resíduo); `BillGenerationRule` (quando gerar a conta); `BillStatus` e `BillIdempotency`; `DueDateCalculator`; `LateFeeCalculator` (multa e juros, **pro rata die**); `CondominiumApportionment` (rateio macro/micro) |
| `consumption` | `EconomyMultiple` (ajuste ao múltiplo de economias, 3.8); `NonMeteredConsumption` (por área, por parâmetro, mínimo fixado, 3.9); `ExpectedReadingRange` (faixa de leitura esperada, 2.5, veículo da armadilha do `BigDecimal(double)`). Estas três classes tocam o ciclo de leitura/consumo e podem migrar para o módulo de medição se ele assumir a cascata de consumo |
| `math` | `Money` (escala 2, modos de arredondamento nomeados), `Apportionment` (repartição por maior resto, sem perder centavo) |
| `domain`, `repository` | 13 entidades JPA (`extends TenantAwareEntity`, sem `@Builder`, jsonb como `String` com `@JdbcTypeCode`, sem `CHAR(n)`) e os repositórios. **Sem `@ManyToOne`**: referências por UUID, como no registry. As entidades têm sufixo `Entity` para não colidir com os tipos puros (`TariffVersion` × `TariffVersionEntity`) |

Migration `db/migration/tenant/V4_1__create_billing_schema.sql` (depende de V1_2 e V3_1): `tariffs`,
`tariff_versions`, `tariff_category_rules`, `tariff_blocks`, `tariff_social_bands`,
`tax_withholding_rates`, `debit_receivables`, `credit_receivables`, `bills`, `bill_items`,
`bill_debits`, `bill_credits`, `bill_taxes`. Todas terminam em `register_tenant_table` (RLS forçada,
policy, auditoria). Foram acrescentadas ao "modelo mínimo" pedido: `tariff_social_bands` e
`tax_withholding_rates` (parâmetros do tenant) e `debit_receivables`/`credit_receivables`, sem as quais
o resíduo do crédito e as parcelas cobradas não teriam onde viver entre uma conta e outra
(`bill_debits`/`bill_credits` são as linhas dentro da conta).

Invariantes impostos pelo banco (não só pelo código):

- vigências da mesma tarifa **não se sobrepõem** (`EXCLUDE USING gist` com `daterange`; precisa de
  `btree_gist`, já criada no init); faixas de desconto social idem;
- **idempotência**: índice único parcial `idx_bills_unit_reference_live` em `(tenant, unidade, mês)` para
  todos os estados menos `CANCELLED`, `CANCELLED_BY_RECTIFICATION` e `PROCESSING_ERROR` (uma retificação
  convive com a conta que substitui; uma conta com erro pode ser reprocessada);
- a conta confere a própria aritmética: `total = água + esgoto + débitos − créditos − impostos` e
  `créditos ≤ água + esgoto + débitos`; linha de tarifa: `líquido = bruto − desconto`;
- FKs compostas `(tenant_id, id)` para `consumer_units`, `categories` e `subcategories` (registry, V3_1);
- `calc_type` só aceita `BLOCKS`: modos não implementados não podem ser gravados e calculados errado.

## NÃO implementado

- **Ciclo mensal**: grupo de faturamento, cronograma, jobs por rota, simulação, encerramento.
- **Emissão**: PDF/segunda via, código de barras, boleto, PIX, e-mail, gráfica, mensagens da fatura.
- **NFAg-e** (modelo 75) e demais documentos fiscais; cálculo de IBS/CBS (relatório 11, R39 a R44).
- Cancelar, retificar, colocar em revisão, alterar vencimento, incluir conta (só os estados existem).
- Estouro/alto/baixo consumo, média, leitura menor/virada, ajuste mensal por dias (cascata do
  relatório 02, seção 3; pertence à medição).
- Tarifa por fórmula pluginável e "bloco direto" (tipos 2 e 4 do legado).
- Cobrança por disponibilidade, economia inativa, bandeiras, cofaturamento, compensações por
  descumprimento de prazo, reajuste/revisão tarifária (R-09, R-10, R-13, R-16, R-23, R-41).
- Correção monetária de débitos em atraso (precisa de fonte de índices); repetição de indébito.
- Rateio de condomínio **por valor** dentro do cálculo da conta (só o rateio de consumo; a repartição
  de valor está em `Apportionment`).
- Tabelas persistidas de consumo mínimo por área e por parâmetro; tabela de parâmetros de faturamento
  do tenant (`billing_parameters`); calendário de feriados; persistência do percentual de esgoto da
  ligação (pertence ao cadastro da ligação).
- Mapeadores entidade → domínio puro, serviços e API REST. (O `sigsan-app` já depende de
  `sigsan-billing` desde 12/10/2026: a migration V4_1 entra no provisionamento e no `FreshInstallTest`.)
- **Bônus social do legado** (crédito condicionado a "conta anterior paga no prazo"): o próprio
  levantamento o marca como regra de uma concessionária (inferência) e ele **contradiz** a Lei
  14.898/2024 (concessão não pode depender de adimplência). Não foi implementado.

## Tarifa social

**Como funciona.** Cada versão de tarifa pode ter uma `SocialTariffRule`: um percentual de desconto sobre
a **parcela fixa** (informado, zero se não houver) e uma lista de **bandas de volume** `(de, até]` por
economia, cada uma com seu percentual (`até` vazio = sem limite). O desconto incide sobre os m³ que
caem dentro de uma banda, faixa de preço a faixa de preço, e só nas economias que o cadastro marca
como sociais (`EconomyGroup.socialEconomies`). O resultado traz bruto, desconto e líquido por linha.
**Percentual e limite de volume são parâmetros da tabela tarifária do tenant** (`tariff_social_bands`,
`tariff_versions.social_min_charge_discount_pct`); nenhum existe no código. Com bandas isso cobre, sem
mudar código, desenhos diferentes de agência (uma banda de 0 a N m³, duas bandas escalonadas, banda
sem limite, desconto também na parte fixa). Quem preferir tratar "residencial social" como categoria
própria (NR 13, art. 6º) pode simplesmente dar a essa categoria a sua própria regra de preços.

**Confirmado no relatório 08 e honrado aqui:** o desconto é sobre a tarifa, por volume, com
percentual e limite definidos pela agência/tabela; a concessão **não depende de adimplência** (a
assinatura do cálculo não recebe situação de pagamento); uma economia por família é cadastro, não
cálculo.

**Fora deste módulo (depende do cadastro, do importador e da mensageria):** classificação automática
por CadÚnico/BPC, desempate de economia por família, aviso na fatura por 3 meses antes da perda,
permanência de 3 meses, relatório anual, Índice de Tarifa Social (R-15), cashback de IBS/CBS (R41).
O prazo apontado pelo relatório (11/12/2026) é do prestador, não deste cálculo.

**Depende de confirmação jurídica** (nada disto foi decidido por palavra do relatório):

1. Se o desconto de 50% até 15 m³ (Lei 14.898/NR 13) incide sobre a **parcela fixa** ou só sobre os m³
   das faixas. Modelado como parâmetro explícito, sem padrão (AGEPAR estende à parte fixa; outras
   agências não).
2. Se o desconto **também reduz o esgoto** (`SewerParameters.applySocialDiscount`, explícito, sem padrão).
3. A data de vigência da Lei (10, 11 ou 12/12/2024) e a aplicação a contratos anteriores à NR 13 (E-03).
4. Percentual e limite de volume por agência (ex.: 30 m³ na ADASA, E-15; 25% de 15 a 20 m³ na ARES-PCJ;
   sem limite na ARSAE-MG). Os arranjos dos testes são só ilustrações do mecanismo.
5. O que a NR 13 chama de faixas "crescentes **não cumulativas**" (R-06). O motor implementa faixas
   **progressivas** (cada m³ ao preço da sua faixa). Se "não cumulativa" significar preço único da
   faixa onde o consumo cai, falta o modo "bloco direto", que **não existe** e é recusado pelo CHECK.

## Parâmetros por tenant

Nada abaixo tem valor padrão. "Persistido" = tabela desta migration; "entrada" = o chamador monta a
partir de configuração do tenant que ainda não tem tabela.

| Parâmetro | Onde |
|---|---|
| Versões, categorias, subcategorias, franquia, tarifa mínima/básica, faixas, fator de economias | persistido: `tariffs`, `tariff_versions`, `tariff_category_rules`, `tariff_blocks` |
| Tarifa social: percentual da fixa, bandas (de, até, %) | persistido: `tariff_versions`, `tariff_social_bands` |
| Tributos retidos, alíquotas e ordem (com vigência) | persistido: `tax_withholding_rates` |
| `pcesgoto`, `pccoleta`, percentual alternativo e seu limite | entrada (`SewerParameters`); pertencem à ligação de esgoto |
| Política do fator de esgoto (2 casas × exato); desconto social no esgoto | entrada (`SewerParameters`); decisão do tenant |
| Multa (única ou por dia, teto), juros (% por N dias, teto), carência em dias úteis | entrada (`LateFeePolicy`) |
| Calendário de dias úteis/feriados | entrada (`Predicate<LocalDate>`) |
| Dia de vencimento da rota, mínimo de dias, dias extras de correio, meses de validade | entrada (`DueDateCalculator`) |
| Incremento máximo de rateio por economia | entrada (`CondominiumApportionment`) |
| Tabelas de consumo por área e por parâmetro; o que fazer com parâmetro ausente | entrada (`NonMeteredConsumption`) |
| Débitos a cobrar e créditos a realizar (parcelas, resíduo) | persistido: `debit_receivables`, `credit_receivables` |

## Arredondamento (cada operação)

| Operação | Modo |
|---|---|
| Valor de linha de faixa e de parcela fixa (`preço × m³ × economias`) | HALF_UP, 2 casas |
| Desconto social | HALF_UP, 2 casas, uma só divisão |
| Rateio por dias: fator de cada vigência | HALF_UP, 4 casas; o último fator é `1 − Σ anteriores` |
| Rateio por dias e fator de esgoto: valor da categoria | HALF_UP, 2 casas; linhas repartidas por maior resto |
| Volume de esgoto (`pccoleta`) | HALF_UP, ao m³ |
| Fator de esgoto | HALF_UP a 2 casas **ou** exato (política explícita) |
| Parcela de débito/crédito | DOWN, 2 casas; centavos na última |
| Base do imposto retido | DOWN, 2 casas |
| Imposto retido e total retido | HALF_DOWN, 2 casas (herdado do legado; nenhuma norma lida define, E-20) |
| Multa e juros | HALF_UP, 2 casas, uma só divisão |
| Faixa de leitura esperada | HALF_UP, ao inteiro |
| Média, divisão por economia, rateio de consumo | divisão inteira (trunca), como no legado |

## Decisões e premissas (onde a especificação é omissa ou foi corrigida)

1. **Fator de economias**: a seção 4.2 usa o número de economias; a 3.2 usa o fator. O motor segue ao pé
   da letra: o fator só entra no consumo mínimo da ligação. `EconomyMultiple` falha, em vez de
   inventar consumo, quando o fator torna o ajuste inconsistente.
2. **Percentual alternativo de esgoto**: compara-se o **volume de esgoto por economia** (após `pccoleta`)
   com o limite; o relatório não diz se é o volume ou o consumo de água.
3. **Rateio por dias**: o valor da categoria é arredondado uma vez; as linhas somam exatamente esse valor
   (maior resto). Difere do legado só nas linhas internas, e nos fatores quando o arredondamento
   independente não fecha 1,0000.
4. **Impostos**: a fórmula "o último = total − Σ anteriores" dá imposto **negativo** em bases pequenas
   (com 1,20/1,00/3,00/0,65%, de R$ 0,51 a R$ 0,59). O excedente passa ao imposto anterior, e assim
   por diante; nos demais casos o resultado é idêntico ao da fórmula.
5. **Juros**: pro rata die, nunca por mês de calendário (armadilha 7). Por isso J-01, J-02 e J-04 do
   relatório têm outro resultado (ver abaixo).
6. **Crédito**: parcela do mês + resíduo; aplicado = mínimo entre isso e o que resta da conta. O ramo
   do legado para "última parcela já concedida na referência" (reprocessamento) não existe: a
   idempotência da conta cobre.
7. **"Sem consumo"** (geração da conta) = valor de água e de esgoto iguais a zero.
8. **Parâmetro de cadastro ausente** (moradores/pontos): o legado supunha 1. Aqui só se usa um valor
   se o tenant o informar explicitamente, e o resultado traz aviso.
9. **Rateio de condomínio**: `aRatear = MAX(0, macro − Σ micros)`, como exige RC-02; logo o parâmetro de
   decremento máximo do legado é inalcançável e não é modelado.
10. **Vencimento (V-03)**: preservado o comportamento do legado, que o relatório marca "a decidir":
    com dia alternativo anterior ao da rota, vale o dia da rota.

## Divergências em relação ao relatório 02

Todos os valores esperados do relatório foram recalculados à mão, de forma independente, antes de
virarem teste. Os de T, M, E, N, D, C, I, G, V, RC e R conferem. Divergem, de propósito:

| Caso | Relatório | Aqui | Motivo |
|---|---|---|---|
| J-01 | juros 1,00 (total 3,00) | 1,10 (total 3,10) | juros por dia (33 dias × 1% / 30) |
| J-02 | juros 0,00 | 0,33 (total 2,33) | idem; 10 dias de atraso |
| J-04 | juros 1,00 por 1 dia de atraso | 0,03 | armadilha 7 |
| N-04 | moradores nulos viram 1 | erro, ou 1 **com aviso** se o tenant mandar | não esconder ausência de dado |
| I-* (seção 4.6) | último imposto = total − Σ anteriores | idem, mas sem valor negativo | ver decisão 4 |
| D-02 | 3,34 / 6,66 (resíduo à 1ª categoria) | idem (`residueToFirst`); `proportional` dá 3,33 / 6,67 | política do legado preservada; a alternativa existe e a escolha é do produto |

Pontos do relatório que conferem mas merecem correção de texto: no T-09 o total 113,49 sai com o fator
de 4 casas (97,07 + 16,42); arredondar cada parcela pela proporção exata de dias daria 97,07 + 16,43 =
113,50, e é esse o "0,01 de divergência" que o relatório menciona; o último fator fechando 1,0000 não
corrige isso em geral, só garante que os fatores somam 1. No RC-02 o texto do pseudocódigo
(`MAX(0, ...)`) e o parâmetro de decremento se contradizem.

Casos A, L e X (média, leitura, estouro/alto/baixo) não estão aqui: são a cascata de consumo da
medição.

## Depende de outros módulos / dívida regulatória futura

Do relatório 08 (tarifas):
R-05/R-06/R-07 (categorias, tarifa básica × consumo mínimo, hidrômetro único com várias economias:
a conta já discrimina por categoria); R-08 (esgoto: proporção, exigência de tratamento por contrato);
R-09 (disponibilidade: 2× a tarifa básica, aviso de 90 dias); R-10 (economia inativa); R-11/R-12
(fatura por modelo da ERI, dados do fornecedor, **mensagens obrigatórias com prova de entrega**);
R-13 (reajuste: data-base, índice, publicação ≥ 30 dias antes da vigência; hoje só
`published_on`/`regulatory_act` como rastro); R-14 (tarifa social: importador CadÚnico/BPC,
classificação, fila manual, histórico, relatório); R-16/R-42 (cofaturamento, separação de débitos,
"serviço de origem" e "pode motivar corte" em cada linha); R-18 (multa e juros por tenant, correção por
índice, CDC art. 52 §1º, pergunta E-02); R-21/R-40 (relatórios e exportadores por agência); R-39 (perfil
regulatório com vigência: `regulatory_parameter`); R-41 (compensações automáticas).

Do relatório 11 (cobrança, fiscal):
**NFAg-e obrigatória em 01/12/2026** (R39); alíquotas de IBS/CBS por competência e ente, nunca fixas
(R40); `tax_event_date` separado de vencimento e emissão (R42); reequilíbrio contratual (R43); declaração
anual de quitação (R35); opções de data de vencimento (R36); retenção mínima de **120 meses** do
histórico de leitura e faturamento, compatível com a LGPD (R38; nada neste módulo apaga conta);
retenção na fonte: dispensa de valor pequeno e regra para autarquia **não confirmadas** (R46); fim de
PIS/Cofins em 2027 (as alíquotas têm vigência); sem PII em URL (R49).

## Testes

Unitários puros (JUnit 5, parametrizados), sem banco. Rodar:

```bash
export SIGSAN_BUILD_DIR="<pasta local visível ao Docker>"
bash scripts/build.sh -pl commercial/sigsan-billing -am test
```

Cobrem: T-01..T-10 e T-08/T-09 (vigências), três vigências, buracos e sobreposição; E-01..E-06;
tarifa social (cinco arranjos, economias mistas, vigências, esgoto); M-01..M-07; N-03..N-05 (e N-01/N-02
no motor); R-01..R-08; D-01/D-02; C-01..C-04 e propriedade de soma; I-01..I-03, resíduo e propriedade
sobre 300 mil bases; G-01..G-05; V-01..V-05; J-01..J-06; RC-01..RC-05; invariantes da seção 10.16; as
armadilhas da seção 7 (`BigDecimal(double)`, "média 105 dá 1074 e não 1073", esgoto 87,5%, resto da
divisão inteira, rateio por valor, juros por mês de calendário, `println`, exceção engolida).

`MigrationConsistencyTest` lê a migration como texto e a compara com as entidades por reflexão (tipo,
tamanho, precisão, nulabilidade, colunas obrigatórias sem mapeamento), com `BillStatus` (CHECK de estados e
índice de idempotência), com o registro de RLS e com a ausência de dado semeado; não precisa de banco.
