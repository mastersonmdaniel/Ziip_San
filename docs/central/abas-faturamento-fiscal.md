# Central — abas Faturamento e Fiscal (P2.11, ADR-0018)

A Central da Ziip (P1.17) ganha duas abas, definidas em
`br.com.ziipsan.platform.central.CentralTab`:

| Aba | Rota | Fonte agregada | Alerta |
|---|---|---|---|
| Faturamento | `/central/faturamento` | Cronograma do ciclo do tenant (P2.5): itens por situação e por grupo (`group_id`), obrigatórios vencidos | `central.billing.cycle_overdue` — item obrigatório com `planned_on` < hoje e não realizado; o sinal carrega o grupo do item mais antigo (RB-07) |
| Fiscal | `/central/fiscal` | Documentos fiscais do tenant (P2.6): contagens por estado, fila e maior idade | `central.fiscal.queue_over_limit` — fila acima do limiar do RNF-08 (RB-08) |

Isolamento (V9_1 §10): as abas leem instantâneos agregados — tenant_id,
grupo de faturamento (só o UUID), contagens, datas previstas e idades.
Nenhum dado pessoal, nenhuma conta
e nenhum documento atravessa para a Central.

**Tenant sem cronograma:** o instantâneo do ciclo com a lista de itens
vazia significa, explicitamente, tenant sem cronograma na referência
(ex.: recém-implantado). A aba sai zerada e sem alerta, e a coleta dos
demais tenants não é abortada. O coletor só monta o instantâneo vazio
com a ausência confirmada na fonte; ausência de dados (instantâneo
nulo ou item malformado) continua erro explícito.

**Fronteira com a P2.33:** a fila de documentos fiscais OPERADA pelo
tenant (rejeições, contingência, inutilização, reenvio) é a tela da
P2.33. A aba Fiscal da Central é só a visão da operadora.

**Limiar do RNF-08:** configurado em `platform.config_item`, chave
`central.billing_fiscal.thresholds`, campo `fiscal_queue_limit_rnf08`.
Sem o campo, a configuração é recusada e o alerta fiscal não é
avaliado (sem padrão silencioso). Ver a pendência no RELATORIO-P2.11.
