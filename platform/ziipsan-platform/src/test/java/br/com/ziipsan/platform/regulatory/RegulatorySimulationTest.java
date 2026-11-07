package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.platform.regulatory.RegulatorySimulation.SimulationResult;
import br.com.ziipsan.platform.regulatory.RegulatorySimulation.SyntheticAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulação pré-ativação (P2.31): diferença por chave e efeito na
 * conta-tipo sobre a massa sintética — média, teto de ciclos e ajuste por
 * dias (RAS-14, JOR-11), vencimentos (RAS-24) e encargos (RAS-16) — sem
 * gravar nada: a classe nem conhece conexão.
 */
class RegulatorySimulationTest {

    private static final SyntheticAccount ACCOUNT = new SyntheticAccount(
            new BigDecimal("200.00"), 10, List.of(10, 12, 14, 16, 18, 20), 0, 30, 30);

    @Test
    @DisplayName("ARIS na conta-tipo: multa de 2% e juros de 0,033% ao dia por 10 dias = R$ 4,66 sobre R$ 200")
    void arisChargesOnSyntheticAccount() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        SimulationResult result = RegulatorySimulation.simulate(Map.of(), aris, ACCOUNT);
        assertThat(result.profileCharges()).isEqualByComparingTo("4.66");
        assertThat(result.currentCharges()).isEqualByComparingTo("0.00");
        assertThat(result.diffs()).hasSize(aris.values().size());
        assertThat(result.divergences()).hasSize(1);
        // Pendencias da ARIS: a idade do atendimento prioritario (sem
        // fabrica) e os dias concretos de vencimento (o perfil confirma o
        // minimo de 6 datas; os dias sao escolha do tenant na ativacao).
        assertThat(result.pendingTenantKeys())
                .containsExactly("bill.due_date_options", "sac.priority_min_age_years");
    }

    @Test
    @DisplayName("Média e teto de ciclos (RAS-14): média de 6 meses = 15; com 3 ciclos estimados o teto dispara")
    void averageAndEstimatedCapEffect() {
        RegulatoryProfileDef aresc = ScRegulatoryProfiles.byKey("ARESC_RES046_2016");
        Map<String, String> current = Map.of("meter.average_months", "3");
        SyntheticAccount account = new SyntheticAccount(new BigDecimal("200.00"), 0,
                List.of(10, 12, 14, 16, 18, 20), 3, 30, 30);
        SimulationResult result = RegulatorySimulation.simulate(current, aresc, account);
        assertThat(result.profileAverageConsumption()).isEqualByComparingTo("15.0000");
        assertThat(result.currentAverageConsumption()).isEqualByComparingTo("18.0000");
        assertThat(result.profileAfterCapTriggered()).isTrue();
        // A diferenca pos-teto da ARESC aparece na chave, com o artigo:
        assertThat(result.diffs())
                .anySatisfy(d -> {
                    assertThat(d.definitionKey()).isEqualTo("meter.after_cap_rule");
                    assertThat(d.profileValueJson()).contains("SETTLE_BALANCE_IN_3_INSTALLMENTS");
                    assertThat(d.sourceArticle()).contains("90");
                });
    }

    @Test
    @DisplayName("Ajuste por dias (JOR-11): ciclo de 40 dias sobre referência de 30 tem fator 1,3333; ciclo na faixa, 1,0000")
    void dayFactorAdjustment() {
        RegulatoryProfileDef agir = ScRegulatoryProfiles.byKey("AGIR_RN001_2013");
        SyntheticAccount longCycle = new SyntheticAccount(new BigDecimal("200.00"), 0,
                List.of(10, 12, 14, 16, 18, 20), 0, 40, 30);
        assertThat(RegulatorySimulation.simulate(Map.of(), agir, longCycle).dayFactor())
                .isEqualByComparingTo("1.3333");
        assertThat(RegulatorySimulation.simulate(Map.of(), agir, ACCOUNT).dayFactor())
                .isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("Vencimentos (RAS-24): a simulação expõe o mínimo de datas e a antecedência de cada perfil")
    void dueDateDiffsExposed() {
        for (String key : List.of("ARIS_RN19_2019", "ARESC_RES046_2016", "AGIR_RN001_2013")) {
            SimulationResult result = RegulatorySimulation.simulate(
                    Map.of(), ScRegulatoryProfiles.byKey(key), ACCOUNT);
            assertThat(result.diffs())
                    .anySatisfy(d -> {
                        assertThat(d.definitionKey()).isEqualTo("bill.due_date_options");
                        assertThat(d.profileValueJson()).contains("\"minCount\":6");
                    })
                    .anySatisfy(d -> {
                        assertThat(d.definitionKey()).isEqualTo("bill.min_days_delivery_to_due");
                        assertThat(d.profileValueJson()).contains("\"default\":5");
                        assertThat(d.profileValueJson()).contains("\"PUBLIC\":10");
                    });
        }
        SimulationResult sul = RegulatorySimulation.simulate(
                Map.of(), ScRegulatoryProfiles.byKey("CISAM_SUL_RN03_2017"), ACCOUNT);
        assertThat(sul.diffs()).anySatisfy(d -> {
            assertThat(d.definitionKey()).isEqualTo("bill.due_date_options");
            assertThat(d.profileValueJson()).contains("\"minCount\":4");
        });
    }

    @Test
    @DisplayName("AGR-Tubarão: encargos sem fábrica usam o vigente na simulação e a chave entra como pendente do tenant")
    void agrSimulationFallsBackToCurrentAndListsPending() {
        RegulatoryProfileDef agr = ScRegulatoryProfiles.byKey("AGR_TUBARAO_RES007_2013");
        Map<String, String> current = Map.of(
                "fee.fine_pct", "2.00", "fee.interest_daily_pct", "0.033");
        SimulationResult result = RegulatorySimulation.simulate(current, agr, ACCOUNT);
        assertThat(result.profileCharges()).isEqualByComparingTo("4.66");
        assertThat(result.pendingTenantKeys())
                .contains("fee.fine_pct", "fee.interest_daily_pct", "meter.average_months",
                        "bill.due_date_options", "cut.min_notice_days");
    }

    @Test
    @DisplayName("Resultado rotulado como ESTIMATIVA: o cálculo autoritativo é o do billing (limitação declarada)")
    void resultIsLabeledAsEstimate() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        SimulationResult result = RegulatorySimulation.simulate(Map.of(), aris, ACCOUNT);
        assertThat(result.notice()).contains("ESTIMATIVA");
        assertThat(result.notice()).contains("billing");
        assertThat(RegulatorySimulation.ESTIMATE_NOTICE).isEqualTo(result.notice());
    }

    @Test
    @DisplayName("Simulação não grava: a classe não tem campo nem dependência de persistência")
    void simulationHasNoPersistenceSurface() {
        List<Class<?>> fieldTypes = Arrays.stream(RegulatorySimulation.class.getDeclaredFields())
                .map(Field::getType).toList();
        assertThat(fieldTypes).doesNotContain(DataSource.class, Connection.class);
        assertThat(Arrays.stream(RegulatorySimulation.class.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getParameterTypes())))
                .noneMatch(t -> t == DataSource.class || t == Connection.class);
    }
}
