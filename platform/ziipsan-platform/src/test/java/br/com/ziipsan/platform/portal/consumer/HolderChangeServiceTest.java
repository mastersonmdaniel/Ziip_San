package br.com.ziipsan.platform.portal.consumer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.20 - troca de titularidade revoga vinculos e invalida o codigo. */
class HolderChangeServiceTest {
    @Test void revogaTodosEInvalidaCodigo() {
        AtomicInteger revoked = new AtomicInteger();
        AtomicBoolean invalidated = new AtomicBoolean();
        var svc = new HolderChangeService(new HolderChangeService.RevocationPort() {
            @Override public int revokeAllLinksOfUnit(UUID t, UUID u, String reason, Instant at) {
                assertThat(reason).isEqualTo(HolderChangeService.REASON_HOLDER_CHANGE);
                revoked.set(3); return 3;
            }
            @Override public void invalidateLinkingCode(UUID t, UUID u, Instant at) { invalidated.set(true); }
        });
        assertThat(svc.onHolderChange(UUID.randomUUID(), UUID.randomUUID())).isEqualTo(3);
        assertThat(invalidated).isTrue();
    }
}
