package br.com.ziipsan.commercial.billing.maintenance;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Efeitos de uma revisao aberta sobre a conta (aceite da P2.20): enquanto a
 * revisao esta aberta e dentro do teto {@code bill.review_max_days}, os
 * efeitos listados em {@code bill.review_effects} ficam suspensos --
 * cobranca, acrescimos, negativacao, corte e debito automatico.
 *
 * <p>Regras inviolaveis, fora do alcance do parametro:
 * <ul>
 *   <li><b>corte de conta em revisao e sempre vedado</b>: mesmo que o
 *       tenant tire CUT do conjunto de efeitos, {@link #isSuspended} devolve
 *       {@code true} para CUT com a revisao aberta e vigente. O parametro
 *       pode suspender <i>mais</i>, nunca liberar o corte;</li>
 *   <li>revisao vencida (alem de {@code review_max_days} contados da
 *       abertura) nao suspende mais nada: o teto e do aceite, e a fila de
 *       tarefas (P2.28) e quem cobra o encerramento -- aqui a regra apenas
 *       para de suspender, sem encerrar a revisao por conta propria.</li>
 * </ul>
 * Regra pura: quem cobra (P3.x), quem negativa (ED-30) e quem debita
 * consultam esta mesma decisao com o estado da revisao da conta.
 */
public final class ReviewSuspensionRule {

    /** Estado minimo da revisao que a regra precisa conhecer. */
    public record ReviewState(boolean open, LocalDate openedOn) {
        public ReviewState {
            if (open && openedOn == null) {
                throw new IllegalArgumentException("Revisao aberta exige a data de abertura");
            }
        }

        public static ReviewState none() {
            return new ReviewState(false, null);
        }

        public static ReviewState openSince(LocalDate openedOn) {
            return new ReviewState(true, openedOn);
        }
    }

    private ReviewSuspensionRule() {
    }

    /** A revisao esta aberta e ainda dentro do teto de dias na data dada? */
    public static boolean isReviewActive(MaintenanceParameters parameters, ReviewState review, LocalDate onDate) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(onDate, "onDate");
        if (!review.open()) {
            return false;
        }
        LocalDate deadline = review.openedOn().plusDays(parameters.reviewMaxDays());
        return !onDate.isAfter(deadline);
    }

    /**
     * O efeito esta suspenso para esta conta na data? CUT responde sempre
     * {@code true} com revisao ativa, qualquer que seja o parametro.
     */
    public static boolean isSuspended(MaintenanceParameters parameters, ReviewState review,
                                      MaintenanceParameters.ReviewEffect effect, LocalDate onDate) {
        Objects.requireNonNull(effect, "effect");
        if (!isReviewActive(parameters, review, onDate)) {
            return false;
        }
        if (effect == MaintenanceParameters.ReviewEffect.CUT) {
            return true;
        }
        Set<MaintenanceParameters.ReviewEffect> configured = parameters.reviewEffects();
        return configured.contains(effect);
    }

    /** Atalho legivel para o portao do corte: conta em revisao ativa nunca e cortada. */
    public static boolean isCutForbidden(MaintenanceParameters parameters, ReviewState review, LocalDate onDate) {
        return isSuspended(parameters, review, MaintenanceParameters.ReviewEffect.CUT, onDate);
    }
}
