package br.com.ziipsan.operations.metering.domain;

import br.com.ziipsan.operations.metering.rules.ConsumptionEffect;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import br.com.ziipsan.operations.metering.rules.OccurrenceBillingMode;
import br.com.ziipsan.operations.metering.rules.ReadingEffect;
import br.com.ziipsan.operations.metering.rules.ReadingOccurrence;
import br.com.ziipsan.operations.metering.rules.UnitSituationChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JOR-10 (P2.5): a linha do catálogo ({@link ReadingAnomaly}) monta a ocorrência
 * completa ({@link ReadingOccurrence}) pelo {@code toOccurrence()}, análogo ao
 * {@code toRule()} da cascata. Códigos FICTÍCIOS, como nos demais testes do módulo
 * (o catálogo é do tenant e sem semente).
 */
class ReadingAnomalyTest {

    private static ReadingAnomaly anomaly() {
        ReadingAnomaly a = new ReadingAnomaly();
        a.setCode("OC-FICTICIA");
        a.setDescription("Ocorrência fictícia de teste");
        a.setForUnitWithoutMeter(false);
        a.setRequiresReading(false);
        a.setGeneratesServiceOrder(true);
        a.setServiceOrderTypeCode("OS-FICTICIA");
        a.setReadingEffectWithoutReading(ReadingEffect.PREVIOUS);
        a.setReadingEffectWithReading(ReadingEffect.INFORMED);
        a.setConsumptionEffectWithoutReading(ConsumptionEffect.AVERAGE);
        a.setConsumptionEffectWithReading(ConsumptionEffect.NORMAL);
        a.setLosesSocialTariff(true);
        a.setSuspendReadingMonths(2);
        a.setActive(true);
        return a;
    }

    @Test
    @DisplayName("toOccurrence monta o efeito completo a partir da linha: regra, tipo de OS, perda de benefício e suspensão")
    void toOccurrenceMountsTheFullEffect() {
        ReadingOccurrence o = anomaly().toOccurrence();

        assertThat(o.code()).isEqualTo("OC-FICTICIA");
        assertThat(o.description()).isEqualTo("Ocorrência fictícia de teste");
        assertThat(o.rule().consumptionEffectWithoutReading()).isEqualTo(ConsumptionEffect.AVERAGE);
        assertThat(o.rule().consumptionEffectWithReading()).isEqualTo(ConsumptionEffect.NORMAL);
        assertThat(o.generatesServiceOrder()).isTrue();
        assertThat(o.serviceOrderTypeCode()).isEqualTo("OS-FICTICIA");
        assertThat(o.losesBenefit()).isTrue();
        assertThat(o.situationChange()).isEqualTo(UnitSituationChange.SUSPEND_READING);
        assertThat(o.effect(false).billingMode()).isEqualTo(OccurrenceBillingMode.BY_AVERAGE);
        assertThat(o.effect(false).serviceOrderTypeCode()).isEqualTo("OS-FICTICIA");
    }

    @Test
    @DisplayName("linha que gera OS sem tipo (ou com tipo sem gerar OS) é recusada ao montar a ocorrência")
    void incoherentServiceOrderRowIsRefused() {
        ReadingAnomaly withoutType = anomaly();
        withoutType.setServiceOrderTypeCode(null);
        assertThatThrownBy(withoutType::toOccurrence).isInstanceOf(MeteringInputException.class);

        ReadingAnomaly withoutOrder = anomaly();
        withoutOrder.setGeneratesServiceOrder(false);
        assertThatThrownBy(withoutOrder::toOccurrence).isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("ocorrência sem OS, sem perda e sem suspensão: tipo nulo e situação NONE")
    void plainOccurrenceHasNoServiceOrder() {
        ReadingAnomaly a = anomaly();
        a.setGeneratesServiceOrder(false);
        a.setServiceOrderTypeCode(null);
        a.setLosesSocialTariff(false);
        a.setSuspendReadingMonths(null);

        ReadingOccurrence o = a.toOccurrence();
        assertThat(o.generatesServiceOrder()).isFalse();
        assertThat(o.serviceOrderTypeCode()).isNull();
        assertThat(o.losesBenefit()).isFalse();
        assertThat(o.situationChange()).isEqualTo(UnitSituationChange.NONE);
    }
}
