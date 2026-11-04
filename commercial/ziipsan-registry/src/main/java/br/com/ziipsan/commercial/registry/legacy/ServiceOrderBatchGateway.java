package br.com.ziipsan.commercial.registry.legacy;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Gateway da OS de lote sobre o motor da P1.31 (P1.2).
 *
 * <p>Tipo declarado (uma vez por tenant, sob demanda): {@code
 * LEGACY_IMPORT_BATCH} — interno, sem aprovação por outra pessoa (a
 * aprovação da carga é o ensaio + a conciliação revisados antes da
 * aplicação, não um passo da OS), efeito {@code LEGACY_IMPORT_CONFIRM}
 * com carga {@code {stagingId, legacyKey, matricula}}.
 *
 * <p>O manipulador do efeito é a finalização da linhagem: staging →
 * unidade → OS. Ele não cria a unidade (o aplicador já a criou); ele
 * confirma, uma vez por alvo, na mesma transação que marca o efeito —
 * que é a garantia da P1.31. Reexecutar a ordem é no-op do motor
 * (EXECUTED volta como está; alvo aplicado é pulado).
 */
public final class ServiceOrderBatchGateway implements LegacyBatchOrderGateway {

    static final String TYPE_CODE = "LEGACY_IMPORT_BATCH";
    static final String EFFECT_CODE = "LEGACY_IMPORT_CONFIRM";
    private static final String EFFECT_SCHEMA = """
            {"required": ["stagingId", "legacyKey"],
             "properties": {"stagingId": "string", "legacyKey": "string", "matricula": "string"}}""";

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final LegacyStagingStore store;

    public ServiceOrderBatchGateway(DataSource dataSource, String schema, UUID tenantId,
                                    LegacyStagingStore store) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null || store == null) {
            throw new IllegalArgumentException("dataSource, schema, tenantId e store são obrigatórios.");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.store = store;
    }

    @Override
    public OpenedOrder openBatchOrder(LegacyBatch batch, List<StagedUnit> appliedUnits, UUID actorId) {
        ServiceOrderService orders = new ServiceOrderService(dataSource, schema, tenantId);
        orders.effects().register(EFFECT_CODE, context -> {
            JsonNode payload = Json.readTree(context.effectPayloadJson());
            String stagingId = payload == null ? null : payload.path("stagingId").asText(null);
            if (stagingId == null || stagingId.isBlank()) {
                throw new IllegalStateException("Efeito de confirmação sem stagingId.");
            }
            store.markUnitApplied(batch.id(), UUID.fromString(stagingId),
                    context.targetId(), context.orderId());
        });
        ensureType(orders, actorId);
        String batchCode = "LEGACY-IMPORT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String description = "Carga de legado " + batch.mappingName() + " (" + batch.externalBatchKey() + ")";
        if (description.length() > 200) {
            description = description.substring(0, 200);
        }
        UUID serviceBatchId = orders.createBatch(batchCode, description, actorId);
        ServiceOrderService.OrderView order = orders.createOrder(new ServiceOrderService.OrderInput(
                TYPE_CODE, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, actorId, null, serviceBatchId, null, null));
        for (StagedUnit unit : appliedUnits) {
            String payload = Json.write(Map.of(
                    "stagingId", unit.id().toString(),
                    "legacyKey", unit.legacyKey(),
                    "matricula", unit.matricula() == null ? "" : unit.matricula()));
            orders.addTarget(order.id(), new ServiceOrderService.TargetInput(
                    TargetType.CONSUMER_UNIT, unit.appliedUnitId(), null, null, EFFECT_CODE, payload), actorId);
        }
        orders.submit(order.id(), actorId);
        orders.execute(order.id(), actorId);
        ServiceOrderService.OrderView closed = orders.closeExecuted(order.id(), actorId);
        return new OpenedOrder(closed.id(), closed.status().name());
    }

    private void ensureType(ServiceOrderService orders, UUID actorId) {
        try {
            orders.findTypeByCode(TYPE_CODE);
        } catch (ApiException.NotFound e) {
            orders.declareType(new ServiceOrderService.ServiceTypeInput(
                    TYPE_CODE, "Carga de legado (lote)", "IMPORT", EFFECT_CODE, EFFECT_SCHEMA,
                    ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                    List.of(TargetType.CONSUMER_UNIT), null, null, null,
                    false, false, false, false, List.of()), actorId);
        }
    }
}
