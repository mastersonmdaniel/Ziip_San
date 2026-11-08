# ED-96 — Instrumento contratual regulatório do prestador (GES-02)

Registro, **por município e por serviço**, do instrumento sob o qual
o tenant presta o serviço — contrato de programa, concessão, PPP,
subdelegação ou prestação direta com agência — como **dado
cadastral**. Pacote novo `br.com.ziipsan.core.regulatory` no módulo
existente `shared/ziipsan-core` (mesmo módulo da P3.31, uma das
dependências).

## O que o cadastro guarda (campos do aceite)

- **Partes** (concedente/titular, prestador, agência reguladora),
  **ato de regulação** declarado, **vigência** (início obrigatório;
  fim sem data declarada fica nulo, nunca presumido);
- **metas contratuais** declaradas no instrumento, com alvo e
  unidade juntos ou nenhum, ano de vencimento e vínculo opcional a
  aditivo e a indicador da P3.41 pelo código declarado;
- **aditivos**, inclusive o de metas da NR 8/ANA
  (`GOAL_UPDATE_NR8`); referência repetida e aditivo anterior ao
  início da vigência são recusados;
- **matriz de riscos (NR 5/ANA)** como primeiro uso sobre o registro
  de riscos da P3.31: cada linha referencia um `cert_risks` existente
  do tenant e declara a alocação do instrumento. O **conteúdo** da
  matriz é do instrumento de cada tenant — nenhum risco, categoria
  ou alocação é semeado [SEM FONTE para conteúdo presumido];
- **indenização de investimentos não amortizados (NR 3/ANA)**: só a
  referência da cláusula e o método **como declarados** no
  instrumento. Nenhuma fórmula é fixada por esta entrega;
- **cláusula de reequilíbrio**: só a referência/descrição. O
  **pleito** de reequilíbrio é da P2.34 (GES-13) — fora da ED-96;
- **documentos**: metadado + referência de armazenamento;
- **vínculo com bens reversíveis** do inventário da ED-56: só ativo
  existente **e reversível**; viaja só o id (DAD-26).

Regra cadastral executável: vigências sobrepostas para o mesmo
município+serviço no mesmo tenant são recusadas; serviços
diferentes, ou vigência sucessiva, são registros distintos.

## Parâmetros (fatos declarados, classe d, REG)

- `reg.contract_type` (enum, domínio fechado do aceite) e
  `reg.risk_matrix_required` (booleano) **sem valor de fábrica**.
  Ausência não vira presunção: a completude anota
  `CONTRACT_TYPE_PARAMETER_UNDECLARED` /
  `RISK_MATRIX_REQUIREMENT_UNDECLARED`. Valor presente é parseado
  estrito e divergência cadastro × parâmetro é lacuna nomeada
  (`CONTRACT_TYPE_DIVERGES_FROM_PARAMETER`).

## Painel de capacidade (Decreto 11.598/2023)

Opcional e **só para tenant sob contrato** (prestação direta é
recusada). Calculado da **exportação do ERP do cliente**
(`ErpExport`, com referência de origem): margem líquida sem
depreciação e amortização > 0, endividamento ≤ 1, retorno sobre o
patrimônio líquido > 0 e suficiência de caixa > 1 — os limiares são
os do aceite, fixos no código, não parâmetros. Denominador nulo ou
não positivo produz `INDETERMINATE`, nunca veredito presumido. O
de-para de contas do ERP para fluxo de caixa disponível e serviço
da dívida é declaração contábil do tenant na exportação
[SEM FONTE local para de-para presumido] — pendência P-ED96-02.

## Fronteiras

- Metas de universalização por município/ano: **ED-77**.
- Pleito de reequilíbrio contratual: **P2.34 (GES-13)**.
- Riscos vivem na **P3.31**; ativos vivem na **P1.35/ED-56**.
- Nenhum consumidor de conta é tocado (item 32 das decisões do
  dono): sem billing, tarifa ou snapshot de conta nesta entrega.

## Banco

`db/pendente/ED-96__regulatory_contract.sql` (7 tabelas `reg_*`,
todas com RLS e `UNIQUE (tenant_id, id)`; toda FK é composta
`(tenant_id, X)` — lição da C3) e
`db/pendente/ED-96__core_regulatory_parameters.sql` (os dois
parâmetros, sem fábrica). Os 14 valores da P3.9 não são tocados.
