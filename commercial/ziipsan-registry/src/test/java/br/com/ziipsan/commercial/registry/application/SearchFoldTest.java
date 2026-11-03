package br.com.ziipsan.commercial.registry.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static br.com.ziipsan.commercial.registry.application.JpqlQuery.containsFolded;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.foldAccents;
import static br.com.ziipsan.commercial.registry.application.JpqlQuery.startsWithFolded;
import static org.assertj.core.api.Assertions.assertThat;

/** P0.22: dobra de acento da busca por nome, sem banco. */
class SearchFoldTest {

    @Test
    @DisplayName("acentos somem dos dois lados: João -> joao, Ângela -> angela, ç -> c")
    void foldsAccents() {
        assertThat(foldAccents("João")).isEqualTo("joao");
        assertThat(foldAccents("Ângela")).isEqualTo("angela");
        assertThat(foldAccents("Conceição")).isEqualTo("conceicao");
        assertThat(foldAccents("Ação")).isEqualTo("acao");
    }

    @Test
    @DisplayName("containsFolded envolve em % e dobra; startsWithFolded só sufixa")
    void foldedPatterns() {
        assertThat(containsFolded("João")).isEqualTo("%joao%");
        assertThat(startsWithFolded("João")).isEqualTo("joao%");
    }

    @Test
    @DisplayName("curingas do usuário continuam escapados depois da dobra")
    void foldedEscapesWildcards() {
        assertThat(containsFolded("100%_!")).isEqualTo("%100!%!_!!%");
    }
}
