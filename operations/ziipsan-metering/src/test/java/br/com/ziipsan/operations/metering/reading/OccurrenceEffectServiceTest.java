package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import br.com.ziipsan.operations.metering.rules.OccurrenceBillingMode;
import br.com.ziipsan.operations.metering.rules.ReadingAnomalyRule;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;
import br.com.ziipsan.operations.metering.rules.ReadingOccurrence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ocorrências lidas do catálogo da P2.5 (ED-15): o efeito devolvido é o
 * da {@link ReadingOccurrence} do catálogo — código e efeitos fictícios
 * de teste, como no teste da P2.5, porque o catálogo é do tenant e sem
 * semente.
 */
class OccurrenceEffectServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private static ReadingOccurrence occurrence() {
        ReadingAnomalyRule rule = new ReadingAnomalyRule("OC-TESTE",
                ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null, false);
        return new ReadingOccurrence("OC-TESTE", "Ocorrência fictícia de teste", rule,
                true, "OS-VERIFICACAO", true, null, null);
    }

    private static OccurrenceEffectService.Catalog catalogOf(Map<String, ReadingOccurrence> byCode) {
        return (tenantId, code) -> Optional.ofNullable(byCode.get(code));
    }

    @Test
    @DisplayName("efeito vem do catálogo da P2.5: OS, perda de benefício e modo de faturar da ocorrência")
    void effectComesFromP25Catalog() {
        var catalog = catalogOf(Map.of("OC-TESTE", occurrence()));
        ReadingOccurrence.Effect withoutReading =
                OccurrenceEffectService.effectFor(catalog, TENANT, "OC-TESTE", false);
        assertThat(withoutReading.billingMode()).isEqualTo(OccurrenceBillingMode.BY_AVERAGE);
        assertThat(withoutReading.generatesServiceOrder()).isTrue();
        assertThat(withoutReading.serviceOrderTypeCode()).isEqualTo("OS-VERIFICACAO");
        assertThat(withoutReading.losesBenefit()).isTrue();

        ReadingOccurrence.Effect withReading =
                OccurrenceEffectService.effectFor(catalog, TENANT, "OC-TESTE", true);
        assertThat(withReading.billingMode()).isEqualTo(OccurrenceBillingMode.BY_MEASURED);
    }

    @Test
    @DisplayName("código fora do catálogo do tenant é recusado, nunca tratado como 'sem efeito'")
    void unknownCodeRejected() {
        var catalog = catalogOf(Map.of("OC-TESTE", occurrence()));
        assertThatThrownBy(() -> OccurrenceEffectService.effectFor(catalog, TENANT, "OC-INEXISTENTE", true))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> OccurrenceEffectService.effectFor(catalog, TENANT, " ", true))
                .isInstanceOf(MeteringInputException.class);
    }
}
