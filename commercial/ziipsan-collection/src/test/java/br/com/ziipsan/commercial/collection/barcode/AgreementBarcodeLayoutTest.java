package br.com.ziipsan.commercial.collection.barcode;

import br.com.ziipsan.commercial.collection.barcode.FreeFieldLayout.CheckDigitAlgorithm;
import br.com.ziipsan.commercial.collection.barcode.FreeFieldLayout.Field;
import br.com.ziipsan.commercial.collection.barcode.InvalidBarcodeException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Layout de campo livre como dado: dois convênios diferentes são duas instâncias, sem subclasse.
 *
 * <p>DV interno do convênio B: matrícula 00004567 em módulo 10. Da direita para a esquerda, pesos
 * 2,1,2,1...: 7x2=14 (1+4=5), 6x1=6, 5x2=10 (1+0=1), 4x1=4, zeros; soma 5+6+1+4 = 16; DV = 10 - 6 = 4.
 */
class AgreementBarcodeLayoutTest {

    /** Convênio A: vencimento (8) + localidade (3) + matrícula (8) + referência MMAAAA (6) = 25. */
    private static AgreementBarcodeLayout layoutA() {
        return new AgreementBarcodeLayout(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11, "0123",
                new FreeFieldLayout(List.of(
                        Field.dueDate("vencimento"),
                        Field.numeric("localidade", 3),
                        Field.numeric("matricula", 8),
                        Field.numeric("referencia", 6))));
    }

    /** Convênio B: localidade (3) + matrícula (8) + DV mod 10 da matrícula (1) + referência (6) + "9" + sequência (6) = 25. */
    private static AgreementBarcodeLayout layoutB() {
        return new AgreementBarcodeLayout(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11, "0456",
                new FreeFieldLayout(List.of(
                        Field.numeric("localidade", 3),
                        Field.numeric("matricula", 8),
                        Field.checkDigit("dvMatricula", CheckDigitAlgorithm.MOD10, "matricula"),
                        Field.numeric("referencia", 6),
                        Field.constant("tipo", "9"),
                        Field.numeric("sequencia", 6))));
    }

    @Test
    @DisplayName("convênio A gera o vetor conhecido e lê de volta os campos e o vencimento")
    void layoutAGeneratesAndParses() {
        AgreementBarcodeLayout a = layoutA();
        CollectionBarcode code = a.build(new BigDecimal("123.45"), LocalDate.of(2026, 11, 10),
                Map.of("localidade", "7", "matricula", "12345678", "referencia", "102026"));
        assertThat(code.digits()).isEqualTo("82810000001234501232026111000712345678102026");

        AgreementBarcodeLayout.ParsedDocument doc = a.parse(code.digits());
        assertThat(doc.amount()).contains(new BigDecimal("123.45"));
        assertThat(doc.dueDate()).contains(LocalDate.of(2026, 11, 10));
        assertThat(doc.fields()).containsEntry("localidade", "007").containsEntry("matricula", "12345678")
                .containsEntry("referencia", "102026");
        assertThat(a.parseDigitableLine(code.digitableLine()).barcode()).isEqualTo(code);
    }

    @Test
    @DisplayName("convênio B (sem vencimento, com DV interno e constante) gera o vetor conhecido")
    void layoutBWithCheckDigit() {
        AgreementBarcodeLayout b = layoutB();
        CollectionBarcode code = b.build(new BigDecimal("999.99"), null,
                Map.of("localidade", "12", "matricula", "4567", "referencia", "112026", "sequencia", "321"));
        assertThat(code.freeField()).isEqualTo("0120000456741120269000321");
        assertThat(code.digits()).isEqualTo("82800000009999904560120000456741120269000321");

        AgreementBarcodeLayout.ParsedDocument doc = b.parse(code.digits());
        assertThat(doc.dueDate()).isEmpty();
        assertThat(doc.fields()).containsEntry("dvMatricula", "4").containsEntry("sequencia", "000321");
    }

    @Test
    @DisplayName("DV interno do campo livre errado é rejeitado")
    void rejectsWrongInnerCheckDigit() {
        AgreementBarcodeLayout b = layoutB();
        // matrícula 00004567 com DV 5 no lugar de 4, DV geral recalculado para o código ser válido
        CollectionBarcode tampered = new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                99999, "0456", "0120000456751120269000321");
        assertThatThrownBy(() -> b.parse(tampered.digits()))
                .isInstanceOfSatisfying(InvalidBarcodeException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.BAD_CHECK_DIGIT));
    }

    @Test
    @DisplayName("código de outro convênio (empresa diferente) não é aceito")
    void rejectsOtherCompany() {
        CollectionBarcode foreign = new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                12345, "0999", "2026111000712345678102026");
        assertThatThrownBy(() -> layoutA().parse(foreign.digits()))
                .isInstanceOfSatisfying(InvalidBarcodeException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.LAYOUT_MISMATCH));
    }

    @Test
    @DisplayName("vencimento inválido no campo livre é rejeitado")
    void rejectsInvalidDueDate() {
        CollectionBarcode bad = new CollectionBarcode(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11,
                12345, "0123", "2026133000712345678102026");
        assertThatThrownBy(() -> layoutA().parse(bad.digits()))
                .isInstanceOfSatisfying(InvalidBarcodeException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_DUE_DATE));
    }

    @Test
    @DisplayName("valor com mais de 2 casas, negativo ou grande demais não é arredondado em silêncio")
    void rejectsBadAmounts() {
        Map<String, String> v = Map.of("localidade", "7", "matricula", "1", "referencia", "102026");
        LocalDate due = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> layoutA().build(new BigDecimal("10.005"), due, v))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutA().build(new BigDecimal("-1.00"), due, v))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutA().build(new BigDecimal("1000000000.00"), due, v))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThat(layoutA().build(new BigDecimal("999999999.99"), due, v).valueUnits()).isEqualTo(99_999_999_999L);
        assertThat(layoutA().build(new BigDecimal("10.5"), due, v).valueUnits()).isEqualTo(1050);
        assertThat(layoutA().build(new BigDecimal("10.50"), due, v).valueUnits()).isEqualTo(1050);
        assertThat(layoutA().build(new BigDecimal("10.500"), due, v).valueUnits()).isEqualTo(1050);
    }

    @Test
    @DisplayName("vencimento é obrigatório se o layout tem o campo e proibido se não tem")
    void dueDateMustMatchLayout() {
        Map<String, String> v = Map.of("localidade", "7", "matricula", "1", "referencia", "102026");
        assertThatThrownBy(() -> layoutA().build(BigDecimal.ONE, null, v)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutB().build(BigDecimal.ONE, LocalDate.of(2026, 1, 1),
                Map.of("localidade", "1", "matricula", "1", "referencia", "1", "sequencia", "1")))
                .isInstanceOf(InvalidBarcodeException.class);
    }

    @Test
    @DisplayName("campo numérico maior que o tamanho, ausente ou desconhecido é rejeitado")
    void rejectsBadFieldValues() {
        LocalDate due = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> layoutA().build(BigDecimal.ONE, due,
                Map.of("localidade", "1234", "matricula", "1", "referencia", "102026")))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutA().build(BigDecimal.ONE, due, Map.of("localidade", "1")))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutA().build(BigDecimal.ONE, due,
                Map.of("localidade", "1", "matricula", "1", "referencia", "102026", "extra", "1")))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layoutA().build(BigDecimal.ONE, due,
                Map.of("localidade", "1A", "matricula", "1", "referencia", "102026")))
                .isInstanceOf(InvalidBarcodeException.class);
    }

    @Test
    @DisplayName("convênio com identificador de valor de referência não gera por valor em reais")
    void referenceValueNeedsExplicitUnits() {
        AgreementBarcodeLayout ref = new AgreementBarcodeLayout(Segment.SANEAMENTO, ValueIdentifier.REFERENCE_MOD10,
                "0123", layoutA().freeField());
        Map<String, String> v = Map.of("localidade", "7", "matricula", "12345678", "referencia", "102026");
        LocalDate due = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> ref.build(BigDecimal.TEN, due, v)).isInstanceOf(InvalidBarcodeException.class);
        assertThat(ref.buildWithReferenceValue(12345, due, v).digits())
                .isEqualTo("82770000001234501232026111000712345678102026");
    }

    @Test
    @DisplayName("layout inconsistente com o segmento é recusado na criação")
    void rejectsInconsistentLayouts() {
        // 25 posições de campo livre exigidas pelo segmento 2; o layout soma 24
        assertThatThrownBy(() -> new AgreementBarcodeLayout(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11, "0123",
                new FreeFieldLayout(List.of(Field.numeric("a", 24)))))
                .isInstanceOf(IllegalArgumentException.class);
        // empresa de 4 posições
        assertThatThrownBy(() -> new AgreementBarcodeLayout(Segment.SANEAMENTO, ValueIdentifier.EFFECTIVE_MOD11, "12345678",
                new FreeFieldLayout(List.of(Field.numeric("a", 25)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o layout vai e volta pelo JSON da coluna barcode_layout")
    void jsonRoundTrip() {
        for (AgreementBarcodeLayout original : List.of(layoutA(), layoutB())) {
            AgreementBarcodeLayout back = AgreementBarcodeLayout.fromJson(original.toJson());
            assertThat(back).isEqualTo(original);
        }
        assertThat(layoutA().toJson()).contains("\"segment\":\"SANEAMENTO\"").contains("\"kind\":\"DUE_DATE\"");
    }

    @Test
    @DisplayName("JSON incompleto ou com valor desconhecido é recusado")
    void rejectsBadJson() {
        assertThatThrownBy(() -> AgreementBarcodeLayout.fromJson("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgreementBarcodeLayout.fromJson("not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreeFieldLayout.fromJson("{\"fields\":[{\"name\":\"a\",\"kind\":\"NOPE\"}]}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("regras do FreeFieldLayout: vencimento só no início, nomes únicos, DV só sobre campo anterior")
    void freeFieldLayoutRules() {
        assertThatThrownBy(() -> new FreeFieldLayout(List.of(Field.numeric("a", 3), Field.dueDate("v"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primeiro");
        assertThatThrownBy(() -> new FreeFieldLayout(List.of(Field.numeric("a", 3), Field.numeric("a", 3))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repetido");
        assertThatThrownBy(() -> new FreeFieldLayout(List.of(
                Field.checkDigit("d", CheckDigitAlgorithm.MOD10, "a"), Field.numeric("a", 3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreeFieldLayout(List.of(
                Field.numeric("a", 3), Field.checkDigit("d", CheckDigitAlgorithm.MOD10, "a"),
                Field.checkDigit("e", CheckDigitAlgorithm.MOD10, "d"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreeFieldLayout(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Field.constant("c", "12A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constante do convênio diferente da esperada é rejeitada na leitura")
    void rejectsWrongConstant() {
        FreeFieldLayout layout = layoutB().freeField();
        // "tipo" deve ser 9; aqui vem 8 (DV interno de 00004567 continua 4)
        assertThatThrownBy(() -> layout.decode("0120000456741120268000321"))
                .isInstanceOfSatisfying(InvalidBarcodeException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.LAYOUT_MISMATCH));
    }
}
