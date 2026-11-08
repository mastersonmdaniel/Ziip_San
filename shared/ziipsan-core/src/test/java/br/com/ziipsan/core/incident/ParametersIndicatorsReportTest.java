package br.com.ziipsan.core.incident;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
class ParametersIndicatorsReportTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID WO1 = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 8, 0);
    NetworkIncident closed(String cause, Double vol, Long repair, int hours, int economies) {
        NetworkIncident i = new NetworkIncident(UUID.randomUUID(), TEN, "OC-X",
                NetworkIncident.Type.INTERRUPTION, false, NetworkIncident.Origin.TELEMETRY, T0,
                new NetworkIncident.AffectedSnapshot(List.of(), economies, null), null);
        i.linkWorkOrder(WO1); i.markRestored(T0.plusHours(hours));
        i.close(new NetworkIncident.ClosureData(cause, vol, repair, WO1), List.of(cause));
        return i;
    }
    @Test @DisplayName("catalogo: so ALAGAMENTO tem fonte; ausente/vazio e causa fora recusam")
    void catalog() {
        assertThat(OutageParameters.SOURCED_CAUSES).containsExactly("ALAGAMENTO");
        assertThatThrownBy(() -> OutageParameters.causeCatalog(null)).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> OutageParameters.causeCatalog(List.of())).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> OutageParameters.requireCauseInCatalog(List.of("ALAGAMENTO"), "OUTRA"))
                .isInstanceOf(IllegalArgumentException.class);
        OutageParameters.requireCauseInCatalog(List.of("ALAGAMENTO"), "ALAGAMENTO");
    }
    @Test @DisplayName("limiares com padrao do aceite e regra so-reduz")
    void thresholds() {
        assertThat(OutageParameters.minDurationHours(null)).isEqualTo(6);
        assertThat(OutageParameters.minDurationHours(3)).isEqualTo(3);
        assertThatThrownBy(() -> OutageParameters.minDurationHours(7)).isInstanceOf(IllegalArgumentException.class);
        assertThat(OutageParameters.emergencySupplyAfterHours(null)).isEqualTo(24);
        assertThatThrownBy(() -> OutageParameters.emergencySupplyAfterHours(25)).isInstanceOf(IllegalArgumentException.class);
        assertThat(OutageParameters.creditEnabled(null)).isFalse();
    }
    @Test @DisplayName("I-04: so paralisacao >= limiar qualifica; I-05 sem denominador e indefinido")
    void indicators() {
        NetworkIncident longOne = closed("ALAGAMENTO", null, null, 8, 100);
        NetworkIncident shortOne = new NetworkIncident(UUID.randomUUID(), TEN, "OC-S",
                NetworkIncident.Type.INTERRUPTION, false, NetworkIncident.Origin.CREW, T0,
                new NetworkIncident.AffectedSnapshot(List.of(), 50, null), null);
        shortOne.markRestored(T0.plusHours(2));
        var base = IncidentIndicators.intermittency(List.of(longOne, shortOne), 6, T0.plusHours(9));
        assertThat(base.qualifying()).containsExactly(longOne);
        assertThat(base.economiesAffected()).isEqualTo(100);
        assertThat(base.economyHours()).isEqualTo(800.0);
        assertThat(IncidentIndicators.overflow(List.of(longOne)).perKm()).isEmpty();
    }
    @Test @DisplayName("JOR-33: relatorio por causa ignora abertas e nao zera desconhecidos")
    void report() {
        var rows = IncidentReport.byCause(List.of(
                closed("ALAGAMENTO", 10.0, 120L, 5, 1), closed("ALAGAMENTO", null, 240L, 7, 1),
                closed("ROMPIMENTO_TUBO", 30.0, null, 4, 1)));
        // ROMPIMENTO_TUBO aqui e codigo de catalogo SINTETICO do ensaio (tenant), nao causa de fabrica.
        assertThat(rows).hasSize(2);
        var flood = rows.stream().filter(r -> r.causeCode().equals("ALAGAMENTO")).findFirst().orElseThrow();
        assertThat(flood.incidents()).isEqualTo(2);
        assertThat(flood.avgRepairMinutesKnown()).isEqualTo(180.0);
        assertThat(flood.volumeKnown()).isEqualTo(1);
        assertThat(flood.totalLostVolumeM3()).isEqualTo(10.0);
    }
    @Test @DisplayName("RAS-29: credito desligado/incompleto = vazio; ligado calcula com teto do tenant")
    void credit() {
        NetworkIncident i = closed("ALAGAMENTO", null, null, 30, 1);
        assertThat(OutageCreditRule.disabled().creditPercent(i, T0.plusHours(30))).isEmpty();
        assertThat(new OutageCreditRule(true, null, null, null).creditPercent(i, T0.plusHours(30))).isEmpty();
        var rule = new OutageCreditRule(true, 24, new BigDecimal("1.0"), new BigDecimal("5.0"));
        assertThat(rule.creditPercent(i, T0.plusHours(30))).contains(new BigDecimal("5.0")); // 6h x 1% = 6 -> teto 5
    }
}
