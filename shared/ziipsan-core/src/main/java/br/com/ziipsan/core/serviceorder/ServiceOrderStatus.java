package br.com.ziipsan.core.serviceorder;

import java.util.Map;
import java.util.Set;

/**
 * Work-order state machine (P1.31; the same graph is enforced by the
 * {@code trg_service_order_transition} trigger in V9_1, so a direct SQL
 * write cannot bypass it either).
 *
 * <p>Internal orders (backoffice, no field trip) may go {@code OPEN} to
 * {@code EXECUTED} directly. Field orders go through {@code SCHEDULED} /
 * {@code IN_EXECUTION}; the scheduling screens themselves are ED-36, the
 * engine only guards the transitions and the safety gates on schedule and
 * close.
 */
public enum ServiceOrderStatus {
    DRAFT,
    OPEN,
    SCHEDULED,
    IN_EXECUTION,
    EXECUTED,
    WAITING_DEPENDENCY,
    PENDING_APPROVAL,
    CLOSED_EXECUTED,
    CLOSED_NOT_EXECUTED;

    private static final Map<ServiceOrderStatus, Set<ServiceOrderStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(OPEN, PENDING_APPROVAL, CLOSED_NOT_EXECUTED),
            OPEN, Set.of(SCHEDULED, IN_EXECUTION, WAITING_DEPENDENCY, PENDING_APPROVAL,
                    EXECUTED, CLOSED_NOT_EXECUTED),
            SCHEDULED, Set.of(IN_EXECUTION, WAITING_DEPENDENCY, CLOSED_NOT_EXECUTED),
            IN_EXECUTION, Set.of(EXECUTED, WAITING_DEPENDENCY, CLOSED_NOT_EXECUTED),
            WAITING_DEPENDENCY, Set.of(OPEN, SCHEDULED, IN_EXECUTION, CLOSED_NOT_EXECUTED),
            PENDING_APPROVAL, Set.of(OPEN, CLOSED_NOT_EXECUTED),
            EXECUTED, Set.of(CLOSED_EXECUTED),
            CLOSED_EXECUTED, Set.of(),
            CLOSED_NOT_EXECUTED, Set.of());

    /** True when this status may move to {@code next}. Staying put is allowed (no-op). */
    public boolean canMoveTo(ServiceOrderStatus next) {
        return this == next || ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == CLOSED_EXECUTED || this == CLOSED_NOT_EXECUTED;
    }
}
