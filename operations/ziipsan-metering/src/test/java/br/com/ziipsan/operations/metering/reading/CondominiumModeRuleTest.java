package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.CondominiumModeRule.Mode;
import br.com.ziipsan.operations.metering.rules.CondominiumApportionment;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Condomínio nas duas modalidades (SAMAE 2.10.10): por economia com rateio
 * do macro, e individualizada sem rateio — e a recusa do híbrido vedado
 * (teste exigido pelo aceite da ED-15 e pelo RAS-13 da P2.5).
 */
class CondominiumModeRuleTest {

    @Test
    @DisplayName("modelo híbrido vedado (STJ Tema 414) é recusado na configuração — teste do aceite")
    void hybridModelRefused() {
        assertThatThrownBy(() -> CondominiumModeRule.validateConfiguration(
                Mode.HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("Tema 414");
        assertThatThrownBy(() -> CondominiumModeRule.apportion(
                Mode.HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER, 100,
                List.of(new CondominiumApportionment.Micro("m1", 10, 1, true)), 10))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("modalidade ausente é recusada: o vínculo não fica sem modalidade por omissão")
    void missingModeRefused() {
        assertThatThrownBy(() -> CondominiumModeRule.validateConfiguration(null))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("modalidade por economia: o excedente do macro é rateado aos micros não medidos")
    void perEconomyApportionsMacroExcess() {
        // macro 100; micros faturados somam 60 (30 medido + 30 não medido); a ratear = 40.
        // Existe micro não medido (2 economias): só ele recebe, 40/2 = 20 por economia -> 40.
        CondominiumApportionment.Result result = CondominiumModeRule.apportion(
                Mode.PER_ECONOMY_FRANCHISE_AND_EXCESS, 100,
                List.of(new CondominiumApportionment.Micro("medido", 30, 1, true),
                        new CondominiumApportionment.Micro("nao-medido", 30, 2, false)),
                999);
        assertThat(result.toBeApportioned()).isEqualTo(40);
        assertThat(result.byMicro()).containsEntry("medido", 0).containsEntry("nao-medido", 40);
    }

    @Test
    @DisplayName("modalidade individualizada: micro sem hidrômetro é recusado (Lei 13.312/2016)")
    void individualizedRejectsUnmeteredMicro() {
        assertThatThrownBy(() -> CondominiumModeRule.apportion(
                Mode.INDIVIDUALIZED_METERING, 100,
                List.of(new CondominiumApportionment.Micro("sem-hidro", 0, 1, false)), 10))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("13.312");
    }

    @Test
    @DisplayName("modalidade individualizada não rateia o macro: cada economia vale pelo seu hidrômetro")
    void individualizedHasNoApportionment() {
        assertThatThrownBy(() -> CondominiumModeRule.apportion(
                Mode.INDIVIDUALIZED_METERING, 100,
                List.of(new CondominiumApportionment.Micro("m1", 25, 1, true)), 10))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("não tem rateio");
    }
}
