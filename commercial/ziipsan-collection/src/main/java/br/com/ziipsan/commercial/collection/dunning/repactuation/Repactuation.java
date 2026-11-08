package br.com.ziipsan.commercial.collection.dunning.repactuation;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Superendividamento (CAD-75, RAS-11; Lei 14.181/2021): a situacao em
 * repactuacao guarda numero do processo, orgao, audiencia e prazo; os
 * EFEITOS sobre encargos, negativacao e acoes vem do parametro
 * {@code dun.repactuation_effects} — conforme a lei e o plano homologado,
 * nunca fixados neste codigo alem do vocabulario.
 */
public final class Repactuation {

    private Repactuation() {
    }

    /** Efeitos possiveis da repactuacao (vocabulario de {@code dun.repactuation_effects}). */
    public enum Effect {
        SUSPEND_CHARGES,
        SUSPEND_NEGATIVATION,
        SUSPEND_ACTIONS,
        SUSPEND_CUTOFF
    }

    /** Situacao em repactuacao de um titular/unidade. */
    public record Case(
            UUID unitId,
            UUID customerId,
            String processNumber,
            String organ,
            LocalDate hearingDate,
            LocalDate deadline,
            LocalDate homologatedPlanDate,
            String homologatedPlanRef) {
        public Case {
            if (unitId == null || customerId == null
                    || processNumber == null || processNumber.isBlank()
                    || organ == null || organ.isBlank()
                    || hearingDate == null || deadline == null) {
                throw new IllegalArgumentException(
                        "repactuacao exige unidade, titular, numero do processo, orgao, audiencia e prazo");
            }
        }

        /** Ha plano homologado importavel como parcelamento de condicoes proprias. */
        public boolean hasHomologatedPlan() {
            return homologatedPlanDate != null && homologatedPlanRef != null && !homologatedPlanRef.isBlank();
        }

        /** A situacao esta vigente na data (ate o prazo, inclusive). */
        public boolean activeAt(LocalDate date) {
            return date != null && !date.isAfter(deadline);
        }
    }

    /**
     * Le {@code dun.repactuation_effects}: array JSON com os nomes dos
     * efeitos. Nome fora do vocabulario e recusado; ausencia do parametro
     * e erro explicito — os efeitos sao os da lei e do plano homologado
     * de cada ente, nao um palpite deste codigo.
     */
    public static Set<Effect> effectsOf(DunningParameters params) {
        JsonNode node = params.requiredNode(DunningParameters.REPACTUATION_EFFECTS);
        if (!node.isArray()) {
            throw new IllegalStateException(DunningParameters.REPACTUATION_EFFECTS
                    + ": esperado array de efeitos, veio " + node.getNodeType());
        }
        EnumSet<Effect> effects = EnumSet.noneOf(Effect.class);
        for (JsonNode item : node) {
            if (!item.isString()) {
                throw new IllegalStateException(DunningParameters.REPACTUATION_EFFECTS
                        + ": efeito deve ser texto, veio " + item.getNodeType());
            }
            try {
                effects.add(Effect.valueOf(item.stringValue()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(DunningParameters.REPACTUATION_EFFECTS
                        + ": efeito fora do vocabulario (SUSPEND_CHARGES, SUSPEND_NEGATIVATION, "
                        + "SUSPEND_ACTIONS, SUSPEND_CUTOFF): recusado", e);
            }
        }
        return Set.copyOf(effects);
    }
}
