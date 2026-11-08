package br.com.ziipsan.platform.engagement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalServiceTest {

    @Test
    void definirMetaExigeModuloLigado() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.feature = false;
        var ex = assertThrows(EngagementValidationException.class, () -> f.goal("10"));
        assertEquals("FEATURE_DISABLED", ex.code());
    }

    @Test
    void definirMetaExigeAdesaoVoluntaria() {
        var f = new EngagementTestSupport.Fixture();
        var ex = assertThrows(EngagementValidationException.class, () -> f.goal("10"));
        assertEquals("NOT_ENROLLED", ex.code());
    }

    @Test
    void adesaoRevogadaNaoDefineMeta() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.enrollments.save(f.enrollments.find(EngagementTestSupport.TENANT,
                        EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT).orElseThrow()
                .optOut(Instant.parse("2026-10-05T10:00:00Z")));
        var ex = assertThrows(EngagementValidationException.class, () -> f.goal("10"));
        assertEquals("ENROLLMENT_REVOKED", ex.code());
    }

    @Test
    void metaDeveSerPositiva() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        assertEquals("GOAL_TARGET_INVALID", assertThrows(EngagementValidationException.class,
                () -> f.goal("0")).code());
        assertEquals("GOAL_TARGET_INVALID", assertThrows(EngagementValidationException.class,
                () -> f.goal("-3")).code());
    }

    @Test
    void redefinirMetaSubstituiAMesmaChave() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        var segunda = f.goalService.defineGoal(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("8"),
                Instant.parse("2026-10-03T10:00:00Z"));
        var gravada = f.goals.find(EngagementTestSupport.TENANT, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD).orElseThrow();
        assertEquals(segunda.id(), gravada.id());
        assertEquals(0, new BigDecimal("8").compareTo(gravada.targetM3()));
    }

    @Test
    void progressoUsaFaturadoQuandoNaoHaTelemetria() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        f.consumption.billed = new BigDecimal("9.5");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        assertEquals(GoalProgress.Source.BILLED, p.source());
        assertEquals(GoalProgress.Status.WITHIN_GOAL, p.status());
        assertEquals(0, new BigDecimal("0.9500").compareTo(p.ratio()));
    }

    @Test
    void telemetriaDaEd17TemPrecedenciaQuandoHouver() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        f.consumption.billed = new BigDecimal("9.5");
        f.consumption.telemetry = new BigDecimal("11");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, false);
        assertEquals(GoalProgress.Source.TELEMETRY, p.source());
        assertEquals(GoalProgress.Status.EXCEEDED, p.status());
    }

    @Test
    void semConsumoNasDuasFontesEhNoDataNuncaZero() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        assertEquals(GoalProgress.Status.NO_DATA, p.status());
        assertEquals(GoalProgress.Source.NONE, p.source());
        assertTrue(p.actualM3() == null && p.ratio() == null);
    }

    @Test
    void progressoExigeModuloLigado() {
        // Correcao D1: o acompanhamento tem a mesma guarda da definicao.
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        f.consumption.billed = new BigDecimal("9.5");
        f.params.feature = false;
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                        EngagementTestSupport.PERIOD, true));
        assertEquals("FEATURE_DISABLED", ex.code());
    }

    @Test
    void progressoExigeAdesaoVoluntaria() {
        // Correcao D1 (sonda P1 da revisao): meta gravada direto no store,
        // SEM adesao alguma — o progresso nao pode ser devolvido.
        var f = new EngagementTestSupport.Fixture();
        f.goals.save(new ConsumptionGoal(java.util.UUID.randomUUID(), EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, new BigDecimal("10"),
                Instant.parse("2026-10-02T10:00:00Z")));
        f.consumption.billed = new BigDecimal("9.5");
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                        EngagementTestSupport.PERIOD, true));
        assertEquals("NOT_ENROLLED", ex.code());
    }

    @Test
    void progressoComAdesaoRevogadaEhRecusado() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        f.consumption.billed = new BigDecimal("9.5");
        f.enrollments.save(f.enrollments.find(EngagementTestSupport.TENANT,
                        EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT).orElseThrow()
                .optOut(Instant.parse("2026-10-05T10:00:00Z")));
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                        EngagementTestSupport.PERIOD, true));
        assertEquals("ENROLLMENT_REVOKED", ex.code());
    }

    @Test
    void progressoSemMetaEhRecusaNomeada() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                        EngagementTestSupport.PERIOD, true));
        assertEquals("GOAL_NOT_FOUND", ex.code());
    }
}
