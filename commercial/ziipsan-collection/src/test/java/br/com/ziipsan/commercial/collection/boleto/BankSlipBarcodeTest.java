package br.com.ziipsan.commercial.collection.boleto;

import br.com.ziipsan.commercial.collection.barcode.CheckDigits;
import br.com.ziipsan.commercial.collection.barcode.FreeFieldLayout;
import br.com.ziipsan.commercial.collection.barcode.FreeFieldLayout.Field;
import br.com.ziipsan.commercial.collection.barcode.InvalidBarcodeException;
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
 * Boleto de cobrança bancária. O vetor principal é o exemplo numérico do manual "Código de Barras
 * - Cobrança" do Banco Santander v36 (abril/2025), com a soma e o DV mostrados no manual:
 * <pre>
 * código de barras: 033 9 8 2046 0000027371 9 0282033 5666124578002 0 101
 * soma dos produtos (módulo 11, pesos 2-9) = 696; 696 mod 11 = 3; DV = 11 - 3 = 8
 * linha digitável:  033990282.7 0335666124.3 5780020101.4 8 20460000027371
 * </pre>
 * Campo 1 {@code 033990282}: soma dos algarismos dos produtos = 43; resto 3; DV = 7 (manual).
 *
 * <p>Os demais vetores foram conferidos com um oráculo independente em Node (fórmulas do módulo 11
 * da cobrança e módulo 10 dos campos), que reproduz o exemplo do manual. O vetor do banco 001
 * exercita o resto 10 (DV 1).
 */
class BankSlipBarcodeTest {

    private static final String SANTANDER_BARCODE = "03398204600000273719028203356661245780020101";
    private static final String SANTANDER_FREE = "9028203356661245780020101";

    @Test
    @DisplayName("exemplo do manual do Santander: código de barras e linha digitável")
    void santanderManualVector() {
        BankSlipBarcode b = new BankSlipBarcode("033", '9', 2046, 27371, SANTANDER_FREE);
        assertThat(b.digits()).isEqualTo(SANTANDER_BARCODE);
        assertThat(b.digitableLine()).isEqualTo("03399028270335666124357800201014820460000027371");
        assertThat(b.digitableLineFormatted()).isEqualTo("03399.02827 03356.661243 57800.201014 8 20460000027371");
        assertThat(b.amount()).isEqualByComparingTo("273.71");
    }

    @Test
    @DisplayName("o DV 8 do manual vem do módulo 11 de cobrança sobre as 43 posições (soma 696)")
    void santanderCheckDigit() {
        String without = SANTANDER_BARCODE.substring(0, 4) + SANTANDER_BARCODE.substring(5);
        assertThat(CheckDigits.mod11Billing(without)).isEqualTo(8);
    }

    @Test
    @DisplayName("os três DV de campo do manual: 7, 3 e 4")
    void santanderFieldCheckDigits() {
        assertThat(CheckDigits.mod10("033990282")).isEqualTo(7);
        assertThat(CheckDigits.mod10("0335666124")).isEqualTo(3);
        assertThat(CheckDigits.mod10("5780020101")).isEqualTo(4);
    }

    @Test
    @DisplayName("banco 001, fator 1000 (22/02/2025), valor R$ 1,00: resto 10 dá DV 1")
    void bank001Vector() {
        BankSlipBarcode b = new BankSlipBarcode("001", '9', 1000, 100, "1111111111222222222233333");
        assertThat(b.digits()).isEqualTo("00191100000000001001111111111222222222233333");
        assertThat(b.digitableLine()).isEqualTo("00191111111111122222722222333332110000000000100");
    }

    @Test
    @DisplayName("banco 237, fator 1667 (21/12/2026), valor R$ 1.234,56")
    void bank237Vector() {
        BankSlipBarcode b = new BankSlipBarcode("237", '9', 1667, 123456, "0123456789012345678901234");
        assertThat(b.digits()).isEqualTo("23794166700001234560123456789012345678901234");
        assertThat(b.digitableLine()).isEqualTo("23790123435678901234356789012343416670000123456");
    }

    @Test
    @DisplayName("banco 341, fator 9999 (13/10/2049), valor máximo, campo livre zerado")
    void bank341MaximumVector() {
        BankSlipBarcode b = new BankSlipBarcode("341", '9', 9999, BankSlipBarcode.MAX_AMOUNT_CENTS,
                "0000000000000000000000000");
        assertThat(b.digits()).isEqualTo("34193999999999999990000000000000000000000000");
        assertThat(b.digitableLine()).isEqualTo("34190000090000000000000000000000399999999999999");
        assertThat(b.amount()).isEqualByComparingTo("99999999.99");
    }

    @Test
    @DisplayName("ler código de barras e linha digitável devolve os mesmos campos")
    void parseRoundTrip() {
        BankSlipBarcode original = new BankSlipBarcode("237", '9', 1667, 123456, "0123456789012345678901234");
        assertThat(BankSlipBarcode.parse(original.digits())).isEqualTo(original);
        assertThat(BankSlipBarcode.fromDigitableLine(original.digitableLine())).isEqualTo(original);
        assertThat(BankSlipBarcode.fromDigitableLine(original.digitableLineFormatted())).isEqualTo(original);
        // a linha impressa do manual do Santander, com pontos e espaços
        assertThat(BankSlipBarcode.fromDigitableLine("033990282.7 0335666124.3 5780020101.4 8 20460000027371").digits())
                .isEqualTo(SANTANDER_BARCODE);
    }

    @Test
    @DisplayName("fator e valor lidos do código: 2046 e R$ 273,71; vencimento pela referência")
    void parseFields() {
        BankSlipBarcode b = BankSlipBarcode.parse(SANTANDER_BARCODE);
        assertThat(b.bankCode()).isEqualTo("033");
        assertThat(b.factor()).isEqualTo(2046);
        assertThat(b.amountCents()).isEqualTo(27371);
        // 07/10/1997 + 2046 dias = 15/05/2003
        assertThat(b.dueDate(LocalDate.of(2003, 5, 1), 30, 30)).contains(LocalDate.of(2003, 5, 15));
    }

    @Test
    @DisplayName("DV geral, DV de campo, tamanho e caractere inválidos são rejeitados")
    void rejectsMalformed() {
        assertReason(() -> BankSlipBarcode.parse("03397204600000273719028203356661245780020101"), Reason.BAD_CHECK_DIGIT);
        assertReason(() -> BankSlipBarcode.parse("0339820460000027371902820335666124578002010"), Reason.BAD_LENGTH);
        assertReason(() -> BankSlipBarcode.parse("0339820460000027371902820335666124578002010X"), Reason.NOT_NUMERIC);
        // campo 2 com DV 4 no lugar de 3
        assertReason(() -> BankSlipBarcode.fromDigitableLine("03399028270335666124457800201014820460000027371"),
                Reason.BAD_BLOCK_CHECK_DIGIT);
        // DV geral 9 no lugar de 8 na linha digitável
        assertReason(() -> BankSlipBarcode.fromDigitableLine("03399028270335666124357800201014920460000027371"),
                Reason.BAD_CHECK_DIGIT);
        assertReason(() -> BankSlipBarcode.fromDigitableLine("123"), Reason.BAD_LENGTH);
    }

    @Test
    @DisplayName("campos fora do intervalo: banco, moeda, fator, valor e campo livre")
    void rejectsOutOfRangeFields() {
        String free = SANTANDER_FREE;
        assertThatThrownBy(() -> new BankSlipBarcode("33", '9', 1, 1, free)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new BankSlipBarcode("033", '7', 1, 1, free)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new BankSlipBarcode("033", '9', 10000, 1, free)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new BankSlipBarcode("033", '9', 1, 10_000_000_000L, free)).isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new BankSlipBarcode("033", '9', 1, 1, "123")).isInstanceOf(InvalidBarcodeException.class);
        assertThat(new BankSlipBarcode("033", '8', 1, 0, free).digits()).hasSize(44);
    }

    @Test
    @DisplayName("convênio descrito como dado gera exatamente o vetor do Santander (07/10/1997 + 2046 = 15/05/2003)")
    void layoutBuildsSantanderVector() {
        BankSlipLayout layout = new BankSlipLayout("033", new FreeFieldLayout(List.of(
                Field.constant("fixo9", "9"),
                Field.numeric("beneficiario", 7),
                Field.numeric("nossoNumero", 13),
                Field.constant("fixo0", "0"),
                Field.constant("modalidade", "101"))));
        BankSlipBarcode code = layout.build(new BigDecimal("273.71"), LocalDate.of(2003, 5, 15),
                Map.of("beneficiario", "0282033", "nossoNumero", "5666124578002"));
        assertThat(code.digits()).isEqualTo(SANTANDER_BARCODE);

        BankSlipLayout.ParsedSlip parsed = layout.parse(code.digits(), LocalDate.of(2003, 5, 1), 30, 30);
        assertThat(parsed.amount()).isEqualByComparingTo("273.71");
        assertThat(parsed.dueDate()).contains(LocalDate.of(2003, 5, 15));
        assertThat(parsed.fields()).containsEntry("beneficiario", "0282033").containsEntry("nossoNumero", "5666124578002");
    }

    @Test
    @DisplayName("boleto de outro banco ou campo livre fora do layout não é aceito")
    void layoutRejectsForeignSlips() {
        BankSlipLayout layout = new BankSlipLayout("033", new FreeFieldLayout(List.of(
                Field.constant("fixo9", "9"), Field.numeric("beneficiario", 7), Field.numeric("nossoNumero", 13),
                Field.constant("fixo0", "0"), Field.constant("modalidade", "101"))));
        BankSlipBarcode other = new BankSlipBarcode("237", '9', 1667, 123456, "0123456789012345678901234");
        assertReason(() -> layout.parse(other.digits(), LocalDate.of(2026, 10, 12), 30, 90), Reason.LAYOUT_MISMATCH);
        BankSlipBarcode badFree = new BankSlipBarcode("033", '9', 1667, 123456, "0123456789012345678901234");
        assertReason(() -> layout.parse(badFree.digits(), LocalDate.of(2026, 10, 12), 30, 90), Reason.LAYOUT_MISMATCH);
    }

    @Test
    @DisplayName("sem vencimento, valor com 3 casas ou negativo, e layout com vencimento no campo livre são recusados")
    void layoutBuildGuards() {
        BankSlipLayout layout = new BankSlipLayout("001", new FreeFieldLayout(List.of(Field.numeric("n", 25))));
        Map<String, String> v = Map.of("n", "1");
        assertThatThrownBy(() -> layout.build(BigDecimal.ONE, null, v)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> layout.build(new BigDecimal("1.005"), LocalDate.of(2026, 12, 1), v))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> layout.build(new BigDecimal("-1"), LocalDate.of(2026, 12, 1), v))
                .isInstanceOf(InvalidBarcodeException.class);
        assertThatThrownBy(() -> new BankSlipLayout("001", new FreeFieldLayout(List.of(
                Field.dueDate("v"), Field.numeric("n", 17)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BankSlipLayout("1", new FreeFieldLayout(List.of(Field.numeric("n", 25)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertReason(Runnable r, Reason reason) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(InvalidBarcodeException.class,
                e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
