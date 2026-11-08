package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Massa SINTETICA declarada: municipio ficticio com 1.000 domicilios (800 urbanos, 200 rurais). */
class CoverageCalculatorTest {

    private IbgeHouseholdBase base() {
        IbgeHouseholdBase base = new IbgeHouseholdBase("SINTETICA-1", "massa sintetica dos testes da ED-77");
        base.add(new IbgeHouseholdBase.HouseholdEntry("MUN-SINT", 1000, 800, 200));
        return base;
    }

    private AgencyCoverageProfile profile(AltSolutionRule rule) {
        return new AgencyCoverageProfile("ARIS", "ARIS RN 47/2025", rule);
    }

    @Test
    void iaaIcaSobreBaseIbge() {
        CoverageCalculator.Coverage c = CoverageCalculator.compute(base(), "MUN-SINT",
                800, 600, 0, profile(AltSolutionRule.DOES_NOT_COUNT));
        assertEquals(0, new BigDecimal("80").compareTo(c.iaaPercent()));
        assertEquals(0, new BigDecimal("60").compareTo(c.icaPercent()));
        // Rateio: urbano + rural fecha no total, nas duas fichas.
        assertEquals(0, c.waterSplit().urbanPercent().add(c.waterSplit().ruralPercent())
                .compareTo(c.waterSplit().totalPercent()));
        assertEquals(0, new BigDecimal("64").compareTo(c.waterSplit().urbanPercent()));
    }

    @Test
    void solucaoAlternativaContaQuandoRegraDeclaradaManda() {
        CoverageCalculator.Coverage c = CoverageCalculator.compute(base(), "MUN-SINT",
                700, 600, 100, profile(AltSolutionRule.COUNTS_AS_COVERED));
        assertEquals(0, new BigDecimal("80").compareTo(c.iaaPercent()));
    }

    @Test
    void solucaoAlternativaNaoContaQuandoRegraDeclaradaExclui() {
        CoverageCalculator.Coverage c = CoverageCalculator.compute(base(), "MUN-SINT",
                700, 600, 100, profile(AltSolutionRule.DOES_NOT_COUNT));
        assertEquals(0, new BigDecimal("70").compareTo(c.iaaPercent()));
    }

    @Test
    void testePorPerfilTresAgenciasSemRegraRecusam() {
        // RAS-33: os tres perfis nomeados nascem UNDECLARED e recusam - nenhum sentido presumido.
        for (AgencyCoverageProfile p : java.util.List.of(
                AgencyCoverageProfile.aris(), AgencyCoverageProfile.agir(), AgencyCoverageProfile.aresc())) {
            IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                    () -> CoverageCalculator.compute(base(), "MUN-SINT", 1, 1, 0, p));
            assertEquals("ALT_SOLUTION_RULE_UNDECLARED", e.code());
        }
    }

    @Test
    void municipioForaDaBaseRecusa() {
        IndicatorValidationException e = assertThrows(IndicatorValidationException.class,
                () -> CoverageCalculator.compute(base(), "MUN-AUSENTE", 1, 1, 0,
                        profile(AltSolutionRule.DOES_NOT_COUNT)));
        assertEquals("IBGE_MUNICIPALITY_NOT_IN_BASE", e.code());
    }

    @Test
    void numeradorAcimaDaBaseRecusa() {
        assertThrows(IndicatorValidationException.class,
                () -> CoverageCalculator.compute(base(), "MUN-SINT", 1001, 0, 0,
                        profile(AltSolutionRule.DOES_NOT_COUNT)));
    }

    @Test
    void baseExigeVersaoFonteESomaZonal() {
        assertThrows(IndicatorValidationException.class, () -> new IbgeHouseholdBase("", "fonte"));
        assertThrows(IndicatorValidationException.class, () -> new IbgeHouseholdBase("v1", " "));
        assertThrows(IndicatorValidationException.class,
                () -> new IbgeHouseholdBase.HouseholdEntry("M", 100, 60, 30));
        IbgeHouseholdBase b = base();
        assertTrue(b.entry("MUN-SINT").isPresent());
        assertThrows(IndicatorValidationException.class,
                () -> b.add(new IbgeHouseholdBase.HouseholdEntry("MUN-SINT", 5, 5, 0)));
    }
}
