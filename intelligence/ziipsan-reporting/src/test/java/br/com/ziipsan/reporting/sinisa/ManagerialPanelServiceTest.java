package br.com.ziipsan.reporting.sinisa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Secao gerencial: valores sao os da fonte (ED-32 via porta) — os esperados
 * abaixo sao os resultados das formulas DA ED-32 conferidos a mao a partir
 * delas (custo 10.000 / 2.500 m3 = 4,0000; margem (8.000-6.000)/8.000 =
 * 0,2500), transportados sem recalculo. Ausencia vira pendencia nomeada.
 */
class ManagerialPanelServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 12, 31);

    @Test
    void readsAllManagerialIndicatorsFromTheEd32Port() {
        ManagerialCostPort port = new ManagerialCostPort() {
            public Optional<BigDecimal> costPerCubicMeter(UUID t, LocalDate s, LocalDate e) {
                return Optional.of(new BigDecimal("4.0000"));
            }
            public Optional<BigDecimal> costPerConnection(UUID t, LocalDate s, LocalDate e) {
                return Optional.of(new BigDecimal("12.5000"));
            }
            public List<CategoryMargin> marginsByCategory(UUID t, LocalDate s, LocalDate e) {
                return List.of(new CategoryMargin("RESIDENCIAL", new BigDecimal("0.2500")));
            }
            public Optional<BigDecimal> serviceOrderTotalCost(UUID t, String os) {
                return Optional.of(new BigDecimal("350.00"));
            }
        };
        var section = new ManagerialPanelService(port).read(TENANT, START, END, "OS-1");
        assertThat(section.complete()).isTrue();
        assertThat(section.costPerCubicMeter()).isEqualByComparingTo("4.0000");
        assertThat(section.costPerConnection()).isEqualByComparingTo("12.5000");
        assertThat(section.marginsByCategory()).singleElement()
                .satisfies(m -> assertThat(m.margin()).isEqualByComparingTo("0.2500"));
        assertThat(section.serviceOrderTotalCost()).isEqualByComparingTo("350.00");
    }

    @Test
    void absentSourceBecomesNamedPendingNeverZero() {
        ManagerialCostPort empty = new ManagerialCostPort() {
            public Optional<BigDecimal> costPerCubicMeter(UUID t, LocalDate s, LocalDate e) {
                return Optional.empty();
            }
            public Optional<BigDecimal> costPerConnection(UUID t, LocalDate s, LocalDate e) {
                return Optional.empty();
            }
            public List<CategoryMargin> marginsByCategory(UUID t, LocalDate s, LocalDate e) {
                return List.of();
            }
            public Optional<BigDecimal> serviceOrderTotalCost(UUID t, String os) {
                return Optional.empty();
            }
        };
        var section = new ManagerialPanelService(empty).read(TENANT, START, END, "OS-9");
        assertThat(section.complete()).isFalse();
        assertThat(section.pendingFields()).containsExactly(
                "custo_por_m3", "custo_por_ligacao", "margem_por_categoria", "custo_total_os");
        assertThat(section.costPerCubicMeter()).isNull();
        assertThat(section.serviceOrderTotalCost()).isNull();
    }

    @Test
    void serviceOrderCostIsOnlyPendingWhenRequested() {
        ManagerialCostPort port = new ManagerialCostPort() {
            public Optional<BigDecimal> costPerCubicMeter(UUID t, LocalDate s, LocalDate e) {
                return Optional.of(new BigDecimal("4.0000"));
            }
            public Optional<BigDecimal> costPerConnection(UUID t, LocalDate s, LocalDate e) {
                return Optional.of(new BigDecimal("12.5000"));
            }
            public List<CategoryMargin> marginsByCategory(UUID t, LocalDate s, LocalDate e) {
                return List.of(new CategoryMargin("COMERCIAL", new BigDecimal("0.1000")));
            }
            public Optional<BigDecimal> serviceOrderTotalCost(UUID t, String os) {
                return Optional.empty();
            }
        };
        var section = new ManagerialPanelService(port).read(TENANT, START, END, null);
        assertThat(section.complete()).isTrue();
        assertThat(section.serviceOrderTotalCost()).isNull();
    }
}
