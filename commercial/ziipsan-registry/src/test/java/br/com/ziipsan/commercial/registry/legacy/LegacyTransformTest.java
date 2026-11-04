package br.com.ziipsan.commercial.registry.legacy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyTransformTest {

    private static LegacyMapping.Transform transform(String raw) {
        return LegacyMapping.Transform.parse(raw);
    }

    @Test
    @DisplayName("TRIM, UPPER e INTEGER")
    void basicTransforms() {
        assertThat(LegacyTransform.apply(transform("TRIM"), "  abc  ")).isEqualTo("abc");
        assertThat(LegacyTransform.apply(transform("UPPER"), "abc")).isEqualTo("ABC");
        assertThat(LegacyTransform.apply(transform("INTEGER"), " 42 ")).isEqualTo(42L);
    }

    @Test
    @DisplayName("INTEGER inválido lança (vira issue na carga, não dado)")
    void integerInvalidThrows() {
        assertThatThrownBy(() -> LegacyTransform.apply(transform("INTEGER"), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DECIMAL_CENTS aceita padrão brasileiro e americano, e recusa mais de 2 casas")
    void decimalCents() {
        assertThat(LegacyTransform.apply(transform("DECIMAL_CENTS"), "1.234,56")).isEqualTo(123456L);
        assertThat(LegacyTransform.apply(transform("DECIMAL_CENTS"), "1234.56")).isEqualTo(123456L);
        assertThat(LegacyTransform.apply(transform("DECIMAL_CENTS"), "-10,00")).isEqualTo(-1000L);
        assertThatThrownBy(() -> LegacyTransform.apply(transform("DECIMAL_CENTS"), "1,234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DATE com padrão e recusa de data impossível")
    void dateTransform() {
        assertThat(LegacyTransform.apply(transform("DATE:dd/MM/yyyy"), "05/03/2020"))
                .isEqualTo(LocalDate.of(2020, 3, 5));
        assertThatThrownBy(() -> LegacyTransform.apply(transform("DATE:dd/MM/yyyy"), "31/02/2020"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DOCUMENT: CPF válido, CNPJ alfanumérico em minúsculas normalizado, inválido sinalizado")
    void documentTransform() {
        LegacyTransform.DocumentValue cpf =
                (LegacyTransform.DocumentValue) LegacyTransform.apply(transform("DOCUMENT"), "111.444.777-35");
        assertThat(cpf.kind()).isEqualTo("CPF");
        assertThat(cpf.valid()).isTrue();
        assertThat(cpf.masked()).doesNotContain("11144477735");

        LegacyTransform.DocumentValue cnpj =
                (LegacyTransform.DocumentValue) LegacyTransform.apply(transform("DOCUMENT"), "12.abc.345/01de-35");
        assertThat(cnpj.kind()).isEqualTo("CNPJ");
        assertThat(cnpj.valid()).isTrue();
        assertThat(cnpj.masked()).doesNotContain("12ABC34501DE35");

        LegacyTransform.DocumentValue invalid =
                (LegacyTransform.DocumentValue) LegacyTransform.apply(transform("DOCUMENT"), "111.444.777-36");
        assertThat(invalid.valid()).isFalse();
    }

    @Test
    @DisplayName("entrada em branco vira ausente em qualquer transformação")
    void blankBecomesNull() {
        assertThat(LegacyTransform.apply(transform("INTEGER"), "   ")).isNull();
        assertThat(LegacyTransform.apply(transform("DATE:dd/MM/yyyy"), null)).isNull();
    }
}
