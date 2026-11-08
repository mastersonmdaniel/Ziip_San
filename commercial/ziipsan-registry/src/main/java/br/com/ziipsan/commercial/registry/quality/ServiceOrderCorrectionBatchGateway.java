package br.com.ziipsan.commercial.registry.quality;

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
 * Gateway da OS de lote da correcao cadastral sobre o motor da P1.31
 * (P3.32, ADR-0039), no mesmo desenho do {@code ServiceOrderBatchGateway}
 * da carga de legado (P1.2):
 *
 * <p>Tipo declarado (uma vez por tenant, sob demanda):
 * {@code CADASTRAL_CORRECTION_BATCH} — interno, sem aprovacao por outra
 * pessoa (a revisao da correcao e campo a campo na campanha da ED-16, nao
 * um passo desta OS), efeito {@code CADASTRAL_CORRECTION_CONFIRM} com carga
 * {@code {findingKeysCsv}} (as chaves dos achados daquele alvo, separadas
 * por virgula — a chave do achado usa {@code |} como separador interno e
 * nunca contem virgula).
 *
 * <p>Diferenca para o gateway do legado: a OS NAO executa nem encerra ao
 * abrir — a correcao e trabalho posterior. Ela e criada e submetida; no
 * encerramento do lote, {@link #executeAndClose} a executa — o motor
 * aplica o efeito de cada alvo uma vez e o manipulador confirma cada alvo,
 * chamando {@link CorrectionConfirmationHandler#onCorrectionConfirmed},
 * que resolve os itens da fila daquela OS. Reexecutar a ordem e no-op do
 * motor.
 *
 * <p>Registro do efeito (correcao D6 da revisao independente): o registry
 * de efeitos do motor e POR INSTANCIA de {@code ServiceOrderService} —
 * registrar o handler so na instancia que abre a OS nao serve para a
 * execucao posterior, que roda em outra instancia/processo. Por isso o
 * registro e fatorado em {@code registerEffect(...)} e chamado TANTO na
 * abertura QUANTO em {@link #executeAndClose}, que constroi a instancia
 * executora e registra o handler NELA antes de executar — o mesmo desenho
 * que a ED-16 adotou sobre esta porta. (A versao anterior registrava o
 * handler apenas na instancia descartavel da abertura e nao expunha
 * caminho de execucao nenhum.)
 */
public final class ServiceOrderCorrectionBatchGateway implements BatchOrderGateway {

    static final String TYPE_CODE = "CADASTRAL_CORRECTION_BATCH";
    static final String EFFECT_CODE = "CADASTRAL_CORRECTION_CONFIRM";
    private static final String EFFECT_SCHEMA = """
            {"required": ["findingKeysCsv"],
             "properties": {"findingKeysCsv": "string"}}""";

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final CorrectionConfirmationHandler handler;

    public ServiceOrderCorrectionBatchGateway(DataSource dataSource, String schema, UUID tenantId,
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
        String batchCode = "QUALITY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String description = "Correcao de qualidade cadastral (P3.32): "
                + findingKeysByUnit.values().stream().mapToInt(List::size).sum()
                + " achado(s) em " + findingKeysByUnit.size() + " unidade(s)";
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

    /**
     * Executa a OS de lote e a encerra como executada — o caminho de
     * execucao real que faltava (D6): constroi a instancia executora do
     * motor, registra o handler do efeito NELA (o registry de efeitos e
     * por instancia) e so entao executa. O handler confirma cada alvo uma
     * vez (garantia do motor) e resolve os itens da fila daquela OS via
     * {@link CorrectionConfirmationHandler}; a fiacao de integracao liga
     * esse handler ao {@code CorrectionQueueService.resolveByServiceOrder}.
     */
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
                throw new IllegalStateException("Efeito de correcao sem findingKeysCsv.");
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
                    TYPE_CODE, "Correcao de qualidade cadastral (lote)", "QUALITY", EFFECT_CODE,
                    EFFECT_SCHEMA,
                    ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                    List.of(TargetType.CONSUMER_UNIT), null, null, null,
                    false, false, false, false, List.of()), actorId);
        }
    }
}
