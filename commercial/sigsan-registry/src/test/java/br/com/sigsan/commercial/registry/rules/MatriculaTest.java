package br.com.sigsan.commercial.registry.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valores calculados à mão: pesos 2, 3, 4... da direita para a esquerda, resto de 11;
 * resto 0 ou 1 dá DV 0, senão DV = 11 - resto.
 */
class MatriculaTest {

    @Test
    @DisplayName("12345: 5·2 + 4·3 + 3·4 + 2·5 + 1·6 = 50; 50 mod 11 = 6; DV = 5")
    void basicCase() {
        assertThat(Matricula.checkDigit("12345")).isEqualTo(5);
        assertThat(Matricula.format(12345)).isEqualTo("12345-5");
    }

    @Test
    @DisplayName("dígito único: 1 → 2 mod 11 = 2 → DV 9; 7 → 14 mod 11 = 3 → DV 8")
    void singleDigits() {
        assertThat(Matricula.checkDigit("1")).isEqualTo(9);
        assertThat(Matricula.checkDigit("7")).isEqualTo(8);
    }

    @Test
    @DisplayName("resto 0 ou 1 dá DV 0: '0' → 0; '99' → 9·2 + 9·3 = 45, 45 mod 11 = 1 → 0")
    void restZeroOrOneGivesZero() {
        assertThat(Matricula.checkDigit("0")).isEqualTo(0);
        assertThat(Matricula.checkDigit("99")).isEqualTo(0);
    }

    @Test
    @DisplayName("resto 10 dá DV 1: '5' → 5·2 = 10, 10 mod 11 = 10 → 11 - 10 = 1")
    void restTenGivesOne() {
        assertThat(Matricula.checkDigit("5")).isEqualTo(1);
    }

    @Test
    @DisplayName("zero à esquerda pesa: '10' → 0·2 + 1·3 = 3 → DV 8")
    void leadingDigitsAreWeighted() {
        assertThat(Matricula.checkDigit("10")).isEqualTo(8);
    }

    @Test
    @DisplayName("os pesos reiniciam em 2 depois do 9: 1234567890 soma 231 = 21·11, resto 0, DV 0")
    void weightsCycle() {
        // da direita: 0·2 + 9·3 + 8·4 + 7·5 + 6·6 + 5·7 + 4·8 + 3·9 + 2·2 + 1·3
        //           = 0 + 27 + 32 + 35 + 36 + 35 + 32 + 27 + 4 + 3 = 231
        assertThat(Matricula.checkDigit("1234567890")).isEqualTo(0);
    }

    @Test
    @DisplayName("validação aceita com e sem hífen e recusa dígito errado")
    void validation() {
        assertThat(Matricula.isValid("12345-5")).isTrue();
        assertThat(Matricula.isValid("123455")).isTrue();
        assertThat(Matricula.isValid("12345-4")).isFalse();
        assertThat(Matricula.isValid("12354-5")).as("dois dígitos trocados").isFalse();
    }

    @Test
    @DisplayName("todo erro de um único dígito é detectado")
    void detectsEverySingleDigitError() {
        String valid = "123455";
        for (int pos = 0; pos < valid.length(); pos++) {
            for (char c = '0'; c <= '9'; c++) {
                if (c == valid.charAt(pos)) continue;
                String bad = valid.substring(0, pos) + c + valid.substring(pos + 1);
                assertThat(Matricula.isValid(bad)).as("%s → %s", valid, bad).isFalse();
            }
        }
    }

    @Test
    @DisplayName("entradas inválidas")
    void invalidInputs() {
        assertThat(Matricula.isValid(null)).isFalse();
        assertThat(Matricula.isValid("")).isFalse();
        assertThat(Matricula.isValid("5")).isFalse();
        assertThat(Matricula.isValid("12a45-5")).isFalse();
        assertThatThrownBy(() -> Matricula.checkDigit("12a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Matricula.checkDigit("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Matricula.format(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
