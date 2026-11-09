package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** O ensaio demonstrado: todas as portas do aceite no nascimento + segunda pessoa. */
class AssayExecutionTest {

    private AssayExecution start(List<AnalystAuthorization> auths,
                                 EnvironmentalControl.Evaluation env,
                                 WestgardControl.Evaluation qc,
                                 MeasurementUncertainty uncertainty,
                                 MethodValidation validation, String analyst) {
        return AssayExecution.start(TestSupport.enabled(), TestSupport.laboratory(), validation,
                auths, TestSupport.validEquipment(), env, qc, uncertainty, null,
                UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                TestSupport.METHOD, TestSupport.PARAM, analyst, TestSupport.DAY);
    }

    @Test void fullDemonstratedAssayHappyPath() {
        AssayExecution a = TestSupport.recordedAssay();
        assertEquals(AssayExecution.Status.RESULT_RECORDED, a.status());
        assertEquals("5.0", a.resultText());
        assertEquals(WestgardControl.Status.IN_CONTROL, a.qcStatus());
        assertEquals(EnvironmentalControl.Verdict.WITHIN_LIMITS, a.environmentalVerdict());
        assertEquals(new BigDecimal("1.0"), a.uncertainty().expandedUncertainty()); // 0,5 x k=2
        a.validateBySecondPerson(TestSupport.VALIDATOR);
        assertEquals(AssayExecution.Status.VALIDATED, a.status());
        assertTrue(a.validated());
    }

    @Test void secondPersonValidationRefusesTheAnalystHimself() {
        AssayExecution a = TestSupport.recordedAssay();
        assertThrows(IllegalArgumentException.class,
                () -> a.validateBySecondPerson(TestSupport.ANALYST));
        // e sem resultado nao ha validacao
        AssayExecution fresh = start(List.of(TestSupport.authorization()), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST);
        assertThrows(IllegalStateException.class,
                () -> fresh.validateBySecondPerson(TestSupport.VALIDATOR));
    }

    @Test void unauthorizedAnalystIsRefused() {
        // sem autorizacao alguma
        assertThrows(IllegalStateException.class, () -> start(List.of(), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST));
        // autorizacao de OUTRO metodo nao autoriza este
        AnalystAuthorization other = AnalystAuthorization.grant(TestSupport.enabled(),
                TestSupport.ANALYST, "METHOD_B", LocalDate.of(2026, 1, 1), null, "SUPERVISOR-1");
        assertThrows(IllegalStateException.class, () -> start(List.of(other), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST));
        // autorizacao vencida nao autoriza
        AnalystAuthorization expired = AnalystAuthorization.grant(TestSupport.enabled(),
                TestSupport.ANALYST, TestSupport.METHOD, LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31), "SUPERVISOR-1");
        assertThrows(IllegalStateException.class, () -> start(List.of(expired), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST));
        // outro analista nao e coberto pela autorizacao de ANALISTA-1
        assertThrows(IllegalStateException.class, () -> start(List.of(TestSupport.authorization()),
                TestSupport.envWithin(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), "ANALISTA-2"));
    }

    @Test void selfAuthorizationIsRefusedAtGrant() {
        assertThrows(IllegalArgumentException.class, () -> AnalystAuthorization.grant(
                TestSupport.enabled(), TestSupport.ANALYST, TestSupport.METHOD,
                LocalDate.of(2026, 1, 1), null, TestSupport.ANALYST));
    }

    @Test void outOfScopeAndUnvalidatedMethodAreRefused() {
        // metodo fora do escopo do laboratorio
        MethodValidation foreign = MethodValidation.validated(TestSupport.enabled(), "METHOD_X",
                TestSupport.PARAM, null, null, "DOSSIE-X", "RT-1", LocalDate.of(2026, 2, 1));
        AnalystAuthorization foreignAuth = AnalystAuthorization.grant(TestSupport.enabled(),
                TestSupport.ANALYST, "METHOD_X", LocalDate.of(2026, 1, 1), null, "SUPERVISOR-1");
        MeasurementUncertainty foreignU = new MeasurementUncertainty("METHOD_X", TestSupport.PARAM,
                new BigDecimal("0.5"), new BigDecimal("2"),
                MeasurementUncertainty.DecisionRule.SIMPLE_ACCEPTANCE, "RT-1");
        assertThrows(IllegalStateException.class, () -> AssayExecution.start(TestSupport.enabled(),
                TestSupport.laboratory(), foreign, List.of(foreignAuth), TestSupport.validEquipment(),
                TestSupport.envWithin(), TestSupport.qcInControl(), foreignU, null,
                UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                "METHOD_X", TestSupport.PARAM, TestSupport.ANALYST, TestSupport.DAY));
        // validacao de outro par nao serve a este ensaio
        MethodValidation otherParam = MethodValidation.validated(TestSupport.enabled(),
                TestSupport.METHOD, "PARAM_B", null, null, "DOSSIE-Y", "RT-1", LocalDate.of(2026, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> start(List.of(TestSupport.authorization()),
                TestSupport.envWithin(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                otherParam, TestSupport.ANALYST));
        // sem dossie nao ha metodo validado (o registro nem nasce)
        assertThrows(IllegalArgumentException.class, () -> MethodValidation.validated(
                TestSupport.enabled(), TestSupport.METHOD, TestSupport.PARAM, null, null,
                " ", "RT-1", LocalDate.of(2026, 2, 1)));
    }

    @Test void environmentalOutOfLimitsAndQcOutOfControlBlock() {
        assertThrows(IllegalStateException.class, () -> start(List.of(TestSupport.authorization()),
                TestSupport.envOut(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST));
        assertThrows(IllegalStateException.class, () -> start(List.of(TestSupport.authorization()),
                TestSupport.envWithin(), TestSupport.qcOutOfControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST));
        // sem orcamento de incerteza nao ha ensaio demonstrado
        assertThrows(NullPointerException.class, () -> start(List.of(TestSupport.authorization()),
                TestSupport.envWithin(), TestSupport.qcInControl(), null,
                TestSupport.methodValidation(), TestSupport.ANALYST));
    }

    @Test void resultHasExactlyOneForm() {
        AssayExecution a = start(List.of(TestSupport.authorization()), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST);
        assertThrows(IllegalArgumentException.class,
                () -> a.recordResult(new BigDecimal("1"), "TEXTO", "u", TestSupport.T0));
        assertThrows(IllegalArgumentException.class,
                () -> a.recordResult(null, " ", "u", TestSupport.T0));
        a.recordResult(null, "AUSENTE", null, TestSupport.T0);
        assertEquals("AUSENTE", a.resultText());
        // segundo registro e recusado
        assertThrows(IllegalStateException.class,
                () -> a.recordResult(new BigDecimal("1"), null, "u", TestSupport.T0));
    }

    @Test void blankQualitativeIsNormalizedToNull_R01i() {
        // (1) numerico + qualitativo em branco: o branco conta como
        // ausente — grava qualitative null e RESULT_RECORDED, e o texto
        // do resultado e o numerico (o branco nunca chega a base, por
        // isso o CHECK XOR do SQL continua a ler null como ausente).
        AssayExecution a = start(List.of(TestSupport.authorization()), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST);
        a.recordResult(new BigDecimal("2.5"), "   ", "u", TestSupport.T0);
        assertEquals(AssayExecution.Status.RESULT_RECORDED, a.status());
        assertNull(a.qualitativeValue());
        assertEquals(new BigDecimal("2.5"), a.numericValue());
        assertEquals("2.5", a.resultText());
        // (2) nenhuma forma + qualitativo em branco: continua a lancar
        // (nenhuma forma), e o ensaio fica IN_PROGRESS — recusar aqui
        // seria mais estrito que a direcao do R-01, por isso nao se recusa
        // o branco em si, apenas se o trata como ausente.
        AssayExecution b = start(List.of(TestSupport.authorization()), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST);
        assertThrows(IllegalArgumentException.class,
                () -> b.recordResult(null, "   ", "u", TestSupport.T0));
        assertThrows(IllegalArgumentException.class,
                () -> b.recordResult(null, "", "u", TestSupport.T0));
        assertEquals(AssayExecution.Status.IN_PROGRESS, b.status());
        // (3) qualitativo com valor: inalterado.
        AssayExecution c = start(List.of(TestSupport.authorization()), TestSupport.envWithin(),
                TestSupport.qcInControl(), TestSupport.uncertainty(),
                TestSupport.methodValidation(), TestSupport.ANALYST);
        c.recordResult(null, "AUSENTE", null, TestSupport.T0);
        assertEquals("AUSENTE", c.qualitativeValue());
        assertEquals("AUSENTE", c.resultText());
        assertEquals(AssayExecution.Status.RESULT_RECORDED, c.status());
    }
}
