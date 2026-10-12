package br.com.sigsan.commercial.collection.barcode;

import br.com.sigsan.commercial.collection.barcode.InvalidBarcodeException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vetores de código de barras de arrecadação (produto 8, segmento 2 = Saneamento).
 *
 * <p>Parte comum: valor 00000012345 (R$ 123,45), empresa 0123, campo livre
 * {@code 2026111000712345678102026} = vencimento 20261110, localidade 007, matrícula 12345678,
 * referência 102026. A área de 43 posições é {@code 82X + 00000012345 + 0123 + campo livre}.
 *
 * <p>Módulo 11 (identificador 8): produtos da direita para a esquerda, pesos 2 a 9 reiniciando.
 * Campo livre: 372; empresa 0123: 22; valor: 101; prefixo 828: 54; soma 549; 549 mod 11 = 10, e
 * resto 10 dá DV 1 (regra da seção 09). Bloco 1 {@code 82810000001}: 2+9+16+6+32 = 65; 65 mod 11
 * = 10; DV 1.
 *
 * <p>Os demais DVs (identificadores 6, 7 e 9, os blocos 2 a 4 e o segmento 6) foram conferidos
 * com um oráculo independente em Node que implementa as fórmulas das seções 07 a 10 do manual e
 * reproduz os exemplos numéricos dele (soma 37, 176, 139 e 705).
 */
class CollectionBarcodeTest {

    private static final String FREE = "2026111000712345678102026";

    private static CollectionBarcode make(ValueIdentifier vid) {
        return new CollectionBarcode(Segment.SANEAMENTO, vid, 12345, "0123", FREE);
    }

    @Test
    @DisplayName("identificador 8 (valor efetivo, módulo 11): DV 1 pelo resto 10")
    void mod11EffectiveVector() {
        CollectionBarcode b = make(ValueIdentifier.EFFECTIVE_MOD11);
        assertThat(b.digits()).isEqualTo("82810000001234501232026111000712345678102026");
        assertThat(b.digitableLine()).isEqualTo("828100000011234501232025611100071230456781020264");
        assertThat(b.digitableLineFormatted())
                .isEqualTo("82810000001-1 23450123202-5 61110007123-0 45678102026-4");
        assertThat(b.effectiveAmount()).contains(new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("identificador 6 (valor efetivo, módulo 10): DV 9")
    void mod10EffectiveVector() {
        CollectionBarcode b = make(ValueIdentifier.EFFECTIVE_MOD10);
        assertThat(b.digits()).isEqualTo("82690000001234501232026111000712345678102026");
        assertThat(b.digitableLine()).isEqualTo("826900000017234501232024611100071236456781020262");
    }

    @Test
    @DisplayName("identificador 7 (referência, módulo 10): sem valor efetivo")
    void mod10ReferenceVector() {
        CollectionBarcode b = make(ValueIdentifier.REFERENCE_MOD10);
        assertThat(b.digits()).isEqualTo("82770000001234501232026111000712345678102026");
        assertThat(b.digitableLine()).isEqualTo("827700000017234501232024611100071236456781020262");
        assertThat(b.effectiveAmount()).isEmpty();
        assertThat(b.valueUnits()).isEqualTo(12345);
    }

    @Test
    @DisplayName("identificador 9 (referência, módulo 11): DV 0 pelo resto 1")
    void mod11ReferenceVector() {
        CollectionBarcode b = make(ValueIdentifier.REFERENCE_MOD11);
        assertThat(b.digits()).isEqualTo("82900000001234501232026111000712345678102026");
        assertThat(b.digitableLine()).isEqualTo("829000000018234501232025611100071230456781020264");
        assertThat(b.effectiveAmount()).isEmpty();
    }

    @Test
    @DisplayName("segmento 6 identifica a empresa por 8 posições do CNPJ e tem campo livre de 21")
    void segmentSixUsesCnpjRootAndShorterFreeField() {
        CollectionBarcode b = new CollectionBarcode(Segment.CARNES_E_DEMAIS_POR_CNPJ,
                ValueIdentifier.EFFECTIVE_MOD10, 5000, "12345678", "123456789012345678901");
        assertThat(b.digits()).isEqualTo("86610000000500012345678123456789012345678901");
        assertThat(b.digitableLine()).isEqualTo("866100000003500012345673812345678906123456789015");
        assertThat(CollectionBarcode.parse(b.digits())).isEqualTo(b);
    }

    @Test
    @DisplayName("campo livre com DV interno: vetor completo")
    void vectorWithInnerCheckDigit() {
        CollectionBarcode b = new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11, 99999,
                "0456", "0120000456741120269000321");
        assertThat(b.digits()).isEqualTo("82800000009999904560120000456741120269000321");
        assertThat(b.digitableLine()).isEqualTo("828000000095999904560127000045674116202690003217");
    }

    @Test
    @DisplayName("ler o código gerado devolve os mesmos campos")
    void parseRoundTrip() {
        for (ValueIdentifier vid : ValueIdentifier.values()) {
            CollectionBarcode original = make(vid);
            assertThat(CollectionBarcode.parse(original.digits())).isEqualTo(original);
            assertThat(CollectionBarcode.fromDigitableLine(original.digitableLine())).isEqualTo(original);
            assertThat(CollectionBarcode.fromDigitableLine(original.digitableLineFormatted())).isEqualTo(original);
        }
    }

    @Test
    @DisplayName("separadores de impressão são aceitos na leitura")
    void parseAcceptsPrintedSeparators() {
        assertThat(CollectionBarcode.parse("8281-0000001 2345 0123 2026111000712345678102026"))
                .isEqualTo(make(ValueIdentifier.EFFECTIVE_MOD11));
    }

    @Test
    @DisplayName("DV geral errado é rejeitado")
    void rejectsBadCheckDigit() {
        assertThatThrownBy(() -> CollectionBarcode.parse("82800000001234501232026111000712345678102026"))
                .isInstanceOfSatisfying(InvalidBarcodeException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.BAD_CHECK_DIGIT));
    }

    @Test
    @DisplayName("dígito de bloco errado na linha digitável é rejeitado, apontando o bloco")
    void rejectsBadBlockCheckDigit() {
        // bloco 3 com DV 1 no lugar de 0
        assertThatThrownBy(() -> CollectionBarcode.fromDigitableLine("828100000011234501232025611100071231456781020264"))
                .isInstanceOfSatisfying(InvalidBarcodeException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.BAD_BLOCK_CHECK_DIGIT);
                    assertThat(e.getMessage()).contains("bloco 3");
                });
    }

    @Test
    @DisplayName("tamanho, produto, segmento, identificador de valor e caracteres inválidos")
    void rejectsMalformedCodes() {
        assertReason("8281000000123450123202611100071234567810202", Reason.BAD_LENGTH);          // 43
        assertReason("92810000001234501232026111000712345678102026", Reason.NOT_COLLECTION_PRODUCT);
        assertReason("80810000001234501232026111000712345678102026", Reason.UNKNOWN_SEGMENT);
        assertReason("88810000001234501232026111000712345678102026", Reason.UNKNOWN_SEGMENT);   // segmento 8 não existe
        assertReason("82510000001234501232026111000712345678102026", Reason.UNKNOWN_VALUE_IDENTIFIER);
        assertReason("8281000000123450123202611100071234567810202X", Reason.NOT_NUMERIC);
        assertThatThrownBy(() -> CollectionBarcode.parse(null)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> CollectionBarcode.fromDigitableLine("123")).isInstanceOf(InvalidBarcodeException.class);
    }

    private static void assertReason(String code, Reason reason) {
        assertThatThrownBy(() -> CollectionBarcode.parse(code))
                .isInstanceOfSatisfying(InvalidBarcodeException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    @DisplayName("campos fora de tamanho: valor de 12 posições, empresa e campo livre de tamanho errado")
    void rejectsOutOfRangeFields() {
        assertThatThrownBy(() -> new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                100_000_000_000L, "0123", FREE)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                1, "012", FREE)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                1, "0123", "123")).isInstanceOf(InvalidBarcodeException.class);
        // 21 posições de campo livre só no segmento 6
        assertThatThrownBy(() -> new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                1, "0123", "123456789012345678901")).isInstanceOf(InvalidBarcodeException.class);
    }

    @Test
    @DisplayName("o maior valor possível, 999.999.999,99, cabe nas 11 posições")
    void maximumValueFits() {
        CollectionBarcode b = new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                CollectionBarcode.MAX_VALUE_UNITS, "0123", FREE);
        assertThat(b.digits().substring(4, 15)).isEqualTo("99999999999");
        assertThat(b.effectiveAmount()).contains(new BigDecimal("999999999.99"));
        assertThat(CollectionBarcode.parse(b.digits())).isEqualTo(b);
    }

    @Test
    @DisplayName("o código gerado sempre tem 44 posições e a linha digitável 48")
    void lengths() {
        for (Segment s : Segment.values()) {
            String company = "1".repeat(s.companyIdLength());
            String free = "2".repeat(s.freeFieldLength());
            CollectionBarcode b = new CollectionBarcode(s, ValueIdentifier.EFFECTIVE_MOD11, 1, company, free);
            assertThat(b.digits()).hasSize(44);
            assertThat(b.digitableLine()).hasSize(48);
        }
    }
}
