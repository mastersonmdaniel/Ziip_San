package br.com.ziipsan.platform.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.18, sem banco: a precedência usuário → tenant → fábrica (Azul Água no
 * modo do sistema) e a regra de que a escolha guardada sobrevive à saída
 * do padrão da lista do tenant. A persistência e o HTTP ficam na IT.
 */
class UserPreferencesServiceTest {

    private static final UserPreferencesService.TenantDefaults NO_DEFAULTS =
            new UserPreferencesService.TenantDefaults(null, null, null, null);

    private static UserPreferencesService.StoredPreferences stored(String mode, String palette, String locale) {
        return new UserPreferencesService.StoredPreferences(mode, palette, locale);
    }

    private static UserPreferencesService.TenantDefaults defaults(String mode, String palette,
                                                                  List<String> allowed, String locale) {
        return new UserPreferencesService.TenantDefaults(mode, palette, allowed, locale);
    }

    @Test
    @DisplayName("sem escolha e sem padrão do tenant: Azul Água no modo do sistema, pt-BR")
    void factoryDefaults() {
        assertThat(UserPreferencesService.resolveMode(stored(null, null, null), NO_DEFAULTS))
                .isEqualTo("system");
        assertThat(UserPreferencesService.resolvePalette(stored(null, null, null), NO_DEFAULTS))
                .isEqualTo("aqua-blue");
        assertThat(UserPreferencesService.resolveLocale(stored(null, null, null), NO_DEFAULTS))
                .isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("escolha do usuário vence o padrão do tenant")
    void userChoiceBeatsTenantDefault() {
        var tenant = defaults("dark", "aqua-green", null, "pt-BR");
        assertThat(UserPreferencesService.resolveMode(stored("light", null, null), tenant)).isEqualTo("light");
        assertThat(UserPreferencesService.resolvePalette(stored(null, "aqua-blue", null), tenant))
                .isEqualTo("aqua-blue");
    }

    @Test
    @DisplayName("sem escolha do usuário, vale o padrão do tenant")
    void tenantDefaultWithoutUserChoice() {
        var tenant = defaults("dark", "aqua-green", List.of("aqua-blue", "aqua-green"), "pt-BR");
        assertThat(UserPreferencesService.resolveMode(stored(null, null, null), tenant)).isEqualTo("dark");
        assertThat(UserPreferencesService.resolvePalette(stored(null, null, null), tenant))
                .isEqualTo("aqua-green");
    }

    @Test
    @DisplayName("padrão retirado da lista do tenant: efetivo cai no padrão dele, escolha guardada intacta")
    void removedPaletteFallsBackWithoutErasingChoice() {
        var tenant = defaults(null, "aqua-blue", List.of("aqua-blue"), null);
        // O resolve devolve o padrão do tenant...
        assertThat(UserPreferencesService.resolvePalette(stored(null, "aqua-green", null), tenant))
                .isEqualTo("aqua-blue");
        // ...e a escolha guardada continua aqua-green (o serviço nunca a reescreve
        // aqui: o teste de persistência na IT confere o campo stored).
        assertThat(stored(null, "aqua-green", null).palette()).isEqualTo("aqua-green");
    }

    @Test
    @DisplayName("padrão do tenant fora da própria lista é ignorado, cai na fábrica")
    void tenantDefaultOutsideItsOwnListIsIgnored() {
        var tenant = defaults(null, "aqua-green", List.of("aqua-blue"), null);
        assertThat(UserPreferencesService.resolvePalette(stored(null, null, null), tenant))
                .isEqualTo("aqua-blue");
    }

    @Test
    @DisplayName("valores inválidos (guardados ou do tenant) caem na fábrica, nunca quebram a resolução")
    void invalidValuesFallBackToFactory() {
        var tenant = defaults("banana", "rosa", null, "en-US");
        assertThat(UserPreferencesService.resolveMode(stored("banana", null, null), tenant)).isEqualTo("system");
        assertThat(UserPreferencesService.resolvePalette(stored(null, "rosa", null), tenant))
                .isEqualTo("aqua-blue");
        assertThat(UserPreferencesService.resolveLocale(stored(null, null, "en-US"), tenant)).isEqualTo("pt-BR");
    }
}
