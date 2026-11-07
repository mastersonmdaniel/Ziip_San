package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adaptador da {@link LeakMeterOrderGateway} sobre o motor real da P1.31
 * ({@link ServiceOrderService}), no mesmo desenho do
 * {@code CoreServiceOrderGateway} da P2.20: uma instancia do motor por
 * chamada, amarrada ao schema/tenant do contexto da requisicao
 * ({@code TenantContextHolder} do core), porque o motor e mono-tenant por
 * construcao e o registro de handlers e por instancia.
 *
 * <p>Traducoes e responsabilidades deste adaptador (e so dele):
 * <ul>
 *   <li>{@code findTypeByCode} do motor lanca {@code NotFound} para tipo
 *       inexistente: {@link #findType} traduz para {@code null}; os demais
 *       erros do motor sobem sem traducao;</li>
 *   <li>{@link #ensureType} declara o tipo quando ele nao existe
 *       (declaracao de {@link LeakMeterOrderType#declaration}), com o ator
 *       do contexto; tipo existente nunca e redeclarado;</li>
 *   <li>{@link #execute} registra no {@code EffectHandlerRegistry} da
 *       instancia o handler do codigo de efeito do tipo da OS (lido do
 *       proprio motor, nao presumido): o handler confere que a carga e um
 *       objeto JSON e invoca o efeito do servico. Sem esse registro o motor
 *       recusaria a execucao com {@code no-effect-handler}.</li>
 * </ul>
 */
@Component
public class CoreLeakMeterOrderGateway implements LeakMeterOrderGateway {

    private final DataSource dataSource;

    public CoreLeakMeterOrderGateway(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TypeInfo findType(LeakMeterOrderType type) {
        ServiceOrderService engine = engine();
        try {
            ServiceOrderService.ServiceTypeView view = engine.findTypeByCode(type.code());
            return new TypeInfo(view.active(), view.requiresApproval());
        } catch (ApiException.NotFound e) {
            return null;
        }
    }

    @Override
    public TypeInfo ensureType(LeakMeterOrderType type, boolean requiresApproval) {
        ServiceOrderService engine = engine();
        try {
            ServiceOrderService.ServiceTypeView view = engine.findTypeByCode(type.code());
            return new TypeInfo(view.active(), view.requiresApproval());
        } catch (ApiException.NotFound e) {
            engine.declareType(type.declaration(requiresApproval), TenantContextHolder.getUserId());
            ServiceOrderService.ServiceTypeView view = engine.findTypeByCode(type.code());
            return new TypeInfo(view.active(), view.requiresApproval());
        }
    }

    @Override
    public UUID createOrder(LeakMeterOrderType type, ServiceOrderEnums.Origin origin, UUID protocolId,
                            UUID createdBy, UUID unitId, UUID parentOrderId, String effectPayloadJson) {
        ServiceOrderService engine = engine();
        ServiceOrderService.OrderView order = engine.createOrder(new ServiceOrderService.OrderInput(
                type.code(), origin, ServiceOrderEnums.ExecutionMode.FIELD,
                ServiceOrderEnums.Priority.NORMAL, createdBy, protocolId, null, parentOrderId, null));
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unitId, null, null, null, effectPayloadJson), createdBy);
        engine.submit(order.id(), createdBy);
        return order.id();
    }

    @Override
    public void approve(UUID orderId, UUID approverId) {
        engine().approve(orderId, approverId);
    }

    @Override
    public void schedule(UUID orderId, OffsetDateTime scheduledFor, UUID actorId) {
        engine().schedule(orderId, scheduledFor, actorId);
    }

    @Override
    public void startExecution(UUID orderId, UUID actorId) {
        engine().startExecution(orderId, actorId);
    }

    @Override
    public void execute(UUID orderId, UUID actorId, LeakMeterEffect effect) {
        ServiceOrderService engine = engine();
        ServiceOrderService.OrderView order = engine.getOrder(orderId);
        ServiceOrderService.ServiceTypeView type = engine.findTypeByCode(order.serviceTypeCode());
        if (type.effectCode() != null) {
            engine.effects().register(type.effectCode(), context -> {
                JsonNode payload = Json.readTree(context.effectPayloadJson());
                if (!payload.isObject()) {
                    throw new IllegalStateException("Carga de efeito da OS " + orderId
                            + " nao e um objeto JSON: nao e uma operacao de vazamento/afericao");
                }
                effect.apply();
            });
        }
        engine.execute(orderId, actorId);
    }

    // ------------------------------------------------------------------

    private ServiceOrderService engine() {
        String schema = TenantContextHolder.getTenant();
        UUID tenantId = TenantContextHolder.getTenantId();
        if (schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalStateException("sem schema/tenant no contexto da requisicao para o motor de OS");
        }
        return new ServiceOrderService(dataSource, schema, tenantId);
    }
}
