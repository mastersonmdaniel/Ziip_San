package br.com.ziipsan.core.inventory;

import java.util.List;

/**
 * Cobertura dos codigos da ED-58 (JOR-32, JOR-36, OPE-29, SG-476,
 * SAMAE 2.17 parte de estoque, CENTRAL-ZIIP §3). MARCADOR HONESTO
 * (padrao das coberturas vizinhas): registra a exigencia -> componente;
 * o ENSAIO do SAMAE e externo e nao e afirmado aqui.
 */
public final class InventoryCoverage {
    private InventoryCoverage() {}

    public record Entry(String code, String requirement, String component) {}

    public static final List<Entry> ED58 = List.of(
            new Entry("JOR-32", "Requisicao com alcada por faixa de valor e papel; controlado sempre humano; autoaprovacao registrada",
                    "WarehouseService.approve/tryAutoApproval + ApprovalPolicy + InventoryParameters (inv.*)"),
            new Entry("JOR-36", "Reserva na aprovacao, entrega registrada, baixa/devolucao no encerramento e liberacao no cancelamento",
                    "WarehouseService (applyReservation/deliver/closeOrder/cancelOrder) + StockBalance"),
            new Entry("OPE-29", "Baixa de campo idempotente por task_id com alcada a posteriori para controlado",
                    "FieldWriteOffService (task_id da ED-20, MaterialUse real)"),
            new Entry("SG-476", "Kit de materiais por tipo de OS preenche a previsao na abertura",
                    "MaterialKit.prefillPlanned -> PlannedMaterials.Line (ED-36 real)"),
            new Entry("SAMAE 2.17 (estoque)", "Estoque por local, entradas, ajustes, inventario e relatorios de materiais e insumos",
                    "WarehouseService + InventoryReports (ensaio externo, nao afirmado)"),
            new Entry("CENTRAL-ZIIP §3", "Aba Estoque da Central: saldos, negativos e alertas por tenant",
                    "InventoryReports.centralView"),
            new Entry("ADR-0028 (i)", "Compras fora do produto: a NF-e confere contra o esperado informado, sem pedido de compra no sistema",
                    "NfeEntry + WarehouseService.receiveFromNfe"),
            new Entry("ADR-0032", "Material por tarefa (ED-20) fecha na baixa desta entrega; previsto x usado e da ED-36",
                    "FieldWriteOffService + PlannedMaterials (ED-36)"),
            new Entry("ISO 45001 / ED-71", "EPI e consumo por tarefa como evidencia",
                    "InventoryStore.TaskConsumption + InventoryReports.taskEvidence"));

    public static List<String> codes() { return ED58.stream().map(Entry::code).distinct().toList(); }
}
