package br.com.sigsan.commercial.collection.barcode;

import br.com.sigsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;

/**
 * Layout de código de barras de arrecadação de <b>um convênio</b> (parâmetro do tenant,
 * persistido em {@code collection_agreements.barcode_layout}). Reúne o que é fixo do convênio
 * (segmento, identificador de valor, código da empresa no banco) e a descrição do campo livre.
 *
 * <p>Gera e lê o código <b>sem subclasse por empresa</b>: dois convênios diferentes são duas
 * instâncias com {@link FreeFieldLayout} diferentes. Um convênio antigo ainda em circulação é
 * outra instância (o GSAN tinha 6 variantes por migração de convênios impressos).
 *
 * @param segment         segmento do layout (2 = Saneamento)
 * @param valueIdentifier identificador de valor (6/7 módulo 10, 8/9 módulo 11)
 * @param companyId       código da empresa: 4 algarismos atribuídos pela FEBRABAN (ou 8, no
 *                        segmento 6, a raiz numérica do CNPJ)
 * @param freeField       descrição do campo livre, com tamanho igual ao do segmento
 */
public record AgreementBarcodeLayout(Segment segment, ValueIdentifier valueIdentifier, String companyId,
                                     FreeFieldLayout freeField) {

    /** Documento lido de um código de barras, segundo este layout. */
    public record ParsedDocument(CollectionBarcode barcode, Optional<BigDecimal> amount,
                                 Optional<LocalDate> dueDate, Map<String, String> fields) {
    }

    public AgreementBarcodeLayout {
        if (segment == null || valueIdentifier == null || freeField == null) {
            throw new IllegalArgumentException("Segmento, identificador de valor e campo livre são obrigatórios");
        }
        if (!Digits.isAllDigits(companyId) || companyId.length() != segment.companyIdLength()) {
            throw new IllegalArgumentException("A identificação da empresa do segmento " + segment.code()
                    + " tem " + segment.companyIdLength() + " algarismos");
        }
        if (freeField.length() != segment.freeFieldLength()) {
            throw new IllegalArgumentException("O campo livre do segmento " + segment.code() + " tem "
                    + segment.freeFieldLength() + " posições, mas o layout soma " + freeField.length());
        }
    }

    /** Gera o código de barras de um documento com valor efetivo (identificador 6 ou 8). */
    public CollectionBarcode build(BigDecimal amount, LocalDate dueDate, Map<String, String> values) {
        if (!valueIdentifier.isEffectiveAmount()) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH,
                    "Este convênio usa valor de referência: informe as unidades com buildWithReferenceValue");
        }
        return buildWithReferenceValue(Digits.toCents(amount), dueDate, values);
    }

    /**
     * Gera o código informando o campo de valor cru (11 posições). Para identificadores de valor
     * efetivo são os centavos; para os de referência (7 e 9) é quantidade de moeda, zero ou o
     * valor a reajustar, conforme o convênio.
     */
    public CollectionBarcode buildWithReferenceValue(long valueUnits, LocalDate dueDate, Map<String, String> values) {
        return new CollectionBarcode(segment, valueIdentifier, valueUnits, companyId,
                freeField.encode(dueDate, values));
    }

    /**
     * Lê um código de barras exigindo que ele pertença a este convênio: mesmo segmento, mesmo
     * identificador de valor, mesma empresa, e campo livre coerente com o layout.
     */
    public ParsedDocument parse(String barcode) {
        return interpret(CollectionBarcode.parse(barcode));
    }

    /** Lê uma linha digitável de 48 posições; mesmas exigências de {@link #parse(String)}. */
    public ParsedDocument parseDigitableLine(String line) {
        return interpret(CollectionBarcode.fromDigitableLine(line));
    }

    /** JSON da coluna {@code barcode_layout} do convênio. */
    public String toJson() {
        // P0.25: serializacao via Json do core (Jackson 3); sem ObjectMapper Jackson 2 aqui.
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("segment", segment.name());
        root.put("valueIdentifier", valueIdentifier.name());
        root.put("companyId", companyId);
        root.put("freeField", Json.readTree(freeField.toJson()));
        return Json.write(root);
    }

    public static AgreementBarcodeLayout fromJson(String json) {
        try {
            JsonNode n = Json.readTree(json);
            if (n == null || !n.hasNonNull("segment") || !n.hasNonNull("valueIdentifier")
                    || !n.hasNonNull("companyId") || !n.hasNonNull("freeField")) {
                throw new IllegalArgumentException(
                        "JSON do convênio precisa de segment, valueIdentifier, companyId e freeField");
            }
            return new AgreementBarcodeLayout(Segment.valueOf(n.get("segment").asText()),
                    ValueIdentifier.valueOf(n.get("valueIdentifier").asText()), n.get("companyId").asText(),
                    FreeFieldLayout.fromJson(Json.write(n.get("freeField"))));
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("JSON de layout inválido", e);
        }
    }


    private ParsedDocument interpret(CollectionBarcode code) {
        if (code.segment() != segment || code.valueIdentifier() != valueIdentifier
                || !code.companyId().equals(companyId)) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH,
                    "O código não pertence a este convênio (segmento, identificador de valor ou empresa diferentes)");
        }
        FreeFieldLayout.Decoded decoded = freeField.decode(code.freeField());
        return new ParsedDocument(code, code.effectiveAmount(), decoded.dueDate(), decoded.values());
    }
}
