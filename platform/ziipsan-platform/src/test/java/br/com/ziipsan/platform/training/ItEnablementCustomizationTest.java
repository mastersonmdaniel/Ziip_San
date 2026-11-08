package br.com.ziipsan.platform.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ItEnablementCustomizationTest {

    private static ItEnablement.ItCompletion fullIt(boolean withDedicated) {
        Set<String> delivered = new HashSet<>();
        Set<String> handsOn = new HashSet<>();
        for (ItEnablement.Topic t : ItEnablement.TOPICS) {
            if (t.mandatory() || (withDedicated
                    && ItEnablement.TOPIC_DEDICATED_INSTANCE.equals(t.key()))) {
                delivered.add(t.key());
                if (t.handsOn()) {
                    handsOn.add(t.key());
                }
            }
        }
        return new ItEnablement.ItCompletion(delivered, handsOn);
    }

    @Test
    @DisplayName("TI completa sem instância dedicada no contrato; com ela exigida e não entregue, falta")
    void itCompletionAndDedicated() {
        assertThat(ItEnablement.evaluate(fullIt(false), false).complete()).isTrue();
        ItEnablement.Result r = ItEnablement.evaluate(fullIt(false), true);
        assertThat(r.complete()).isFalse();
        assertThat(r.failures())
                .anyMatch(f -> f.contains(ItEnablement.TOPIC_DEDICATED_INSTANCE));
        assertThat(ItEnablement.evaluate(fullIt(true), true).complete()).isTrue();
    }

    @Test
    @DisplayName("tópico obrigatório entregue sem a prática não completa a TI")
    void handsOnMissing() {
        ItEnablement.ItCompletion c = fullIt(false);
        Set<String> handsOn = new HashSet<>(c.handsOnDoneKeys());
        handsOn.remove("BACKUP_E_RESTAURACAO");
        ItEnablement.Result r = ItEnablement.evaluate(
                new ItEnablement.ItCompletion(c.deliveredTopicKeys(), handsOn), false);
        assertThat(r.complete()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains("BACKUP_E_RESTAURACAO"));
    }

    private static CustomizationDoc.Customization accepted(String key) {
        return new CustomizationDoc.Customization(key, CustomizationDoc.Category.PARAMETER,
                "Parâmetro de exemplo sintético", "Descrição do exemplo sintético.",
                "P0.19", CustomizationDoc.Status.ACCEPTED, "ADMIN-TENANT",
                LocalDate.of(2026, 11, 15), "Restaurar o valor padrão de fábrica.");
    }

    @Test
    @DisplayName("registro com item aceito, decisão e reversão está completo e faz round-trip JSON")
    void customizationCompleteAndRoundTrip() {
        CustomizationDoc.Customization original = accepted("PARAM-EXEMPLO-01");
        CustomizationDoc.Registry registry = new CustomizationDoc.Registry(
                List.of(original), false);
        assertThat(CustomizationDoc.completeness(registry).complete()).isTrue();
        String json = CustomizationDoc.toJson(registry);
        CustomizationDoc.Registry back = CustomizationDoc.importRegistry(json);
        assertThat(back.declaredNone()).isEqualTo(registry.declaredNone());
        assertThat(back.items()).hasSize(1);
        CustomizationDoc.Customization roundTripped = back.items().get(0);
        // Round-trip campo a campo (R6): nada pode se perder nem mudar na
        // serialização — chave, categoria, textos, origem, situação,
        // decisão (quem/quando) e nota de reversão.
        assertThat(roundTripped.key()).isEqualTo(original.key());
        assertThat(roundTripped.category()).isEqualTo(original.category());
        assertThat(roundTripped.title()).isEqualTo(original.title());
        assertThat(roundTripped.description()).isEqualTo(original.description());
        assertThat(roundTripped.sourceRef()).isEqualTo(original.sourceRef());
        assertThat(roundTripped.status()).isEqualTo(original.status());
        assertThat(roundTripped.decidedBy()).isEqualTo(original.decidedBy());
        assertThat(roundTripped.decidedOn()).isEqualTo(original.decidedOn());
        assertThat(roundTripped.rollbackNote()).isEqualTo(original.rollbackNote());
        assertThat(roundTripped).isEqualTo(original);
        assertThat(back).isEqualTo(registry);
        assertThat(CustomizationDoc.completeness(back).complete()).isTrue();
    }

    @Test
    @DisplayName("item pendente de aceite e aceito sem reversão deixam a documentação incompleta")
    void customizationIncomplete() {
        CustomizationDoc.Customization pending = new CustomizationDoc.Customization(
                "TEMA-01", CustomizationDoc.Category.BRAND_THEME, "Tema de exemplo",
                "Descrição.", "P1.19", CustomizationDoc.Status.PENDING_ACCEPTANCE,
                null, null, null);
        CustomizationDoc.Customization noRollback = new CustomizationDoc.Customization(
                "PERFIL-01", CustomizationDoc.Category.ACCESS_PROFILE, "Perfil de exemplo",
                "Descrição.", "ED-06", CustomizationDoc.Status.ACCEPTED, "ADMIN-TENANT",
                LocalDate.of(2026, 11, 15), null);
        CustomizationDoc.Result r = CustomizationDoc.completeness(
                new CustomizationDoc.Registry(List.of(pending, noRollback), false));
        assertThat(r.complete()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains("TEMA-01"));
        assertThat(r.failures()).anyMatch(f -> f.contains("PERFIL-01"));
    }

    @Test
    @DisplayName("registro vazio só é completo com a declaração explícita de inexistência")
    void emptyRegistry() {
        assertThat(CustomizationDoc.completeness(
                new CustomizationDoc.Registry(List.of(), false)).complete()).isFalse();
        assertThat(CustomizationDoc.completeness(
                new CustomizationDoc.Registry(List.of(), true)).complete()).isTrue();
        assertThat(CustomizationDoc.completeness(
                new CustomizationDoc.Registry(List.of(accepted("X-01")), true)).complete())
                .isFalse();
    }
}
