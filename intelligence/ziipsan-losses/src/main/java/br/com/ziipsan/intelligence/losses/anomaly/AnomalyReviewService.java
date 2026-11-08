package br.com.ziipsan.intelligence.losses.anomaly;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Revisao humana e portao de acao da ED-94 — a fronteira DURA do
 * ADR-0042: "nenhum modelo sem humano no circuito; sem decisao
 * exclusivamente automatizada".
 *
 * <p>O desenho e por TIPO, nao por convencao:
 * <ul>
 *   <li>{@link AnomalyDetector} devolve apenas {@link AnomalyAlert}
 *       com {@code status = PROPOSED} e nao conhece nenhuma porta de
 *       acao;</li>
 *   <li>a unica fabrica de {@link ReviewedAlert} e
 *       {@link #review(AnomalyAlert, HumanReview)} — sem revisao
 *       registrada (revisor, decisao, justificativa, instante), nao
 *       existe objeto de alerta revisado;</li>
 *   <li>{@link #executeFollowUp(ReviewedAlert, FollowUpPort)} e o
 *       unico metodo que toca a {@link FollowUpPort}: ele recusa
 *       alerta descartado, recusa confirmacao sem tipo de desfecho e
 *       e a porta (motor de OS da P1.31 / pesquisa de campo) que e
 *       fiacao da integracao — esta entrega nao cria OS de verdade,
 *       apenas o pedido pos-revisao.</li>
 * </ul>
 *
 * <p>Uma revisao por alerta e garantida na persistencia pela
 * {@code UNIQUE (tenant_id, alert_id)} de {@code anomaly_reviews} no
 * SQL pendente; a referencia do desfecho (numero da OS/pesquisa
 * devolvido pela porta) volta gravada na mesma linha da revisao.
 */
public final class AnomalyReviewService {

    private AnomalyReviewService() {}

    /** Decisao do revisor humano sobre um alerta proposto. */
    public enum ReviewDecision { CONFIRMED, DISMISSED }

    /** Desfecho possivel APOS confirmacao (o aceite: "OS ou pesquisa gerada so depois da revisao"). */
    public enum FollowUpKind { SERVICE_ORDER, FIELD_SURVEY }

    /**
     * Revisao humana registrada. Justificativa e obrigatoria nos dois
     * desfechos (confirmar sem dizer por que, ou descartar sem dizer
     * por que, nao e revisao auditavel). {@code followUpKind} so pode
     * vir preenchido numa CONFIRMACAO.
     */
    public record HumanReview(String reviewerId, ReviewDecision decision, String rationale,
                              LocalDateTime reviewedAt, FollowUpKind followUpKind) {
        public HumanReview {
            if (reviewerId == null || reviewerId.isBlank())
                throw new IllegalArgumentException("revisao humana exige o revisor identificado (ADR-0042)");
            Objects.requireNonNull(decision, "decisao da revisao obrigatoria");
            if (rationale == null || rationale.isBlank())
                throw new IllegalArgumentException("revisao humana exige justificativa registrada (ADR-0042)");
            Objects.requireNonNull(reviewedAt, "instante da revisao obrigatorio");
            if (decision == ReviewDecision.DISMISSED && followUpKind != null)
                throw new IllegalArgumentException(
                        "alerta descartado na revisao nao tem desfecho: followUpKind deve ser vazio");
        }
    }

    /**
     * Porta do desfecho pos-revisao (fiacao da integracao): abre a OS
     * no motor da P1.31 ou a pesquisa de campo e devolve a referencia
     * criada. Nunca e chamada pelo detector.
     */
    public interface FollowUpPort {
        String openFollowUp(FollowUpKind kind, AnomalyAlert alert, HumanReview review);
    }

    /**
     * Registra a revisao de um alerta PROPOSED. Alerta que ja nao esta
     * PROPOSED nao se revisa de novo neste metodo (a situacao pos-revisao
     * vive no {@link ReviewedAlert} devolvido e na linha de
     * {@code anomaly_reviews}).
     */
    public static ReviewedAlert review(AnomalyAlert alert, HumanReview review) {
        Objects.requireNonNull(alert, "alerta obrigatorio");
        Objects.requireNonNull(review, "revisao humana obrigatoria");
        if (alert.status() != AnomalyAlert.Status.PROPOSED)
            throw new IllegalStateException(
                    "so alerta PROPOSED pode ser revisado; situacao atual: " + alert.status());
        if (review.reviewedAt().isBefore(alert.detectedAt()))
            throw new IllegalArgumentException(
                    "revisao anterior a deteccao do alerta nao e revisao deste alerta");
        return new ReviewedAlert(alert, review);
    }

    /**
     * Executa o desfecho de um alerta CONFIRMADO com tipo de desfecho
     * declarado, via a porta da integracao, e devolve a referencia
     * criada por ela. Qualquer outro caminho lanca excecao ANTES de
     * tocar a porta: descartado nunca gera acao; confirmado sem tipo
     * de desfecho e confirmacao de ciencia, nao pedido de acao.
     */
    public static String executeFollowUp(ReviewedAlert reviewed, FollowUpPort port) {
        Objects.requireNonNull(reviewed, "alerta revisado obrigatorio");
        Objects.requireNonNull(port, "porta de desfecho obrigatoria");
        HumanReview review = reviewed.review();
        if (review.decision() != ReviewDecision.CONFIRMED)
            throw new IllegalStateException(
                    "alerta descartado na revisao humana nao gera OS nem pesquisa (ADR-0042)");
        if (review.followUpKind() == null)
            throw new IllegalStateException(
                    "confirmacao sem tipo de desfecho (SERVICE_ORDER/FIELD_SURVEY) nao gera acao: "
                            + "foi ciencia do alerta, nao pedido");
        String ref = port.openFollowUp(review.followUpKind(), reviewed.alert(), review);
        if (ref == null || ref.isBlank())
            throw new IllegalStateException("porta de desfecho nao devolveu a referencia criada");
        return ref;
    }
}
