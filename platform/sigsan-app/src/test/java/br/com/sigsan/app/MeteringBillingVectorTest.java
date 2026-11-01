package br.com.sigsan.app;

import br.com.sigsan.commercial.billing.tariff.CategoryRule;
import br.com.sigsan.commercial.billing.tariff.TariffBlock;
import br.com.sigsan.commercial.billing.tariff.TariffEngine;
import br.com.sigsan.commercial.billing.tariff.TariffGroup;
import br.com.sigsan.commercial.billing.tariff.TariffVersion;
import br.com.sigsan.operations.metering.rules.CondominiumApportionment;
import br.com.sigsan.operations.metering.rules.EconomyGroup;
import br.com.sigsan.operations.metering.rules.ExpectedReadingRange;
import br.com.sigsan.operations.metering.rules.NonMeteredConsumption;
import br.com.sigsan.operations.metering.rules.NonMeteredFacts;
import br.com.sigsan.operations.metering.rules.NonMeteredMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vetor comum metering × billing (P0.25).
 *
 * <p>O billing tinha cópias de NonMeteredConsumption, ExpectedReadingRange e
 * CondominiumApportionment; elas foram apagadas e o metering ficou como único dono.
 * Este teste vive no sigsan-app porque ele é o único módulo que depende dos dois:
 * o billing não depende do metering (e não pode, para não criar dependência circular).
 *
 * <p>Os vetores são os mesmos nos dois lados: 2 economias RES com mínimo 10 m³ cada,
 * faixa esperada de leitura 100–200, e um condomínio com macro 100 m³ e dois micros
 * de 30 m³ cada. Se um lado mudar a regra sem o outro, este teste fica vermelho.
 */
class MeteringBillingVectorTest {

    private static final UUID RES = new UUID(0L, 1L);

    /** Tarifa fictícia só para o teste (nenhum valor real de prestador). RES: mínimo 10 m³. */
    private static TariffVersion t1() {
        CategoryRule res = CategoryRule.forCategory(RES, 10, new BigDecimal("30.00"), null, List.of(
                new TariffBlock(20, new BigDecimal("4.00")),
                new TariffBlock(50, new BigDecimal("6.00")),
                new TariffBlock(null, new BigDecimal("9.00"))));
        return new TariffVersion(new UUID(0L, 100L), LocalDate.of(2026, 1, 1), null, List.of(res), null);
    }

    @Test
    @DisplayName("não medido por tarifa: metering e billing concordam no mínimo de 2 economias RES")
    void nonMeteredMinimumAgreesAcrossModules() {
        var meteringGroups = List.of(EconomyGroup.simple("RES", 2, 10));
        int metering = NonMeteredConsumption.compute(NonMeteredMode.BY_TARIFF, meteringGroups,
                NonMeteredFacts.NONE, YearMonth.of(2026, 1)).consumption();

        int billing = TariffEngine.minimumConsumption(t1(), List.of(TariffGroup.of(RES, 2)));

        assertThat(metering).as("metering: 2 economias x mínimo 10").isEqualTo(20);
        assertThat(billing).as("billing: 2 economias x mínimo 10").isEqualTo(20);
        assertThat(metering).isEqualTo(billing);
    }

    @Test
    @DisplayName("faixa esperada: leitura dentro e fora da faixa 100-200")
    void expectedReadingRangeVector() {
        var range = new ExpectedReadingRange(100, 200, 3);

        assertThat(range.contains(150)).isTrue();
        assertThat(range.isOutOfRange(250)).isTrue();
        assertThat(range.isOutOfRange(50)).isTrue();
    }

    @Test
    @DisplayName("rateio de condomínio: 40 m³ a ratear viram 20 m³ por micro, e o billing recebe 50 m³ por micro")
    void condominiumApportionmentFeedsBilling() {
        var result = CondominiumApportionment.apportion(new CondominiumApportionment.Input(100, List.of(
                new CondominiumApportionment.Micro("A", 30, 1, false),
                new CondominiumApportionment.Micro("B", 30, 1, false)), 0));

        assertThat(result.toBeApportioned()).isEqualTo(40);
        assertThat(result.perEconomy()).isEqualTo(20);
        assertThat(result.byMicro()).containsEntry("A", 20).containsEntry("B", 20);

        // Cada micro fatura o próprio consumo + o rateio, e o billing cobra sobre esse total.
        int microAConsumption = 30 + result.byMicro().get("A");
        var charges = TariffEngine.categoriesFor(t1(), List.of(TariffGroup.of(RES, 1)), microAConsumption);
        assertThat(charges).hasSize(1);
        assertThat(charges.get(0).consumption()).isEqualTo(50);
        assertThat(charges.get(0).economies()).isEqualTo(1);
    }
}
