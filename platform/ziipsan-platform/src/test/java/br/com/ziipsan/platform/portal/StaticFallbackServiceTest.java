package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.3/PLT-40 - contingencia estatica: objeto cifrado no fechamento e
 * leitura por link assinado SEM nenhuma API de faturamento (api derrubada
 * por construcao: o servico so conhece cipher, signer e store).
 */
class StaticFallbackServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private static final class FakeStore implements StaticFallbackService.FallbackStore {
        final Map<String, StaticFallbackService.FallbackObject> map = new HashMap<>();
        @Override public void put(StaticFallbackService.FallbackObject o) { map.put(o.tenantId() + ":" + o.billId(), o); }
        @Override public Optional<StaticFallbackService.FallbackObject> get(UUID t, UUID b) {
            return Optional.ofNullable(map.get(t + ":" + b));
        }
    }

    private static final StaticFallbackService.LinkSigner SIGNER = new StaticFallbackService.LinkSigner() {
        @Override public String sign(String payload) { return Integer.toHexString(("segredo-teste" + payload).hashCode()); }
        @Override public boolean verify(String payload, String signature) { return sign(payload).equals(signature); }
    };

    @Test
    void objetoDoFechamentoECifradoENaoContemPixEmClaro() {
        var store = new FakeStore();
        var service = new StaticFallbackService(PortalTestSupport.cipher(), SIGNER, store);
        var object = service.buildAtClosing(TENANT, UUID.randomUUID(), UUID.randomUUID(),
                "JVBERi0xLjQK", "pix-copia-e-cola-secreto", NOW);
        assertThat(object.ciphertext()).doesNotContain("pix-copia-e-cola-secreto");
        assertThat(store.map).hasSize(1);
    }

    @Test
    void linkAssinadoServeComApiDerrubadaEExpiraERevoga() {
        var store = new FakeStore();
        var service = new StaticFallbackService(PortalTestSupport.cipher(), SIGNER, store);
        UUID bill = UUID.randomUUID();
        var object = service.buildAtClosing(TENANT, bill, UUID.randomUUID(), "JVBERi0xLjQK", "pix-123", NOW);
        String link = service.issueSignedLink(object, true, NOW, Duration.ofMinutes(15));
        Map<String, Object> content = service.openSignedLink(link, Set.of(), NOW.plusSeconds(60));
        assertThat(content.get("pixCopyPaste")).isEqualTo("pix-123");
        // Expirado.
        assertThatThrownBy(() -> service.openSignedLink(link, Set.of(), NOW.plus(Duration.ofMinutes(16))))
                .isInstanceOf(ApiException.Forbidden.class);
        // Revogado.
        assertThatThrownBy(() -> service.openSignedLink(link, Set.of(bill), NOW.plusSeconds(60)))
                .isInstanceOf(ApiException.Forbidden.class);
        // Assinatura adulterada.
        assertThatThrownBy(() -> service.openSignedLink(link + "x", Set.of(), NOW.plusSeconds(60)))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void semContatoVerificadoNaoHaLink() {
        var store = new FakeStore();
        var service = new StaticFallbackService(PortalTestSupport.cipher(), SIGNER, store);
        var object = service.buildAtClosing(TENANT, UUID.randomUUID(), UUID.randomUUID(), "QQ==", "pix", NOW);
        assertThatThrownBy(() -> service.issueSignedLink(object, false, NOW, Duration.ofMinutes(15)))
                .isInstanceOf(ApiException.Forbidden.class);
    }
}
