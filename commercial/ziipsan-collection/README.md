# ziipsan-collection: arrecadação, boleto e retorno bancário

Módulo `commercial/ziipsan-collection` (pacote `br.com.ziipsan.commercial.collection`). Regras de domínio
**puras** (sem I/O, sem relógio, sem valor padrão) mais a persistência do tenant. Nada aqui é
semeado: convênios, índices de correção, feriados e percentuais são dados do tenant.

O GSAN foi usado só como referência de domínio (relatório 03). **Nenhum código, SQL ou layout dele foi
copiado**; os layouts bancários vêm das especificações listadas abaixo.

## Especificações usadas (versão e URL)

| Fonte | Versão / data | URL | Como foi lida |
|---|---|---|---|
| FEBRABAN, "Layout Padrão de Arrecadação/Recebimento com Utilização do Código de Barras" | **versão 08**, vigência 01/06/2026 (arquivo de 11/05/2026) | https://cmsarquivos.febraban.org.br/Arquivos/documentos/PDF/Layout%20-%20C%C3%B3digo%20de%20Barras%20-%20Vers%C3%A3o%208%20-%2011_05_2026.pdf (índice: https://portal.febraban.org.br/pagina/3425/33/pt-br/layout-febraban) | Texto extraído do PDF em 11/10/2026. Seções 01 a 10 lidas (código de barras, retorno A/G/Z, DV módulo 10 e 11, com exemplos numéricos). As páginas do "formulário padrão" (seção 11) são imagens e não foram lidas |
| Banco Santander, "Código de Barras - Cobrança" | **v36**, abril/2025 | https://cms.santander.com.br/sites/WPS/documentos/arq-cobranca-barras-port-jul22/25-05-06_134319_codigo-de+barras-santander-abril-2025-v36-ptbr.pdf | Texto extraído do PDF em 11/10/2026. Única fonte oficial (de banco) obtida para o boleto de cobrança: estrutura FEBRABAN de 44 posições, módulo 11, linha digitável de 47, fator de vencimento com a nova data-base e um exemplo numérico completo |
| Resolução BCB 443/2024 | texto vigente (alterada pelas Res. BCB 467/2025, 515/2025, 596/2026) | API do BCB: `https://www.bcb.gov.br/api/conteudo/app/normativos/exibenormativo?p1=Resolu%C3%A7%C3%A3o%20BCB&p2=443` | Texto completo baixado e pesquisado: **não contém** as expressões "código de barras", "fator de vencimento" nem "linha digitável" |
| CDC art. 52 §1º (Lei 8.078/1990, redação da Lei 9.298/1996) | citado no relatório 11 | https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm | Não relido por mim; vem do relatório 11 (confiança "alta" lá) |

## Implementado

1. **Código de barras de arrecadação (produto 8)** (`barcode`): `CollectionBarcode` gera e lê as 44
   posições e a linha digitável de 48 (4 blocos de 11 + DV). Segmentos 1 a 7 e 9, identificador de
   valor 6/7 (módulo 10) e 8/9 (módulo 11), valor de 11 posições, empresa de 4 posições (8 no segmento 6),
   campo livre de 25 (21 no segmento 6). `CheckDigits` tem os três DV distintos (módulo 10, módulo 11
   da arrecadação, módulo 11 da cobrança).
2. **Layout por convênio como dado, nunca subclasse** (`FreeFieldLayout`, `AgreementBarcodeLayout`):
   o campo livre é uma lista de trechos (vencimento `AAAAMMDD` obrigatoriamente primeiro, números de
   tamanho fixo, constantes, dígito verificador interno). Serializa em JSON na coluna
   `collection_agreements.barcode_layout`. Gera e lê, e rejeita código de outro convênio.
3. **Boleto de cobrança bancária** (`boleto`): `BankSlipBarcode` (44 posições, DV módulo 11, linha
   digitável de 47 com 3 DV módulo 10), `BankSlipLayout` (campo livre de 25 como dado) e
   `DueDateFactor`.
4. **Fator de vencimento com a virada de 22/02/2025**: ciclo legado (base 07/10/1997, 9999 em
   21/02/2025) e ciclo atual (22/02/2025 = 1000, 9999 em 13/10/2049). A leitura exige referência e
   janela; ver "Decisões".
5. **Acréscimos por impontualidade** (`surcharge`): `SurchargeCalculator`, `SurchargePolicy`,
   `FineRule` (trava de 2%), `InterestRule`, `CorrectionRule`, `BusinessCalendar`.
6. **Classificação de pagamentos** (`payment`): `PaymentClassifier`, 15 situações, tolerâncias e
   política de duplicidade por tenant.
7. **Retorno FEBRABAN 150** (`returnfile`): `Febraban150ReturnParser` (A, G, Z) e
   `Febraban150Validator` (NSA, convênio, banco, versão, trailer, coerência G x código de barras).
   Arquivo de teste **sintético**: `src/test/resources/samples/febraban150-retorno-SINTETICO.ret`
   (banco 999, convênio SINT-0001; nenhum dado real).
8. **Persistência**: 7 entidades (`extends TenantAwareEntity`, sem `@Builder`, jsonb como `String` com
   `@JdbcTypeCode(SqlTypes.JSON)`) e `V6_1__create_collection_schema.sql`.

### Tabelas (V6_1)

`collection_agreements`, `bank_return_files`, `payments`, `payment_status_events`,
`surcharge_indexes`, `holidays` e uma sétima, `collection_policies`, que **não estava na lista do
pedido**: sem ela os parâmetros do tenant (multa, juros, carência, tolerâncias) não teriam onde
morar. Todas terminam em `register_tenant_table`; FKs compostas `(tenant_id, id)`; nenhuma coluna
de regra tem `DEFAULT`; sem `CHAR(n)`; sem dado semeado; `payments` não guarda dado pessoal
(`unit_id` e `document_id` são UUID sem FK, e nunca vão em URL ou query string).

## Parâmetros por tenant (nenhum tem valor padrão)

| Parâmetro | Onde | Observação |
|---|---|---|
| Multa (%), teto próprio e norma que o permite | `collection_policies.fine_*` | Trava de **2,00%** (CDC art. 52 §1º) no código e no CHECK da tabela; acima disso só com teto próprio **justificado** |
| Juros: `NONE` ou `SIMPLE_PER_DAY` (taxa mensal, base de dias do mês, teto) | `interest_*` | Só juros simples pro rata die (ver "Decisões") |
| Correção: `NONE` ou `BY_INDEX` (índice, meses mínimos, aceita negativa) | `correction_*` + `surcharge_indexes` | Fator ausente lança `MissingIndexException` |
| Carência em dias úteis; rolar vencimento para dia útil | `grace_business_days`, `due_date_rolls_to_business_day` | |
| Feriados (nacional, estadual, municipal) | `holidays` | O módulo não traz nenhum |
| Tolerância de classificação e de ajuste; vencedor da duplicidade | `classify_tolerance`, `adjust_tolerance`, `duplicate_winner` | O GSAN fixa R$ 0,03 e R$ 2,00 no código |
| Layout do código de barras do convênio | `collection_agreements.barcode_layout` | |
| Versões de layout (A.09) aceitas no retorno | `accepted_return_versions` | Ver pendências |
| Norma-fonte e vigência | `collection_policies.norm_source`, `valid_from/valid_to` | A não sobreposição de vigências é da aplicação |

## Decisões e premissas

* **Juros**: só `pro rata die` simples (`principal x taxa/100 x dias corridos / base`). O modo "por mês de
  calendário" do GSAN **não foi implementado**: exige decisão do dono (cobraria 1 mês cheio de uma vez
  ao virar o mês e nada antes).
* **Carência**: dias úteis em `(vencimento, avaliação]` <= carência dá acréscimo zero; passada a
  carência, tudo conta **desde o vencimento**. A data de avaliação é sempre explícita (pagamento ou data-base
  da dívida em aberto) e a carência vale igual para as duas. O relatório 03 §9 aponta que o GSAN
  descarta o resultado de `subtrairNumeroDiasDeUmaData`; **o que ele pretendia com o recuo não está documentado**, então não foi
  reproduzido. Decisão pendente do dono.
* **Multa já cobrada** é abatida do total da multa (`max(0, principal x % - já cobrada)`); o GSAN multiplicava
  `(principal - já cobrada) x %`.
* **Arredondamento**: sem arredondamento intermediário; multa, juros e correção arredondados uma vez a 2
  casas, `HALF_UP`; total = soma dos três já arredondados. Testado, inclusive no empate exato.
* **EXCESS x AMOUNT_MISMATCH**: pagamento único maior que o documento (além das tolerâncias) é `EXCESS`; o
  relatório 03 sugere `VALOR_NAO_CONFERE` (pseudocódigo "não conferido linha a linha"). Separei
  porque a maior (devolver/creditar) e a menor (cobrar a diferença) têm tratamentos opostos.
* **Janela do fator de vencimento**: `DueDateFactor.decode(fator, referência, diasAntes, diasDepois)`
  devolve a data só se **exatamente uma** das duas candidatas cair na janela; sem janela padrão. O
  relatório 11 sugere "a candidata mais próxima da emissão": pode errar em silêncio, por isso a janela.
* Sábado e domingo nunca são dia útil.
* A tabela de 15 situações usa nomes próprios; `TO_WRITE_DOWN`, `OPEN`, `DUPLICATE_REFUNDED` e as duas
  `CREDIT_RECOVERY_*` não saem do classificador (vêm de outros fluxos).

## Divergências com os relatórios 03 e 11, conferidas na especificação

1. **Identificação da empresa por CNPJ**: o relatório 11 (R05) apresenta "ou as 8 primeiras posições do CNPJ" como
   alternativa geral. No layout v08 (seção 05) essa forma pertence ao **segmento 6** (legado, sem novas
   codificações desde a v8), e só com CNPJ numérico. Saneamento (segmento 2) usa o **código de 4 posições
   atribuído pela FEBRABAN**. O módulo implementa assim.
2. **"Cada banco define o campo livre"** (relatório 11, R05): o texto "cada banco definirá a forma de
   identificação da empresa a partir da 20ª posição" é do **segmento 9** (uso exclusivo do banco). O campo
   livre dos demais segmentos é da empresa e "será devolvido inalterado".
3. **Distância entre as duas datas do mesmo fator**: o relatório 11 (R04) diz 10.000 dias; são **9.000 dias**
   (data legada = base + f; data atual = base + 10.000 + (f - 1000)). Está em teste.
4. **Fator de vencimento**: confirmado em fonte de banco (Santander v36: 22/02/2025 = 1000, 23 = 1001, 24 = 1002) e
   a regra já constava da v34 de setembro/2021 do mesmo manual. Também confirmados 13/10/2049 como o último 9999 e os
   vetores 1667 (21/12/2026) e 3000 (15/08/2030) do relatório 11. **O documento primário da FEBRABAN continua
   não localizado.** A Res. BCB 443 não trata de código de barras nem de fator (busca no texto completo).
5. **Quatorze estados**: o relatório 03 conta 14 linhas, mas a linha "15, 16" tem dois códigos, então são
   **15 situações**.
6. **DV da arrecadação e da cobrança são diferentes**: módulo 11 da arrecadação dá 0 para resto 0 ou 1; o da
   cobrança dá 1 (0, 10 e 11 viram 1). Confirmado nas duas fontes e testado (`CheckDigitsTest`).
7. **Exemplos do próprio manual FEBRABAN**: os exemplos das seções 08 e 10 usam o identificador de valor `2`
   (inexistente: o campo só admite 6 a 9). Servem para a aritmética do DV (soma 139 e 705) e foram usados assim, não
   como código "legível".
8. **Datas do registro G**: o manual rotula G.03/G.04 como "AAAA/MM/DD", mas o campo é `X(08)`: 8 posições, sem barras.
   O parser lê `AAAAMMDD`; confirmar com um arquivo real de cada banco.
9. **A.09 (versão do layout)**: o manual v08 continua dizendo "nova = versão 07, disponível a partir de 01.03.2023" e
   não informa o valor para o layout 08. Confirma o relatório 11; por isso a versão aceita é parâmetro.

## NÃO implementado (e por quê)

* **Pix** (cobrança, Pix Automático, cobrança híbrida), **débito automático** (B/C/E/F; o relatório 11 R15 manda não
  construir como padrão), **DDA**, **registro de boleto na base centralizada (NPC)**, **NFAg-e**, **split payment**,
  **conciliação** (aviso bancário, float, extrato), **devolução/repetição de indébito**.
* **CNAB 240 (T/U)**: a V11.0 da FEBRABAN é preliminar segundo o relatório 11 e eu não a li; a regra do projeto é não
  implementar de memória. **CNAB 400/444**: sem documento FEBRABAN oficial.
* **Arquivos parciais de arrecadação (v03)**: o layout não foi lido. O parser A/G/Z lê o formato do manual v08; se
  o arquivo parcial tiver diferenças, ficam por conferir.
* **Layout de campo livre dos ~20 bancos do primeiro escopo** (BB, Bradesco, Sicoob, Caixa, Itaú etc.): só a estrutura
  geral e o exemplo do Santander (nos testes) foram confirmados. Cada banco precisa do manual oficial no onboarding do convênio.
* **Camada de serviço**: não há `@Service`, job, importador que grave `payments`, nem REST. As regras são puras e as entidades
  existem, mas ninguém ainda orquestra "arquivo -> pagamentos -> classificação -> trilha".
* Validador de CPF/CNPJ alfanumérico (já existe em `ziipsan-registry`, `DocumentNumber`).

## Pendências para o dono / jurídico

1. Juros: pro rata die ou por mês de calendário? A multa de 2% do CDC art. 52 §1º se aplica à tarifa? (relatório 11, perguntas 9 e 10)
2. O que o recuo da carência no GSAN pretendia (item de carência acima)?
3. Valor de A.09 para o layout 08 e quais bancos já entregam a v08 (relatório 11, pergunta 3).
4. Janela de decodificação do fator de vencimento: documento primário da FEBRABAN.
5. Arrecadação (código 8) e/ou boleto de cobrança no primeiro escopo (relatório 11, pergunta 1).
6. Forma de captura do boleto de cobrança nos arquivos de retorno (CNAB 240 V11.0 final).

## Como testar

```bash
export ZIIPSAN_BUILD_DIR="<pasta local visível ao Docker>"
bash scripts/build.sh -pl commercial/ziipsan-collection -am test
```

A migration `V6_1` e o mapeamento das 7 entidades foram validados **uma vez**, fora do repositório, num PostgreSQL 15
descartável (não o banco de integração), com `register_tenant_table` real e `current_tenant_id()` e o trigger de
auditoria em versões de teste mínimas: a migration aplicou sem erro, o `hibernate.hbm2ddl.auto=validate` passou nas 7
entidades e os CHECKs de multa (2%), juros, correção, feriado e índice foram exercitados com inserts válidos e
inválidos (um defeito de `NULL` em dois CHECKs foi achado e corrigido assim). O `FreshInstallTest`, que prova a
instalação com todos os módulos, **não foi executado** a pedido: rodar antes de integrar.

Os testes do módulo são unitários puros (nenhum precisa de banco). Os vetores numéricos têm três origens: exemplos
dos manuais (FEBRABAN v08 seções 07 a 10 e Santander v36), contas feitas à mão (nos comentários dos testes) e um
oráculo independente em Node que implementa as mesmas fórmulas, conferido contra os exemplos dos manuais.
