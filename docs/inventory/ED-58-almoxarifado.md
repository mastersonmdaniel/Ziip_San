# ED-58 — Almoxarifado: materiais e estoque para a OS, com alçada e baixa automática

Pacote novo `br.com.ziipsan.core.inventory` no `shared/ziipsan-core`
(mesmo módulo do núcleo de OS da P1.31, do backoffice da ED-36 e do
campo da ED-20 — as ligações reais são por tipo desse módulo, sem cópia).

## Fluxo (ADR-0032)

1. Abertura da OS: o kit do tipo (SG-476, `MaterialKit`) preenche o
   previsto (`PlannedMaterials` da ED-36).
2. Seleção de material na OS gera a **requisição** (`Requisition`) com
   valor pelo custo médio vigente.
3. **Alçada** (`ApprovalPolicy` + `inv.*` do tenant): controlado nunca é
   automático; automático só até `inv.auto_approval_limit`, registrado
   (ator SYSTEM); humano exige o papel da faixa de `inv.approval_bands`;
   requisitante nunca decide a própria (403 + auditoria).
4. Aprovação **reserva**: disponível cai (pode ficar negativo, com
   alerta e relatório diário), físico não muda.
5. **Entrega registrada**: reserva e físico baixam juntos.
6. **Encerramento**: baixa o usado, devolve a sobra e apropria o custo
   (usado × custo médio) na OS. Usado além do entregue é baixa
   automática com piso zero: a falta vira pendência de regularização.
7. **OS cancelada** libera a reserva.
8. **Campo (ED-20)**: a baixa chega por `task_id`
   (`FieldWriteOffService` sobre o `MaterialUse` real da ED-20),
   idempotente; controlado gera alçada a posteriori.

## Saldos e custo

Físico / reservado / disponível por item × local, custo médio ponderado
móvel nas entradas. Negativo de **físico** nunca nasce de caminho
automático: só pode nascer de ato humano registrado e justificado —
entrega de requisição aprovada com falta conhecida, ajuste manual
justificado e contagem de inventário justificada; caminho automático
nunca empurra o físico abaixo de zero.

## Entradas

Manual (com custo), ajuste com justificativa obrigatória, inventário
(divergência = ajuste justificado) e **NF-e**: XML lido com parser do
JDK (DOCTYPE recusado), emitente validado no cadastro da P3.31 por
documento, conferência item/quantidade contra o esperado; divergência
não posta entrada. Compras seguem fora do produto (ADR-0028, i) — não
há pedido de compra no sistema.

## Serializados, lotes, transferências

Hidrômetro: série + lacre do recebimento à sucata
(`SerializedMeterTracking`, custódia de estoque; o ciclo do hidrômetro
instalado é da ED-14). Lotes com validade para reagentes/químicos/EPI
(CA obrigatório no EPI), alerta por `inv.lot_expiry_alert_days`.
Transferência entre locais com estado `IN_TRANSIT`.

## Relatórios e Central

`InventoryReports`: saldos, relatório de materiais/insumos, relatório
diário de negativos com prazo de regularização, ponto de reposição por
item, evidência de consumo/EPI por tarefa (ISO 45001 → ED-71) e a aba
Estoque da Central (`centralView`: saldos, negativos e alertas por
tenant). Toda operação grava movimento no ledger e auditoria.
