package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;
import br.com.ziipsan.core.tenant.TenantContextHolder;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Implementação real de {@link ClickOrderEngine} sobre o motor de OS
 * da P1.31, no mesmo desenho do {@code CoreLeakMeterOrderGateway} do
 * billing: **uma instância do motor por chamada**, amarrada ao
 * schema/tenant do contexto da requisição ({@code TenantContextHolder}),
 * porque o motor é mono-tenant por construção e o registo de
 * verificadores é por instância.
 *
 * <p>Verificadores GIS: o motor só regista {@code CONSUMER_UNIT} de
 * fábrica. As classes GIS tornam-se declaramáveis quando o módulo
 * GIS regista os seus verificadores (javadoc de {@code TargetType};
 * precedente {@code ServiceOrderEngineIT}): {@code GIS_POINT} sobre
 * {@code gis_node} e {@code GIS_LINEAR} sobre {@code gis_arc}, pelo
 * {@code sqlVerifier} do próprio core. O tipo de serviço do tenant
 * tem ainda de aceitar estas classes em {@code targetClasses} — sem
 * isso o motor recusa o alvo com a validação nomeada dele, e a
 * recusa sobe sem tradução.
 */
final class CoreClickOrderEngine implements ClickOrderEngine {

    private final ServiceOrderService engine;

    private CoreClickOrderEngine(ServiceOrderService engine) {
        this.engine = engine;
    }

    static CoreClickOrderEngine fromContext(DataSource dataSource) {
        String schema = TenantContextHolder.getTenant();
        UUID tenantId = TenantContextHolder.getTenantId();
        if (schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalStateException(
                    "sem schema/tenant no contexto da requisição para o motor de OS");
        }
        ServiceOrderService engine = new ServiceOrderService(dataSource, schema, tenantId);
        engine.verifiers().register(TargetType.GIS_POINT,
                TargetVerifierRegistry.sqlVerifier(schema, "gis_node"));
        engine.verifiers().register(TargetType.GIS_LINEAR,
                TargetVerifierRegistry.sqlVerifier(schema, "gis_arc"));
        return new CoreClickOrderEngine(engine);
    }

    @Override
    public ServiceOrderEnums.TypeIndicator typeIndicator(String serviceTypeCode) {
        return engine.findTypeByCode(serviceTypeCode).indicator();
    }

    @Override
    public UUID createOrder(String serviceTypeCode,
                            ServiceOrderEnums.ExecutionMode executionMode, UUID requestedBy) {
        ServiceOrderService.OrderView order = engine.createOrder(new ServiceOrderService.OrderInput(
                serviceTypeCode, ServiceOrderEnums.Origin.INTERNAL, executionMode,
                ServiceOrderEnums.Priority.NORMAL, requestedBy, null, null, null, null));
        return order.id();
    }

    @Override
    public void addTarget(UUID orderId, TargetType targetType, UUID targetId, UUID actorId) {
        engine.addTarget(orderId, new ServiceOrderService.TargetInput(
                targetType, targetId, null, null, null, null), actorId);
    }

    @Override
    public void submit(UUID orderId, UUID actorId) {
        engine.submit(orderId, actorId);
    }
}
