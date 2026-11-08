package br.com.ziipsan.platform.engagement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderAndInvoiceTest {

    private EngagementTestSupport.Fixture baseComMetaEProgresso(String billed) {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        f.consumption.billed = new BigDecimal(billed);
        return f;
    }

    @Test
    void lembreteDeMetaAtingidaDespachaUmaUnicaVezPelaEd41() {
        var f = baseComMetaEProgresso("9");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        assertTrue(f.reminderService.notifyGoalOutcome(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, p));
        assertEquals(1, f.reminders.sent.size());
        assertTrue(f.reminders.sent.get(0).startsWith("GOAL_ACHIEVED|"));
        // segunda chamada no mesmo periodo: deduplicado, nao despacha de novo
        assertFalse(f.reminderService.notifyGoalOutcome(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, p));
        assertEquals(1, f.reminders.sent.size());
    }

    @Test
    void lembreteDeMetaExcedidaUsaOTipoCerto() {
        var f = baseComMetaEProgresso("12");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        assertTrue(f.reminderService.notifyGoalOutcome(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, p));
        assertTrue(f.reminders.sent.get(0).startsWith("GOAL_EXCEEDED|"));
    }

    @Test
    void semAdesaoNenhumLembreteEhDespachado() {
        // Pos-correcao D1 o proprio progress() tambem exige adesao, entao o
        // progresso e obtido COM adesao e a adesao e REVOGADA antes do
        // lembrete: o servico de lembretes re-checa e nao despacha nada.
        var f = baseComMetaEProgresso("9");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        f.enrollments.save(f.enrollments.find(EngagementTestSupport.TENANT,
                        EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT).orElseThrow()
                .optOut(java.time.Instant.parse("2026-10-05T10:00:00Z")));
        assertFalse(f.reminderService.notifyGoalOutcome(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, p));
        assertTrue(f.reminders.sent.isEmpty());
    }

    @Test
    void lembreteDeRelatorioSoQuandoPublicado() {
        var f = baseComMetaEProgresso("10");
        var suprimido = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohortN(4, "12"));
        assertFalse(f.reminderService.notifyPeerReportAvailable(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, suprimido));
        var publicado = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohortN(5, "12"));
        assertTrue(f.reminderService.notifyPeerReportAvailable(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, publicado));
        assertTrue(f.reminders.sent.get(0).startsWith("PEER_REPORT_AVAILABLE|"));
    }

    @Test
    void mensagemImpressaSoDoModeloAprovadoComAgregados() {
        var f = baseComMetaEProgresso("10");
        f.params.invoiceTemplate = "Consumo {own_m3} m3; media de domicilios semelhantes "
                + "{peer_average_m3} m3; sua meta {goal_m3} m3 ({period}).";
        var report = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohort("8", "10", "12", "14", "16"));
        var goal = f.goals.find(EngagementTestSupport.TENANT, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD);
        var msg = f.invoiceService.build(EngagementTestSupport.TENANT, report, goal);
        assertEquals(InvoiceMessageService.Outcome.RENDERED, msg.outcome());
        assertEquals("Consumo 10.00 m3; media de domicilios semelhantes 12.00 m3; "
                + "sua meta 10.00 m3 (2026-10).", msg.text());
    }

    @Test
    void semModeloDaAgenciaNaoHaMensagem() {
        var f = baseComMetaEProgresso("10");
        var report = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohortN(5, "12"));
        var msg = f.invoiceService.build(EngagementTestSupport.TENANT, report, Optional.empty());
        assertEquals(InvoiceMessageService.Outcome.TEMPLATE_NOT_CONFIGURED, msg.outcome());
        assertNull(msg.text());
    }

    @Test
    void relatorioSuprimidoNaoGeraMensagemMesmoComModelo() {
        var f = baseComMetaEProgresso("10");
        f.params.invoiceTemplate = "Media {peer_average_m3}";
        var report = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohortN(4, "12"));
        var msg = f.invoiceService.build(EngagementTestSupport.TENANT, report, Optional.empty());
        assertEquals(InvoiceMessageService.Outcome.REPORT_NOT_PUBLISHED, msg.outcome());
        assertNull(msg.text());
    }

    @Test
    void modeloComVariavelForaDoConjuntoEhRecusado() {
        var f = baseComMetaEProgresso("10");
        f.params.invoiceTemplate = "Vizinho consumiu {neighbor_m3}";
        var report = f.peerService.generate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                EngagementTestSupport.cohortN(5, "12"));
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.invoiceService.build(EngagementTestSupport.TENANT, report, Optional.empty()));
        assertEquals("TEMPLATE_VARIABLE_UNKNOWN", ex.code());
    }
}
