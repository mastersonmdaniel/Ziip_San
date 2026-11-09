package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Liga/desliga da norma (ADR-0041): aceite da ED-51 pede teste ligada E desligada. */
class Lims17025ActivationTest {

    @Test void enabledRequiresEdition2017ScopeAndDistinctPeople() {
        Lims17025Activation a = TestSupport.enabled();
        assertTrue(a.enabled());
        assertEquals("2017", a.edition());
        assertEquals(Lims17025Activation.REQUIRED_EDITION, a.edition());
        // edicao diversa da fixada no aceite e recusada
        assertThrows(IllegalArgumentException.class, () -> new Lims17025Activation(
                true, "2005", "escopo", TestSupport.PROPOSER, TestSupport.ACCEPTER));
        // escopo em branco e recusado
        assertThrows(IllegalArgumentException.class, () -> Lims17025Activation.enabled(
                " ", TestSupport.PROPOSER, TestSupport.ACCEPTER));
        // proposta e aceite pela mesma pessoa sao recusados (ADR-0041)
        assertThrows(IllegalArgumentException.class, () -> Lims17025Activation.enabled(
                "escopo", TestSupport.PROPOSER, TestSupport.PROPOSER));
    }

    @Test void disabledBlocksEveryBirth() {
        Lims17025Activation off = Lims17025Activation.disabled();
        assertThrows(IllegalStateException.class, off::requireEnabled);
        // laboratorio nao nasce com a norma desligada
        assertThrows(IllegalStateException.class, () -> Laboratory.register(off,
                UUID.randomUUID(), TestSupport.TENANT, "Lab",
                List.of(new Laboratory.ScopeEntry(TestSupport.METHOD, TestSupport.PARAM, true))));
        // autorizacao nao nasce desligada
        assertThrows(IllegalStateException.class, () -> AnalystAuthorization.grant(off,
                TestSupport.ANALYST, TestSupport.METHOD, TestSupport.DAY, null, "SUP-1"));
        // ensaio nao nasce desligado
        assertThrows(IllegalStateException.class, () -> AssayExecution.start(off,
                TestSupport.laboratory(), TestSupport.methodValidation(),
                List.of(TestSupport.authorization()), TestSupport.validEquipment(),
                TestSupport.envWithin(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                null, UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                TestSupport.METHOD, TestSupport.PARAM, TestSupport.ANALYST, TestSupport.DAY));
    }

    @Test void enabledLetsBirthsHappen() {
        Laboratory lab = TestSupport.laboratory();
        assertTrue(lab.covers(TestSupport.METHOD, TestSupport.PARAM));
        AssayExecution a = TestSupport.validatedAssay();
        assertTrue(a.validated());
    }

    @Test void laboratoryWithoutScopeDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> Laboratory.register(TestSupport.enabled(),
                UUID.randomUUID(), TestSupport.TENANT, "Lab", List.of()));
        // par fora do escopo: nao cobre, e o ensaio recusa (ver AssayExecutionTest)
        Laboratory lab = TestSupport.laboratory();
        assertFalse(lab.covers("METHOD_X", TestSupport.PARAM));
        assertTrue(lab.coversAsAccredited(TestSupport.METHOD, TestSupport.PARAM));
        assertFalse(lab.coversAsAccredited("METHOD_B", "PARAM_B"));
    }
}
