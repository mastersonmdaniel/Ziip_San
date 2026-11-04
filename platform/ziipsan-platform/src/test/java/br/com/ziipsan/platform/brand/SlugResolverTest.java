package br.com.ziipsan.platform.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.19 — extração do slug do tenant a partir do host (MARCA-E-TEMAS §3.3).
 */
class SlugResolverTest {

    @Test
    @DisplayName("primeiro rótulo é o slug; porta e maiúsculas são normalizadas")
    void firstLabelIsSlug() {
        assertThat(SlugResolver.extractSlug("acme.example.test")).contains("acme");
        assertThat(SlugResolver.extractSlug("acme.example.test:8443")).contains("acme");
        assertThat(SlugResolver.extractSlug("ACME.Example.TEST")).contains("acme");
        assertThat(SlugResolver.extractSlug("acme.example.test.")).contains("acme");
    }

    @Test
    @DisplayName("rótulos reservados nunca são slugs")
    void reservedLabelsAreNotSlugs() {
        assertThat(SlugResolver.extractSlug("app.example.test")).isEmpty();
        assertThat(SlugResolver.extractSlug("www.example.test")).isEmpty();
        assertThat(SlugResolver.extractSlug("APP.example.test")).isEmpty();
    }

    @Test
    @DisplayName("localhost, IP e host de um só rótulo não têm slug")
    void localhostIpAndSingleLabelHaveNoSlug() {
        assertThat(SlugResolver.extractSlug("localhost")).isEmpty();
        assertThat(SlugResolver.extractSlug("localhost:8080")).isEmpty();
        assertThat(SlugResolver.extractSlug("127.0.0.1")).isEmpty();
        assertThat(SlugResolver.extractSlug("127.0.0.1:5432")).isEmpty();
        assertThat(SlugResolver.extractSlug("[::1]:8080")).isEmpty();
        assertThat(SlugResolver.extractSlug("ziipsan")).isEmpty();
        assertThat(SlugResolver.extractSlug(null)).isEmpty();
        assertThat(SlugResolver.extractSlug("  ")).isEmpty();
    }

    @Test
    @DisplayName("rótulo inválido não é slug; ápice vira candidato (o miss cai na marca do produto)")
    void invalidLabelIsNotSlug() {
        assertThat(SlugResolver.extractSlug("-bad.example.test")).isEmpty();
        assertThat(SlugResolver.extractSlug("bad-.example.test")).isEmpty();
        // Sem base de subdomínio configurada, o ápice não se distingue de um
        // subdomínio: o primeiro rótulo é candidato e, sem tenant, o
        // controller responde a marca do produto com o mesmo status.
        assertThat(SlugResolver.extractSlug("example.test")).contains("example");
    }
}
