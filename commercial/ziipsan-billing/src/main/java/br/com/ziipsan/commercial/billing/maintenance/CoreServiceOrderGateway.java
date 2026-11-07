package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Adaptador da {@link ServiceOrderGateway} sobre o motor real da P1.31
 * ({@link ServiceOrderService}), no mesmo desenho do
 * {@code InternalOrderService} do cadastro (P1.3): uma instancia do motor
 * por chamada, amarrada ao schema/tenant do contexto da requisicao
 * ({@code TenantContextHolder} do core), porque o motor e mono-tenant por
 * construcao e o registro de handlers e por instancia.
 *
 * <p>Traducoes e responsabilidades deste adaptador (e so dele):
 * <ul>
 *   <li>{@code findTypeByCode} do motor lanca {@code NotFound} para tipo
 *       inexistente: {@link #findType} traduz para {@code null}; os demais
 *       erros do motor (conflito de transicao, validacao, tipo inativo)
 *       sobem sem traducao -- sao a resposta verdadeira do motor;</li>
 *   <li>{@link #ensureType} declara o tipo {@code BILL_*} quando ele nao
 *       existe (declaracao de {@link BillOrderType#declaration}), com o
 *       ator do contexto; tipo existente nunca e redeclarado;</li>
 *   <li>{@link #execute} registra no {@code EffectHandlerRegistry} da
 *       instancia o handler do codigo de efeito do tipo da OS (lido do
 *       proprio motor, nao presumido): o handler confere a carga (precisa
 *       carregar o {@code operationRecordId} do registro de idempotencia)
 *       e invoca o efeito de manutencao do servico. Sem esse registro o
 *       motor recusaria a execucao com {@code no-effect-handler}.</li>
 * </ul>
 */
@Component
public class CoreServiceOrderGateway implements ServiceOrderGateway {

    private final DataSource dataSource;

    public CoreServiceOrderGateway(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TypeInfo findType(BillOrderType type) {
        ServiceOrderService engine = engine();
        try {
            ServiceOrderService.ServiceTypeView view = engine.findTypeByCode(type.code());
            return new TypeInfo(view.active(), view.requiresApproval());
        } catch (ApiException.NotFound e) {
            return null;
        }
    }

    @Override
    public TypeInfo ensureType(BillOrderType type, boolean requiresApproval) {
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
    public UUID createOrder(BillOrderType type, ServiceOrderEnums.Origin origin, UUID protocolId,
                            UUID createdBy, UUID unitId, String effectPayloadJson) {
        ServiceOrderService engine = engine();
        ServiceOrderService.OrderView order = engine.createOrder(new ServiceOrderService.OrderInput(
                type.code(), origin, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, createdBy, protocolId, null, null, null));
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
    public void execute(UUID orderId, UUID actorId, MaintenanceEffect effect) {
        ServiceOrderService engine = engine();
        ServiceOrderService.OrderView order = engine.getOrder(orderId);
        ServiceOrderService.ServiceTypeView type = engine.findTypeByCode(order.serviceTypeCode());
        if (type.effectCode() != null) {
            engine.effects().register(type.effectCode(), context -> {
                JsonNode payload = Json.readTree(context.effectPayloadJson());
                if (!payload.hasNonNull("operationRecordId")) {
                    throw new IllegalStateException("Carga de efeito da OS " + orderId
                            + " sem operationRecordId: nao e uma operacao de manutencao de conta");
                }
                effect.apply();
            });
        }
        engine.execute(orderId, actorId);
    }

    @Override
    public UUID createSubmittedOrder(String typeCode, UUID unitId, UUID createdBy) {
        ServiceOrderService engine = engine();
        ServiceOrderService.OrderView order = engine.createOrder(new ServiceOrderService.OrderInput(
                typeCode, ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, createdBy, null, null, null, null));
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, unitId, null, null, null, null), createdBy);
        engine.submit(order.id(), createdBy);
        return order.id();
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
