package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso46001CatalogTest {

    @Test @DisplayName("catalogo do aceite: 2019 publicada EM REVISAO e adotavel; edicao 2 projeto acompanhado")
    void trackedEditions() {
        var editions = Iso46001Catalog.trackedEditions();
        assertThat(editions).hasSize(2);
        var current = editions.get(0);
        assertThat(current.edition()).isEqualTo("2019");
        assertThat(current.status()).isEqualTo(Iso46001Catalog.EditionStatus.PUBLISHED_UNDER_REVISION);
        assertThat(current.adoptable()).isTrue();
        var draft = editions.get(1);
        assertThat(draft.status()).isEqualTo(Iso46001Catalog.EditionStatus.DRAFT_TRACKED);
        assertThat(draft.adoptable()).isFalse();
        assertThat(Iso46001Catalog.isAdoptable("2019")).isTrue();
        assertThat(Iso46001Catalog.isAdoptable("2 (projeto)")).isFalse();
    }

    @Test @DisplayName("projeto acompanhado nao admite requisito prescrito nem adocao")
    void draftGuards() {
        assertThatThrownBy(() -> new Iso46001Catalog.EditionEntry("2 (projeto)",
                Iso46001Catalog.EditionStatus.DRAFT_TRACKED, true, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NAO e adotavel");
        assertThatThrownBy(() -> new Iso46001Catalog.EditionEntry("2 (projeto)",
                Iso46001Catalog.EditionStatus.DRAFT_TRACKED, false, List.of("requisito inventado")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nenhum requisito");
        assertThat(Iso46001Catalog.trackedEditions())
                .allSatisfy(e -> assertThat(e.prescribedRequirements()).isEmpty());
    }
}
