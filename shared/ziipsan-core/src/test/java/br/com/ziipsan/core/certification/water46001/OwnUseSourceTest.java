package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnUseSourceTest {

    @Test @DisplayName("fonte = componentes declarados no balanco da ED-85, verbatim; total e a soma declarada")
    void componentsVerbatim() {
        var src = new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3, 120.0, 30.0, 500.0);
        assertThat(src.operationalM3()).isEqualTo(120.0);
        assertThat(src.emergencyM3()).isEqualTo(30.0);
        assertThat(src.exportedM3()).isEqualTo(500.0);
        assertThat(src.totalM3()).isEqualTo(650.0);
    }

    @Test @DisplayName("componente nao declarada no balanco recusa nomeando-a, nunca vira zero")
    void missingComponentRefuses() {
        assertThatThrownBy(() -> new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3,
                null, 30.0, 500.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operacionais");
        assertThatThrownBy(() -> new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3,
                120.0, null, 500.0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("emergenciais");
        assertThatThrownBy(() -> new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3,
                120.0, 30.0, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exportada");
    }

    @Test @DisplayName("sem referencia ao snapshot do balanco, nao ha fonte")
    void balanceRefRequired() {
        assertThatThrownBy(() -> new OwnUseSource(null, UUID.randomUUID(), 2026, 3, 1.0, 1.0, 1.0))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ED-85");
        assertThatThrownBy(() -> new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 13, 1.0, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3, -1.0, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
