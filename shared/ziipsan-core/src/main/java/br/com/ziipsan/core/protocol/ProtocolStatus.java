package br.com.ziipsan.core.protocol;

import java.util.Map;
import java.util.Set;

/**
 * State machine of a service protocol (P1.36, plan line 663).
 *
 * <p>The CLOSED close-reason rule (a classified reason is mandatory on
 * closure) and the terminal timestamp are CHECKs in V8_1; the graph
 * itself is enforced here, mirroring ServiceOrderStatus. Closing is
 * allowed from ANSWERED (the answer was given) and from IN_PROGRESS (the
 * work was done and the answer recorded out of band); it is not allowed
 * from OPEN (nothing was done yet) or ANSWER_PENDING (the answer the
 * user waits for does not exist yet). Cancelling is allowed while the
 * request is still unanswered; an answered protocol closes, it is not
 * cancelled.
 */
public enum ProtocolStatus {
    OPEN,
    IN_PROGRESS,
    ANSWER_PENDING,
    ANSWERED,
    CLOSED,
    CANCELLED;

    private static final Map<ProtocolStatus, Set<ProtocolStatus>> ALLOWED = Map.of(
            OPEN, Set.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, Set.of(ANSWER_PENDING, CLOSED, CANCELLED),
            ANSWER_PENDING, Set.of(ANSWERED, IN_PROGRESS, CANCELLED),
            ANSWERED, Set.of(CLOSED, IN_PROGRESS),
            CLOSED, Set.of(),
            CANCELLED, Set.of());

    /** True when this status may move to {@code next}. Staying put is allowed (no-op). */
    public boolean canMoveTo(ProtocolStatus next) {
        return this == next || ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
