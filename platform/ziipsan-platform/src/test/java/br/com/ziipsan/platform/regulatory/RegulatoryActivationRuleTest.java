package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.console.PlatformConsoleRules;
import br.com.ziipsan.platform.regulatory.RegulatoryActivationRule.PlannedVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Envelope de ativação do perfil (P2.31 sobre a P0.19): proposta completa,
 * quem propõe diferente de quem aprova, efeito só futuro e hash do aceite
 * igual ao do console de parâmetros (P1.6).
 */
class RegulatoryActivationRuleTest {

    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);
    private static final UUID PROPOSER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID APPROVER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("ARIS sem o valor do tenant para a chave pendente: proposta recusada nomeando a chave")
    void proposalMissingTenantValueRefused() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(aris, Map.of(), FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sac.priority_min_age_years");
    }

    @Test
    @DisplayName("ARIS completa: uma versão por valor confirmado mais o valor do tenant, com citação de origem")
    void completeProposalPlansEveryVersion() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        // A lista de dias e fixture do tenant (escolha dele na ativacao):
        // o perfil confirma o minimo de 6 datas, nunca os dias.
        List<PlannedVersion> planned = RegulatoryActivationRule.planVersions(
                aris, Map.of("sac.priority_min_age_years", "60",
                        "bill.due_date_options", "[5, 10, 15, 20, 25, 28]"), FROM, null);
        long confirmed = aris.values().stream()
                .filter(v -> v.confirmation() == RegulatoryProfileDef.Confirmation.CONFIRMED).count();
        assertThat(planned).hasSize((int) confirmed + 1);
        assertThat(planned).anySatisfy(v -> {
            assertThat(v.definitionKey()).isEqualTo("meter.after_cap_rule");
            assertThat(v.normCitation()).contains("RN 19/2019");
            assertThat(v.contentHash()).matches("[0-9a-f]{64}");
        });
        assertThat(planned).anySatisfy(v -> {
            assertThat(v.definitionKey()).isEqualTo("sac.priority_min_age_years");
            assertThat(v.valueJson()).isEqualTo("60");
        });
        // A versao de bill.due_date_options e a LISTA do tenant (formato
        // LIST_INT do billing), nunca o {"minCount":6} do catalogo.
        assertThat(planned).anySatisfy(v -> {
            assertThat(v.definitionKey()).isEqualTo("bill.due_date_options");
            assertThat(v.valueJson()).isEqualTo("[5,10,15,20,25,28]");
        });
    }

    @Test
    @DisplayName("ARESC: ausência confirmada (sem teto de idade) não gera versão — a ausência é o dado")
    void confirmedAbsencePlansNoVersion() {
        RegulatoryProfileDef aresc = ScRegulatoryProfiles.byKey("ARESC_RES046_2016");
        List<PlannedVersion> planned = RegulatoryActivationRule.planVersions(
                aresc, Map.of("bill.due_date_options", "[5, 10, 15, 20, 25, 28]"), FROM, null);
        assertThat(planned).noneMatch(v -> v.definitionKey().equals("cut.max_debt_age_days"));
        assertThat(planned).anyMatch(v -> v.definitionKey().equals("cut.min_notice_days"));
    }

    @Test
    @DisplayName("Valor do tenant com regra recusada por lei federal, ou chave fora do perfil: proposta recusada")
    void tenantValueAgainstFederalFloorRefused() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(
                aris, Map.of("chave.inexistente", "1"), FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pertence ao perfil");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(
                aris, Map.of("sac.priority_min_age_years", "\"SOCIAL_LOSS_AFTER_TWO_CUTS\""),
                FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14.898");
    }

    @Test
    @DisplayName("Quem propõe não aprova, e a vigência precisa ser futura em relação à aprovação (P0.19)")
    void approvalSeparationAndFutureEffect() {
        assertThatThrownBy(() -> RegulatoryActivationRule.validateApproval(
                PROPOSER, PROPOSER, LocalDate.of(2026, 12, 1), FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não aprova");
        assertThatThrownBy(() -> RegulatoryActivationRule.validateApproval(
                PROPOSER, APPROVER, LocalDate.of(2027, 1, 2), FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futuro");
        assertThatCode(() -> RegulatoryActivationRule.validateApproval(
                PROPOSER, APPROVER, LocalDate.of(2026, 12, 1), FROM))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Hash da ativação é o mesmo do console de parâmetros (PlatformConsoleRules) sobre o valor canonizado")
    void contentHashMatchesConsoleRules() {
        String valueJson = "{\"minDays\":27,\"maxDays\":33}";
        String canonical = Json.readTree(valueJson).toString();
        String expected = PlatformConsoleRules.contentHash(
                "meter.reading_cycle_days", canonical, FROM, null);
        assertThat(RegulatoryActivationRule.contentHash(
                "meter.reading_cycle_days", valueJson, FROM, null)).isEqualTo(expected);
        assertThat(RegulatoryActivationRule.contentHash(
                "meter.reading_cycle_days", valueJson, FROM, null))
                .isNotEqualTo(RegulatoryActivationRule.contentHash(
                        "meter.reading_cycle_days", "{\"minDays\":28,\"maxDays\":33}", FROM, null));
    }

    @Test
    @DisplayName("Aceite com hash divergente da versão é recusado (P0.19)")
    void acceptanceHashMismatchRefused() {
        assertThatThrownBy(() -> RegulatoryActivationRule.validateAcceptanceHash(
                "a".repeat(64), "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
        assertThatCode(() -> RegulatoryActivationRule.validateAcceptanceHash(
                "a".repeat(64), "a".repeat(64))).doesNotThrowAnyException();
    }
}
