package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.3 - protocolo + OS pelo portal; representacao da P1.41 com escopo, validade e registro. */
class PortalRequestPolicyTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private final java.util.concurrent.atomic.AtomicReference<UUID> protocolRequester =
            new java.util.concurrent.atomic.AtomicReference<>();

    private PortalRequestPolicy policy() {
        return new PortalRequestPolicy(
                new PortalRequestPolicy.ProtocolPort() {
                    @Override public UUID openProtocol(UUID t, String spec, UUID unit, UUID req) {
                        protocolRequester.set(req);
                        return UUID.fromString("11111111-1111-1111-1111-111111111111");
                    }
                    @Override public String trackingStatus(UUID t, UUID protocol) { return "IN_PROGRESS"; }
                },
                new PortalRequestPolicy.ServiceOrderPort() {
                    @Override public UUID createInternalOrder(UUID t, UUID protocol, String type, UUID unit) {
                        return UUID.fromString("22222222-2222-2222-2222-222222222222");
                    }
                });
    }

    @Test
    void pedidoDoTitularGeraProtocoloOsERegistro() {
        var opened = policy().openRequest(TENANT, HOLDER, null, "CONTACT_CHANGE",
                "ALTERACAO_CONTATO", "CONTACT_CHANGE", UNIT, "a".repeat(64), NOW);
        assertThat(opened.protocolId()).isNotNull();
        assertThat(opened.serviceOrderId()).isNotNull();
        assertThat(opened.accessLog().result()).isEqualTo("FOUND");
        assertThat(opened.accessLog().documentBlindIndex()).hasSize(64);
        assertThat(policy().track(TENANT, opened.protocolId())).isEqualTo("IN_PROGRESS");
    }

    @Test
    void representanteDentroDoEscopoEVigenciaPassa() {
        var rep = new PortalRequestPolicy.Representation(UUID.randomUUID(), HOLDER,
                Set.of("CONTACT_CHANGE"), NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        var opened = policy().openRequest(TENANT, HOLDER, rep, "CONTACT_CHANGE",
                "ALTERACAO_CONTATO", "CONTACT_CHANGE", UNIT, null, NOW);
        assertThat(opened.accessLog().actorCustomerId()).isEqualTo(rep.representativeCustomerId());
        // O solicitante do protocolo e o representante, nao o titular.
        assertThat(protocolRequester.get()).isEqualTo(rep.representativeCustomerId());
    }

    @Test
    void representacaoDeOutroTitularERecusadaComRegistroDaTentativa() {
        var rep = new PortalRequestPolicy.Representation(UUID.randomUUID(), UUID.randomUUID(),
                Set.of("CONTACT_CHANGE"), NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        var ex = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> policy().openRequest(TENANT, HOLDER, rep, "CONTACT_CHANGE",
                        "ALTERACAO_CONTATO", "CONTACT_CHANGE", UNIT, null, NOW),
                PortalRequestPolicy.RepresentationDeniedException.class);
        assertThat(ex.accessLog().result()).isEqualTo("DENIED");
        assertThat(ex.accessLog().actorCustomerId()).isEqualTo(rep.representativeCustomerId());
    }

    @Test
    void representanteForaDoEscopoOuVencidoERecusadoComRegistroDaTentativa() {
        var wrongScope = new PortalRequestPolicy.Representation(UUID.randomUUID(), HOLDER,
                Set.of("SOCIAL_TARIFF"), NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        var ex = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> policy().openRequest(TENANT, HOLDER, wrongScope, "CONTACT_CHANGE",
                        "ALTERACAO_CONTATO", "CONTACT_CHANGE", UNIT, null, NOW),
                PortalRequestPolicy.RepresentationDeniedException.class);
        assertThat(ex.accessLog().result()).isEqualTo("DENIED");

        var expired = new PortalRequestPolicy.Representation(UUID.randomUUID(), HOLDER,
                Set.of("CONTACT_CHANGE"), NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        assertThatThrownBy(() -> policy().openRequest(TENANT, HOLDER, expired, "CONTACT_CHANGE",
                "ALTERACAO_CONTATO", "CONTACT_CHANGE", UNIT, null, NOW))
                .isInstanceOf(PortalRequestPolicy.RepresentationDeniedException.class);
    }
}
