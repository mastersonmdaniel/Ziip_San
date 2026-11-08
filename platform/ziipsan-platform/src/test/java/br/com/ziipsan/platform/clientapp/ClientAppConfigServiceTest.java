package br.com.ziipsan.platform.clientapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientAppConfigServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static ClientAppParameters params(Optional<String> android, Optional<String> ios, boolean hourly) {
        return new ClientAppParameters() {
            @Override public Optional<String> androidStoreAccount() { return android; }
            @Override public Optional<String> iosStoreAccount() { return ios; }
            @Override public boolean hourlyProfileVisibleToCustomer(UUID tenantId) { return hourly; }
        };
    }

    private static final AccessibilityGate.Checklist A11Y_OK =
            new AccessibilityGate.Checklist(true, true, true, true, true, true, true);

    private static ClientAppConfigService.TenantBranding branding() {
        return new ClientAppConfigService.TenantBranding("Saneamento Exemplo", UUID.randomUUID(), "#00548F");
    }

    @Test
    @DisplayName("pipeline gera identificador e client deterministicos a partir do slug, nas duas plataformas")
    void deterministicGeneration() {
        var svc = new ClientAppConfigService(params(Optional.of("prestador-play"), Optional.of("prestador-apple"), true));
        var android = svc.draftFor(TENANT, "samae-exemplo", ClientAppPlatform.ANDROID, branding(),
                "app.exemplo.invalid", "https://api.exemplo.invalid");
        var ios = svc.draftFor(TENANT, "samae-exemplo", ClientAppPlatform.IOS, branding(),
                "app.exemplo.invalid", "https://api.exemplo.invalid");
        assertThat(android.appIdentifier()).isEqualTo("br.com.ziipsan.cliente.samae-exemplo");
        assertThat(ios.appIdentifier()).isEqualTo(android.appIdentifier());
        assertThat(android.keycloakClientId()).isEqualTo("ziipsan-cliente-samae-exemplo");
        assertThat(android.status()).isEqualTo(ClientAppStatus.DRAFT);
        assertThat(android.redirectUri()).isEqualTo("https://app.exemplo.invalid/auth/callback");
        assertThat(android.storeAccountRef()).isEqualTo("prestador-play");
        assertThat(ios.storeAccountRef()).isEqualTo("prestador-apple");
    }

    @Test
    @DisplayName("slug invalido e recusado na geracao (identificador de loja nao aceita improviso)")
    void invalidSlugRejected() {
        assertThatThrownBy(() -> ClientAppConfig.appIdentifierFor("Slug Com Espaco", ClientAppPlatform.ANDROID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientAppConfig.keycloakClientIdFor(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("submissao sem conta de loja da plataforma e recusada (PLT-33)")
    void submitWithoutStoreAccountRejected() {
        var svc = new ClientAppConfigService(params(Optional.empty(), Optional.of("prestador-apple"), true));
        var draft = svc.draftFor(TENANT, "samae-exemplo", ClientAppPlatform.ANDROID, branding(),
                "app.exemplo.invalid", "https://api.exemplo.invalid");
        assertThat(draft.storeAccountRef()).isNull();
        assertThatThrownBy(() -> svc.submitForReview(draft, A11Y_OK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem conta de loja");
    }

    @Test
    @DisplayName("submissao com acessibilidade incompleta e recusada nomeando o item (SG-782)")
    void submitWithoutA11yRejected() {
        var svc = new ClientAppConfigService(params(Optional.of("prestador-play"), Optional.of("x"), true));
        var draft = svc.draftFor(TENANT, "samae-exemplo", ClientAppPlatform.ANDROID, branding(),
                "app.exemplo.invalid", "https://api.exemplo.invalid");
        var incomplete = new AccessibilityGate.Checklist(true, false, true, true, true, true, true);
        assertThatThrownBy(() -> svc.submitForReview(draft, incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VoiceOver");
    }

    @Test
    @DisplayName("ciclo DRAFT -> IN_REVIEW -> PUBLISHED exige versao semver e build positivo")
    void fullLifecycle() {
        var svc = new ClientAppConfigService(params(Optional.of("prestador-play"), Optional.of("x"), true));
        var draft = svc.draftFor(TENANT, "samae-exemplo", ClientAppPlatform.ANDROID, branding(),
                "app.exemplo.invalid", "https://api.exemplo.invalid");
        var inReview = svc.submitForReview(draft, A11Y_OK);
        assertThat(inReview.status()).isEqualTo(ClientAppStatus.IN_REVIEW);
        var published = svc.markPublished(inReview, "1.2.0", 42);
        assertThat(published.status()).isEqualTo(ClientAppStatus.PUBLISHED);
        assertThat(published.publishedVersion()).isEqualTo("1.2.0");
        assertThat(published.publishedBuild()).isEqualTo(42);
        assertThatThrownBy(() -> svc.markPublished(inReview, "1.2", 42))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.markPublished(draft, "1.2.0", 42))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PUBLISHED sem versao ou sem conta e impossivel por construcao (espelho do CHECK da V11_1)")
    void publishedInvariants() {
        assertThatThrownBy(() -> new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.x", "Nome", "ziipsan-cliente-x", "conta",
                ClientAppStatus.PUBLISHED, null, 1, "app.exemplo.invalid",
                "https://api.exemplo.invalid", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.x", "Nome", "ziipsan-cliente-x", null,
                ClientAppStatus.PUBLISHED, "1.0.0", 1, "app.exemplo.invalid",
                "https://api.exemplo.invalid", null, "#ZZZZZZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D5: PUBLISHED sem build e inconstrutivel, build nao positivo e versao fora de semver sao recusados")
    void publishedBuildAndSemverInvariants() {
        // PUBLISHED com versao e conta, mas SEM build (lacuna D5):
        assertThatThrownBy(() -> new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.x", "Nome", "ziipsan-cliente-x", "conta",
                ClientAppStatus.PUBLISHED, "1.0.0", null, "app.exemplo.invalid",
                "https://api.exemplo.invalid", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // Build zero/negativo em qualquer estado:
        assertThatThrownBy(() -> new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.x", "Nome", "ziipsan-cliente-x", "conta",
                ClientAppStatus.PUBLISHED, "1.0.0", 0, "app.exemplo.invalid",
                "https://api.exemplo.invalid", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // Versao fora de semver x.y.z (o SQL agora tem o mesmo regex):
        assertThatThrownBy(() -> new ClientAppConfig(TENANT, ClientAppPlatform.IOS,
                "br.com.ziipsan.cliente.x", "Nome", "ziipsan-cliente-x", "conta",
                ClientAppStatus.PUBLISHED, "1.0", 1, "app.exemplo.invalid",
                "https://api.exemplo.invalid", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
