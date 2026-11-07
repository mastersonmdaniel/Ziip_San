package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import br.com.ziipsan.commercial.registry.application.ConnectionCycleFactsService.CombinedFacts;
import br.com.ziipsan.commercial.registry.application.ConnectionCycleFactsService.CycleFacts;
import br.com.ziipsan.commercial.registry.application.ConnectionForkParameters;
import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-13 (correção A4): o caminho FATOS -> REGRA inteiro — os fatos no
 * formato do serviço do cadastro entram no adaptador e a regra do
 * ciclo decide sobre a saída convertida (papéis por nome, volumes
 * BigDecimal dos dois lados, água+esgoto combinados, sewerOutOfUse da
 * fonte entregue no CombinedFacts).
 */
class ConnectionCycleFactsAdapterTest {

    private static CycleFacts facts(ConnectionStatus.Kind kind, ConnectionStatus.SystemRole role,
                                    boolean bills, boolean minimumConsumption,
                                    Integer minimumConsumptionValue, BigDecimal minimumVolume,
                                    boolean alternative, BigDecimal alternativeVolume) {
        return new CycleFacts(UUID.randomUUID(), kind, kind + "-" + role, role,
                bills, minimumConsumption, minimumConsumptionValue, minimumVolume,
                alternative, alternativeVolume, false, List.of(UUID.randomUUID()));
    }

    @Test
    @DisplayName("fatos->regra: mínimo da ligação e base de esgoto saem da conversão combinada")
    void factsToRulePath() {
        CombinedFacts combined = new CombinedFacts(
                facts(ConnectionStatus.Kind.WATER, ConnectionStatus.SystemRole.ACTIVE,
                        true, true, 10, null, false, null),
                facts(ConnectionStatus.Kind.SEWER, ConnectionStatus.SystemRole.ACTIVE,
                        true, false, null, new BigDecimal("10"), false, null),
                false, ConnectionForkParameters.parse(Map.of()));
        ConnectionCycleRule.ConnectionFacts ruleFacts =
                ConnectionCycleFactsAdapter.toRuleFacts(combined);
        assertThat(ruleFacts.waterRole()).isEqualTo(ConnectionCycleRule.ConnectionRole.ACTIVE);
        assertThat(ruleFacts.sewerRole()).isEqualTo(ConnectionCycleRule.ConnectionRole.ACTIVE);
        assertThat(ruleFacts.sewerOutOfUse()).isFalse();

        BillingParameters parameters = BillingParameters.fromValues(Map.of(
                BillingParameters.NO_WATER_VOLUME_RULE, "\"MINIMUM_VOLUME\""));
        ConnectionCycleRule.CycleReading reading =
                ConnectionCycleRule.read(ruleFacts, parameters, 7);
        assertThat(reading.billedWaterConsumption()).isEqualTo(10);
        assertThat(reading.sewerBaseVolume()).isEqualTo(10);
        assertThat(reading.sewerBilled()).isTrue();
    }

    @Test
    @DisplayName("fatos->regra: volume BigDecimal da fonte alternativa atravessa sem conversão perdida (18 m³)")
    void factsToRuleAlternativeVolume() {
        CombinedFacts combined = new CombinedFacts(
                facts(ConnectionStatus.Kind.WATER, ConnectionStatus.SystemRole.NO_CONNECTION,
                        false, false, null, null, false, null),
                facts(ConnectionStatus.Kind.SEWER, ConnectionStatus.SystemRole.ACTIVE,
                        true, false, null, new BigDecimal("10"), true, new BigDecimal("18")),
                false, ConnectionForkParameters.parse(Map.of()));
        ConnectionCycleRule.CycleReading reading = ConnectionCycleRule.read(
                ConnectionCycleFactsAdapter.toRuleFacts(combined),
                BillingParameters.fromValues(Map.of(
                        BillingParameters.NO_WATER_VOLUME_RULE, "\"ALTERNATIVE_SOURCE_VOLUME\"")),
                0);
        assertThat(reading.billedWaterConsumption()).isNull();
        assertThat(reading.sewerBaseVolume()).isEqualTo(18);
    }

    @Test
    @DisplayName("fatos->regra: sewerOutOfUse do CombinedFacts chega à regra; papel nulo vira papel nulo")
    void factsToRuleOutOfUseAndNullRole() {
        CombinedFacts combined = new CombinedFacts(
                facts(ConnectionStatus.Kind.WATER, ConnectionStatus.SystemRole.CUT,
                        false, false, null, null, false, null),
                facts(ConnectionStatus.Kind.SEWER, ConnectionStatus.SystemRole.DISCONNECTED,
                        false, false, null, new BigDecimal("10"), false, null),
                true, ConnectionForkParameters.parse(Map.of()));
        ConnectionCycleRule.ConnectionFacts ruleFacts =
                ConnectionCycleFactsAdapter.toRuleFacts(combined);
        assertThat(ruleFacts.sewerOutOfUse()).isTrue();
        assertThat(ruleFacts.waterRole()).isEqualTo(ConnectionCycleRule.ConnectionRole.CUT);
        assertThat(ConnectionCycleFactsAdapter.toRole(null)).isNull();
        assertThatThrownBy(() -> ConnectionCycleFactsAdapter.toRuleFacts(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
