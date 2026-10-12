package br.com.sigsan.commercial.collection.barcode;

import java.math.BigDecimal;
import java.util.Optional;

import static br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;

/**
 * Código de barras de arrecadação/recebimento (contas de concessionárias e tributos), 44
 * posições, produto {@code 8}. Layout FEBRABAN versão 08, vigência 01/06/2026.
 *
 * <pre>
 * posição  tam  conteúdo
 *  01       1   produto: constante 8
 *  02       1   segmento (2 = Saneamento)
 *  03       1   identificador de valor: 6/7 (módulo 10) ou 8/9 (módulo 11)
 *  04       1   dígito verificador geral
 *  05-15   11   valor (centavos, se identificador 6 ou 8)
 *  16-19    4   identificação da empresa/órgão (código FEBRABAN)   | 16-23: 8 primeiras do CNPJ
 *  20-44   25   campo livre da empresa/órgão                       | 24-44: 21 (segmento 6)
 * </pre>
 *
 * <p>Não há fator de vencimento neste código: se o convênio usa data de vencimento, ela vai nas 8
 * primeiras posições do campo livre, em {@code AAAAMMDD} (ver {@link FreeFieldLayout}). O fator
 * de vencimento existe só no boleto de cobrança bancária (pacote {@code boleto}).
 *
 * <p>O campo livre é da empresa e "será devolvido inalterado" (FEBRABAN); aqui ele é só uma
 * sequência de algarismos. Quem lhe dá significado é o {@link AgreementBarcodeLayout} do convênio
 * (parâmetro por tenant), nunca uma subclasse por empresa.
 *
 * @param valueUnits valor do campo de valor (posições 05-15) como inteiro; com identificador de
 *                   valor efetivo (6/8) são centavos de real
 */
public record CollectionBarcode(Segment segment, ValueIdentifier valueIdentifier, long valueUnits,
                                String companyId, String freeField) {

    public static final int LENGTH = 44;
    public static final int VALUE_DIGITS = 11;
    public static final long MAX_VALUE_UNITS = 99_999_999_999L;
    public static final char PRODUCT_COLLECTION = '8';
    public static final int DIGITABLE_LENGTH = 48;
    static final int BLOCK_SIZE = 11;

    public CollectionBarcode {
        if (segment == null || valueIdentifier == null) {
            throw new InvalidBarcodeException(Reason.LAYOUT_MISMATCH, "Segmento e identificador de valor são obrigatórios");
        }
        if (valueUnits < 0 || valueUnits > MAX_VALUE_UNITS) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                    "O valor precisa caber em 11 posições: " + valueUnits);
        }
        if (!Digits.isAllDigits(companyId) || companyId.length() != segment.companyIdLength()) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                    "A identificação da empresa do segmento " + segment.code() + " tem "
                            + segment.companyIdLength() + " algarismos");
        }
        if (!Digits.isAllDigits(freeField) || freeField.length() != segment.freeFieldLength()) {
            throw new InvalidBarcodeException(Reason.FIELD_OUT_OF_RANGE,
                    "O campo livre do segmento " + segment.code() + " tem " + segment.freeFieldLength()
                            + " algarismos");
        }
    }

    /** Valor em reais, quando o identificador é de valor efetivo; vazio se for valor de referência. */
    public Optional<BigDecimal> effectiveAmount() {
        return valueIdentifier.isEffectiveAmount() ? Optional.of(Digits.fromCents(valueUnits)) : Optional.empty();
    }

    /** Os 44 algarismos, com o DV geral calculado. */
    public String digits() {
        String withoutDv = withoutCheckDigit();
        int dv = valueIdentifier.checkDigit(withoutDv);
        return withoutDv.substring(0, 3) + dv + withoutDv.substring(3);
    }

    /** Linha digitável de 48 algarismos: 4 blocos de 11 posições, cada um seguido de seu DV. */
    public String digitableLine() {
        String d = digits();
        StringBuilder sb = new StringBuilder(DIGITABLE_LENGTH);
        for (int b = 0; b < 4; b++) {
            String block = d.substring(b * BLOCK_SIZE, (b + 1) * BLOCK_SIZE);
            sb.append(block).append(valueIdentifier.checkDigit(block));
        }
        return sb.toString();
    }

    /** Linha digitável com hífen entre bloco e DV e espaço entre blocos, como impressa na conta. */
    public String digitableLineFormatted() {
        String l = digitableLine();
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < 4; b++) {
            if (b > 0) {
                sb.append(' ');
            }
            sb.append(l, b * 12, b * 12 + 11).append('-').append(l.charAt(b * 12 + 11));
        }
        return sb.toString();
    }

    private String withoutCheckDigit() {
        return "" + PRODUCT_COLLECTION + segment.code() + valueIdentifier.code()
                + Digits.leftPad(valueUnits, VALUE_DIGITS) + companyId + freeField;
    }

    // ---------------------------------------------------------------- leitura

    /**
     * Lê um código de barras de 44 posições (aceita separadores de impressão) e confere o DV geral.
     *
     * @throws InvalidBarcodeException se não for arrecadação, se o segmento ou o identificador de
     *                                 valor não existirem ou se o DV não bater
     */
    public static CollectionBarcode parse(String raw) {
        String d = Digits.normalize(raw);
        if (d.length() != LENGTH) {
            throw new InvalidBarcodeException(Reason.BAD_LENGTH,
                    "O código de barras de arrecadação tem 44 posições, recebido: " + d.length());
        }
        if (d.charAt(0) != PRODUCT_COLLECTION) {
            throw new InvalidBarcodeException(Reason.NOT_COLLECTION_PRODUCT,
                    "Arrecadação começa com 8, recebido: " + d.charAt(0));
        }
        Segment segment = Segment.fromCode(d.charAt(1));
        ValueIdentifier vid = ValueIdentifier.fromCode(d.charAt(2));
        String withoutDv = d.substring(0, 3) + d.substring(4);
        int expected = vid.checkDigit(withoutDv);
        int found = d.charAt(3) - '0';
        if (expected != found) {
            throw new InvalidBarcodeException(Reason.BAD_CHECK_DIGIT,
                    "DV geral incorreto: esperado " + expected + ", recebido " + found);
        }
        int idEnd = 15 + segment.companyIdLength();
        return new CollectionBarcode(segment, vid, Long.parseLong(d.substring(4, 15)),
                d.substring(15, idEnd), d.substring(idEnd));
    }

    /**
     * Lê uma linha digitável de 48 algarismos, conferindo o DV de cada um dos 4 blocos e o DV
     * geral do código de barras reconstituído.
     */
    public static CollectionBarcode fromDigitableLine(String raw) {
        String l = Digits.normalize(raw);
        if (l.length() != DIGITABLE_LENGTH) {
            throw new InvalidBarcodeException(Reason.BAD_LENGTH,
                    "A linha digitável de arrecadação tem 48 posições, recebido: " + l.length());
        }
        ValueIdentifier vid = ValueIdentifier.fromCode(l.charAt(2));
        StringBuilder barcode = new StringBuilder(LENGTH);
        for (int b = 0; b < 4; b++) {
            String block = l.substring(b * 12, b * 12 + 11);
            int expected = vid.checkDigit(block);
            int found = l.charAt(b * 12 + 11) - '0';
            if (expected != found) {
                throw new InvalidBarcodeException(Reason.BAD_BLOCK_CHECK_DIGIT,
                        "DV do bloco " + (b + 1) + " incorreto: esperado " + expected + ", recebido " + found);
            }
            barcode.append(block);
        }
        return parse(barcode.toString());
    }
}
