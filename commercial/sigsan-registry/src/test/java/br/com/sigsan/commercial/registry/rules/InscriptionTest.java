package br.com.sigsan.commercial.registry.rules;

import br.com.sigsan.commercial.registry.rules.Inscription.Layout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InscriptionTest {

    @Test
    @DisplayName("formato padrão LLL.SSS.QQQ.LLLL.SSS com zeros à esquerda")
    void defaultFormat() {
        assertThat(new Inscription(1, 2, 3, 4, 5).format()).isEqualTo("001.002.003.0004.005");
        assertThat(new Inscription(999, 999, 999, 9999, 999).format()).isEqualTo("999.999.999.9999.999");
        assertThat(new Inscription(0, 0, 0, 0, 0).format()).isEqualTo("000.000.000.0000.000");
    }

    @Test
    @DisplayName("parse aceita com e sem pontos e devolve inteiros")
    void parse() {
        Inscription a = Inscription.parse("001.002.003.0004.005");
        Inscription b = Inscription.parse("00100200300040" + "05");
        assertThat(a).isEqualTo(new Inscription(1, 2, 3, 4, 5));
        assertThat(b).isEqualTo(a);
    }

    @Test
    @DisplayName("format e parse são inversos")
    void roundTrip() {
        Inscription i = new Inscription(12, 345, 6, 7890, 1);
        assertThat(Inscription.parse(i.format())).isEqualTo(i);
    }

    @Test
    @DisplayName("a largura é configurável por tenant")
    void customLayout() {
        Layout layout = new Layout(2, 2, 4, 5, 2);
        Inscription i = new Inscription(7, 8, 9, 10, 1);
        assertThat(i.format(layout)).isEqualTo("07.08.0009.00010.01");
        assertThat(Inscription.parse("07.08.0009.00010.01", layout)).isEqualTo(i);
        assertThat(layout.totalDigits()).isEqualTo(15);
    }

    @Test
    @DisplayName("parte que não cabe na largura é erro, nunca truncada")
    void overflowIsAnError() {
        assertThatThrownBy(() -> new Inscription(1000, 1, 1, 1, 1).format())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("localidade");
        assertThatThrownBy(() -> new Inscription(1, 1, 1, 10000, 1).format())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lote");
    }

    @Test
    @DisplayName("parse recusa quantidade errada de dígitos, letras e nulo")
    void parseRejectsBadInput() {
        assertThatThrownBy(() -> Inscription.parse("001.002.003.0004.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inscription.parse("001.002.003.0004.00A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Inscription.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valores negativos e larguras inválidas são recusados")
    void invalidConstruction() {
        assertThatThrownBy(() -> new Inscription(-1, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Layout(0, 3, 3, 4, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Layout(3, 3, 3, 10, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
