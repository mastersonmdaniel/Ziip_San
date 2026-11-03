package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static br.com.ziipsan.commercial.registry.application.CustomerService.decodeCursor;
import static br.com.ziipsan.commercial.registry.application.CustomerService.encodeCursor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P0.22: cursor opaco da API — round-trip e rejeição de cursor malformado (400, não 500). */
class KeysetCursorCodecTest {

    @Test
    @DisplayName("round-trip preserva nome com espaço e acento dobrado pelo lowercase")
    void roundTrip() {
        UUID id = UUID.randomUUID();
        String encoded = encodeCursor(new KeysetCursor("maria da conceicao", id));
        assertThat(encoded).doesNotContain(" ");
        assertThat(decodeCursor(encoded)).isEqualTo(new KeysetCursor("maria da conceicao", id));
    }

    @Test
    @DisplayName("nulo ou branco = primeira página")
    void nullIsFirstPage() {
        assertThat(decodeCursor(null)).isNull();
        assertThat(decodeCursor("   ")).isNull();
    }

    @Test
    @DisplayName("base64 inválido, falta de ponto ou UUID inválido: 400 invalid-cursor")
    void malformedIs400() {
        assertThatThrownBy(() -> decodeCursor("!!!"))
                .isInstanceOf(ApiException.BadRequest.class).hasMessageContaining("cursor");
        assertThatThrownBy(() -> decodeCursor("abc"))
                .isInstanceOf(ApiException.BadRequest.class).hasMessageContaining("cursor");
        assertThatThrownBy(() -> decodeCursor(encodeCursor(new KeysetCursor("ana", UUID.randomUUID())) + "x"))
                .isInstanceOf(ApiException.BadRequest.class).hasMessageContaining("cursor");
    }
}
