package br.com.ziipsan.grs.mtr.service;

import br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus.*;

/**
 * Transições válidas do MTR.
 *
 * <pre>
 *   EMITTED ──► COLLECTED ──► IN_TRANSIT ──► RECEIVED ──► DESTINED
 *      │            │  └────────────────────►   ▲  └──► REJECTED
 *      │            └──► CANCELLED               │
 *      └──► CANCELLED / EXPIRED         (IN_TRANSIT → CANCELLED)
 * </pre>
 *
 * <p>{@code IN_TRANSIT} é opcional: nenhum fluxo do serviço o marca hoje, então
 * {@code COLLECTED → RECEIVED} é permitido. A versão anterior só permitia
 * {@code COLLECTED → IN_TRANSIT}, estado que nada alcançava, e por isso {@code receiveMtr} falhava
 * sempre com "Invalid status transition: COLLECTED -> RECEIVED".
 *
 * <p>{@code DESTINED}, {@code REJECTED}, {@code CANCELLED} e {@code EXPIRED} são terminais.
 *
 * <p>Atenção: este é o fluxo interno do ZIIPSAN. O MTR só tem valor legal se emitido no SINIR ou em
 * sistema estadual, e os estados oficiais podem diferir destes (ver
 * {@code docs/levantamento/12-regulatorio-residuos-mtr-sinir.md}).
 */
public final class MtrStateMachine {

    private static final Map<MtrStatus, Set<MtrStatus>> TRANSITIONS = new EnumMap<>(MtrStatus.class);

    static {
        TRANSITIONS.put(EMITTED, EnumSet.of(COLLECTED, CANCELLED, EXPIRED));
        TRANSITIONS.put(COLLECTED, EnumSet.of(IN_TRANSIT, RECEIVED, CANCELLED));
        TRANSITIONS.put(IN_TRANSIT, EnumSet.of(RECEIVED, CANCELLED));
        TRANSITIONS.put(RECEIVED, EnumSet.of(DESTINED, REJECTED));
        TRANSITIONS.put(DESTINED, EnumSet.noneOf(MtrStatus.class));
        TRANSITIONS.put(REJECTED, EnumSet.noneOf(MtrStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(MtrStatus.class));
        TRANSITIONS.put(EXPIRED, EnumSet.noneOf(MtrStatus.class));
    }

    private MtrStateMachine() {
    }

    public static boolean canTransition(MtrStatus from, MtrStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<MtrStatus> allowedFrom(MtrStatus from) {
        return Set.copyOf(TRANSITIONS.getOrDefault(from, Set.of()));
    }

    public static boolean isTerminal(MtrStatus status) {
        return TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }

    /** @throws IllegalStateException se a transição não é permitida. */
    public static void require(MtrStatus from, MtrStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid status transition: " + from + " -> " + to);
        }
    }
}
