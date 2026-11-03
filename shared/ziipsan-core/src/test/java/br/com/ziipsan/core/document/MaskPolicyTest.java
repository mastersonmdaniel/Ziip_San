package br.com.ziipsan.core.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P0.11, ADR-0008: o teto de máscara é validado pelo sistema. Política acima do permitido é
 * recusada na construção, e nenhuma máscara aceita deixa menos de 6 dígitos-base desconhecidos
 * no CPF nem expõe dígito verificador (teste de propriedade do ADR).
 */
class MaskPolicyTest {

    @Test
    @DisplayName("padrão de fábrica: 3 dígitos-base visíveis, o teto do ADR-0008")
    void factoryDefaultIsTheCap() {
        assertThat(MaskPolicy.DEFAULT.visibleCpfBaseDigits()).isEqualTo(3);
        assertThat(MaskPolicy.MAX_VISIBLE_CPF_BASE_DIGITS).isEqualTo(3);
    }

    @ParameterizedTest(name = "teto acima do permitido recusado: {0} dígitos visíveis")
    @ValueSource(ints = {4, 5, 9, -1})
    @DisplayName("teto acima do permitido (ou negativo) é recusado")
    void aboveCapIsRefused(int visible) {
        assertThatThrownBy(() -> new MaskPolicy(visible))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teto de máscara");
    }

    @Test
    @DisplayName("propriedade: para toda política aceita, restam pelo menos 6 dígitos-base desconhecidos e nenhum verificador visível")
    void acceptedPoliciesKeepSixUnknownBaseDigitsAndHideCheckDigits() {
        String[] cpfs = {"529.982.247-25", "123.456.789-09", "111.444.777-35"};
        for (int visible = 0; visible <= MaskPolicy.MAX_VISIBLE_CPF_BASE_DIGITS; visible++) {
            MaskPolicy policy = new MaskPolicy(visible);
            for (String cpf : cpfs) {
                String masked = DocumentNumber.mask(cpf, policy);
                assertThat(MaskPolicy.violatesCap(masked))
                        .as("máscara %s da política %s viola o teto", masked, policy).isFalse();
                String compact = masked.replace(".", "").replace("-", "");
                long unknownBase = compact.substring(0, 9).chars().filter(c -> c == '*').count();
                assertThat(unknownBase).as("dígitos-base desconhecidos em %s", masked).isGreaterThanOrEqualTo(6);
                assertThat(compact.substring(9)).isEqualTo("**");
            }
        }
    }

    @Test
    @DisplayName("varredura: máscara antiga de 6 dígitos e máscara com verificador violam o teto")
    void sweepDetectsOldAndCheckDigitMasks() {
        assertThat(MaskPolicy.violatesCap("***.982.247-**")).isTrue(); // formato pré-P0.11
        assertThat(MaskPolicy.violatesCap("***.***.247-25")).isTrue(); // verificador visível
        assertThat(MaskPolicy.violatesCap("***.***.247-**")).isFalse();
        assertThat(MaskPolicy.violatesCap("**.***.***/0001-**")).isFalse();
        assertThat(MaskPolicy.violatesCap("**.***.***/0001-81")).isTrue(); // verificador do CNPJ
        assertThat(MaskPolicy.violatesCap("documento inválido")).isFalse();
        assertThat(MaskPolicy.violatesCap(null)).isFalse();
    }
}
