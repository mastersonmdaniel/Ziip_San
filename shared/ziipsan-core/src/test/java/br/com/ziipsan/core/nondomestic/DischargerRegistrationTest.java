package br.com.ziipsan.core.nondomestic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cadastro, marca, caracterizacao, termo e autorizacao (CAD-70). */
class DischargerRegistrationTest {

    private static DischargerRegistration fresh() {
        return new DischargerRegistration(UUID.randomUUID(), TestSupport.TENANT, TestSupport.UNIT_ID,
                DischargerRegistration.Route.COMMERCIAL_UNIT, DischargerRegistration.Kind.LAUNDRY,
                null, "PONTO-LAV-1");
    }

    @Test
    void registrationCarriesTheNonDomesticDischargerFlag() {
        DischargerRegistration d = fresh();
        assertEquals("non_domestic_discharger", d.consumerUnitFlag());
        assertEquals(DischargerRegistration.Status.REGISTERED, d.status());
        assertEquals("LAUNDRY", d.chargeClassCode());
    }

    @Test
    void allAcceptanceKindsAreRegistrableAndOtherNeedsDescription() {
        for (DischargerRegistration.Kind kind : List.of(
                DischargerRegistration.Kind.INDUSTRY, DischargerRegistration.Kind.AGROINDUSTRY,
                DischargerRegistration.Kind.HOSPITAL, DischargerRegistration.Kind.FUEL_STATION,
                DischargerRegistration.Kind.LAUNDRY)) {
            DischargerRegistration d = new DischargerRegistration(UUID.randomUUID(), TestSupport.TENANT,
                    TestSupport.UNIT_ID, DischargerRegistration.Route.COMMERCIAL_UNIT, kind, null, "P-1");
            assertEquals(kind, d.kind());
        }
        assertThrows(IllegalArgumentException.class, () -> new DischargerRegistration(
                UUID.randomUUID(), TestSupport.TENANT, TestSupport.UNIT_ID,
                DischargerRegistration.Route.COMMERCIAL_UNIT, DischargerRegistration.Kind.OTHER,
                null, "P-1"));
        DischargerRegistration other = new DischargerRegistration(UUID.randomUUID(), TestSupport.TENANT,
                TestSupport.UNIT_ID, DischargerRegistration.Route.COMMERCIAL_UNIT,
                DischargerRegistration.Kind.OTHER, "OFICINA [FIXTURE]", "P-1");
        assertEquals("OTHER:OFICINA [FIXTURE]", other.chargeClassCode());
    }

    @Test
    void characterizationRefusesPretreatmentNotAtUserExpense() {
        // O record recusa na construcao: pre-tratamento NAO as expensas do usuario nao entra.
        assertThrows(IllegalArgumentException.class, () -> new EffluentCharacterization(
                List.of(new EffluentCharacterization.CharacterizedParameter(
                        "PARAM_A", new BigDecimal("8"), null, "u")),
                "PRE [FIXTURE]", false, "U-1", TestSupport.T0));
        // E a caracterizacao valida (as expensas do usuario) entra e fica registrada.
        EffluentCharacterization ok = TestSupport.characterization();
        assertTrue(ok.pretreatmentAtUserExpense());
        assertEquals(1, ok.parameters().size());
    }

    @Test
    void authorizationRequiresCharacterizationAndCommitmentTerm() {
        DischargerRegistration d = fresh();
        assertThrows(IllegalStateException.class,
                () -> d.authorize("AUT-1", TestSupport.JAN, null));
        d.recordCharacterization(TestSupport.characterization());
        assertThrows(IllegalStateException.class,
                () -> d.authorize("AUT-1", TestSupport.JAN, null));
        d.recordCommitmentTerm("TERMO-1 [FIXTURE]");
        d.authorize("AUT-1 [FIXTURE]", TestSupport.JAN, LocalDate.of(2026, 12, 31));
        assertTrue(d.authorizedOn(LocalDate.of(2026, 6, 1)));
        assertFalse(d.authorizedOn(LocalDate.of(2027, 1, 1)));
        assertThrows(IllegalStateException.class,
                () -> d.authorize("AUT-2", TestSupport.JAN, null));
    }

    @Test
    void suspensionBlocksAuthorizationAndReinstateRestoresIt() {
        DischargerRegistration d = TestSupport.authorizedDischarger();
        assertTrue(d.authorizedOn(LocalDate.of(2026, 3, 1)));
        d.suspend("nao conformidade [FIXTURE]");
        assertFalse(d.authorizedOn(LocalDate.of(2026, 3, 1)));
        assertThrows(IllegalStateException.class, () -> d.suspend("de novo"));
        d.reinstate();
        assertTrue(d.authorizedOn(LocalDate.of(2026, 3, 1)));
    }
}
