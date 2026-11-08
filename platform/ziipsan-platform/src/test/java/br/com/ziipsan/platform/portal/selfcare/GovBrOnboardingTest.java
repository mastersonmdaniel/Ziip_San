package br.com.ziipsan.platform.portal.selfcare;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GovBrOnboardingTest {
    private static final GovBrOnboarding.EligibilityChecklist FULL =
            new GovBrOnboarding.EligibilityChecklist(true, true, true, true);

    @Test void ligaComChecklistCompletoTenantPublico() {
        assertNotNull(GovBrOnboarding.enable(Ed38TestSupport.TENANT, "AUTARCHY", true, FULL, "SILVER"));
        assertNotNull(GovBrOnboarding.enable(Ed38TestSupport.TENANT, "SEM", true, FULL, "GOLD"));
        assertNotNull(GovBrOnboarding.enable(Ed38TestSupport.TENANT, "PUBLIC_COMPANY", true, FULL, "SILVER"));
    }
    @Test void tenantPrivadoRecusado() {
        assertThrows(IllegalStateException.class,
                () -> GovBrOnboarding.enable(Ed38TestSupport.TENANT, "PRIVATE", true, FULL, "SILVER"));
    }
    @Test void checklistIncompletoRecusa() {
        var partial = new GovBrOnboarding.EligibilityChecklist(true, true, false, true);
        assertThrows(IllegalStateException.class,
                () -> GovBrOnboarding.enable(Ed38TestSupport.TENANT, "AUTARCHY", true, partial, "SILVER"));
        assertFalse(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", partial, "SILVER"));
    }
    @Test void nivelBronzeComoMinimoRecusado() {
        assertThrows(IllegalStateException.class, () -> GovBrOnboarding.parseMinLevel("BRONZE"));
        assertThrows(IllegalStateException.class, () -> GovBrOnboarding.parseMinLevel(null));
        assertEquals(GovBrOnboarding.MinLevel.GOLD, GovBrOnboarding.parseMinLevel("gold"));
    }
    @Test void loginLocalMantidoLigadoEDesligado() {
        // Correcao D4: a disponibilidade DERIVA dos canais configurados
        // no realm (estado real), nao de constante — variamos o estado.
        var soLocal = GovBrOnboarding.parseEntryChannels(Set.of("LOCAL"));
        var localEGovbr = GovBrOnboarding.parseEntryChannels(Set.of("LOCAL", "GOVBR"));
        var soGovbr = GovBrOnboarding.parseEntryChannels(Set.of("GOVBR"));
        // gov.br DESLIGADO (so LOCAL configurado): login local disponivel, opcao gov.br some.
        assertTrue(GovBrOnboarding.localLoginAvailable(soLocal));
        assertFalse(GovBrOnboarding.govbrOptionVisible(false, "AUTARCHY", FULL, "SILVER"));
        // gov.br LIGADO (LOCAL + GOVBR configurados): login local CONTINUA disponivel.
        assertTrue(GovBrOnboarding.localLoginAvailable(localEGovbr));
        assertTrue(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, "SILVER"));
        // Derivacao de verdade: realm configurado SEM o canal LOCAL nao tem login local.
        assertFalse(GovBrOnboarding.localLoginAvailable(soGovbr));
        assertFalse(GovBrOnboarding.localLoginAvailable(Set.of()));
        assertFalse(GovBrOnboarding.localLoginAvailable(null));
        // Nome de canal fora do dominio da P3.3 e recusado na leitura do estado.
        assertThrows(IllegalArgumentException.class,
                () -> GovBrOnboarding.parseEntryChannels(Set.of("MAGIC_LINK")));
    }
    @Test void opcaoGovbrRespeitaNivelMinimoConfigurado() {
        // Correcao D4: com minimo invalido/ausente, enable() recusaria —
        // a opcao tambem nao pode aparecer.
        assertFalse(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, "BRONZE"));
        assertFalse(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, null));
        assertFalse(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, "  "));
        assertTrue(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, "GOLD"));
        assertTrue(GovBrOnboarding.govbrOptionVisible(true, "AUTARCHY", FULL, "silver"));
    }
    @Test void oidcExigePkceS256() {
        assertThrows(IllegalArgumentException.class,
                () -> new GovBrOnboarding.OidcBrokerConfig("ziipsan-portal", "code", "plain", true, true));
        assertEquals("S256", GovBrOnboarding.OidcBrokerConfig.standard().codeChallengeMethod());
    }
}
