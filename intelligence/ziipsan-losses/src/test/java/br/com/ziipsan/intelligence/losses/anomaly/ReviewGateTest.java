package br.com.ziipsan.intelligence.losses.anomaly;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portao do ADR-0042 (ED-94): nenhuma acao sem revisao humana
 * registrada. A porta de desfecho e um duble honesto declarado que so
 * conta chamadas — ele prova que o caminho SEM revisao (ou com
 * descarte) nunca a toca.
 */
class ReviewGateTest {

    private static final LocalDateTime DETECTED = LocalDateTime.of(2026, 3, 9, 3, 0);

    private static AnomalyAlert proposedAlert() {
        return new AnomalyAlert("DMC-NORTE", AnomalySeries.SeriesKind.NIGHT_FLOW_DMC, "L/h",
                AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION, DETECTED, DETECTED.minusWeeks(4), DETECTED,
                110.0, 100.0, 1.4826, 6.7449, "deviationK=3.0 sigmas robustos (MAD)",
                0.5, "Desvio na mesma hora/dia da semana: valor 110...",
                List.of(new AnomalySeries.SeriesPoint(DETECTED, 110.0)),
                "rules=[SAME_HOUR_WEEKDAY_DEVIATION];deviationK=3.0",
                AnomalyAlert.Status.PROPOSED);
    }

    private static final class CountingPort implements AnomalyReviewService.FollowUpPort {
        final AtomicInteger calls = new AtomicInteger();
        AnomalyReviewService.FollowUpKind lastKind;

        @Override
        public String openFollowUp(AnomalyReviewService.FollowUpKind kind,
                                   AnomalyAlert alert, AnomalyReviewService.HumanReview review) {
            calls.incrementAndGet();
            lastKind = kind;
            return "OS-2026-0001";
        }
    }

    @Test
    void confirmedWithFollowUpKindOpensExactlyOnce() {
        CountingPort port = new CountingPort();
        ReviewedAlert reviewed = AnomalyReviewService.review(proposedAlert(),
                new AnomalyReviewService.HumanReview("operador-7",
                        AnomalyReviewService.ReviewDecision.CONFIRMED,
                        "vazao noturna subiu 3 semanas seguidas; abrir OS de pesquisa de vazamento",
                        DETECTED.plusHours(2), AnomalyReviewService.FollowUpKind.SERVICE_ORDER));
        assertEquals(AnomalyAlert.Status.CONFIRMED, reviewed.resultingStatus());
        String ref = AnomalyReviewService.executeFollowUp(reviewed, port);
        assertEquals("OS-2026-0001", ref);
        assertEquals(1, port.calls.get());
        assertEquals(AnomalyReviewService.FollowUpKind.SERVICE_ORDER, port.lastKind);
    }

    @Test
    void dismissedNeverOpensFollowUp() {
        CountingPort port = new CountingPort();
        ReviewedAlert reviewed = AnomalyReviewService.review(proposedAlert(),
                new AnomalyReviewService.HumanReview("operador-7",
                        AnomalyReviewService.ReviewDecision.DISMISSED,
                        "manobra programada de rede naquela madrugada; serie explicada",
                        DETECTED.plusHours(1), null));
        assertEquals(AnomalyAlert.Status.DISMISSED, reviewed.resultingStatus());
        assertThrows(IllegalStateException.class,
                () -> AnomalyReviewService.executeFollowUp(reviewed, port));
        assertEquals(0, port.calls.get(), "descartado nao pode tocar a porta de acao");
    }

    @Test
    void confirmedWithoutFollowUpKindIsAcknowledgementNotAction() {
        CountingPort port = new CountingPort();
        ReviewedAlert reviewed = AnomalyReviewService.review(proposedAlert(),
                new AnomalyReviewService.HumanReview("operador-7",
                        AnomalyReviewService.ReviewDecision.CONFIRMED,
                        "confirmo a anomalia; aguardar mais uma semana antes de agir",
                        DETECTED.plusHours(1), null));
        assertThrows(IllegalStateException.class,
                () -> AnomalyReviewService.executeFollowUp(reviewed, port));
        assertEquals(0, port.calls.get());
    }

    @Test
    void reviewRequiresIdentifiedReviewerAndRationale() {
        assertThrows(IllegalArgumentException.class, () -> new AnomalyReviewService.HumanReview(
                " ", AnomalyReviewService.ReviewDecision.CONFIRMED, "ok",
                DETECTED.plusHours(1), AnomalyReviewService.FollowUpKind.FIELD_SURVEY));
        assertThrows(IllegalArgumentException.class, () -> new AnomalyReviewService.HumanReview(
                "operador-7", AnomalyReviewService.ReviewDecision.CONFIRMED, " ",
                DETECTED.plusHours(1), AnomalyReviewService.FollowUpKind.FIELD_SURVEY));
    }

    @Test
    void dismissedWithFollowUpKindIsContradictionAndRefused() {
        assertThrows(IllegalArgumentException.class, () -> new AnomalyReviewService.HumanReview(
                "operador-7", AnomalyReviewService.ReviewDecision.DISMISSED, "nao procede",
                DETECTED.plusHours(1), AnomalyReviewService.FollowUpKind.SERVICE_ORDER));
    }

    @Test
    void reviewBeforeDetectionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AnomalyReviewService.review(proposedAlert(),
                new AnomalyReviewService.HumanReview("operador-7",
                        AnomalyReviewService.ReviewDecision.CONFIRMED, "ok",
                        DETECTED.minusMinutes(1), AnomalyReviewService.FollowUpKind.SERVICE_ORDER)));
    }

    @Test
    void directConstructionBypassingReviewIsRefused() {
        // correcao R1 da revisao cruzada: os testes vivem NO MESMO
        // pacote do dominio — a construcao direta de ReviewedAlert
        // (a rota que review() nao fiscalizava) agora revalida as
        // invariantes no construtor e recusa o que review() recusaria
        AnomalyAlert neverProposed = new AnomalyAlert("DMC-NORTE",
                AnomalySeries.SeriesKind.NIGHT_FLOW_DMC, "L/h",
                AnomalyRule.SAME_HOUR_WEEKDAY_DEVIATION, DETECTED, DETECTED, DETECTED,
                110.0, null, null, null, "limiar", 0.9, "expl",
                List.of(new AnomalySeries.SeriesPoint(DETECTED, 110.0)), "snap",
                AnomalyAlert.Status.CONFIRMED);
        AnomalyReviewService.HumanReview validReview = new AnomalyReviewService.HumanReview(
                "operador-7", AnomalyReviewService.ReviewDecision.CONFIRMED, "ok",
                DETECTED.plusHours(1), AnomalyReviewService.FollowUpKind.SERVICE_ORDER);
        assertThrows(IllegalStateException.class,
                () -> new ReviewedAlert(neverProposed, validReview),
                "alerta que nunca foi PROPOSED nao compoe ReviewedAlert nem direto");
        AnomalyReviewService.HumanReview preDetection = new AnomalyReviewService.HumanReview(
                "operador-7", AnomalyReviewService.ReviewDecision.CONFIRMED, "ok",
                DETECTED.minusDays(10), AnomalyReviewService.FollowUpKind.SERVICE_ORDER);
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewedAlert(proposedAlert(), preDetection),
                "revisao anterior a deteccao nao compoe ReviewedAlert nem direto");
        // e a rota legitima segue intacta: review() emite o objeto
        ReviewedAlert viaReview = AnomalyReviewService.review(proposedAlert(), validReview);
        assertEquals(AnomalyAlert.Status.CONFIRMED, viaReview.resultingStatus());
    }

    @Test
    void detectorOnlyProposesNeverConfirms() {
        // serie sintetica com anomalia conhecida: TODOS os alertas nascem PROPOSED
        java.util.List<AnomalySeries.SeriesPoint> points = new java.util.ArrayList<>();
        LocalDateTime t0 = LocalDateTime.of(2026, 3, 2, 0, 0);
        for (int i = 0; i < 30; i++) points.add(new AnomalySeries.SeriesPoint(t0.plusHours(i), 5));
        AnomalySeries series = new AnomalySeries("LIG-9",
                AnomalySeries.SeriesKind.CONSUMPTION_CONNECTION, "L", points);
        List<AnomalyAlert> alerts = AnomalyDetector.evaluate(series,
                AnomalyParametersTest.config("ALL", AnomalyParametersTest.FULL_SENSITIVITY));
        assertTrue(!alerts.isEmpty());
        assertTrue(alerts.stream().allMatch(a -> a.status() == AnomalyAlert.Status.PROPOSED),
                "o motor propoe; quem confirma e o humano (ADR-0042)");
    }
}
