package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** PD-10: parecer NAO existe no projeto — o estado padrao bloqueia, e o teste prova cada portao. */
class ProtestControlTest {
    private ActiveDebtEnrollment.Enrollment enrollment(LocalDate due, LocalDate today) {
        var s = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(today), new ActiveDebtEnrollment.Book(),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        return s.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ActiveDebtEnrollment.DebtItem(UUID.randomUUID(), due, new BigDecimal("1500.00"), null)), false, Set.of());
    }
    private static DunningPorts.LegalOpinionPort noOpinion() { return (t, s) -> Optional.empty(); }
    private static DunningPorts.LegalOpinionPort favorable() {
        return (t, s) -> Optional.of(new DunningPorts.LegalOpinionPort.Opinion("Procuradoria", Instant.now(), "PARECER-PD10-TESTE"));
    }
    private ProtestControl control(Map<String, String> params, DunningPorts.LegalOpinionPort port, LocalDate today) {
        return new ProtestControl(ActiveDebtTestSupport.gateAt(today), ActiveDebtTestSupport.params(params), port,
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
    }

    @Test @DisplayName("PADRAO (parametro ausente): protesto bloqueado — da.protest_enabled nasce desligado")
    void blockedByDefault() {
        var c = control(Map.of(), favorable(), LocalDate.of(2026, 11, 8));
        assertThatThrownBy(() -> c.authorize(enrollment(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8)), false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("da.protest_enabled");
        assertThat(ActiveDebtTestSupport.params(Map.of()).protestEnabled()).isFalse();
    }

    @Test @DisplayName("ligado mas SEM parecer PD-10 registrado: bloqueado pelo parecer")
    void blockedWithoutOpinion() {
        var c = control(Map.of("da.protest_enabled", "true"), noOpinion(), LocalDate.of(2026, 11, 8));
        assertThatThrownBy(() -> c.authorize(enrollment(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8)), false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PD-10");
    }

    @Test @DisplayName("ligado + parecer, mas divida prescrita: bloqueado pela prescricao")
    void blockedWhenPrescribed() {
        var e = enrollment(LocalDate.of(2020, 1, 10), LocalDate.of(2026, 11, 8)); // inscreve ok em 2026 (venc. 2020)
        // relogio avancado para alem do termo (2030) via gate de 2031:
        var c = new ProtestControl(ActiveDebtTestSupport.gateAt(LocalDate.of(2031, 1, 1)),
                ActiveDebtTestSupport.params(Map.of("da.protest_enabled", "true")), favorable(),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> c.authorize(e, false)).isInstanceOf(IllegalStateException.class).hasMessageContaining("PROTEST");
    }

    @Test @DisplayName("os tres portoes abertos (cenario de teste, nao o estado real): autoriza com a referencia do parecer")
    void authorizedOnlyWhenAllGatesOpen() {
        var c = control(Map.of("da.protest_enabled", "true"), favorable(), LocalDate.of(2026, 11, 8));
        var auth = c.authorize(enrollment(LocalDate.of(2025, 1, 10), LocalDate.of(2026, 11, 8)), false);
        assertThat(auth.opinionReference()).isEqualTo("PARECER-PD10-TESTE");
    }
}
