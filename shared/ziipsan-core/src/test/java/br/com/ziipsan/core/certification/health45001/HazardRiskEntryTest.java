package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HazardRiskEntryTest {

    private static final Iso45001Activation ON =
            Iso45001Activation.enabled("SYN-escopo", UUID.randomUUID(), UUID.randomUUID());
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID BY = UUID.randomUUID();
    private static final LocalDate ON_DATE = LocalDate.of(2026, 1, 10);

    private static HazardRiskEntry entry(HazardRiskEntry.Category category, int sev, int lik,
                                         int scale, List<String> factors) {
        return HazardRiskEntry.register(ON, "SYN-ATV-1", "SYN atividade", "SYN local", ORG,
                category, "SYN perigo", sev, lik, scale, List.of("SYN controle"), factors, BY,
                ON_DATE);
    }

    @Test
    @DisplayName("registro válido: escore = severidade x probabilidade na escala declarada")
    void validEntry() {
        HazardRiskEntry e = entry(HazardRiskEntry.Category.MECHANICAL, 4, 2, 5, List.of());
        assertThat(e.riskScore()).isEqualTo(8);
        assertThat(e.requiresActionPlan(8)).isTrue();
        assertThat(e.requiresActionPlan(9)).isFalse();
    }

    @Test
    @DisplayName("valor fora da escala declarada pelo tenant é recusado")
    void outOfScaleRefused() {
        assertThatThrownBy(() -> entry(HazardRiskEntry.Category.PHYSICAL, 6, 1, 5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(HazardRiskEntry.Category.PHYSICAL, 1, 1, 2, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem controle declarado não nasce")
    void noControlRefused() {
        assertThatThrownBy(() -> HazardRiskEntry.register(ON, "SYN-A", "SYN", "SYN", ORG,
                HazardRiskEntry.Category.CHEMICAL, "SYN", 1, 1, 5, List.of(), List.of(), BY, ON_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("psicossocial: fatores obrigatórios na categoria e vedados fora dela")
    void psychosocialField() {
        HazardRiskEntry e = entry(HazardRiskEntry.Category.PSYCHOSOCIAL, 3, 3, 5,
                List.of("SYN-FATOR-1"));
        assertThat(e.psychosocialFactors()).containsExactly("SYN-FATOR-1");
        assertThatThrownBy(() -> entry(HazardRiskEntry.Category.PSYCHOSOCIAL, 1, 1, 5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> entry(HazardRiskEntry.Category.PHYSICAL, 1, 1, 5,
                List.of("SYN-FATOR-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("escopo por área da P3.30 é obrigatório")
    void orgUnitRequired() {
        assertThatThrownBy(() -> HazardRiskEntry.register(ON, "SYN-A", "SYN", "SYN", null,
                HazardRiskEntry.Category.OTHER, "SYN", 1, 1, 5, List.of("SYN-C"), List.of(), BY,
                ON_DATE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("norma desligada: nem pela fábrica nem pelo construtor canônico")
    void disabledRefused() {
        Iso45001Activation off = Iso45001Activation.disabled();
        assertThatThrownBy(() -> HazardRiskEntry.register(off, "SYN-A", "SYN", "SYN", ORG,
                HazardRiskEntry.Category.OTHER, "SYN", 1, 1, 5, List.of("SYN-C"), List.of(), BY,
                ON_DATE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HazardRiskEntry(off, "SYN-A", "SYN", "SYN", ORG,
                HazardRiskEntry.Category.OTHER, "SYN", 1, 1, 5, List.of("SYN-C"), List.of(), BY,
                ON_DATE)).isInstanceOf(IllegalStateException.class);
    }
}
