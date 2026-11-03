package br.com.ziipsan.commercial.fiscal.xml;

import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.commercial.fiscal.rules.TaxEngine;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import br.com.ziipsan.commercial.fiscal.xml.DpsXmlBuilder.DpsInput;
import br.com.ziipsan.commercial.fiscal.xml.NfseEventXmlBuilder.EventInput;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corpus de XML de referência (P1.10, ADR-0003): os mesmos arquivos rodam no CI do Ziip
 * Cloud e no CI do Ziip SAN, com o mesmo resultado nos dois. Cada caso fixa a entrada e o
 * resultado esperado; nenhum caso contém XML assinado, Signature ou certificado (README do
 * corpus). Valores monetários e alíquotas são strings no JSON, nunca float.
 */
class FiscalContractCorpusTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "florianopolis-5440.json", "ibs-cbs-10000.json", "half-even.json",
            "dps-id-cpf.json", "cancel-event.json", "vigencia-2027.json"})
    void corpusCase(String file) {
        Map<String, Object> kase = load(file);
        switch (str(kase, "kind")) {
            case "tax-and-dps" -> taxAndDps(kase);
            case "tax-only" -> taxOnly(kase);
            case "rounding" -> rounding(kase);
            case "dps-id" -> dpsId(kase);
            case "event" -> event(kase);
            case "vigencia" -> vigencia(kase);
            default -> throw new IllegalArgumentException("kind desconhecido: " + kase.get("kind"));
        }
    }

    @Test
    void corpusNeverContainsSignatureOrCertificate() {
        for (String file : List.of("florianopolis-5440.json", "cancel-event.json")) {
            String raw = raw(file);
            assertFalse(raw.contains("Signature"), file);
            assertFalse(raw.contains("X509Certificate"), file);
        }
    }

    private void taxAndDps(Map<String, Object> kase) {
        TaxEngine.TaxResult result = calculate(kase);
        assertExpected(kase, result);
        Map<String, Object> dps = map(kase, "dps");
        String numberField = String.format("%015d", number(dps));
        String dpsId = "CPF".equals(str(dps, "inscriptionKind"))
                ? DpsId.ofCpf(str(dps, "ibge"), str(dps, "inscription"), str(dps, "series"), numberField)
                : DpsId.ofCnpj(str(dps, "ibge"), str(dps, "inscription"), str(dps, "series"), numberField);
        assertEquals(str(kase, "expectedDpsId"), dpsId);
        String xml = DpsXmlBuilder.build(new DpsInput(dpsId, str(dps, "dhEmi"), str(dps, "series"),
                number(dps), str(dps, "ibge"), str(dps, "inscription"), str(dps, "taker"),
                new BigDecimal(str(kase, "gross")), result, bool(dps, "withIbsCbs")));
        assertEquals(str(kase, "expectedXml"), xml);
    }

    private void taxOnly(Map<String, Object> kase) {
        assertExpected(kase, calculate(kase));
    }

    private void rounding(Map<String, Object> kase) {
        for (Object item : list(kase, "cases")) {
            Map<String, Object> entry = cast(item);
            assertEquals(new BigDecimal(str(entry, "expected")),
                    TaxEngine.roundHalfEven(new BigDecimal(str(entry, "input"))));
        }
    }

    private void dpsId(Map<String, Object> kase) {
        String id = "CPF".equals(str(kase, "inscriptionKind"))
                ? DpsId.ofCpf(str(kase, "ibge"), str(kase, "inscription"), str(kase, "series"),
                        String.format("%015d", number(kase)))
                : DpsId.ofCnpj(str(kase, "ibge"), str(kase, "inscription"), str(kase, "series"),
                        String.format("%015d", number(kase)));
        assertEquals(str(kase, "expectedDpsId"), id);
    }

    private void event(Map<String, Object> kase) {
        String eventId = DpsId.eventId(str(kase, "accessKey"), str(kase, "eventCode"));
        assertEquals(str(kase, "expectedEventId"), eventId);
        String xml = NfseEventXmlBuilder.build(new EventInput(str(kase, "accessKey"),
                str(kase, "eventCode"), str(kase, "dhEvento"), str(kase, "reason")));
        assertEquals(str(kase, "expectedXml"), xml);
    }

    private void vigencia(Map<String, Object> kase) {
        List<TaxRateRow> rows = new ArrayList<>();
        for (Object item : list(kase, "rows")) {
            Map<String, Object> row = cast(item);
            String validTo = (String) row.get("validTo");
            rows.add(new TaxRateRow(str(row, "taxCode"), str(row, "scope"),
                    LocalDate.parse(str(row, "validFrom")),
                    validTo == null ? null : LocalDate.parse(validTo),
                    new BigDecimal(str(row, "rate"))));
        }
        for (Object item : list(kase, "queries")) {
            Map<String, Object> query = cast(item);
            String expected = (String) query.get("expectedRate");
            if (expected == null) {
                assertThrows(IllegalStateException.class, () -> TaxRateValidity.rateFor(rows,
                        str(query, "taxCode"), LocalDate.parse(str(query, "competence"))));
            } else {
                assertEquals(new BigDecimal(expected), TaxRateValidity.rateFor(rows,
                        str(query, "taxCode"), LocalDate.parse(str(query, "competence"))));
            }
        }
    }

    private TaxEngine.TaxResult calculate(Map<String, Object> kase) {
        Map<String, Object> rates = map(kase, "rates");
        return TaxEngine.calculate(new BigDecimal(str(kase, "gross")), new TaxEngine.TaxRates(
                new BigDecimal(str(rates, "iss")), new BigDecimal(str(rates, "irrf")),
                new BigDecimal(str(rates, "csrf")), new BigDecimal(str(rates, "cbs")),
                new BigDecimal(str(rates, "ibs"))));
    }

    private void assertExpected(Map<String, Object> kase, TaxEngine.TaxResult result) {
        Map<String, Object> expected = map(kase, "expected");
        assertEquals(new BigDecimal(str(expected, "iss")), result.iss());
        assertEquals(new BigDecimal(str(expected, "irrf")), result.irrf());
        assertEquals(new BigDecimal(str(expected, "csrf")), result.csrf());
        assertEquals(new BigDecimal(str(expected, "cbs")), result.cbsInformative());
        assertEquals(new BigDecimal(str(expected, "ibs")), result.ibsInformative());
        assertEquals(new BigDecimal(str(expected, "net")), result.net());
    }

    // --- leitura do corpus ---

    private Map<String, Object> load(String file) {
        try (InputStream in = getClass().getResourceAsStream("/fiscal-contract-fixtures/" + file)) {
            assertTrue(in != null, "caso do corpus ausente: " + file);
            return Json.readMap(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("falha ao ler o caso " + file, e);
        }
    }

    private String raw(String file) {
        try (InputStream in = getClass().getResourceAsStream("/fiscal-contract-fixtures/" + file)) {
            assertTrue(in != null, "caso do corpus ausente: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("falha ao ler o caso " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> parent, String key) {
        return (List<Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean bool(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    private static long number(Map<String, Object> map) {
        Object value = map.get("number");
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

}
