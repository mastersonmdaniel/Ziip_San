package br.com.ziipsan.core.nondomestic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Notificacao ANTES da penalidade; valor/base da penalidade sao do processo [SEM FONTE]. */
class NonConformityCaseTest {

    private static NonConformityCase detected() {
        return new NonConformityCase(UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                NonConformityCase.Origin.PROVIDER_SAMPLING, "PARAM_A",
                "fora do padrao revisado [FIXTURE]", TestSupport.T0);
    }

    @Test
    void penaltyBeforeNotificationIsRefused() {
        NonConformityCase c = detected();
        assertThrows(IllegalStateException.class, () -> c.applyPenalty(
                new BigDecimal("100.00"), "BASE [FIXTURE]", TestSupport.T0.plusSeconds(60)));
    }

    @Test
    void penaltyNeedsDeclaredAmountAndBasis() {
        NonConformityCase c = detected();
        c.recordNotification("NOTIF-1 [FIXTURE]", TestSupport.T0.plusSeconds(3600));
        assertThrows(IllegalArgumentException.class, () -> c.applyPenalty(
                null, "BASE [FIXTURE]", TestSupport.T0.plusSeconds(7200)));
        assertThrows(IllegalArgumentException.class, () -> c.applyPenalty(
                new BigDecimal("0"), "BASE [FIXTURE]", TestSupport.T0.plusSeconds(7200)));
        assertThrows(IllegalArgumentException.class, () -> c.applyPenalty(
                new BigDecimal("100.00"), " ", TestSupport.T0.plusSeconds(7200)));
        c.applyPenalty(new BigDecimal("100.00"), "BASE [FIXTURE]", TestSupport.T0.plusSeconds(7200));
        assertEquals(NonConformityCase.Status.PENALIZED, c.status());
        assertEquals(new BigDecimal("100.00"), c.penaltyAmount());
    }

    @Test
    void resolutionNeedsCorrectionEvidenceAndReinspectionOrder() {
        NonConformityCase c = detected();
        c.recordNotification("NOTIF-1 [FIXTURE]", TestSupport.T0.plusSeconds(3600));
        assertThrows(IllegalArgumentException.class, () -> c.resolve(
                null, UUID.randomUUID(), TestSupport.T0.plusSeconds(7200)));
        assertThrows(IllegalArgumentException.class, () -> c.resolve(
                "EVID-1 [FIXTURE]", null, TestSupport.T0.plusSeconds(7200)));
        UUID os = UUID.randomUUID();
        c.resolve("EVID-1 [FIXTURE]", os, TestSupport.T0.plusSeconds(7200));
        assertEquals(NonConformityCase.Status.RESOLVED, c.status());
        assertEquals(os, c.reinspectionOrderId());
    }

    @Test
    void notificationCannotPredateDetection() {
        NonConformityCase c = detected();
        assertThrows(IllegalArgumentException.class, () -> c.recordNotification(
                "NOTIF-1", TestSupport.T0.minusSeconds(1)));
    }
}
