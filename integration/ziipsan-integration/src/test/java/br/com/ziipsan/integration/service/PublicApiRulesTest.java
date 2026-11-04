package br.com.ziipsan.integration.service;

import br.com.ziipsan.integration.PublicApiScopes;
import br.com.ziipsan.integration.PublicApiVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.26: constantes e regras puras da API pública (sem banco). */
class PublicApiRulesTest {

    @Test
    @DisplayName("os escopos da v1 são exatamente os cinco do plano, no catálogo recurso:ação")
    void scopesAreExactlyTheV1Set() {
        assertThat(PublicApiScopes.ALL).containsExactlyInAnyOrder(
                "properties:read", "bills:read", "payments:read",
                "service-orders:read", "service-orders:write");
        assertThat(PublicApiScopes.PLATFORM_RATE_LIMIT_PER_MINUTE).isEqualTo(1000);
        assertThat(PublicApiScopes.ACCESS_TOKEN_TTL_SECONDS).isEqualTo(3600);
        assertThat(PublicApiScopes.SECRET_OVERLAP_HOURS).isEqualTo(24);
        assertThat(PublicApiScopes.DELIVERY_RETRY_DAYS).isEqualTo(30);
        assertThat(PublicApiScopes.MAX_CONSECUTIVE_FAILURES).isEqualTo(5);
    }

    @Test
    @DisplayName("a v1 é conhecida e corrente; nenhuma versão está descontinuada hoje")
    void versionsCurrentOnly() {
        var versions = PublicApiVersions.current();
        assertThat(versions.isKnown(1)).isTrue();
        assertThat(versions.isKnown(2)).isFalse();
        assertThat(versions.isDeprecated(1)).isFalse();
        assertThat(versions.sunsetFor(1)).isEmpty();
        assertThat(versions.knownVersions()).containsExactly(1);
    }

    @Test
    @DisplayName("versão descontinuada carrega Sunset e continua conhecida até a data")
    void deprecatedVersionCarriesSunset() {
        var versions = new PublicApiVersions(java.util.Map.of(1, "Sat, 01 Jan 2028 00:00:00 GMT"));
        assertThat(versions.isDeprecated(1)).isTrue();
        assertThat(versions.sunsetFor(1)).contains("Sat, 01 Jan 2028 00:00:00 GMT");
    }

    @Test
    @DisplayName("o limite efetivo é o menor entre cliente, tenant e teto da plataforma")
    void effectiveRateLimitIsTheMinimum() {
        assertThat(ApiClientService.effectiveRateLimit(null, null)).isEqualTo(1000);
        assertThat(ApiClientService.effectiveRateLimit(100, null)).isEqualTo(100);
        assertThat(ApiClientService.effectiveRateLimit(null, 50)).isEqualTo(50);
        assertThat(ApiClientService.effectiveRateLimit(100, 50)).isEqualTo(50);
        assertThat(ApiClientService.effectiveRateLimit(5000, 5000)).isEqualTo(1000);
    }

    @Test
    @DisplayName("a validação de allowlist aceita IP e CIDR e recusa o resto")
    void cidrOrIpValidation() {
        assertThat(ApiClientService.isCidrOrIp("203.0.113.5")).isTrue();
        assertThat(ApiClientService.isCidrOrIp("198.51.100.0/24")).isTrue();
        assertThat(ApiClientService.isCidrOrIp("198.51.100.0/33")).isFalse();
        assertThat(ApiClientService.isCidrOrIp("nao-um-ip")).isFalse();
        assertThat(ApiClientService.isCidrOrIp(null)).isFalse();
    }

    @Test
    @DisplayName("o hash do segredo é determinístico e a comparação é exata")
    void secretHashRoundTrip() {
        String hash = SecretHash.hash("ziip_sk_teste");
        assertThat(SecretHash.hash("ziip_sk_teste")).isEqualTo(hash);
        assertThat(SecretHash.matches("ziip_sk_teste", hash)).isTrue();
        assertThat(SecretHash.matches("ziip_sk_outro", hash)).isFalse();
        assertThat(SecretHash.matches(null, hash)).isFalse();
    }

    @Test
    @DisplayName("o segredo anterior só vale dentro da janela de sobreposição")
    void previousSecretWindow() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        assertThat(SecretHash.previousStillValid("hash", now.plusSeconds(3600), now)).isTrue();
        assertThat(SecretHash.previousStillValid("hash", now.minusSeconds(1), now)).isFalse();
        assertThat(SecretHash.previousStillValid(null, now.plusSeconds(3600), now)).isFalse();
    }

    @Test
    @DisplayName("o token opaco embute o tenant e o tenant é recuperável dele")
    void tokenEmbedsTenant() {
        UUID tenant = UUID.randomUUID();
        String token = ApiTokenService.generateToken(tenant);
        assertThat(token).startsWith("ziip_at_");
        assertThat(ApiTokenService.tokenTenant(token)).isEqualTo(tenant);
        assertThat(ApiTokenService.tokenTenant("ziip_at_invalido")).isNull();
        assertThat(ApiTokenService.tokenTenant("Bearer abc")).isNull();
        assertThat(ApiTokenService.tokenTenant(null)).isNull();
    }
}
