package br.com.ziipsan.platform.engagement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamificationServiceTest {

    private GoalProgress progress(EngagementTestSupport.Fixture f, String billed, boolean closed) {
        f.consumption.billed = new BigDecimal(billed);
        return f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, closed);
    }

    @Test
    void semMarcosConfiguradosNenhumMarcoEhConcedido() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.goal("10");
        var r = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, progress(f, "5", true));
        assertEquals(GamificationService.Status.MILESTONES_NOT_CONFIGURED, r.status());
        assertTrue(r.achievedMilestoneCodes().isEmpty());
    }

    @Test
    void marcosSaoDaMetaDoProprioUsuario() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.milestones = "0.90=ABAIXO_DA_META;1.00=META_CUMPRIDA";
        f.goal("10");
        // realizado 9,0 -> ratio 0,9000: atinge os dois marcos configurados
        var r = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, progress(f, "9.0", true));
        assertEquals(GamificationService.Status.EVALUATED, r.status());
        assertEquals(java.util.List.of("ABAIXO_DA_META", "META_CUMPRIDA"), r.achievedMilestoneCodes());
        // realizado 9,5 -> ratio 0,9500: so o marco de 1,00
        var r2 = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, progress(f, "9.5", true));
        assertEquals(java.util.List.of("META_CUMPRIDA"), r2.achievedMilestoneCodes());
        // realizado 11 -> ratio 1,1000: nenhum marco
        var r3 = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, progress(f, "11", true));
        assertTrue(r3.achievedMilestoneCodes().isEmpty());
    }

    @Test
    void periodoAbertoNaoConcedeMarco() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.milestones = "1.00=META_CUMPRIDA";
        f.goal("10");
        var r = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, progress(f, "5", false));
        assertEquals(GamificationService.Status.PERIOD_OPEN, r.status());
        assertTrue(r.achievedMilestoneCodes().isEmpty());
    }

    @Test
    void semDadoNaoConcedeMarco() {
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.milestones = "1.00=META_CUMPRIDA";
        f.goal("10");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        var r = f.gamification.evaluate(EngagementTestSupport.TENANT,
                EngagementTestSupport.CUSTOMER, p);
        assertEquals(GamificationService.Status.NO_DATA, r.status());
    }

    @Test
    void formatoDeMarcosInvalidoEhRecusado() {
        assertEquals("MILESTONES_FORMAT_INVALID", assertThrows(EngagementValidationException.class,
                () -> GamificationMilestones.parse("abc")).code());
        assertEquals("MILESTONE_FRACTION_INVALID", assertThrows(EngagementValidationException.class,
                () -> GamificationMilestones.parse("1.50=ACIMA")).code());
        assertEquals("MILESTONE_CODE_INVALID", assertThrows(EngagementValidationException.class,
                () -> GamificationMilestones.parse("0.50=codigo-minusculo")).code());
        assertEquals("MILESTONE_DUPLICATED", assertThrows(EngagementValidationException.class,
                () -> GamificationMilestones.parse("0.50=A;0.90=A")).code());
        // vazio/ausente = nao configurado, nao erro
        assertTrue(GamificationMilestones.parse(null).isEmpty());
        assertTrue(GamificationMilestones.parse("  ").isEmpty());
    }

    @Test
    void gamificacaoExigeAdesaoComoAsDemaisOperacoes() {
        // Pos-correcao D1 o proprio progress() tambem exige adesao, entao o
        // progresso e obtido COM adesao e a adesao e REVOGADA antes da
        // avaliacao: a gamificacao re-checa e recusa com o codigo nomeado.
        var f = new EngagementTestSupport.Fixture().enrolled();
        f.params.milestones = "1.00=META_CUMPRIDA";
        f.goal("10");
        f.consumption.billed = new BigDecimal("5");
        var p = f.goalService.progress(EngagementTestSupport.TENANT, EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT,
                EngagementTestSupport.PERIOD, true);
        f.enrollments.save(f.enrollments.find(EngagementTestSupport.TENANT,
                        EngagementTestSupport.CUSTOMER, EngagementTestSupport.UNIT).orElseThrow()
                .optOut(java.time.Instant.parse("2026-10-05T10:00:00Z")));
        var ex = assertThrows(EngagementValidationException.class, () ->
                f.gamification.evaluate(EngagementTestSupport.TENANT,
                        EngagementTestSupport.CUSTOMER, p));
        assertEquals("ENROLLMENT_REVOKED", ex.code());
    }
}
