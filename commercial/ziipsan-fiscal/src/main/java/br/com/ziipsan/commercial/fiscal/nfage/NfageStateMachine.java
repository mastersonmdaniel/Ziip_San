package br.com.ziipsan.commercial.fiscal.nfage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Máquina de estados da NFAg-e (os 9 estados de {@link NfageStatus}, DAD-18). Regra
 * pura: nenhuma transição fora da tabela é permitida, e os estados terminais
 * (REJECTED, CANCELLED, REPLACED, VOIDED) não têm saída — um documento rejeitado não
 * "volta": a correção é um documento novo, com número novo de {@code fiscal_series}.
 *
 * <pre>
 * RESERVED ──&gt; SIGNED ──&gt; TRANSMITTED ──&gt; AUTHORIZED ──&gt; CANCELLED (evento)
 *    │            │              │   │             └───────&gt; REPLACED (substituição)
 *    │            │              │   └───────&gt; REJECTED
 *    │            │              └───────────&gt; PENDING_RECONCILIATION ──&gt; AUTHORIZED | REJECTED
 *    └────────────┴──&gt; VOIDED (inutilização do número reservado, antes da transmissão)
 * </pre>
 */
public final class NfageStateMachine {

    private static final Map<NfageStatus, Set<NfageStatus>> TRANSITIONS = transitions();

    private static final Set<NfageStatus> LIVE = EnumSet.of(
            NfageStatus.RESERVED, NfageStatus.SIGNED, NfageStatus.TRANSMITTED,
            NfageStatus.AUTHORIZED, NfageStatus.PENDING_RECONCILIATION);

    private static Map<NfageStatus, Set<NfageStatus>> transitions() {
        Map<NfageStatus, Set<NfageStatus>> m = new EnumMap<>(NfageStatus.class);
        m.put(NfageStatus.RESERVED, EnumSet.of(NfageStatus.SIGNED, NfageStatus.VOIDED));
        m.put(NfageStatus.SIGNED, EnumSet.of(NfageStatus.TRANSMITTED, NfageStatus.VOIDED));
        m.put(NfageStatus.TRANSMITTED, EnumSet.of(
                NfageStatus.AUTHORIZED, NfageStatus.REJECTED, NfageStatus.PENDING_RECONCILIATION));
        m.put(NfageStatus.PENDING_RECONCILIATION, EnumSet.of(
                NfageStatus.AUTHORIZED, NfageStatus.REJECTED));
        m.put(NfageStatus.AUTHORIZED, EnumSet.of(NfageStatus.CANCELLED, NfageStatus.REPLACED));
        m.put(NfageStatus.REJECTED, EnumSet.noneOf(NfageStatus.class));
        m.put(NfageStatus.CANCELLED, EnumSet.noneOf(NfageStatus.class));
        m.put(NfageStatus.REPLACED, EnumSet.noneOf(NfageStatus.class));
        m.put(NfageStatus.VOIDED, EnumSet.noneOf(NfageStatus.class));
        return Map.copyOf(m);
    }

    private NfageStateMachine() {
    }

    public static boolean canTransition(NfageStatus from, NfageStatus to) {
        Objects.requireNonNull(from, "Estado de origem é obrigatório");
        Objects.requireNonNull(to, "Estado de destino é obrigatório");
        return TRANSITIONS.get(from).contains(to);
    }

    /**
     * @throws IllegalStateException se a transição não estiver na tabela — transição
     *                               ilegal é erro explícito, nunca ignorada
     */
    public static void requireTransition(NfageStatus from, NfageStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Transição de estado da NFAg-e não permitida: " + from + " -> " + to);
        }
    }

    /** Documento vivo: conta para a unicidade de origem e é o que a reexecução devolve. */
    public static boolean isLive(NfageStatus status) {
        Objects.requireNonNull(status, "Estado é obrigatório");
        return LIVE.contains(status);
    }

    /** Estado terminal: nenhuma transição de saída (REJECTED, CANCELLED, REPLACED, VOIDED). */
    public static boolean isTerminal(NfageStatus status) {
        Objects.requireNonNull(status, "Estado é obrigatório");
        return TRANSITIONS.get(status).isEmpty();
    }
}
