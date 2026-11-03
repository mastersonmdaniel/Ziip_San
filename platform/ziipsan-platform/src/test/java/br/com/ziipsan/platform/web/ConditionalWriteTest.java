package br.com.ziipsan.platform.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P1.11: formato do ETag e comparação do If-Match (sem banco). */
class ConditionalWriteTest {

    @Test
    @DisplayName("ETag é a versão entre aspas; If-Match exato, curinga e fraco passam; outro falha")
    void etagAndIfMatch() {
        assertThat(ConditionalWrite.etagFor(3)).isEqualTo("\"v3\"");
        assertThat(ConditionalWrite.matches("\"v3\"", 3)).isTrue();
        assertThat(ConditionalWrite.matches("*", 3)).isTrue();
        assertThat(ConditionalWrite.matches("W/\"v3\"", 3)).isTrue();
        assertThat(ConditionalWrite.matches("\"v2\"", 3)).isFalse();
        assertThat(ConditionalWrite.matches(null, 3)).isFalse();
        assertThat(ConditionalWrite.matches("lixo", 3)).isFalse();
    }
}
