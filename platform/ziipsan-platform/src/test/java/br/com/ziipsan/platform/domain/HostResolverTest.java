package br.com.ziipsan.platform.domain;

import br.com.ziipsan.platform.domain.HostResolver.Kind;
import br.com.ziipsan.platform.domain.HostResolver.Resolution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2.27 — resolução do tenant pelo host antes do login: o domínio
 * próprio vence o slug; sem isso, um host de domínio próprio cairia
 * no slug de outro tenant.
 */
class HostResolverTest {

    private static final UUID CUSTOM_TENANT = UUID.randomUUID();
    private static final UUID SLUG_TENANT = UUID.randomUUID();

    private static Resolution resolve(String host) {
        Map<String, UUID> custom = Map.of("portal.cliente.example", CUSTOM_TENANT,
                "app.cliente.example", CUSTOM_TENANT);
        Map<String, UUID> slugs = Map.of("portal", SLUG_TENANT, "acme", SLUG_TENANT);
        return HostResolver.resolve(host,
                h -> Optional.ofNullable(custom.get(h)),
                s -> Optional.ofNullable(slugs.get(s)));
    }

    @Test
    @DisplayName("domínio próprio vence o slug de mesmo primeiro rótulo")
    void customDomainBeatsSlug() {
        Resolution resolution = resolve("portal.cliente.example");
        assertThat(resolution.kind()).isEqualTo(Kind.CUSTOM_DOMAIN);
        assertThat(resolution.tenantId()).isEqualTo(CUSTOM_TENANT);
        assertThat(resolution.identifier()).isEqualTo("portal.cliente.example");
    }

    @Test
    @DisplayName("sem domínio próprio, o slug resolve; sem slug, nenhum tenant")
    void slugFallbackAndNone() {
        Resolution slug = resolve("acme.example.test");
        assertThat(slug.kind()).isEqualTo(Kind.SUBDOMAIN);
        assertThat(slug.tenantId()).isEqualTo(SLUG_TENANT);
        assertThat(resolve("desconhecido.example.test").kind()).isEqualTo(Kind.NONE);
    }

    @Test
    @DisplayName("porta, maiúsculas e ponto final da raiz são normalizados; localhost, IP e reservado não resolvem")
    void normalizationAndNonHosts() {
        assertThat(resolve("APP.Cliente.EXAMPLE:8443.").kind()).isEqualTo(Kind.CUSTOM_DOMAIN);
        assertThat(resolve("localhost").kind()).isEqualTo(Kind.NONE);
        assertThat(resolve("127.0.0.1").kind()).isEqualTo(Kind.NONE);
        assertThat(resolve("[::1]:8080").kind()).isEqualTo(Kind.NONE);
        assertThat(resolve("app.example.test").kind()).isEqualTo(Kind.NONE);
        assertThat(resolve(null).kind()).isEqualTo(Kind.NONE);
        assertThat(resolve("  ").kind()).isEqualTo(Kind.NONE);
    }
}
