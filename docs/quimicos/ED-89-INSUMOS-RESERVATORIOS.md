# ED-89 — Insumos químicos de tratamento e reservatórios

Pacote novo `br.com.ziipsan.core.chemicals` no `shared/ziipsan-core`
(mesmo módulo da qualidade da ED-50 e do almoxarifado da ED-58 — as
ligações reais são por tipo/porta desse módulo, sem cópia).

## Desenho

1. **Produto sobre a ED-58**: `ChemProduct` referencia o item de
   estoque pelo código. O item tem de ser `CHEMICAL` e controlado por
   lote na ED-58 (`ChemProduct.requireMatchingItem` confere contra o
   `InventoryItem.Item` REAL). Saldo, custo médio e lote/validade
   continuam na ED-58; a ED-89 não mantém saldo próprio. Cadastro
   exige a **ficha de dados de segurança (FDS)** com data de revisão e
   o local de armazenamento com tipo **SILO, TANK ou CYLINDER** (os
   três do acceptance).
2. **Recebimento por NF-e**: parser e conferência REAIS da ED-58
   (`NfeEntry.parse/check`). Divergência não posta nada. Cada linha
   exige lote, validade e **laudo do fornecedor** declarados e
   conferidos; lote vencido na data do recebimento é recusado.
   Idempotente pela chave de acesso da NF-e.
3. **Consumo e conciliação**: `DailyConsumption` por dia × unidade de
   tratamento, com volume tratado (m³), **dosagem declarada por
   lançamento** (unidade do item por m³ — dado do tenant/unidade, sem
   fonte presumida) e consumo real. Teórico = dosagem × volume. Sem a
   tolerância do tenant (`chem.reconciliation_tolerance_pct`, chave
   proposta), o veredito é `REPORTED` — nunca presumido.
4. **Estoque mínimo**: `ChemReports.coverage` — cobertura em dias =
   disponível da ED-58 ÷ consumo médio diário real lançado (média
   sobre os dias distintos com lançamento, sem janela presumida).
   Alerta quando cobertura < `chem.min_stock_days`. Sem histórico, a
   cobertura é indeterminada: não alerta nem declara folga.
5. **Polícia Federal (opcional por tenant)**: catálogo GLOBAL
   versionado `ControlledCatalog` (Lei 10.357/2001; Portaria MJSP
   204/2022 alt. 223/2022), **sem nenhuma entrada semeada** — a lista
   vigente é carga normativa do Ziip, identificada no tenant por
   `chem.controlled_products_list`. Ligar o controle exige, nesta
   ordem: conferência prévia dos insumos contra a lista vigente
   (`runPreEnableCheck`), cadastro PF de cada produto casado e
   licença de funcionamento vigente. Licença vencida bloqueia o
   recebimento de produto controlado (produto comum não é afetado).
   Informações periódicas: obrigação com prazo **declarado** da norma
   vigente [SEM FONTE: nenhuma periodicidade consta das fontes];
   vencida sem envio, alerta.
6. **Limpeza de reservatório**: plano por reservatório com
   `ops.reservoir_cleaning_interval_months` (**valor da agência**, sem
   padrão de norma — ausência recusa nomeada). OS recorrente pela
   porta `CleaningOrderPort` (núcleo P1.31/ED-36), uma por
   (reservatório, vencimento). Execução só encerra com **laudo**,
   produto usado e **volume descartado em m³** (unidade declarada
   nesta entrega), e avança o plano.
7. **Saídas (OPE-07)**: `ChemReports.publicCleaningRecords` é o
   registro público do portal (Decreto 5.440/2005, Anexo, arts. 16 e
   17; Portaria GM/MS 888/2021, art. 14, XVII) — só execuções com
   laudo, sem dado pessoal. `annualReportSource(ano)` alimenta o
   relatório anual da **ED-79**; `discardedVolumes(período)` alimenta
   o balanço da **ED-85** como volume autorizado não faturado de
   limpeza de reservatório (ficha de perdas, OPE-05).

## Fronteiras

- Nenhum preço, tarifa ou item de conta é criado aqui (item 32 das
  decisões do dono: a ED-89 não é consumidora de conta; o produto da
  limpeza é referência ao item da ED-58, sem valor próprio).
- Validade de lote: alertas reusam `LotControl` REAL da ED-58 e a
  janela `inv.lot_expiry_alert_days` dela.
- Persistência JPA/JDBC, controllers, telas do portal e a fiação das
  portas são da integração; o SQL vai em `db/pendente/` sem número.
