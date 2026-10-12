package br.com.sigsan.commercial.registry.rules;

import br.com.sigsan.commercial.registry.rules.DocumentNumber.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada dígito verificador abaixo foi calculado à mão.
 *
 * <p>CPF 529.982.247-25: dv1 = (5·10+2·9+9·8+9·7+8·6+2·5+2·4+4·3+7·2 = 295) · 10 mod 11 = 2;
 * dv2 com o dv1 = (5·11+2·10+9·9+9·8+8·7+2·6+2·5+4·4+7·3+2·2 = 347) · 10 mod 11 = 5.
 *
 * <p>CNPJ 11.222.333/0001-81: soma1 = 102 → 102 mod 11 = 3 → dv1 = 8; soma2 = 120 → mod 11 = 10 → dv2 = 1.
 *
 * <p>CNPJ alfanumérico 12.ABC.345/01DE-35: A=17, B=18, C=19, D=20, E=21;
 * soma1 = 459 → mod 11 = 8 → dv1 = 3; soma2 = 424 → mod 11 = 6 → dv2 = 5.
 */
class DocumentNumberTest {

    @Test
    @DisplayName("CPF 529.982.247-25 é válido, com e sem pontuação")
    void validCpf() {
        assertThat(DocumentNumber.isValidCpf("529.982.247-25")).isTrue();
        assertThat(DocumentNumber.isValidCpf("52998224725")).isTrue();
        assertThat(DocumentNumber.kind("529.982.247-25")).isEqualTo(Kind.CPF);
    }

    @Test
    @DisplayName("CPF com dígito errado, repetido ou de tamanho errado é inválido")
    void invalidCpf() {
        assertThat(DocumentNumber.isValidCpf("529.982.247-26")).isFalse();
        assertThat(DocumentNumber.isValidCpf("529.982.247-35")).isFalse();
        assertThat(DocumentNumber.isValidCpf("111.111.111-11")).as("passa no cálculo, não existe").isFalse();
        assertThat(DocumentNumber.isValidCpf("123")).isFalse();
        assertThat(DocumentNumber.isValidCpf(null)).isFalse();
    }

    @Test
    @DisplayName("CNPJ numérico 11.222.333/0001-81 é válido")
    void validNumericCnpj() {
        assertThat(DocumentNumber.isValidCnpj("11.222.333/0001-81")).isTrue();
        assertThat(DocumentNumber.isValidCnpj("11222333000181")).isTrue();
        assertThat(DocumentNumber.kind("11.222.333/0001-81")).isEqualTo(Kind.CNPJ);
    }

    @Test
    @DisplayName("CNPJ alfanumérico 12.ABC.345/01DE-35 é válido, em maiúsculas ou minúsculas")
    void validAlphanumericCnpj() {
        assertThat(DocumentNumber.isValidCnpj("12.ABC.345/01DE-35")).isTrue();
        assertThat(DocumentNumber.isValidCnpj("12abc34501de35")).isTrue();
        assertThat(DocumentNumber.kind("12.ABC.345/01DE-35")).isEqualTo(Kind.CNPJ);
    }

    @Test
    @DisplayName("CNPJ alfanumérico com dígito errado ou letra nos dígitos verificadores é inválido")
    void invalidAlphanumericCnpj() {
        assertThat(DocumentNumber.isValidCnpj("12.ABC.345/01DE-36")).isFalse();
        assertThat(DocumentNumber.isValidCnpj("12.ABC.345/01DE-3A")).as("DV é sempre numérico").isFalse();
        assertThat(DocumentNumber.isValidCnpj("12.ABD.345/01DE-35")).as("uma letra trocada").isFalse();
    }

    @Test
    @DisplayName("CNPJ repetido ou de tamanho errado é inválido")
    void invalidCnpj() {
        assertThat(DocumentNumber.isValidCnpj("00.000.000/0000-00")).isFalse();
        assertThat(DocumentNumber.isValidCnpj("11.222.333/0001-82")).isFalse();
        assertThat(DocumentNumber.isValidCnpj("1122233300018")).isFalse();
    }

    @Test
    @DisplayName("cálculo dos dígitos verificadores de CNPJ")
    void cnpjCheckDigits() {
        assertThat(DocumentNumber.cnpjCheckDigits("112223330001")).isEqualTo("81");
        assertThat(DocumentNumber.cnpjCheckDigits("12ABC34501DE")).isEqualTo("35");
        assertThatThrownBy(() -> DocumentNumber.cnpjCheckDigits("123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("normalização remove pontuação e põe em maiúsculas")
    void normalize() {
        assertThat(DocumentNumber.normalize("529.982.247-25")).isEqualTo("52998224725");
        assertThat(DocumentNumber.normalize("12.abc.345/01de-35")).isEqualTo("12ABC34501DE35");
        assertThat(DocumentNumber.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("máscara: CPF mostra só o miolo; CNPJ mostra só o estabelecimento; inválido não é exibido")
    void mask() {
        assertThat(DocumentNumber.mask("529.982.247-25")).isEqualTo("***.982.247-**");
        assertThat(DocumentNumber.mask("11.222.333/0001-81")).isEqualTo("**.***.***/0001-**");
        assertThat(DocumentNumber.mask("12.ABC.345/01DE-35")).isEqualTo("**.***.***/01DE-**");
        assertThat(DocumentNumber.mask("123")).isEqualTo("documento inválido");
        assertThat(DocumentNumber.mask("529.982.247-25")).doesNotContain("529").doesNotContain("25");
    }

    @Test
    @DisplayName("formatação para exibição autorizada")
    void format() {
        assertThat(DocumentNumber.format("52998224725")).isEqualTo("529.982.247-25");
        assertThat(DocumentNumber.format("11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThat(DocumentNumber.format("12ABC34501DE35")).isEqualTo("12.ABC.345/01DE-35");
    }
}
