package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.ConnectionForkParameters.SewerCascadeDecision;
import br.com.ziipsan.commercial.registry.application.ConnectionForkParameters.SewerOutOfUseOnWaterCut;
import br.com.ziipsan.commercial.registry.application.ConnectionForkParameters.Values;
import br.com.ziipsan.commercial.registry.application.ConnectionForkParameters.VoluntarySuppressionWithDebt;
import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-13: teste POR OPÇÃO de cada bifurcação REAL do catálogo
 * (ADR-0038; PARAMETROS-DO-TENANT) e do padrão mais protetivo na
 * ausência de valor aceito. Desconhecido = erro explícito em CADA
 * chave (correção M5/B1 da revisão).
 */
class ConnectionForkParametersTest {

    @Test
    @DisplayName("ausência de valor aceito: padrões do catálogo (ALLOW protetivo; AUTOMATIC)")
    void defaultsAreCatalogDefaults() {
        Values v = ConnectionForkParameters.parse(Map.of());
        assertThat(v.suppressionWithDebt()).isEqualTo(VoluntarySuppressionWithDebt.ALLOW);
        assertThat(v.sewerOutOfUseOnWaterCut()).isEqualTo(SewerOutOfUseOnWaterCut.AUTOMATIC);
        assertThat(v.alternativePctEnabled()).isNull();
        assertThat(v.sewerCascadeOnWaterCutOrSuppression())
                .isEqualTo(SewerCascadeDecision.APPLY_IN_SAME_ORDER);
    }

    @Test
    @DisplayName("parse(null) equivale a ausência: padrões do catálogo")
    void nullMapUsesDefaults() {
        Values v = ConnectionForkParameters.parse(null);
        assertThat(v.suppressionWithDebt()).isEqualTo(VoluntarySuppressionWithDebt.ALLOW);
        assertThat(v.sewerOutOfUseOnWaterCut()).isEqualTo(SewerOutOfUseOnWaterCut.AUTOMATIC);
    }

    @ParameterizedTest
    @EnumSource(VoluntarySuppressionWithDebt.class)
    @DisplayName("supressão com débito: cada opção produz o portão dela (com e sem débito/permissão)")
    void suppressionPerOption(VoluntarySuppressionWithDebt rule) {
        Values v = ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.VOLUNTARY_SUPPRESSION_WITH_DEBT, "\"" + rule.name() + "\""));
        assertThat(v.suppressionWithDebt()).isEqualTo(rule);
        // Sem débito: todas permitem, com ou sem permissão.
        assertThat(v.suppressionAllowed(false, false)).isTrue();
        assertThat(v.suppressionAllowed(false, true)).isTrue();
        ConnectionForkParameters.requireSuppressionAllowed(v, false, false);
        // Com débito: ALLOW permite mantendo o débito; ALLOW_WITH_PERMISSION
        // só com permissão; BLOCK nunca.
        boolean expectedWithDebtNoPermission = rule == VoluntarySuppressionWithDebt.ALLOW;
        assertThat(v.suppressionAllowed(true, false)).isEqualTo(expectedWithDebtNoPermission);
        assertThat(v.suppressionAllowed(true, true)).isEqualTo(rule != VoluntarySuppressionWithDebt.BLOCK);
        if (expectedWithDebtNoPermission) {
            ConnectionForkParameters.requireSuppressionAllowed(v, true, false);
            assertThat(v.debtRemainsCollectible(true)).isTrue();
        } else {
            assertThatThrownBy(() -> ConnectionForkParameters.requireSuppressionAllowed(v, true, false))
                    .isInstanceOf(ApiException.Conflict.class);
        }
        if (rule == VoluntarySuppressionWithDebt.BLOCK) {
            assertThatThrownBy(() -> ConnectionForkParameters.requireSuppressionAllowed(v, true, true))
                    .isInstanceOf(ApiException.Conflict.class)
                    .hasMessageContaining("BLOCK");
            assertThat(v.debtRemainsCollectible(true)).isFalse();
        } else {
            ConnectionForkParameters.requireSuppressionAllowed(v, true, true);
        }
    }

    @ParameterizedTest
    @EnumSource(SewerOutOfUseOnWaterCut.class)
    @DisplayName("esgoto fora de uso no corte da água: cada opção produz a cascata dela")
    void outOfUsePerOption(SewerOutOfUseOnWaterCut rule) {
        Values v = ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT, "\"" + rule.name() + "\""));
        assertThat(v.sewerOutOfUseOnWaterCut()).isEqualTo(rule);
        SewerCascadeDecision expected = switch (rule) {
            case AUTOMATIC -> SewerCascadeDecision.APPLY_IN_SAME_ORDER;
            case SERVICE_ORDER_ONLY -> SewerCascadeDecision.SEPARATE_ORDER_REQUIRED;
            case NONE -> SewerCascadeDecision.NO_EFFECT;
        };
        assertThat(v.sewerCascadeOnWaterCutOrSuppression()).isEqualTo(expected);
    }

    @Test
    @DisplayName("percentual sobre fonte alternativa: ligado/desligado lidos; ausência é null (ERI decide)")
    void alternativePctPerOption() {
        assertThat(ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED, "true"))
                .alternativePctEnabled()).isTrue();
        assertThat(ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED, "false"))
                .alternativePctEnabled()).isFalse();
        assertThat(ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED, "true"))
                .alternativePctExplicitlyEnabled()).isTrue();
    }

    @Test
    @DisplayName("valor desconhecido é erro explícito em CADA chave, nunca o padrão silencioso")
    void unknownValueIsExplicitErrorPerKey() {
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.VOLUNTARY_SUPPRESSION_WITH_DEBT, "\"FREE_FOREVER\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection.voluntary_suppression_with_debt");
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT, "\"FREE_FOREVER\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sewer.out_of_use_on_water_cut");
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED, "\"FREE_FOREVER\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sewer.alternative_pct_enabled");
    }

    @Test
    @DisplayName("valor de tipo JSON errado é erro intencional (texto exigido nas enums; booleano no pct)")
    void nonTextualValueIsExplicitError() {
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.VOLUNTARY_SUPPRESSION_WITH_DEBT, "42")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection.voluntary_suppression_with_debt");
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT, "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sewer.out_of_use_on_water_cut");
        assertThatThrownBy(() -> ConnectionForkParameters.parse(Map.of(
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED, "\"true\"")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sewer.alternative_pct_enabled");
    }
}
