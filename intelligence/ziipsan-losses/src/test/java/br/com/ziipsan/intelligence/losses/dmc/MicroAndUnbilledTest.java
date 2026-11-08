package br.com.ziipsan.intelligence.losses.dmc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class MicroAndUnbilledTest {
    @Test @DisplayName("JOR-34 pro-rata: micro 2900 m3 em 29 dias -> periodo macro de 31 dias = 3100 m3")
    void proRata() {
        var r = MicroPeriodAdjustment.adjust(2900.0,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
        assertThat(r.method()).isEqualTo(MicroPeriodAdjustment.Method.PRO_RATA_DAILY_AVERAGE);
        assertThat(r.microDays()).isEqualTo(29);
        assertThat(r.macroDays()).isEqualTo(31);
        assertThat(r.adjustedM3()).isCloseTo(3100.0, within(1e-9));
        assertThat(r.originalM3()).isEqualTo(2900.0);
    }

    @Test @DisplayName("JOR-34 periodos coincidentes: metodo NONE, sem fator escondido")
    void coincidentIsNone() {
        var r = MicroPeriodAdjustment.adjust(3100.0,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
        assertThat(r.method()).isEqualTo(MicroPeriodAdjustment.Method.NONE);
        assertThat(r.adjustedM3()).isEqualTo(3100.0);
        assertThatThrownBy(() -> MicroPeriodAdjustment.adjust(1.0,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("OPE-05 metodos: medido, vazao x tempo (10 L/s x 1800 s = 18 m3) e fixo declarado")
    void unbilledMethods() {
        UUID wo = UUID.randomUUID();
        var measured = AuthorizedUnbilledVolume.of(wo, AuthorizedUnbilledVolume.Category.NETWORK_FLUSHING,
                LossParameters.UnbilledEstimateMethod.MEASURED, 42.0, null, null, null);
        assertThat(measured.volumeM3()).isEqualTo(42.0);
        var flowTime = AuthorizedUnbilledVolume.of(wo, AuthorizedUnbilledVolume.Category.FIREFIGHTING,
                LossParameters.UnbilledEstimateMethod.FLOW_TIME, null, 10.0, 1800L, null);
        assertThat(flowTime.volumeM3()).isCloseTo(18.0, within(1e-9));
        var fixed = AuthorizedUnbilledVolume.of(wo, AuthorizedUnbilledVolume.Category.SOCIAL_TANKER,
                LossParameters.UnbilledEstimateMethod.FIXED_BY_SERVICE_TYPE, null, null, null, 8.0);
        assertThat(fixed.volumeM3()).isEqualTo(8.0);
        assertThat(AuthorizedUnbilledVolume.totalM3(List.of(measured, flowTime, fixed)))
                .isCloseTo(68.0, within(1e-9));
    }

    @Test @DisplayName("OPE-05: entrada que o metodo exige e falta = recusa nomeada, nunca zero")
    void unbilledMissingRefuses() {
        UUID wo = UUID.randomUUID();
        assertThatThrownBy(() -> AuthorizedUnbilledVolume.of(wo,
                AuthorizedUnbilledVolume.Category.RESERVOIR_CLEANING,
                LossParameters.UnbilledEstimateMethod.MEASURED, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizedUnbilledVolume.of(wo,
                AuthorizedUnbilledVolume.Category.NETWORK_FLUSHING,
                LossParameters.UnbilledEstimateMethod.FLOW_TIME, null, 10.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuthorizedUnbilledVolume.of(wo,
                AuthorizedUnbilledVolume.Category.SOCIAL_TANKER,
                LossParameters.UnbilledEstimateMethod.FIXED_BY_SERVICE_TYPE, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("bloco: exclusao e o MEDIDO no macromedidor do contrato; acima do contratado e flag, sem truncar")
    void bulkExport() {
        var c = new BulkExportContract("CT-01", "PE-ETA-NORTE", "MACRO-EXP-1", 5000.0, 5200.0);
        assertThat(c.excludedVolumeM3()).isEqualTo(5200.0);
        assertThat(c.exceedsContracted()).isTrue();
        assertThatThrownBy(() -> new BulkExportContract("CT-02", "PE", " ", 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
