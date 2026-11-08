# ED-62 — Integração com ERP do cliente (Betha, IPM, Fiorilli)

Conectores ERP sobre o hub da P3.37 (`ExternalConnector`, kind `ERP`),
um por fornecedor (`erp-betha`, `erp-ipm`, `erp-fiorilli`), canal por
API ou arquivo conforme o parâmetro declarado `erp.<p>.transport`.

## Regra de ouro: nenhum leiaute inventado

Betha, IPM e Fiorilli são fornecedores reais e **nenhum leiaute deles
está nas fontes locais**. Por isso:

- a carga nasce **canônica** no ZiipSan (`ErpPayloads`, a partir dos
  objetos reais da ED-32/ED-36/ED-58);
- a tradução ao ERP usa **somente** o `ErpLayoutMapping` **declarado**
  na implantação a partir do leiaute publicado pelo fornecedor —
  campo sem mapeamento = recusa nomeada `[A DEFINIR]`;
- a versão do parceiro é a **declarada** em `erp.<p>.layout_version`;
- a prova contra o leiaute publicado/ambiente de homologação do
  fornecedor é **pendência externa B1** — o núcleo é provado com o
  dublê honesto `FakeErpTransport` (só testes), declarado como dublê.

## Fluxos

| Fluxo | Origem real | Porta/regra |
|---|---|---|
| Lançamentos contábeis | `JournalEntry` da ED-32 (linhas D e C) | centro de custo via `CostCenterLink` (código da unidade da P3.30) |
| Previsão de materiais da OS (SG-476, TR-521) | `PlannedMaterials.Line` da ED-36 | natureza de despesa obrigatória (`MaterialErpHook`, gancho da ED-58) |
| Movimentos de material | `StockMovement` da ED-58 | só quando `erp.<p>.stock_official = ERP` (ADR-0032); com `ZIIPSAN`, a ED-58 é a dona e a exportação recusa |
| Indicadores compras/RH/orçamento | leitura do ERP | **somente leitura** para o painel estratégico e a alínea **t** do roteiro VISAN (ADR-0028, item i) — nenhum módulo de compras/RH/orçamento é construído |
| Contrato de terceiros | gancho da ED-62 sobre o contrato (incl. empreiteiras da ED-37) | nº de empenho **ou** ordem de compra externa (declarados) e nº PNCP quando houver |

Idempotência por lote no `ErpExportService` (mesma disciplina do
`HubDispatchService` da P3.37); modo operável vem do hub (produção só
com aceite). Cruzamento com o cadastro imobiliário municipal é da
**ED-95** e está fora desta entrega.
