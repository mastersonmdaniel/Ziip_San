package br.com.ziipsan.core.serviceorder;

import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effect handlers (ADR-0045 item 3: the package registers its effects
 * through the nucleus interfaces; the nucleus knows no effect by name).
 *
 * <p>An effect is applied by the engine exactly once per target, inside
 * the same transaction that marks it applied (see
 * {@link ServiceOrderService#execute}). There is no reversal: correcting
 * an applied effect means opening an inverse order linked by
 * {@code reverses_order_id}.
 *
 * <p>When no handler is registered for an effect code, execution is
 * refused instead of silently marking the effect applied: a recorded
 * effect that never happened would be worse than a blocked order. The
 * owning module (registry for cadastral effects, billing for postings)
 * registers its handlers; operational types carry no effect code at all
 * and execute without a handler.
 */
public final class EffectHandlerRegistry {

    /** Context handed to a handler; the connection is the engine's transaction. */
    public record EffectContext(
            Connection connection,
            String schema,
            UUID tenantId,
            UUID orderId,
            UUID targetId,
            TargetType targetType,
            String effectCode,
            String effectPayloadJson,
            UUID actorId) {
    }

    /** A handler performs the mutation of one effect. It must not commit. */
    @FunctionalInterface
    public interface EffectHandler {
        void apply(EffectContext context) throws Exception;
    }

    private final Map<String, EffectHandler> handlers = new ConcurrentHashMap<>();

    public void register(String effectCode, EffectHandler handler) {
        if (effectCode == null || effectCode.isBlank() || handler == null) {
            throw new IllegalArgumentException("effect code and handler are required");
        }
        handlers.put(effectCode, handler);
    }

    public boolean hasHandler(String effectCode) {
        return effectCode != null && handlers.containsKey(effectCode);
    }

    public EffectHandler handler(String effectCode) {
        return handlers.get(effectCode);
    }
}
