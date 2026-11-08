package br.com.ziipsan.intelligence.losses.anomaly;

import java.util.Objects;

/**
 * Alerta DEPOIS da revisao humana (ED-94; ADR-0042). O construtor e
 * package-private de proposito: fora deste pacote nao ha como montar
 * um {@code ReviewedAlert} — ele so nasce em
 * {@link AnomalyReviewService#review(AnomalyAlert,
 * AnomalyReviewService.HumanReview)}, que exige revisor, decisao,
 * justificativa e instante. E este o objeto que
 * {@link AnomalyReviewService#executeFollowUp} aceita: sem revisao
 * registrada, o pedido de OS/pesquisa nem compila no chamador.
 *
 * <p><b>Defesa em profundidade (correcao R1 da revisao cruzada):</b>
 * o construtor REPETE as duas invariantes de
 * {@code AnomalyReviewService.review(...)} — alerta {@code PROPOSED}
 * e instante da revisao nao anterior a deteccao. Assim, nem codigo
 * no mesmo pacote (padrao comum de adaptadores em monorepo) monta um
 * {@code ReviewedAlert} que {@code review(...)} jamais emitiria: o
 * selo nao depende de um unico ponto de validacao.
 */
public final class ReviewedAlert {

    private final AnomalyAlert alert;
    private final AnomalyReviewService.HumanReview review;

    ReviewedAlert(AnomalyAlert alert, AnomalyReviewService.HumanReview review) {
        this.alert = Objects.requireNonNull(alert, "alerta obrigatorio");
        this.review = Objects.requireNonNull(review, "revisao obrigatoria");
        if (alert.status() != AnomalyAlert.Status.PROPOSED)
            throw new IllegalStateException(
                    "so alerta PROPOSED pode compor um ReviewedAlert; situacao atual: "
                            + alert.status());
        if (review.reviewedAt().isBefore(alert.detectedAt()))
            throw new IllegalArgumentException(
                    "revisao anterior a deteccao do alerta nao e revisao deste alerta");
    }

    public AnomalyAlert alert() { return alert; }

    public AnomalyReviewService.HumanReview review() { return review; }

    /** Situacao resultante: CONFIRMED ou DISMISSED, espelho da decisao humana. */
    public AnomalyAlert.Status resultingStatus() {
        return review.decision() == AnomalyReviewService.ReviewDecision.CONFIRMED
                ? AnomalyAlert.Status.CONFIRMED
                : AnomalyAlert.Status.DISMISSED;
    }

    public boolean isConfirmed() {
        return review.decision() == AnomalyReviewService.ReviewDecision.CONFIRMED;
    }
}
