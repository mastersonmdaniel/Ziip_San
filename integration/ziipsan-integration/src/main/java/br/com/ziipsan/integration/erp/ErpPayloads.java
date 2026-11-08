package br.com.ziipsan.integration.erp;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.JournalEntry;
import br.com.ziipsan.core.backoffice.PlannedMaterials;
import br.com.ziipsan.core.inventory.StockMovement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ED-62: construcao das cargas canonicas que os conectores ERP enviam,
 * a partir dos objetos REAIS das dependencias:
 * <ul>
 *   <li>lancamentos contabeis da ED-32 ({@code JournalEntry}) — duas
 *       linhas por lancamento (D e C), como na exportacao diaria da
 *       ED-32, com o centro de custo do gancho P3.30
 *       ({@code ErpHooks.CostCenterLink} resolvido, obrigatorio: sem
 *       ele a carga nao e construida — nunca ha omissao silenciosa);</li>
 *   <li>previsao de materiais da OS (SG-476, TR-521): linhas
 *       {@code PlannedMaterials.Line} REAIS da ED-36 com a natureza de
 *       despesa do gancho da ED-58;</li>
 *   <li>movimentos de material REAIS da ED-58 ({@code StockMovement})
 *       com a natureza de despesa do gancho;</li>
 *   <li>gancho do contrato de terceiros (empenho/ordem/PNCP).</li>
 * </ul>
 * A carga canonica e lista ordenada de pares campo=valor; quem a traduz
 * ao leiaute do fornecedor e o {@link ErpLayoutMapping} declarado.
 */
public final class ErpPayloads {
    private ErpPayloads() {}

    /** Operacoes do conector ERP sobre o hub (nomes estaveis). */
    public static final String OP_ACCOUNTING = "erp.accounting.export";
    public static final String OP_FORECAST = "erp.stock.forecast";
    public static final String OP_MOVEMENTS = "erp.stock.movements";
    public static final String OP_CONTRACT_HOOK = "erp.contract.hook";
    public static final String OP_INDICATORS = "erp.indicators.read";

    public static List<List<Map.Entry<String, String>>> accountingLines(JournalEntry entry, ErpHooks.CostCenterLink costCenterLink) {
        Objects.requireNonNull(entry, "lancamento contabil da ED-32 e obrigatorio");
        Objects.requireNonNull(costCenterLink, "vinculo de centro de custo (gancho P3.30) e obrigatorio");
        List<List<Map.Entry<String, String>>> out = new ArrayList<>();
        out.add(accountingLine(entry, entry.debitAccountCode(), "D", costCenterLink.erpCostCenterCode()));
        out.add(accountingLine(entry, entry.creditAccountCode(), "C", costCenterLink.erpCostCenterCode()));
        return List.copyOf(out);
    }

    private static List<Map.Entry<String, String>> accountingLine(JournalEntry e, String account, String dc, String costCenter) {
        List<Map.Entry<String, String>> line = new ArrayList<>();
        line.add(Map.entry("data", e.factDate().toString()));
        line.add(Map.entry("mes_contabil", e.accountingMonth().toString()));
        line.add(Map.entry("evento", e.eventKey().name()));
        line.add(Map.entry("conta", account));
        line.add(Map.entry("dc", dc));
        line.add(Map.entry("valor", e.amount().toPlainString()));
        line.add(Map.entry("historico", e.history() == null ? "" : e.history()));
        line.add(Map.entry("origem", e.sourceReference()));
        // centro_custo e SEMPRE emitido: vem do CostCenterLink resolvido
        // (record valida codigo nao vazio) — sem omissao silenciosa (R2).
        line.add(Map.entry("centro_custo", costCenter));
        return List.copyOf(line);
    }

    /** Previsao de materiais da OS ao estoque do cliente (SG-476, TR-521). */
    public static List<Map.Entry<String, String>> forecastLine(PlannedMaterials.Line line, ErpHooks.MaterialErpHook hook, String orderReference) {
        Objects.requireNonNull(line, "linha prevista da ED-36 e obrigatoria");
        Objects.requireNonNull(hook, "gancho de natureza de despesa (ED-58) e obrigatorio para exportar a previsao");
        if (!hook.materialCode().equals(line.materialCode())) {
            throw new IllegalArgumentException("gancho de natureza e de outro material: " + hook.materialCode() + " != " + line.materialCode());
        }
        if (orderReference == null || orderReference.isBlank()) throw new IllegalArgumentException("referencia da OS e obrigatoria na previsao (SG-476)");
        return List.of(
                Map.entry("material", line.materialCode()),
                Map.entry("quantidade", line.plannedQty().toPlainString()),
                Map.entry("natureza_despesa", hook.expenseNatureCode()),
                Map.entry("referencia", orderReference));
    }

    /** Movimento de material da OS (ED-58) para quando o estoque oficial e o do ERP (ADR-0032). */
    public static List<Map.Entry<String, String>> movementLine(StockMovement movement, ErpHooks.MaterialErpHook hook) {
        Objects.requireNonNull(movement, "movimento de estoque da ED-58 e obrigatorio");
        Objects.requireNonNull(hook, "gancho de natureza de despesa (ED-58) e obrigatorio para exportar o movimento");
        if (!hook.materialCode().equals(movement.itemCode())) {
            throw new IllegalArgumentException("gancho de natureza e de outro material: " + hook.materialCode() + " != " + movement.itemCode());
        }
        return List.of(
                Map.entry("material", movement.itemCode()),
                Map.entry("tipo_movimento", movement.type().name()),
                Map.entry("quantidade", movement.qty().toPlainString()),
                Map.entry("natureza_despesa", hook.expenseNatureCode()),
                Map.entry("referencia", movement.reference() == null ? "" : movement.reference()));
    }

    /** Gancho do contrato de terceiros: empenho/ordem de compra externa e PNCP. */
    public static List<Map.Entry<String, String>> contractHookLine(ErpHooks.ThirdPartyContractHook hook, String contractNumber) {
        Objects.requireNonNull(hook, "gancho de contrato de terceiros e obrigatorio");
        if (contractNumber == null || contractNumber.isBlank()) throw new IllegalArgumentException("numero do contrato e obrigatorio no gancho");
        List<Map.Entry<String, String>> line = new ArrayList<>();
        line.add(Map.entry("contrato", contractNumber));
        if (hook.commitmentNumber() != null) line.add(Map.entry("empenho", hook.commitmentNumber()));
        if (hook.externalPurchaseOrder() != null) line.add(Map.entry("ordem_compra_externa", hook.externalPurchaseOrder()));
        if (hook.pncpNumber() != null) line.add(Map.entry("pncp", hook.pncpNumber()));
        return List.copyOf(line);
    }

    /** Junta linhas renderizadas no leiaute declarado (uma por linha, '\n'). */
    public static String renderAll(ErpLayoutMapping mapping, List<List<Map.Entry<String, String>>> canonicalLines) {
        Objects.requireNonNull(mapping, "mapeamento de leiaute declarado e obrigatorio");
        Objects.requireNonNull(canonicalLines, "linhas canonicas sao obrigatorias");
        StringBuilder sb = new StringBuilder();
        for (List<Map.Entry<String, String>> line : canonicalLines) {
            sb.append(mapping.render(line)).append('\n');
        }
        return sb.toString();
    }
}
