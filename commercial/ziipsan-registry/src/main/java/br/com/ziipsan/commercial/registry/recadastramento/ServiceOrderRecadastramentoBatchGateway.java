package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.quality.BatchOrderGateway;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Gateway da OS interna de lote do recadastramento sobre o motor da
 * P1.31 (ED-16, ADR-0039), no mesmo desenho do gateway de correcao da
 * P3.32 ({@code ServiceOrderCorrectionBatchGateway}): implementa a
 * porta {@link BatchOrderGateway} da fila — na fiacao da campanha, o
 * {@code CorrectionQueueService} e construido com ESTE gateway, e e ele
 * quem abre a OS do lote — e o {@link BatchOrderCloser} do encerramento.
 *
 * <p>Tipo declarado (uma vez por tenant, sob demanda):
 * {@code RECADASTRAMENTO_BATCH} — interno, sem aprovacao por outra
 * pessoa (a revisao do recadastramento e a aprovacao CAMPO A CAMPO do
 * {@code RecadastramentoService}, nao um passo desta OS), efeito
 * {@code RECADASTRAMENTO_FIELD_APPLY} com carga {@code {findingKeysCsv}}
 * (as chaves dos achados daquele alvo na fila da P3.32, separadas por
 * virgula — a chave do achado usa {@code |} como separador interno e
 * nunca contem virgula).
 *
 * <p>A OS NAO executa nem encerra ao abrir: a coleta e a aprovacao sao
 * trabalho posterior. Ela e criada e submetida; no encerramento do lote,
 * {@link #executeAndClose} a executa — o motor aplica o efeito de cada
 * alvo UMA vez e chama o manipulador de confirmacao, que registra a
 * confirmacao no lote — e a encerra como executada. Reexecutar e no-op
 * do motor para efeitos ja aplicados.
 */
public final class ServiceOrderRecadastramentoBatchGateway implements BatchOrderGateway, BatchOrderCloser {

    static final String TYPE_CODE = "RECADASTRAMENTO_BATCH";
    static final String EFFECT_CODE = "RECADASTRAMENTO_FIELD_APPLY";
    private static final String EFFECT_SCHEMA = """
            {"required": ["findingKeysCsv"],
             "properties": {"findingKeysCsv": "string"}}""";

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final CorrectionConfirmationHandler handler;

    public ServiceOrderRecadastramentoBatchGateway(DataSource dataSource, String schema, UUID tenantId,
                                                   CorrectionConfirmationHandler handler) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null || handler == null) {
            throw new IllegalArgumentException(
                    "dataSource, schema, tenantId e handler de confirmacao sao obrigatorios.");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.handler = handler;
    }

    @Override
    public OpenedCorrectionOrder openCorrectionBatch(Map<UUID, List<String>> findingKeysByUnit,
                                                     UUID actorId) {
        if (findingKeysByUnit == null || findingKeysByUnit.isEmpty()) {
            throw new IllegalArgumentException("OS de lote sem alvo nao existe: informe as unidades.");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("ator e obrigatorio para abrir a OS de lote.");
        }
        ServiceOrderService orders = new ServiceOrderService(dataSource, schema, tenantId);
        registerEffect(orders);
        ensureType(orders, actorId);
        String batchCode = "RECAD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String description = "Recadastramento (ED-16): "
                + findingKeysByUnit.values().stream().mapToInt(List::size).sum()
                + " achado(s) da fila da P3.32 em " + findingKeysByUnit.size() + " unidade(s)";
        if (description.length() > 200) {
            description = description.substring(0, 200);
        }
        UUID serviceBatchId = orders.createBatch(batchCode, description, actorId);
        ServiceOrderService.OrderView order = orders.createOrder(new ServiceOrderService.OrderInput(
                TYPE_CODE, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actorId, null, serviceBatchId, null, null));
        for (Map.Entry<UUID, List<String>> entry : findingKeysByUnit.entrySet()) {
            String payload = Json.write(Map.of("findingKeysCsv", String.join(",", entry.getValue())));
            orders.addTarget(order.id(), new ServiceOrderService.TargetInput(
                    TargetType.CONSUMER_UNIT, entry.getKey(), null, null, EFFECT_CODE, payload), actorId);
        }
        ServiceOrderService.OrderView submitted = orders.submit(order.id(), actorId);
        return new OpenedCorrectionOrder(submitted.id(), submitted.status().name());
    }

    @Override
    public void executeAndClose(UUID orderId, UUID actorId) {
        if (orderId == null || actorId == null) {
            throw new IllegalArgumentException("OS e ator sao obrigatorios para executar e encerrar o lote.");
        }
        ServiceOrderService orders = new ServiceOrderService(dataSource, schema, tenantId);
        registerEffect(orders);
        orders.execute(orderId, actorId);
        orders.closeExecuted(orderId, actorId);
    }

    private void registerEffect(ServiceOrderService orders) {
        orders.effects().register(EFFECT_CODE, context -> {
            JsonNode payload = Json.readTree(context.effectPayloadJson());
            String csv = payload == null ? null : payload.path("findingKeysCsv").asText(null);
            if (csv == null || csv.isBlank()) {
                throw new IllegalStateException("Efeito do recadastramento sem findingKeysCsv.");
            }
            handler.onCorrectionConfirmed(context.orderId(), context.targetId(),
                    Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
        });
    }

    private void ensureType(ServiceOrderService orders, UUID actorId) {
        try {
            orders.findTypeByCode(TYPE_CODE);
        } catch (ApiException.NotFound e) {
            orders.declareType(new ServiceOrderService.ServiceTypeInput(
                    TYPE_CODE, "Recadastramento (lote)", "RECADASTRAMENTO", EFFECT_CODE,
                    EFFECT_SCHEMA,
                    ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                    List.of(TargetType.CONSUMER_UNIT), null, null, null,
                    false, false, false, false, List.of()), actorId);
        }
    }
}
