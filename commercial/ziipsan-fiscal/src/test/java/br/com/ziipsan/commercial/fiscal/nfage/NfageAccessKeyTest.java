package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vetores da chave de acesso do modelo 75 com o DV calculado À MÃO (módulo 11, pesos
 * 2..9 da direita para a esquerda) e fixo como literal — o DV esperado NUNCA é gerado
 * pela classe sob teste (lição da revisão cruzada da P3.6: teste circular passa mesmo
 * com o algoritmo errado). Conferência independente registrada em cada vetor.
 */
class NfageAccessKeyTest {

    // Vetor 1: cUF 42, AAMM 2612, CNPJ 11222333000181, mod 75, série 001, nNF 000000123,
    // tpEmis 1, cNF 87654321. Soma ponderada = 590; 590 % 11 = 7; DV = 11 - 7 = 4.
    private static final String KEY_1 = "42261211222333000181750010000001231876543214";

    // Vetor 2: cUF 35, AAMM 2701, CNPJ 34028316000103, mod 75, série 002, nNF 000987654,
    // tpEmis 1, cNF 12345678. Soma ponderada = 757; 757 % 11 = 9; DV = 11 - 9 = 2.
    private static final String KEY_2 = "35270134028316000103750020009876541123456782";

    // Vetor 3: cUF 42, AAMM 2612, CNPJ 11222333000181, mod 75, série 001, nNF 000000001,
    // tpEmis 1, cNF 00000001. Soma ponderada = 333; 333 % 11 = 3; DV = 11 - 3 = 8.
    private static final String KEY_3 = "42261211222333000181750010000000011000000018";

    @Test
    @DisplayName("vetores com DV calculado à mão: a chave montada é exatamente o literal esperado")
    void handComputedVectors() {
        assertThat(NfageAccessKey.build(42, YearMonth.of(2026, 12), "11222333000181",
                1, 123, 1, "87654321")).isEqualTo(KEY_1);
        assertThat(NfageAccessKey.build(35, YearMonth.of(2027, 1), "34028316000103",
                2, 987654, 1, "12345678")).isEqualTo(KEY_2);
        assertThat(NfageAccessKey.build(42, YearMonth.of(2026, 12), "11.222.333/0001-81",
                1, 1, 1, "00000001")).isEqualTo(KEY_3);
        assertThat(NfageAccessKey.checkDigit(KEY_1.substring(0, 43))).isEqualTo(4);
        assertThat(NfageAccessKey.checkDigit(KEY_2.substring(0, 43))).isEqualTo(2);
        assertThat(NfageAccessKey.checkDigit(KEY_3.substring(0, 43))).isEqualTo(8);
    }

    @Test
    @DisplayName("parse devolve cada campo do leiaute e isValid aceita os vetores")
    void parseRoundTrip() {
        assertThat(NfageAccessKey.isValid(KEY_1)).isTrue();
        assertThat(NfageAccessKey.isValid(KEY_2)).isTrue();
        NfageAccessKey.Parsed p = NfageAccessKey.parse(KEY_1);
        assertThat(p.ufCode()).isEqualTo(42);
        assertThat(p.competence()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(p.issuerCnpj()).isEqualTo("11222333000181");
        assertThat(p.series()).isEqualTo(1);
        assertThat(p.number()).isEqualTo(123L);
        assertThat(p.emissionType()).isEqualTo(1);
        assertThat(p.numericCode()).isEqualTo("87654321");
        assertThat(p.checkDigit()).isEqualTo(4);
    }

    @Test
    @DisplayName("DV errado e modelo diferente de 75 são inválidos (modelo 65 com DV certo também é recusado)")
    void invalidKeysRejected() {
        // Vetor 1 com o DV trocado de 4 para 5.
        assertThat(NfageAccessKey.isValid("42261211222333000181750010000001231876543215")).isFalse();
        // Mesmos campos do vetor 1, mas modelo 65 e DV correto DESSE leiaute
        // (soma ponderada 582; 582 % 11 = 10; DV = 1): recusa é pelo modelo, não pelo DV.
        assertThat(NfageAccessKey.isValid("42261211222333000181650010000001231876543211")).isFalse();
        assertThat(NfageAccessKey.isValid("123")).isFalse();
        assertThat(NfageAccessKey.isValid(null)).isFalse();
        assertThatThrownBy(() -> NfageAccessKey.parse("42261211222333000181750010000001231876543215"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CNPJ alfanumérico não compõe a chave (condicional ao MOC) e CNPJ inválido é erro explícito")
    void issuerDocumentRules() {
        // 12ABC34501DE35 é CNPJ alfanumérico VÁLIDO pela P1.39 — a recusa aqui é da
        // chave numérica, não da validação do documento (ver javadoc da classe).
        assertThatThrownBy(() -> NfageAccessKey.build(42, YearMonth.of(2026, 12), "12ABC34501DE35",
                1, 123, 1, "87654321"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alfanumérico");
        assertThatThrownBy(() -> NfageAccessKey.build(42, YearMonth.of(2026, 12), "11222333000182",
                1, 123, 1, "87654321"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inválido");
    }

    @Test
    @DisplayName("campo fora do leiaute é erro, nunca ajuste silencioso")
    void layoutBoundsEnforced() {
        assertThatThrownBy(() -> NfageAccessKey.build(42, YearMonth.of(2026, 12), "11222333000181",
                1000, 123, 1, "87654321")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NfageAccessKey.build(42, YearMonth.of(2026, 12), "11222333000181",
                1, 0, 1, "87654321")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NfageAccessKey.build(42, YearMonth.of(2026, 12), "11222333000181",
                1, 123, 1, "123")).isInstanceOf(IllegalArgumentException.class);
    }
}
