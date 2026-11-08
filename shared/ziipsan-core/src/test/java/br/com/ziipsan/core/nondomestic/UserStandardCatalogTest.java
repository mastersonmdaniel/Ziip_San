package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.effluent.EffluentCatalog;
import br.com.ziipsan.core.effluent.EffluentStandard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Catalogo do prestador SOBRE o da ED-105: precedencia declarada, sem presumir veredito. */
class UserStandardCatalogTest {

    private static final LocalDate MAR = LocalDate.of(2026, 3, 1);

    @Test
    void providerStandardPrevailsOverBase() {
        // Prestador: MAX 5 (revisado). Base ED-105: MAX 10 (revisado). Valor 8: fora no prestador.
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", true),
                TestSupport.federalMax("BASE-1", "PARAM_A", "10", true));
        var a = catalog.evaluate("PARAM_A", new BigDecimal("8"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.NON_CONFORMING, a.verdict());
        assertEquals(UserStandardCatalog.Source.PROVIDER, a.source());
        var b = catalog.evaluate("PARAM_A", new BigDecimal("4"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.CONFORMING, b.verdict());
        assertEquals(UserStandardCatalog.Source.PROVIDER, b.source());
    }

    @Test
    void fallsBackToReviewedBaseWhenProviderHasNoLine() {
        UserStandardCatalog catalog = TestSupport.userCatalog(null,
                TestSupport.federalMax("BASE-1", "PARAM_A", "10", true));
        var a = catalog.evaluate("PARAM_A", new BigDecimal("8"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.CONFORMING, a.verdict());
        assertEquals(UserStandardCatalog.Source.BASE_ED105, a.source());
    }

    @Test
    void unreviewedProviderLineDoesNotShadowReviewedBase() {
        // Linha do prestador existe mas NAO revisada: nao fundamenta nem sombreia a base revisada.
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", false),
                TestSupport.federalMax("BASE-1", "PARAM_A", "10", true));
        var a = catalog.evaluate("PARAM_A", new BigDecimal("8"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.CONFORMING, a.verdict());
        assertEquals(UserStandardCatalog.Source.BASE_ED105, a.source());
    }

    @Test
    void noReviewedLineAnywhereMeansIndeterminate() {
        UserStandardCatalog catalog = TestSupport.userCatalog(
                TestSupport.federalMax("PRESTADOR-1", "PARAM_A", "5", false), null);
        var a = catalog.evaluate("PARAM_A", new BigDecimal("999"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.INDETERMINATE, a.verdict());
        assertEquals(UserStandardCatalog.Source.NONE, a.source());
        UserStandardCatalog empty = TestSupport.userCatalog(null, null);
        var b = empty.evaluate("PARAM_A", new BigDecimal("1"), null,
                EffluentStandard.Jurisdiction.FEDERAL, null, MAR);
        assertEquals(UserStandardCatalog.Assessment.Verdict.INDETERMINATE, b.verdict());
    }

    @Test
    void declaredVersionMustMatchProviderCatalogVersion() {
        assertThrows(IllegalArgumentException.class, () -> new UserStandardCatalog(
                "OUTRA-VERSAO", EffluentCatalog.empty("PRESTADOR-1"), EffluentCatalog.empty("BASE-1")));
    }
}
