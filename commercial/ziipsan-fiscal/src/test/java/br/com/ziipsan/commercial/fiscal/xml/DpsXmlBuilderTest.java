package br.com.ziipsan.commercial.fiscal.xml;

import br.com.ziipsan.commercial.fiscal.rules.TaxEngine;
import br.com.ziipsan.commercial.fiscal.xml.DpsXmlBuilder.DpsInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forma canônica do porte (P1.10): versão por IBSCBS, escape, nunca Signature. */
class DpsXmlBuilderTest {

    private static final TaxEngine.TaxResult TAXES = TaxEngine.calculate(
            new BigDecimal("5440.00"), TaxEngine.standard2026Rates());

    private DpsInput input(boolean withIbsCbs, String taker) {
        return new DpsInput("4205407212ABC34501DE3500001000000000000001", "2026-11-03T10:00:00-03:00",
                "00001", 1L, "4205407", "12ABC34501DE35", taker,
                new BigDecimal("5440.00"), TAXES, withIbsCbs);
    }

    @Test
    void versionFollowsIbsCbsGroup() {
        assertTrue(DpsXmlBuilder.build(input(false, null)).contains("<DPS versao=\"1.00\">"));
        assertFalse(DpsXmlBuilder.build(input(false, null)).contains("<IBSCBS>"));
        String with = DpsXmlBuilder.build(input(true, null));
        assertTrue(with.contains("<DPS versao=\"1.01\">"));
        assertTrue(with.contains("<vCbs>48.96</vCbs>"));
        assertTrue(with.contains("<vIbs>5.44</vIbs>"));
    }

    @Test
    void valuesAreEscapedAndSignatureNeverPresent() {
        String xml = DpsXmlBuilder.build(input(true, "A & B <Tomador>"));
        assertTrue(xml.contains("A &amp; B &lt;Tomador&gt;"));
        assertFalse(xml.contains("Signature"));
        assertTrue(xml.contains("<vBruto>5440.00</vBruto>"));
        assertTrue(xml.contains("<vLiquido>5105.44</vLiquido>"));
    }

    @Test
    void dpsIdMustHave42Positions() {
        DpsInput bad = new DpsInput("curto", "2026-11-03T10:00:00-03:00",
                "00001", 1L, "4205407", "12ABC34501DE35", null,
                new BigDecimal("5440.00"), TAXES, true);
        assertThrows(IllegalArgumentException.class, () -> DpsXmlBuilder.build(bad));
    }
}
