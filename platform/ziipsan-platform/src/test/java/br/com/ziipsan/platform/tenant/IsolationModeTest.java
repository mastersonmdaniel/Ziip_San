package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** P4.6: o enum espelha a lista fechada da core V7 (SCHEMA, SHARED, DEDICATED). */
class IsolationModeTest {

    @Test
    @DisplayName("parse: em branco/ausente vale o padrao SCHEMA; texto valido e normalizado")
    void parseDefaultsAndNormalizes() {
        assertThat(IsolationMode.parse(null)).isEqualTo(IsolationMode.SCHEMA);
        assertThat(IsolationMode.parse("")).isEqualTo(IsolationMode.SCHEMA);
        assertThat(IsolationMode.parse(" shared ")).isEqualTo(IsolationMode.SHARED);
        assertThat(IsolationMode.parse("DEDICATED")).isEqualTo(IsolationMode.DEDICATED);
        assertThat(IsolationMode.SCHEMA.columnValue()).isEqualTo("SCHEMA");
    }

    @Test
    @DisplayName("parse: modo inventado e recusado antes do banco, nomeando o valor")
    void parseRejectsInventedMode() {
        assertThatThrownBy(() -> IsolationMode.parse("PARTIAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PARTIAL");
    }

    @Test
    @DisplayName("sharesSchema/dedicatedDatabase: so SHARED partilha e so DEDICATED e banco proprio")
    void modePredicates() {
        assertThat(IsolationMode.SHARED.sharesSchema()).isTrue();
        assertThat(IsolationMode.SCHEMA.sharesSchema()).isFalse();
        assertThat(IsolationMode.DEDICATED.dedicatedDatabase()).isTrue();
        assertThat(IsolationMode.SHARED.dedicatedDatabase()).isFalse();
    }
}
