package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Encarregado da fonte única (lgpd.dpo_contact) exibido nos três lugares do aceite, e
 * política de privacidade publicada/versionada no portal. Nome/contato são dados de
 * teste fictícios declarados — os reais são o parâmetro do tenant.
 */
class DpoDisplayAndPolicyTest {

    private static final String DPO_JSON =
            "{\"name\": \"Encarregada de Teste\", \"contact\": \"privacidade@example.invalid\"}";

    @Test
    @DisplayName("encarregado exibido no portal, na política e nos documentos, da mesma fonte")
    void dpoDisplayedInAllThreePlaces() {
        DpoDisplay.DpoContact contact = DpoDisplay.fromParameterValue(DPO_JSON);
        for (DpoDisplay.Place place : DpoDisplay.Place.values()) {
            String rendered = DpoDisplay.render(contact, place);
            var node = Json.readTree(rendered);
            assertThat(node.get("name").asString()).isEqualTo("Encarregada de Teste");
            assertThat(node.get("contact").asString()).isEqualTo("privacidade@example.invalid");
            assertThat(node.get("label").asString()).isNotBlank();
        }
        assertThat(DpoDisplay.Place.values()).hasSize(3);
    }

    @Test
    @DisplayName("sem encarregado registrado, a exibição é recusada — nunca inventada")
    void missingDpoRefused() {
        assertThatThrownBy(() -> DpoDisplay.fromParameterValue(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lgpd.dpo_contact");
        assertThatThrownBy(() -> DpoDisplay.fromParameterValue("{\"name\": \"Sem contato\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name e contact");
    }

    @Test
    @DisplayName("portal exibe a publicada de maior versão; publicar supersede a anterior")
    void portalShowsLatestPublishedPolicy() {
        PrivacyPolicy v1 = new PrivacyPolicy(1, "Política", "texto v1",
                PrivacyPolicy.Status.DRAFT, null)
                .publish(Instant.parse("2026-10-01T00:00:00Z"));
        PrivacyPolicy v2 = new PrivacyPolicy(2, "Política", "texto v2",
                PrivacyPolicy.Status.DRAFT, null)
                .publish(Instant.parse("2026-11-01T00:00:00Z"));
        PrivacyPolicy draft3 = new PrivacyPolicy(3, "Política", "texto v3",
                PrivacyPolicy.Status.DRAFT, null);

        PrivacyPolicy current = PrivacyPolicy.currentForPortal(List.of(v1, v2, draft3));
        assertThat(current.version()).isEqualTo(2);
        assertThat(v1.supersede().status()).isEqualTo(PrivacyPolicy.Status.SUPERSEDED);
    }

    @Test
    @DisplayName("republicar a mesma versão é recusado (a data de publicação é prova)")
    void republishRefused() {
        PrivacyPolicy published = new PrivacyPolicy(1, "Política", "texto",
                PrivacyPolicy.Status.DRAFT, null)
                .publish(Instant.parse("2026-10-01T00:00:00Z"));
        assertThatThrownBy(() -> published.publish(Instant.parse("2026-11-01T00:00:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("já está publicada");
    }

    @Test
    @DisplayName("sem versão publicada, o portal não exibe rascunho")
    void noPublishedPolicyRefused() {
        PrivacyPolicy draft = new PrivacyPolicy(1, "Política", "texto",
                PrivacyPolicy.Status.DRAFT, null);
        assertThatThrownBy(() -> PrivacyPolicy.currentForPortal(List.of(draft)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicada");
    }
}
