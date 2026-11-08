package br.com.ziipsan.integration.erp;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.JournalEntry;
import br.com.ziipsan.core.backoffice.PlannedMaterials;
import br.com.ziipsan.core.inventory.StockMovement;
import br.com.ziipsan.integration.hub.ExternalConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-62: servico de exportacao/leitura sobre o conector ERP. Aplica as
 * regras do aceite ANTES de qualquer chamada ao fornecedor:
 * <ul>
 *   <li>movimentos de material so sao exportados quando o estoque
 *       oficial e o do ERP (ADR-0032); quando o oficial e o ZiipSan, a
 *       ED-58 segue dona e a exportacao RECUSA;</li>
 *   <li>previsao de materiais (SG-476) e movimentos exigem a natureza de
 *       despesa declarada do material (gancho ED-58);</li>
 *   <li>lancamentos da ED-32 so saem com o mapeamento de leiaute
 *       declarado completo (campo faltante = recusa nomeada) e com o
 *       centro de custo resolvido pelo gancho P3.30
 *       ({@code ErpHooks.CostCenterLink} do tenant): centro de custo
 *       ausente/nao resolvido = recusa nomeada {@code [A DEFINIR]} —
 *       nunca e omitido em silencio da carga;</li>
 *   <li>idempotencia por lote: repetir a mesma chave devolve o lote
 *       guardado sem chamar o conector de novo (mesma disciplina do
 *       HubDispatchService da P3.37, aqui no nivel do lote).</li>
 * </ul>
 * Indicadores sao LIDOS (ADR-0028 i): este servico nao cria pedido de
 * compra, folha ou orcamento — so transporta o que o ERP devolve.
 */
public final class ErpExportService {

    public record ExportBatch(String idempotencyKey, String operation, String renderedPayload,
                              ExternalConnector.ConnectorResponse response) {
        public ExportBatch {
            if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("chave de idempotencia do lote e obrigatoria");
            Objects.requireNonNull(operation, "operacao do lote e obrigatoria");
            Objects.requireNonNull(renderedPayload, "carga renderizada do lote e obrigatoria");
            Objects.requireNonNull(response, "resposta do conector e obrigatoria");
        }
        public boolean succeeded() { return response.isSuccess(); }
    }

    private final ErpConnector connector;
    private final ErpParameters parameters;
    private final ErpLayoutMapping mapping;
    private final br.com.ziipsan.integration.hub.IntegrationMode mode;
    private final Map<String, ExportBatch> completedByKey = new LinkedHashMap<>();

    /** {@code mode} e o modo OPERAVEL ja conferido pelo hub (producao so com aceite, P3.37). */
    public ErpExportService(ErpConnector connector, ErpParameters parameters, ErpLayoutMapping mapping,
                            br.com.ziipsan.integration.hub.IntegrationMode mode) {
        this.mode = Objects.requireNonNull(mode, "modo operavel do hub (P3.37) e obrigatorio");
        this.connector = Objects.requireNonNull(connector, "conector ERP e obrigatorio");
        this.parameters = Objects.requireNonNull(parameters, "parametros erp.* sao obrigatorios");
        this.mapping = Objects.requireNonNull(mapping, "mapeamento de leiaute declarado e obrigatorio");
        if (mapping.provider() != connector.provider()) {
            throw new IllegalArgumentException("mapeamento e de outro fornecedor: " + mapping.provider() + " != " + connector.provider());
        }
        if (!mapping.layoutVersion().equals(connector.partnerVersion())) {
            throw new IllegalArgumentException("versao do mapeamento (" + mapping.layoutVersion()
                    + ") difere da versao declarada do conector (" + connector.partnerVersion() + ")");
        }
    }

    public synchronized ExportBatch exportAccounting(UUID tenantId, String credentialRef, String correlationId,
                                                     String idempotencyKey, List<JournalEntry> entries,
                                                     ErpHooks.CostCenterLink costCenterLink) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        ErpHooks.CostCenterLink resolvedLink = requireCostCenterLink(tenantId, costCenterLink);
        Objects.requireNonNull(entries, "lancamentos da ED-32 sao obrigatorios");
        if (entries.isEmpty()) throw new IllegalArgumentException("lote contabil vazio nao e exportado");
        List<List<Map.Entry<String, String>>> lines = new ArrayList<>();
        for (JournalEntry e : entries) lines.addAll(ErpPayloads.accountingLines(e, resolvedLink));
        return dispatch(tenantId, credentialRef, correlationId, idempotencyKey, ErpPayloads.OP_ACCOUNTING, lines);
    }

    private static ErpHooks.CostCenterLink requireCostCenterLink(UUID tenantId, ErpHooks.CostCenterLink link) {
        if (link == null) {
            throw new IllegalStateException("centro de custo [A DEFINIR]: a exportacao contabil exige o CostCenterLink"
                    + " resolvido (gancho P3.30, unidade organizacional -> centro de custo do ERP) — centro de custo"
                    + " ausente NAO e omitido em silencio da carga (ED-62)");
        }
        if (!link.tenantId().equals(tenantId)) {
            throw new IllegalStateException("centro de custo [A DEFINIR]: CostCenterLink de outro tenant nao resolve"
                    + " o centro de custo desta exportacao (gancho P3.30) — vinculo nao resolvido NAO e omitido em silencio (ED-62)");
        }
        return link;
    }

    /** Previsao de materiais da OS enviada ao estoque do cliente (SG-476, TR-521). */
    public synchronized ExportBatch sendMaterialForecast(UUID tenantId, String credentialRef, String correlationId,
                                                         String idempotencyKey, String orderReference,
                                                         List<PlannedMaterials.Line> planned,
                                                         Map<String, ErpHooks.MaterialErpHook> hookByMaterial) {
        Objects.requireNonNull(planned, "previsto de materiais da ED-36 e obrigatorio");
        Objects.requireNonNull(hookByMaterial, "ganchos de natureza de despesa sao obrigatorios");
        if (planned.isEmpty()) throw new IllegalArgumentException("previsao vazia nao e enviada");
        List<List<Map.Entry<String, String>>> lines = new ArrayList<>();
        for (PlannedMaterials.Line line : planned) {
            lines.add(ErpPayloads.forecastLine(line, requireHook(hookByMaterial, line.materialCode()), orderReference));
        }
        return dispatch(tenantId, credentialRef, correlationId, idempotencyKey, ErpPayloads.OP_FORECAST, lines);
    }

    /** Movimentos exportados SOMENTE quando o estoque oficial e o do ERP (ADR-0032). */
    public synchronized ExportBatch exportStockMovements(UUID tenantId, String credentialRef, String correlationId,
                                                         String idempotencyKey, List<StockMovement> movements,
                                                         Map<String, ErpHooks.MaterialErpHook> hookByMaterial) {
        if (parameters.stockOfficial(connector.provider()) != ErpProvider.StockOfficial.ERP) {
            throw new IllegalStateException("estoque oficial e o ZiipSan (erp." + connector.provider().name().toLowerCase(java.util.Locale.ROOT)
                    + ".stock_official=ZIIPSAN): a ED-58 e a dona do estoque e movimentos NAO sao exportados (ADR-0032)");
        }
        Objects.requireNonNull(movements, "movimentos da ED-58 sao obrigatorios");
        Objects.requireNonNull(hookByMaterial, "ganchos de natureza de despesa sao obrigatorios");
        if (movements.isEmpty()) throw new IllegalArgumentException("lote de movimentos vazio nao e exportado");
        List<List<Map.Entry<String, String>>> lines = new ArrayList<>();
        for (StockMovement m : movements) {
            lines.add(ErpPayloads.movementLine(m, requireHook(hookByMaterial, m.itemCode())));
        }
        return dispatch(tenantId, credentialRef, correlationId, idempotencyKey, ErpPayloads.OP_MOVEMENTS, lines);
    }

    public synchronized ExportBatch sendContractHook(UUID tenantId, String credentialRef, String correlationId,
                                                     String idempotencyKey, ErpHooks.ThirdPartyContractHook hook,
                                                     String contractNumber) {
        return dispatch(tenantId, credentialRef, correlationId, idempotencyKey, ErpPayloads.OP_CONTRACT_HOOK,
                List.of(ErpPayloads.contractHookLine(hook, contractNumber)));
    }

    private static ErpHooks.MaterialErpHook requireHook(Map<String, ErpHooks.MaterialErpHook> hooks, String materialCode) {
        ErpHooks.MaterialErpHook hook = hooks.get(materialCode);
        if (hook == null) {
            throw new IllegalStateException("material " + materialCode + " sem natureza de despesa declarada [A DEFINIR]: a exportacao ao ERP recusa em vez de presumir a natureza (gancho ED-58 da ED-62)");
        }
        return hook;
    }

    private ExportBatch dispatch(UUID tenantId, String credentialRef, String correlationId, String idempotencyKey,
                                 String operation, List<List<Map.Entry<String, String>>> canonicalLines) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        ExportBatch prior = completedByKey.get(tenantId + "|" + idempotencyKey);
        if (prior != null) return prior;
        String rendered = ErpPayloads.renderAll(mapping, canonicalLines);
        ExternalConnector.ConnectorContext ctx = new ExternalConnector.ConnectorContext(
                tenantId, mode, credentialRef, correlationId);
        ExternalConnector.ConnectorRequest req = new ExternalConnector.ConnectorRequest(
                correlationId, idempotencyKey, operation, rendered);
        ExternalConnector.ConnectorResponse response = connector.send(ctx, req);
        ExportBatch batch = new ExportBatch(idempotencyKey, operation, rendered, response);
        if (response.isSuccess() || response.outcome() == ExternalConnector.Outcome.REJECTED) {
            completedByKey.put(tenantId + "|" + idempotencyKey, batch);
        }
        return batch;
    }
}
