package br.com.ziipsan.integration.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.26: versionamento do caminho — segmento /v{n}/ conhecido ou 404. */
class PublicApiAuthFilterVersionTest {

    @Test
    @DisplayName("versionOf extrai o número do segmento /v{n}/ e devolve nulo sem segmento")
    void versionOfParsing() {
        assertThat(PublicApiAuthFilter.versionOf("/api/v1/public-api/v1/properties")).isEqualTo(1);
        assertThat(PublicApiAuthFilter.versionOf("/api/v1/public-api/v99/bills")).isEqualTo(99);
        assertThat(PublicApiAuthFilter.versionOf("/api/v1/public-api/oauth/token")).isNull();
        assertThat(PublicApiAuthFilter.versionOf("/api/v1/public-api/vX/properties")).isNull();
    }
}
