package br.com.ziipsan.core.cutoff;

import java.sql.Connection;
import java.util.UUID;

/**
 * Ports of P3.4 (the P3.36/P3.5 pattern): the neighbouring deliveries
 * were not mirrored into this task, so their surfaces are consumed
 * through these interfaces instead of invented signatures. The
 * integration binds the real implementations; an unbound port fails
 * explicitly, never silently.
 */
public final class CutoffPorts {

    private CutoffPorts() {
    }

    /**
     * Delivery proof of a notice, from the notifications delivery of
     * P3.5: the status derives from the notice's delivery attempts
     * (only DELIVERED is proof — {@code DeliveryProofRule} of P3.5).
     */
    @FunctionalInterface
    public interface DeliveryProofPort {
        CutoffEnums.DeliveryProofStatus proofStatusOf(UUID noticeId);
    }

    /**
     * The connection-status effect itself, owned by the registry module
     * (P1.3: every status write goes through the OS effect applier of
     * ADR-0039). {@code CutoffService} registers these as the effect
     * handlers of the cutoff/reconnection types in the P1.31 engine;
     * without a binding the engine refuses execution ("no-effect-handler")
     * instead of marking an effect applied that never happened.
     *
     * <p>The {@link Connection} is the ENGINE's transaction connection
     * (from {@code EffectContext}): the status write MUST use it and
     * must not commit — the ADR-0039 applier is transactional, so the
     * effect and the engine's "applied" mark commit or roll back
     * together. (Cross-review correction: the first version of this
     * port dropped the connection, which would have let a bound
     * implementation commit the status change on its own transaction
     * while the engine rolled back, or vice versa.)
     */
    public interface ConnectionEffectPort {
        void applyCut(Connection connection, UUID consumerUnitId, UUID serviceOrderId, String cutLevel);

        void applyReconnection(Connection connection, UUID consumerUnitId, UUID serviceOrderId);
    }
}
