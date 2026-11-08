package br.com.ziipsan.platform.clientapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushAndProvisioningTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    @Test
    @DisplayName("push valido carrega so codigos: alerta da ED-17, chaves de modelo e tela permitida")
    void pushWithoutPersonalDataPasses() {
        var payload = PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "telemetry.continuous-consumption",
                "titleKey", "push.continuous.title",
                "bodyKey", "push.continuous.body",
                "deepLink", "/consumo"));
        assertThat(payload.alertCode()).isEqualTo("telemetry.continuous-consumption");
        assertThat(payload.deepLink()).isEqualTo("/consumo");
    }

    @Test
    @DisplayName("push com CPF, e-mail, telefone ou valor monetario em qualquer chave e recusado")
    void pushWithPersonalDataRejected() {
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "bodyKey", "Conta de 123.456.789-09 disponivel")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "bodyKey", "Valor R$ 152,30")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "titleKey", "aviso@exemplo.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "titleKey", "+55 47 99999-0000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("push com chave livre, alerta desconhecido ou deep link com identificador e recusado")
    void pushOutsideContractRejected() {
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "customerName", "Fulano")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of("alertCode", "marketing.promo")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "deepLink", "/conta/00000000-0000-0000-0000-0000000000a3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D1: nome e endereco em texto livre em titleKey/bodyKey sao recusados (sonda da revisao)")
    void pushFreeTextNameAndAddressRejected() {
        // Vetores literais da sonda do revisor: PASSAVAM na versao original.
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "titleKey", "Ola Maria Silva, sua conta venceu")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "bodyKey", "Rua das Flores 123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "bodyKey", "Ola Maria Silva, sua conta venceu")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "outage.scheduled", "titleKey", "Rua das Flores 123")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D1: chave de modelo fora do conjunto fechado ou de outro alerta e recusada; a do proprio alerta passa")
    void pushTemplateKeyClosedSet() {
        // Formato plausivel, mas fora do conjunto fechado:
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "titleKey", "push.qualquer.title")))
                .isInstanceOf(IllegalArgumentException.class);
        // Chave de modelo valida, mas de OUTRO alerta:
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "titleKey", "push.peak.title")))
                .isInstanceOf(IllegalArgumentException.class);
        // title no lugar do body (e vice-versa):
        assertThatThrownBy(() -> PushPayloadSanitizer.sanitize(Map.of(
                "alertCode", "bill.available", "bodyKey", "push.bill.title")))
                .isInstanceOf(IllegalArgumentException.class);
        // O par fechado do proprio alerta passa, para todos os alertas:
        for (var e : PushPayloadSanitizer.TEMPLATE_STEM_BY_ALERT_CODE.entrySet()) {
            var p = PushPayloadSanitizer.sanitize(Map.of(
                    "alertCode", e.getKey(),
                    "titleKey", "push." + e.getValue() + ".title",
                    "bodyKey", "push." + e.getValue() + ".body"));
            assertThat(p.titleKey()).isEqualTo("push." + e.getValue() + ".title");
            assertThat(p.bodyKey()).isEqualTo("push." + e.getValue() + ".body");
        }
        assertThat(PushPayloadSanitizer.ALLOWED_TEMPLATE_KEYS).hasSize(12);
    }

    @Test
    @DisplayName("client do pipeline: publico, so authorization code, PKCE S256, redirect unico https sem curinga")
    void keycloakClientSpec() {
        var config = new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.samae-exemplo", "Saneamento Exemplo",
                "ziipsan-cliente-samae-exemplo", null, ClientAppStatus.DRAFT, null, null,
                "app.exemplo.invalid", "https://api.exemplo.invalid", null, "#00548F");
        var spec = KeycloakClientProvisioning.specFor(config);
        assertThat(spec.realm()).isEqualTo("ziipsan-portal");
        assertThat(spec.publicClient()).isTrue();
        assertThat(spec.implicitFlowEnabled()).isFalse();
        assertThat(spec.directAccessGrantsEnabled()).isFalse();
        assertThat(spec.pkceCodeChallengeMethod()).isEqualTo("S256");
        assertThat(spec.redirectUris()).containsExactly("https://app.exemplo.invalid/auth/callback");
        assertThatThrownBy(() -> new KeycloakClientProvisioning.ClientSpec(
                "ziipsan-portal", "c", true, true, false, false, "S256",
                java.util.List.of("https://app.exemplo.invalid/*"), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeycloakClientProvisioning.ClientSpec(
                "ziipsan", "c", true, true, false, false, "S256",
                java.util.List.of("https://app.exemplo.invalid/auth/callback"), java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tempo quase real: curva segue o parametro da ED-17, polling tem piso de 60s e leitura velha e stale")
    void realtimePolicy() {
        ClientAppParameters off = new ClientAppParameters() {
            @Override public Optional<String> androidStoreAccount() { return Optional.empty(); }
            @Override public Optional<String> iosStoreAccount() { return Optional.empty(); }
            @Override public boolean hourlyProfileVisibleToCustomer(UUID tenantId) { return false; }
        };
        var policy = new ConsumerRealtimePolicy(off);
        assertThat(policy.hourlyCurveVisible(TENANT)).isFalse();
        assertThat(ConsumerRealtimePolicy.effectivePollInterval(Duration.ofSeconds(5)))
                .isEqualTo(Duration.ofSeconds(60));
        assertThat(ConsumerRealtimePolicy.effectivePollInterval(Duration.ofMinutes(5)))
                .isEqualTo(Duration.ofMinutes(5));
        var now = OffsetDateTime.parse("2026-11-09T12:00:00Z");
        assertThat(ConsumerRealtimePolicy.isStale(now.minusHours(3), now)).isTrue();
        assertThat(ConsumerRealtimePolicy.isStale(now.minusMinutes(30), now)).isFalse();
    }

    @Test
    @DisplayName("portao SG-782: checklist completo libera; qualquer item falso bloqueia nomeando o item")
    void accessibilityGate() {
        var ok = new AccessibilityGate.Checklist(true, true, true, true, true, true, true);
        assertThat(ok.releasable()).isTrue();
        AccessibilityGate.assertReleasable(ok);
        var bad = new AccessibilityGate.Checklist(true, true, true, false, true, true, true);
        assertThat(bad.releasable()).isFalse();
        assertThatThrownBy(() -> AccessibilityGate.assertReleasable(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alvos de toque");
        assertThatThrownBy(() -> AccessibilityGate.assertReleasable(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
