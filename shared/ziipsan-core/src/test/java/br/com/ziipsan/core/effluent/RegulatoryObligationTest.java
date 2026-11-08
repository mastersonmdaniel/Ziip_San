package br.com.ziipsan.core.effluent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JOR-16 nos DOIS casos exigidos pelo aceite: tenant COM estacao tem
 * a obrigacao REGULATORY; tenant SEM estacao nao tem — e nenhum
 * pacote (ISO 14001, esgoto, agua bruta) altera o resultado.
 */
class RegulatoryObligationTest {

    private static RegulatoryObligation.StationPresence presence(
            boolean eta, boolean ete, boolean iso, boolean sewer, boolean raw) {
        return new RegulatoryObligation.StationPresence(TestSupport.TENANT, eta, ete, iso, sewer, raw);
    }

    @Test
    void tenantWithWtpHasRegulatoryObligation() {
        var obligation = RegulatoryObligation.evaluate(presence(true, false, false, false, false));
        assertTrue(obligation.isPresent());
        assertEquals(RegulatoryObligation.ObligationClass.REGULATORY, obligation.get().obligationClass());
        assertEquals(RegulatoryObligation.CODE, obligation.get().code());
        assertEquals(java.util.List.of("CONAMA 430/2011", "CONAMA 498/2020"), obligation.get().legalBases());
        assertTrue(obligation.get().components()
                .contains(RegulatoryObligation.Component.EFFLUENT_SELF_MONITORING));
        assertTrue(obligation.get().components()
                .contains(RegulatoryObligation.Component.SLUDGE_GENERATION_AND_DESTINATION));
    }

    @Test
    void tenantWithWwtpHasRegulatoryObligation() {
        assertTrue(RegulatoryObligation.evaluate(presence(false, true, false, false, false)).isPresent());
        assertTrue(RegulatoryObligation.evaluate(presence(true, true, false, false, false)).isPresent());
    }

    @Test
    void tenantWithoutStationHasNoObligation() {
        assertTrue(RegulatoryObligation.evaluate(presence(false, false, false, false, false)).isEmpty());
    }

    @Test
    void iso14001DoesNotCreateObligationWithoutStation() {
        // ISO ligada, pacotes ligados, SEM estacao: continua ausente.
        assertTrue(RegulatoryObligation.evaluate(presence(false, false, true, true, true)).isEmpty());
    }

    @Test
    void obligationIgnoresAllPackagesWhenStationExists() {
        // Todos os pacotes DESLIGADOS, com ETE: obrigacao ativa (JOR-16).
        assertTrue(RegulatoryObligation.evaluate(presence(false, true, false, false, false)).isPresent());
        // E continua ativa com qualquer combinacao de pacotes.
        assertTrue(RegulatoryObligation.evaluate(presence(true, false, true, false, true)).isPresent());
    }

    @Test
    void requireActiveRefusesTenantWithoutStation() {
        assertThrows(IllegalStateException.class,
                () -> RegulatoryObligation.requireActive(presence(false, false, true, true, true)));
    }
}
