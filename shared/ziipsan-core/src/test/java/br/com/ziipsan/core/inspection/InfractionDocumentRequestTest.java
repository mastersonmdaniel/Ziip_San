package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aceite ED-24 + P2.30: o auto e emitido pelo motor de modelos; as variaveis
 * sao exatamente as do catalogo INFRACTION_NOTICE da P2.30 (conferido no
 * TemplateVariableCatalog da plataforma, espelho da P2.32).
 */
class InfractionDocumentRequestTest {

    @Test
    void buildsExactlyCatalogVariables() {
        InfractionDocumentRequest.Request request = InfractionDocumentRequest.build(
                "NOME EXEMPLO", "000000-0", "2026-000001", "Descricao exemplo", 30);
        assertEquals("INFRACTION_NOTICE", request.documentType());
        assertEquals(InfractionDocumentRequest.CATALOG_VARIABLES, request.variables().keySet());
        assertEquals("30", request.variables().get("appeal_days"));
    }

    @Test
    void missingCatalogVariableIsRefused() {
        Map<String, String> variables = new HashMap<>(InfractionDocumentRequest.build(
                "NOME EXEMPLO", "000000-0", "2026-000001", "Descricao exemplo", 30).variables());
        variables.remove("protocol_number");
        assertThrows(IllegalArgumentException.class,
                () -> new InfractionDocumentRequest.Request("INFRACTION_NOTICE", variables));
    }

    @Test
    void variableOutsideCatalogIsRefused() {
        Map<String, String> variables = new HashMap<>(InfractionDocumentRequest.build(
                "NOME EXEMPLO", "000000-0", "2026-000001", "Descricao exemplo", 30).variables());
        variables.put("invented_variable", "x");
        assertThrows(IllegalArgumentException.class,
                () -> new InfractionDocumentRequest.Request("INFRACTION_NOTICE", variables));
    }
}
