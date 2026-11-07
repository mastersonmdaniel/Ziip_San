package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.List;

/**
 * The three service types of P3.4, declared THROUGH the P1.31 engine
 * (no parallel order engine): cutoff and reconnection are FIELD types
 * whose effect changes the connection status of a CONSUMER_UNIT target;
 * the inspection type is the ADR-0039 answer to consumption after a
 * cutoff — operational, no commercial effect, and the unit's situation
 * only changes when THIS order is closed (its effect handler belongs to
 * the registry/integration, like the other status effects).
 *
 * <p>Deadlines of the types are NOT declared here: the reconnection SLA
 * is {@code rec.sla_hours} of the agency (computed by
 * {@link ReconnectionRule} into {@code reconnection_orders.sla_due_at}),
 * and inventing an engine deadline would duplicate it.
 */
public final class CutoffOrderTypes {

    public static final String CUTOFF_TYPE_CODE = "WATER_CUTOFF";
    public static final String RECONNECTION_TYPE_CODE = "WATER_RECONNECTION";
    public static final String INSPECTION_TYPE_CODE = "CONNECTION_INSPECTION";

    public static final String CUT_EFFECT_CODE = "CONNECTION_CUT";
    public static final String RECONNECT_EFFECT_CODE = "CONNECTION_RECONNECT";

    /** Close reason the engine requires in its catalog for the revalidation cancellation (class a). */
    public static final String REVALIDATION_CLOSE_REASON = "CUT_REVALIDATION_FAILED";

    private static final String EFFECT_SCHEMA =
            "{\"required\":[\"consumerUnitId\"],\"properties\":{\"consumerUnitId\":\"string\"}}";

    private CutoffOrderTypes() {
    }

    public static ServiceOrderService.ServiceTypeInput cutoffType() {
        return new ServiceOrderService.ServiceTypeInput(
                CUTOFF_TYPE_CODE, "Corte de agua", "CUTOFF", CUT_EFFECT_CODE, EFFECT_SCHEMA,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }

    public static ServiceOrderService.ServiceTypeInput reconnectionType() {
        return new ServiceOrderService.ServiceTypeInput(
                RECONNECTION_TYPE_CODE, "Religacao de agua", "RECONNECTION", RECONNECT_EFFECT_CODE, EFFECT_SCHEMA,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }

    public static ServiceOrderService.ServiceTypeInput inspectionType() {
        return new ServiceOrderService.ServiceTypeInput(
                INSPECTION_TYPE_CODE, "Fiscalizacao de ligacao cortada", "INSPECTION", null, null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, true,
                ServiceOrderEnums.OperationalOrigin.INSPECTION,
                List.of(TargetType.CONSUMER_UNIT), null, null, null,
                false, false, false, false, List.of());
    }
}
