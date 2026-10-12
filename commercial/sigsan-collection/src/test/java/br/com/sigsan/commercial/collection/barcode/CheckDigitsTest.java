package br.com.sigsan.commercial.collection.barcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vetores do próprio manual FEBRABAN "Layout Padrão de Arrecadação/Recebimento com Utilização do
 * Código de Barras" v08 (seções 07 a 10) e contas feitas à mão.
 *
 * <p>Seção 07, módulo 10 de 01230067896: produtos 0 1 4 3 0 0 12 7 16 9 12; soma dos algarismos =
 * 0+1+4+3+0+0+1+2+7+1+6+9+1+2 = 37; 37 mod 10 = 7; DAC = 10 - 7 = 3.
 *
 * <p>Seção 09, módulo 11 de 01230067896: produtos 0 3 4 27 0 0 36 35 32 27 12 = 176; 176 mod 11 = 0;
 * DAC 0.
 *
 * <p>Seções 08 e 10, área auxiliar de 43 posições {@code 822 + 0000215048 2009741232 2015409829
 * 0108605940}: módulo 10 dá soma 139, resto 9, DAC 1; módulo 11 dá soma 705, resto 1, DAC 0.
 *
 * <p>Os casos de um algarismo são conta de cabeça: com um só algarismo d (peso 2), o módulo 10 soma
 * os algarismos de 2d e o módulo 11 usa o resto de 2d por 11.
 */
class CheckDigitsTest {

    @Test
    @DisplayName("seção 07 do manual: módulo 10 de 01230067896 é 3")
    void mod10SpecExample() {
        assertThat(CheckDigits.mod10("01230067896")).isEqualTo(3);
    }

    @Test
    @DisplayName("seção 09 do manual: módulo 11 de 01230067896 é 0")
    void mod11SpecExample() {
        assertThat(CheckDigits.mod11Collection("01230067896")).isEqualTo(0);
    }

    @Test
    @DisplayName("seção 08 do manual: DV geral módulo 10 das 43 posições é 1 (soma 139)")
    void mod10GeneralDigitSpecExample() {
        String area = "822" + "0000215048" + "2009741232" + "2015409829" + "0108605940";
        assertThat(area).hasSize(43);
        assertThat(CheckDigits.mod10(area)).isEqualTo(1);
    }

    @Test
    @DisplayName("seção 10 do manual: DV geral módulo 11 das 43 posições é 0 (soma 705, resto 1)")
    void mod11GeneralDigitSpecExample() {
        String area = "822" + "0000215048" + "2009741232" + "2015409829" + "0108605940";
        assertThat(CheckDigits.mod11Collection(area)).isEqualTo(0);
    }

    @Test
    @DisplayName("módulo 10: resto 0 dá DAC 0; produto de 2 algarismos soma os algarismos")
    void mod10Edges() {
        assertThat(CheckDigits.mod10("0")).isEqualTo(0);      // soma 0, resto 0
        assertThat(CheckDigits.mod10("5")).isEqualTo(9);      // 5x2=10 -> 1+0=1 -> 10-1
        assertThat(CheckDigits.mod10("1")).isEqualTo(8);      // 1x2=2 -> 10-2
        assertThat(CheckDigits.mod10("9")).isEqualTo(1);      // 9x2=18 -> 1+8=9 -> 10-9
        assertThat(CheckDigits.mod10("49")).isEqualTo(7);     // 9x2=18->9; 4x1=4 -> 13 -> 10-3
    }

    @Test
    @DisplayName("módulo 11 da arrecadação: resto 0 e 1 dão 0, resto 10 dá 1")
    void mod11CollectionRemainderRules() {
        assertThat(CheckDigits.mod11Collection("0")).isEqualTo(0); // soma 0, resto 0
        assertThat(CheckDigits.mod11Collection("6")).isEqualTo(0); // 12, resto 1
        assertThat(CheckDigits.mod11Collection("5")).isEqualTo(1); // 10, resto 10 -> 11-10
        assertThat(CheckDigits.mod11Collection("1")).isEqualTo(9); // 2, resto 2 -> 11-2
        assertThat(CheckDigits.mod11Collection("4")).isEqualTo(3); // 8, resto 8 -> 11-8
    }

    @Test
    @DisplayName("módulo 11 da cobrança: 0, 10 e 11 viram 1 (a arrecadação dá 0 nos mesmos restos 0 e 1)")
    void mod11BillingRemainderRules() {
        assertThat(CheckDigits.mod11Billing("0")).isEqualTo(1); // resto 0 -> 11 -> 1
        assertThat(CheckDigits.mod11Billing("6")).isEqualTo(1); // resto 1 -> 10 -> 1
        assertThat(CheckDigits.mod11Billing("5")).isEqualTo(1); // resto 10 -> 1
        assertThat(CheckDigits.mod11Billing("1")).isEqualTo(9); // resto 2 -> 9
        assertThat(CheckDigits.mod11Billing("4")).isEqualTo(3); // resto 8 -> 3
    }

    @Test
    @DisplayName("módulo 11 com mais de 8 algarismos reinicia o peso em 2")
    void mod11WeightsRestart() {
        // 49 -> 9x2 + 4x3 = 30; 30 mod 11 = 8; DAC = 3
        assertThat(CheckDigits.mod11Collection("49")).isEqualTo(3);
        // "100000000" (9 algarismos): o 1 recebe peso 2 (reinício) depois de 8 pesos -> soma 2
        assertThat(CheckDigits.mod11Collection("100000000")).isEqualTo(9);
    }

    @Test
    @DisplayName("só algarismos")
    void rejectsNonDigits() {
        assertThatThrownBy(() -> CheckDigits.mod10("12A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckDigits.mod11Collection("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckDigits.mod11Billing(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
