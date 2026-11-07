package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fato normativo da obrigatoriedade (Ato Conjunto RFB/CGIBS 4/2026): fatos geradores a
 * partir de 01/12/2026 exigem NFAg-e; fronteira conferida dia a dia.
 */
class NfageObrigatoriedadeTest {

    @Test
    @DisplayName("constantes: início em 01/12/2026 com o dispositivo do Ato Conjunto 4/2026")
    void normativeFact() {
        assertThat(NfageObrigatoriedade.INICIO_OBRIGATORIEDADE).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(NfageObrigatoriedade.DISPOSITIVO).isEqualTo("Ato Conjunto RFB/CGIBS nº 4/2026");
    }

    @Test
    @DisplayName("fronteira: 30/11/2026 não exige; 01/12/2026 exige")
    void boundary() {
        assertThat(NfageObrigatoriedade.exigeNfage(LocalDate.of(2026, 11, 30))).isFalse();
        assertThat(NfageObrigatoriedade.exigeNfage(LocalDate.of(2026, 12, 1))).isTrue();
        assertThat(NfageObrigatoriedade.exigeNfage(LocalDate.of(2026, 12, 2))).isTrue();
        assertThat(NfageObrigatoriedade.exigeNfage(LocalDate.of(2027, 1, 1))).isTrue();
    }

    @Test
    @DisplayName("fato gerador sem data é erro explícito, nunca 'não obrigatório'")
    void missingDateIsAnError() {
        assertThatThrownBy(() -> NfageObrigatoriedade.exigeNfage(null))
                .isInstanceOf(NullPointerException.class);
    }
}
