package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aceite da P3.40: a privacidade do titular é obrigação de TODO tenant — não depende de
 * pacote (ED-74) nem de flag. Estes testes são a guarda dessa frase.
 */
class PrivacyAvailabilityTest {

    @Test
    @DisplayName("tenant sem a ED-74 tem a privacidade do titular ativa")
    void tenantWithoutEd74HasPrivacyActive() {
        assertThat(PrivacyAvailability.isActive(Set.of("sanitation"), Map.of())).isTrue();
    }

    @Test
    @DisplayName("tenant sem pacote nenhum e com suposta flag de desligamento continua ativo")
    void tenantWithoutPackagesOrWithOffFlagStaysActive() {
        assertThat(PrivacyAvailability.isActive(Set.of(), Map.of())).isTrue();
        assertThat(PrivacyAvailability.isActive(Set.of(), Map.of("lgpd.enabled", "false")))
                .isTrue();
        assertThat(PrivacyAvailability.isActive(Set.of("ed-74"), Map.of("privacy", "off")))
                .isTrue();
    }
}
