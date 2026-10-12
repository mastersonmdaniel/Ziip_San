# sigsan-metering

Hidrômetro, leitura e **consumo faturado** (a cascata de cálculo). Pacote base
`br.com.sigsan.operations.metering`.

O GSAN é referência de domínio aprovada pelo dono do projeto (relatório
`docs/levantamento/02-gsan-leitura-consumo-faturamento.md`). Tudo aqui foi escrito do zero a partir das
regras descritas lá: nenhum código, SQL, nome de coluna `abcd_*` ou texto do GSAN foi copiado.

Regra do módulo: **nenhum número de política no código de produção.** Tarifa, limites de consumo,
patamares de estouro, faixas de leitura, anormalidades e janelas vêm do prestador (por tenant), como
parâmetro. O que falta é erro ou aviso, nunca um valor presumido calado.

## O que foi implementado

### 1. Domínio puro (`rules/`), sem I/O

| Classe | Papel |
|---|---|
| `ConsumptionCalculator` | A cascata do consumo de água de uma unidade em um mês (relatório 02, seção 3). Entrada `ConsumptionInput` + política `ConsumptionPolicy`, saída `ConsumptionResult` com **tipo**, **anormalidade** e **avisos**. |
| `AverageConsumption` | Consumo médio (3.1): retroação, elegibilidade por tipo e anormalidade, fallback para o mínimo da ligação com aviso. |
| `ConnectionMinimum` | Consumo mínimo da ligação (3.2). |
| `ReadingValidator` | Validação do retorno de leitura (2.4) e faixa de leitura esperada (2.5), com a virada do mostrador. |
| `NonMeteredConsumption` | Ligação sem hidrômetro (3.9): por tarifa, área, pontos de utilização ou moradores. |
| `SewageVolumeCalculator` | **Volume** de esgoto (3.10): poço, mínimo de esgoto, percentual de coleta. O valor em reais é do billing. |
| `CondominiumApportionment` | Rateio de consumo macro/micro (3.11). |

Ordem dos passos da cascata, para ligação com hidrômetro:

1. média e consumo mínimo da ligação;
2. leitura: hidrômetro substituído no período; atual maior (com checagem de faixa esperada), igual
   (hidrômetro parado) ou menor (virada ou leitura inválida) que a anterior; ou ausência de leitura;
3. anormalidade de leitura (informada ou gerada), com e sem leitura atual;
4. ajuste mensal por dias, se a rota o faz;
5. estouro, alto e baixo consumo, com a ação escalonada por reincidência (mês 1, 2 ou 3);
6. situação especial de faturamento (fixa o consumo e/ou paralisa o faturamento);
7. consumo mínimo fixado na ligação, ou consumo informado;
8. ajuste de múltiplas economias (o resto não é faturado);
9. permissão de faturar pelo tipo de consumo (o faturado zera; o calculado fica, é a base da média).

Para ligação sem hidrômetro: consumo mínimo fixado, ou 3.9; depois situação especial e permissão.

Uso típico (o job por rota monta a entrada a partir do banco):

```java
ConsumptionCalculator calculator = new ConsumptionCalculator(policyOfTheTenant);
ConsumptionResult result = calculator.calculate(input);   // lança MeteringInputException se faltar dado
ConsumptionRecord record = /* find ou new, por (unidade, serviço, referência) */;
record.fillFromWater(result, anomalyIdOfKind(result.anomaly()), readingAnomalyIdOf(result.billedReadingAnomalyCode()));
```

### 2. Persistência

* Migration `src/main/resources/db/migration/tenant/V5_1__create_metering_schema.sql`.
* Entidades JPA em `domain/` (`extends TenantAwareEntity`, sem `@Builder`, jsonb como `String` com
  `@JdbcTypeCode(SqlTypes.JSON)`, sem `CHAR(n)`) e repositórios em `repository/`.

| Tabela | Conteúdo |
|---|---|
| `meters` | Hidrômetro: número, marca/modelo, dígitos do mostrador, aprovação do Inmetro, situação. |
| `meter_installations` | Histórico temporal. `EXCLUDE USING gist` com `btree_gist`: uma unidade não tem dois hidrômetros do mesmo tipo (água, poço) ao mesmo tempo, e um hidrômetro não está em duas unidades ao mesmo tempo. Intervalo `[instalação, retirada)`: troca no mesmo dia é permitida. |
| `reading_anomalies` | Catálogo, **sem semente**. Quatro efeitos obrigatórios e sem padrão. `system_role` substitui os ids numéricos fixos do legado. |
| `consumption_anomalies` | Catálogo, **sem semente**. `kind` liga a linha à condição que a cascata detecta. |
| `meter_readings` | Leitura aceita (a recusada vai para o relatório de erros e não é gravada). |
| `consumption_records` | Consumo faturado por (unidade, serviço, referência) com tipo e anormalidade gravados, avisos e snapshot em jsonb. Único por (tenant, unidade, serviço, referência): refaturar atualiza, não duplica. |

Todas terminam em `SELECT register_tenant_table(...)`. **Nenhuma usa `p_audit => false`**: leitura e
consumo são uma linha por unidade e mês (não é série temporal de alto volume), e a correção de uma
leitura é exatamente o que precisa de trilha. Quando existir a tabela bruta de telemetria (leituras de
minuto a minuto), essa sim deve usar `p_audit => false`.

FKs compostas `(tenant_id, id)` para o pai; a unidade consumidora (`consumer_units`, V3_1) é referenciada
por UUID. O `usuário` do leiturista (`reader_id`) não tem FK porque `users` não tem
`UNIQUE (tenant_id, id)`.

### 3. Testes

`src/test`: **200 testes JUnit 5**, todos unitários puros (sem banco, sem Spring). Os casos do relatório
02 seção 10 que cabem ao módulo estão parametrizados: R-01 a R-08, L-01 a L-07, A-01 a A-04, X-01 a X-06,
M-01 a M-03, N-01 a N-05, RC-01 a RC-04 e os volumes de E-01, E-02 e E-06. Cada resultado esperado foi
recalculado à mão, de forma independente, e depois conferido com o relatório; a conta está no
comentário de cada teste. **Todos os valores de teste são fictícios** (`Fixtures.java`); o código de
produção não tem nenhum.

Também há invariantes varridos (resto de múltiplas economias, leitura faturada sempre dentro do
mostrador, faixa esperada sempre contém "anterior + média") e `MigrationConsistencyTest`, que mantém a
migration, os enums e as entidades de acordo sem banco (CHECKs = enums, colunas = campos, nenhum
`INSERT`, toda tabela registrada).

Como rodar:

```bash
export SIGSAN_BUILD_DIR="<pasta local visível ao Docker>"
bash scripts/build.sh -pl operations/sigsan-metering -am test
```

## Como o SQL e o mapeamento foram verificados (e o que não foi)

* Não foi rodado o `FreshInstallTest` nem o banco de integração compartilhado (regra desta entrega).
* A migration foi aplicada, na ordem das versões (V1_1 a V3_1, depois V5_1), num PostgreSQL 15 + PostGIS
  **descartável e privado** (container próprio, já removido), e as constraints foram exercitadas:
  sobreposição na unidade, hidrômetro em duas unidades, troca no mesmo dia, intervalo vazio, retirada sem
  leitura, catálogo sem efeitos, efeito inválido sem leitura, `system_role` duplicado, idempotência de
  consumo, referência fora do dia 1, FK composta.
* O mapeamento das seis entidades foi validado com `hibernate.hbm2ddl.auto=validate` contra esse banco, e
  um roundtrip (persistir, limpar, recarregar, inclusive jsonb e enums) passou com a RLS e o trigger de
  auditoria ativos. Esse teste de rascunho foi apagado depois.
* **Resolvido (12/10/2026):** `platform/sigsan-app/pom.xml` depende de `sigsan-metering`; a migration
  V5_1 e as entidades entram no provisionamento e no `FreshInstallTest`.

## O que NÃO foi implementado

* **Ligação cortada com dias de corte** (relatório 02, 3.3 passo 9): o resultado traz o aviso
  `CUT_CONNECTION_DAYS_RULE_NOT_IMPLEMENTED` sempre que a ligação está cortada.
* **Ajuste por dias para hidrômetro novo/substituído** ("consumo diário desde a instalação", mínimo se
  `diasConsumoLido <= 9`): o ajuste não é aplicado e há aviso. Também não é somado o consumo do
  hidrômetro anterior até a retirada (aviso `REPLACED_METER_PRIOR_CONSUMPTION_EXCLUDED`).
* **Efeito de anormalidade "fixo"** (código 7 do legado): o relatório não diz de onde vem o valor fixo.
* **Rateio por área comum** (tipo 5), rateio com poço no macro, e o rateio por **valor** em reais (RC-05,
  "maior resto"): são do faturamento.
* **Valor do esgoto** (percentual de esgoto sobre a tarifa, percentual alternativo, política do fator de 2
  casas, E-03 a E-05): é do billing. Aqui só o volume.
* **Tarifa** (T-*, vigências), conta, créditos/débitos, impostos, acréscimos, vencimento (V-*, J-*, I-*,
  C-*, D-*, G-*): módulos `billing` e `dunning`.
* **Mensagem na conta, carta e OS** das ações de anormalidade (`consumption_anomaly_action` do legado): o
  cálculo devolve o mês de escalonamento aplicado; a tabela de ações não foi criada (as ações entram na
  `ConsumptionPolicy`, montada pelo chamador).
* **Suspensão de leitura por anormalidade recorrente**, **faixa falsa e fiscalização amostral**,
  **geração do roteiro, pacotes, app offline, telemetria bruta**: são do módulo `sigsan-reading`. As
  colunas do catálogo (`suspend_*`) existem, sem lógica.
* **Serviços de aplicação** (job por rota, resolução do catálogo, leitura do histórico), controllers e
  eventos. O módulo entrega a regra, o modelo e os repositórios.
* **Onboarding do catálogo**: o tenant precisa criar uma linha de `consumption_anomalies` por `kind`
  (e as de `reading_anomalies` que usar, com `system_role` para hidrômetro parado). Não há semente.
* **`consumer_units.meter_id`** (registry, V1_1) continua um UUID solto: o vínculo correto agora é
  `meter_installations`. Quem for dono dessa coluna deve removê-la ou derivá-la da instalação vigente.

## Arredondamento: uma regra documentada por operação

| Operação | Regra |
|---|---|
| Consumo médio | Truncamento (`DOWN`) de soma / meses. 125/6 = 20,83 vira 20. |
| Ponta da faixa de leitura esperada | `HALF_UP` para inteiro, sobre fatores `BigDecimal` exatos; depois módulo `10^dígitos`. |
| Leitura ausente fora da tolerância | Média diária com 3 casas `HALF_DOWN`, vezes os dias, `HALF_UP` para inteiro. |
| Ajuste por dias | Consumo diário com 4 casas `HALF_UP`, vezes os dias, `HALF_UP` para inteiro (consumo e leitura). |
| Fator de anormalidade de leitura | Truncamento (`DOWN`). |
| Fator da ação de estouro/alto/baixo | `HALF_UP` para inteiro. |
| Patamares "vezes a média" | `HALF_UP` para inteiro. Baixo consumo compara sem arredondar (`consumo x 100 < média x %`). |
| Múltiplas economias | Divisão inteira (`DOWN`); o resto é descartado **com aviso**. |
| Área virtual (não medido) | 2 casas `HALF_UP`. |
| Volume de esgoto | `volume x coleta / 100`, `HALF_UP` para inteiro; não vale para tipos de média. |
| Rateio de condomínio | Divisão inteira (`DOWN`); resto e corte por teto são avisados. |

Os modos de arredondamento são constantes documentadas em cada regra, não parâmetros. O truncamento da
média e o `HALF_UP` do volume de esgoto são candidatos a política do prestador (armadilha 3 do relatório).

## Decisões regulatórias e de política parametrizáveis por tenant

Tudo isto chega por parâmetro (`ConsumptionPolicy`, `ConsumptionInput`, catálogos), nunca do código:

* Meses da média, teto de retroação, tipos de consumo e anormalidades que entram na média
  (`AveragePolicy`; norma da agência reguladora, relatório 08 R-04).
* Faixas e fatores da leitura esperada (`ExpectedRangePolicy`).
* Plausibilidade da virada: consumo máximo e múltiplo da média (`RolloverPolicy`).
* Tolerância de dias para faturar a média sem leitura; tolerância do ajuste por dias; piso da leitura que
  acusa hidrômetro parado.
* Janela de aceitação da data da leitura (`ReadingValidator`, meses para cada lado da referência).
* Qual anormalidade de leitura é "hidrômetro parado" e "parado sem consumo" (`system_role`).
* Efeitos de cada anormalidade de leitura, com e sem leitura (catálogo).
* Patamares de estouro, alto e baixo consumo, por categoria (`CategoryThresholds`), e as ações com
  escalonamento de 3 meses (`ConsumptionAnomalyAction`).
* Modo de cálculo do não medido (tarifa, área, pontos, moradores) e as tabelas de cada um.
* Consumo mínimo por economia (vem da tarifa), economias equivalentes, consumo mínimo fixado na ligação.
* Quais tipos de consumo permitem faturar em cada situação de ligação.
* Situação especial de faturamento (vigência, consumo fixo, paralisação).
* Percentual de coleta e volume mínimo de esgoto; teto de incremento do rateio de condomínio.
* Mensagem na conta e se cada anormalidade entra na média (`consumption_anomalies`).

## Interpretações e divergências em relação ao relatório 02

O relatório é uma fonte secundária calculada por agente. Nenhum **resultado numérico** de caso de teste
divergiu da conta feita à mão; as divergências abaixo são de regra, texto e invariantes:

1. **Ordem dos passos.** O resumo (0.2) põe estouro/alto/baixo **antes** do ajuste por dias; o detalhe
   (3.3, passos 5 e 6) põe **depois**. Seguido o detalhe: comparar com patamares mensais só faz sentido
   depois de normalizar o consumo ao ciclo.
2. **Faixa que atravessa a virada.** Se só a ponta final passa de `10^dígitos - 1`, a faixa vira
   `[início, fim do mostrador] + [0, fim]`. O teste do legado ("menor que o início OU maior que o fim")
   acusaria toda leitura como fora da faixa. `ExpectedReadingRange.contains` trata a virada.
3. **Invariante 10.16 ("faturado é múltiplo de economias").** É falso quando a soma dos mínimos não é
   múltiplo do número de economias: RES 1 (mínimo 10) + COM 1 (mínimo 7), medido 25, fatura 25. O
   invariante correto é "(faturado - mínimo) é múltiplo de economias" (com excesso) ou "faturado é
   múltiplo" (sem excesso). Os testes usam o correto.
4. **Múltiplas economias com economias equivalentes (3.8).** A fórmula só é coerente quando o mínimo da
   ligação é a soma dos mínimos por economia reais. Com equivalentes o resto pode ficar negativo e o
   consumo seria **inflado** (3 economias, 1 equivalente, medido 12: faturaria 30). A cascata recusa
   (`INCONSISTENT_ECONOMY_ADJUSTMENT`).
5. **Rateio, limite de decremento (3.11).** `aSerRateado = MAX(0, ...)` nunca é negativo, então o limite
   de decremento do relatório nunca age. Não há parâmetro de decremento.
6. **Constantes soltas no legado, hoje parâmetros:** `30` em `|dias - 30|` (virou `cycleDays`), `200` e
   `2 x média` da virada, `3` do ajuste por dias, `atual > 1` do hidrômetro parado, janela de `±1` mês.
7. **Efeito "mínimo" assimétrico (3.4):** sem leitura é o mínimo da ligação, com leitura é **zero**.
   Preservado como está; confirmar com o prestador. O legado tem dois códigos para "média" (0 e 2) e dois
   para "leitura informada" (3 e 4) que se comportam igual; aqui há um de cada.
8. **Retroação da média.** O relatório diz que o mês sem registro "retroage". Interpretado que o mês com
   registro **não elegível** também gasta o teto de retroação.
9. **Reincidência de estouro.** `ESTOURO_COBRANCA_MEDIA` (cobra a média) e `ESTOURO_CONSUMO` são a mesma
   família ao olhar o histórico; sem isso o estouro seguinte a um mês cobrado pela média nunca seria
   reincidente. **Lacuna:** o relatório não diz o que fazer com reincidente acima do consumo máximo;
   o consumo é mantido, a anormalidade registrada e há aviso `BURST_RECURRENCE_ABOVE_MAXIMUM` (revisão).
10. **Escalonamento 1/2/3.** Mês 1 = não ocorreu no mês anterior; 2 = só no anterior; 3 = nos dois meses
    anteriores. Ocorrência só em ref-2 conta como mês 1.
11. **Ajuste por dias** só atua sobre consumo de diferença de leituras (REAL/ESTIMADO), não sobre média
    já substituída por regra. O relatório não restringe; restringido por coerência.
12. **Mínimo fixado x informado (3.3 passo 8).** O "senão se" foi lido literalmente: se o mínimo fixado
    existe mas **não dispara** (consumo >= mínimo), o consumo informado ainda vale.
13. **Leitura anterior base:** igual à informada quando a anterior informada é igual à atual informada
    (hidrômetro parado); senão a faturada. Hidrômetro "substituído no período" = instalado depois da leitura
    anterior e até a atual (o relatório cita ainda "mês anterior com leitura/anormalidade", sem detalhar).
14. **Leitura faturada** (que vira a "anterior" do mês seguinte) não muda com a ação de estouro/alto/baixo
    (o relatório é silencioso); muda com anormalidade de leitura, ajuste por dias e resto de economias.
15. **Não medido:** a multiplicação difere entre modos (economias equivalentes na área; economias reais
    nos parâmetros), como o relatório descreve. Área ausente é erro (o legado usa "menor área da faixa ou
    1"); moradores/pontos ausentes assumem 1 **com aviso**. O ajuste de múltiplas economias **não** vale
    para não medido (N-02: 20, não 18).
16. **Faturamento paralisado** zera o faturado e mantém o calculado (base da média).

## Dependências e premissas

* Depende de `sigsan-core` (entidade base, `Json`). A migration depende de V1_1/V1_2 (helper) e V3_1
  (registry: `consumer_units` com `UNIQUE (tenant_id, id)`), e de `btree_gist`, que o init do banco
  (`infra/postgres/init/01-security-roles.sql`) cria uma vez por banco; a migration **não** a cria (num
  tenant ela cairia no schema dele) e para com mensagem clara se faltar.
* `reference` é sempre o **dia 1** do mês (competência), com `CHECK`; use `setReferenceMonth(YearMonth)`.
  Alinhar com o módulo de faturamento antes de juntar as tabelas.
* Unidade de consumo: m³ inteiros. Leitura: inteiro de 1 a 9 dígitos.
* `ConsumptionResult.calculatedConsumption` é a base da média futura (a coluna `calculated_consumption`):
  `AveragePolicy` espera receber esse valor no histórico, não o faturado.
