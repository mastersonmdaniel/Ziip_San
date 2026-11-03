package br.com.ziipsan.commercial.fiscal.xml;

import br.com.ziipsan.commercial.fiscal.xml.NfseEventXmlBuilder.EventInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Evento canônico do porte: Id PRE + chave (50) + código (6), escape, sem Signature. */
class NfseEventXmlBuilderTest {

    @Test
    void cancelEventHasPreIdAndEscapedReason() {
        String key = "4205407212ABC34501DE3500001000000000000001SIMULADO";
        String xml = NfseEventXmlBuilder.build(
                new EventInput(key, "101101", "2026-11-03T11:00:00-03:00", "Erro & ajuste"));
        assertTrue(xml.contains("<infEvento Id=\"PRE" + key + "101101\">"));
        assertTrue(xml.contains("<tpEvento>101101</tpEvento>"));
        assertTrue(xml.contains("<motivo>Erro &amp; ajuste</motivo>"));
        assertFalse(xml.contains("Signature"));
    }
}
