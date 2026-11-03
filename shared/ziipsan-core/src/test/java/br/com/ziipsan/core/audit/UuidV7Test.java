package br.com.ziipsan.core.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    @DisplayName("v7: versao 7, variante 2 e timestamp embutido proximo de agora")
    void versionVariantAndTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.generate();
        long after = System.currentTimeMillis();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.isVersion7(id)).isTrue();
        assertThat(UuidV7.timestampMillis(id)).isBetween(before, after);
    }

    @Test
    @DisplayName("v7: 10 mil ids sem colisao")
    void unique() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7.generate())).isTrue();
        }
    }

    @Test
    @DisplayName("v7: timestamps embutidos nao voltam no tempo na sequencia gerada")
    void timestampsDoNotGoBackwards() {
        long previous = -1L;
        for (int i = 0; i < 1_000; i++) {
            long ts = UuidV7.timestampMillis(UuidV7.generate());
            assertThat(ts).isGreaterThanOrEqualTo(previous);
            previous = ts;
        }
    }

    @Test
    @DisplayName("nao-v7: timestamp devolve -1 e isVersion7 devolve false")
    void nonV7() {
        UUID v4 = UUID.randomUUID();
        assertThat(UuidV7.isVersion7(v4)).isFalse();
        assertThat(UuidV7.timestampMillis(v4)).isEqualTo(-1L);
        assertThat(UuidV7.isVersion7(null)).isFalse();
    }
}
