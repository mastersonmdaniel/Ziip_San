package br.com.ziipsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catálogo de ocorrências de leitura com o efeito completo de cada uma (JOR-10, P2.5):
 * um teste por linha de efeito que o código atual produz.
 *
 * <p>O catálogo é do tenant e sem semente (V5_1): não existe lista fechada de códigos de
 * ocorrência para enumerar — os códigos abaixo são FICTÍCIOS, como os de {@link Fixtures}.
 * O que é fechado, e é o que estes testes cobrem linha a linha, é a matriz de efeitos que a
 * cascata suporta: cada {@link ConsumptionEffect} no caso em que ele vale (sem leitura só
 * valem AVERAGE, MINIMUM e NOT_METERED; com leitura valem os seis), mais os efeitos fora da
 * cascata que o JOR-10 exige explicitar: tipo de OS (catálogo da P1.31, por código), perda de
 * benefício e mudança de situação.
 */
class ReadingOccurrenceTest {

    /** Regra da cascata com os efeitos dados; leitura faturada: anterior sem leitura, informada com leitura. */
    private static ReadingAnomalyRule rule(String code, ConsumptionEffect without, ConsumptionEffect with) {
        return new ReadingAnomalyRule(code, ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                without, with, null, null, false);
    }

    private static ReadingOccurrence occurrence(ReadingAnomalyRule rule) {
        return ReadingOccurrence.ofEffects("Ocorrência fictícia de teste", rule);
    }

    // ---- Sem leitura atual (só os três efeitos que existem sem um valor medido) ----------------

    @Test
    @DisplayName("ocorrência sem leitura por média: fatura BY_AVERAGE e vale o efeito de leitura da regra (anterior)")
    void withoutReadingByAverage() {
        ReadingOccurrence o = occurrence(rule("OC-SEM-MEDIA", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL));

        ReadingOccurrence.Effect e = o.effect(false);
        assertThat(e.billingMode()).isEqualTo(OccurrenceBillingMode.BY_AVERAGE);
        assertThat(e.consumptionEffect()).isEqualTo(ConsumptionEffect.AVERAGE);
        assertThat(e.readingEffect()).isEqualTo(ReadingEffect.PREVIOUS);
        assertThat(e.generatesServiceOrder()).isFalse();
        assertThat(e.serviceOrderTypeCode()).isNull();
        assertThat(e.losesBenefit()).isFalse();
        assertThat(e.situationChange()).isEqualTo(UnitSituationChange.NONE);
    }

    @Test
    @DisplayName("ocorrência sem leitura por mínimo: fatura BY_MINIMUM (o mínimo da ligação, na cascata)")
    void withoutReadingByMinimum() {
        ReadingOccurrence o = occurrence(rule("OC-SEM-MINIMO", ConsumptionEffect.MINIMUM, ConsumptionEffect.NORMAL));

        ReadingOccurrence.Effect e = o.effect(false);
        assertThat(e.billingMode()).isEqualTo(OccurrenceBillingMode.BY_MINIMUM);
        assertThat(e.consumptionEffect()).isEqualTo(ConsumptionEffect.MINIMUM);
    }

    @Test
    @DisplayName("ocorrência sem leitura não medida: fatura BY_NOT_METERED_ESTIMATE")
    void withoutReadingNotMetered() {
        ReadingOccurrence o = occurrence(rule("OC-SEM-NAOMEDIDO", ConsumptionEffect.NOT_METERED, ConsumptionEffect.NORMAL));

        assertThat(o.effect(false).billingMode()).isEqualTo(OccurrenceBillingMode.BY_NOT_METERED_ESTIMATE);
        assertThat(o.effect(false).consumptionEffect()).isEqualTo(ConsumptionEffect.NOT_METERED);
    }

    // ---- Com leitura atual (os seis efeitos) ---------------------------------------------------

    @Test
    @DisplayName("ocorrência com leitura por média: fatura BY_AVERAGE mesmo havendo leitura")
    void withReadingByAverage() {
        ReadingOccurrence o = occurrence(rule("OC-COM-MEDIA", ConsumptionEffect.AVERAGE, ConsumptionEffect.AVERAGE));

        ReadingOccurrence.Effect e = o.effect(true);
        assertThat(e.billingMode()).isEqualTo(OccurrenceBillingMode.BY_AVERAGE);
        assertThat(e.readingEffect()).isEqualTo(ReadingEffect.INFORMED);
    }

    @Test
    @DisplayName("ocorrência com leitura por mínimo: fatura BY_MINIMUM (zero, na cascata — assimetria documentada)")
    void withReadingByMinimum() {
        ReadingOccurrence o = occurrence(rule("OC-COM-MINIMO", ConsumptionEffect.AVERAGE, ConsumptionEffect.MINIMUM));

        assertThat(o.effect(true).billingMode()).isEqualTo(OccurrenceBillingMode.BY_MINIMUM);
        assertThat(o.effect(true).consumptionEffect()).isEqualTo(ConsumptionEffect.MINIMUM);
    }

    @Test
    @DisplayName("ocorrência com leitura normal: fatura BY_MEASURED (mantém o medido)")
    void withReadingMeasured() {
        ReadingOccurrence o = occurrence(rule("OC-COM-NORMAL", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL));

        assertThat(o.effect(true).billingMode()).isEqualTo(OccurrenceBillingMode.BY_MEASURED);
        assertThat(o.effect(true).consumptionEffect()).isEqualTo(ConsumptionEffect.NORMAL);
    }

    @Test
    @DisplayName("ocorrência com leitura pelo maior: fatura BY_MAX_OF_AVERAGE_AND_MEASURED")
    void withReadingMaxOfAverageAndMeasured() {
        ReadingOccurrence o = occurrence(rule("OC-COM-MAX",
                ConsumptionEffect.AVERAGE, ConsumptionEffect.MAX_OF_AVERAGE_AND_MEASURED));

        assertThat(o.effect(true).billingMode())
                .isEqualTo(OccurrenceBillingMode.BY_MAX_OF_AVERAGE_AND_MEASURED);
    }

    @Test
    @DisplayName("ocorrência com leitura pelo menor: fatura BY_MIN_OF_AVERAGE_AND_MEASURED")
    void withReadingMinOfAverageAndMeasured() {
        ReadingOccurrence o = occurrence(rule("OC-COM-MIN",
                ConsumptionEffect.AVERAGE, ConsumptionEffect.MIN_OF_AVERAGE_AND_MEASURED));

        assertThat(o.effect(true).billingMode())
                .isEqualTo(OccurrenceBillingMode.BY_MIN_OF_AVERAGE_AND_MEASURED);
    }

    @Test
    @DisplayName("ocorrência com leitura não medida: fatura BY_NOT_METERED_ESTIMATE mesmo havendo leitura")
    void withReadingNotMetered() {
        ReadingOccurrence o = occurrence(rule("OC-COM-NAOMEDIDO",
                ConsumptionEffect.NOT_METERED, ConsumptionEffect.NOT_METERED));

        assertThat(o.effect(true).billingMode()).isEqualTo(OccurrenceBillingMode.BY_NOT_METERED_ESTIMATE);
    }

    // ---- Efeitos fora da cascata (JOR-10: OS, benefício, situação) ------------------------------

    @Test
    @DisplayName("ocorrência que gera OS: o efeito carrega o tipo de OS por código (catálogo da P1.31, sem código inventado)")
    void generatesServiceOrderCarriesTheTypeCode() {
        // "TIPO-OS-FICTICIO" é um rótulo de teste: em produção o valor é o código do tipo de OS
        // que o tenant cadastrou no catálogo da P1.31; esta frente não cria código nenhum.
        ReadingOccurrence o = new ReadingOccurrence("OC-COM-OS", "Ocorrência fictícia com OS",
                rule("OC-COM-OS", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                true, "TIPO-OS-FICTICIO", false, null, null);

        assertThat(o.effect(false).generatesServiceOrder()).isTrue();
        assertThat(o.effect(false).serviceOrderTypeCode()).isEqualTo("TIPO-OS-FICTICIO");
        assertThat(o.effect(true).serviceOrderTypeCode()).isEqualTo("TIPO-OS-FICTICIO");
    }

    @Test
    @DisplayName("gerar OS sem informar o tipo é recusado: linha de catálogo incompleta não vira padrão")
    void generatesServiceOrderWithoutTypeIsRefused() {
        assertThatThrownBy(() -> new ReadingOccurrence("OC-OS-SEMTIPO", "Ocorrência fictícia",
                rule("OC-OS-SEMTIPO", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                true, null, false, null, null))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("tipo de OS");
        assertThatThrownBy(() -> new ReadingOccurrence("OC-TIPO-SEMOS", "Ocorrência fictícia",
                rule("OC-TIPO-SEMOS", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                false, "TIPO-OS-FICTICIO", false, null, null))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("não gera OS");
    }

    @Test
    @DisplayName("ocorrência com perda de benefício: losesBenefit vale nos dois casos (sem e com leitura)")
    void losesBenefit() {
        ReadingOccurrence o = new ReadingOccurrence("OC-PERDE-BENEFICIO", "Ocorrência fictícia",
                rule("OC-PERDE-BENEFICIO", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                false, null, true, null, null);

        assertThat(o.effect(false).losesBenefit()).isTrue();
        assertThat(o.effect(true).losesBenefit()).isTrue();
    }

    @Test
    @DisplayName("ocorrência que suspende a leitura por meses: situação vira SUSPEND_READING")
    void suspendsReadingByMonths() {
        ReadingOccurrence o = new ReadingOccurrence("OC-SUSPENDE-MESES", "Ocorrência fictícia",
                rule("OC-SUSPENDE-MESES", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                false, null, false, 2, null);

        assertThat(o.situationChange()).isEqualTo(UnitSituationChange.SUSPEND_READING);
        assertThat(o.effect(false).situationChange()).isEqualTo(UnitSituationChange.SUSPEND_READING);
        assertThat(o.suspendReadingMonths()).isEqualTo(2);
    }

    @Test
    @DisplayName("ocorrência que suspende a leitura por reincidência: situação vira SUSPEND_READING")
    void suspendsReadingAfterOccurrences() {
        ReadingOccurrence o = new ReadingOccurrence("OC-SUSPENDE-REINCIDENCIA", "Ocorrência fictícia",
                rule("OC-SUSPENDE-REINCIDENCIA", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                false, null, false, null, 3);

        assertThat(o.situationChange()).isEqualTo(UnitSituationChange.SUSPEND_READING);
        assertThat(o.suspendAfterOccurrences()).isEqualTo(3);
    }

    @Test
    @DisplayName("ocorrência de imóvel sem hidrômetro: forUnitWithoutMeter vem da regra da cascata")
    void forUnitWithoutMeter() {
        ReadingAnomalyRule noMeter = new ReadingAnomalyRule("OC-SEM-HIDROMETRO",
                ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null, true);

        assertThat(occurrence(noMeter).forUnitWithoutMeter()).isTrue();
        assertThat(occurrence(rule("OC-COM-HIDROMETRO", ConsumptionEffect.AVERAGE,
                ConsumptionEffect.NORMAL)).forUnitWithoutMeter()).isFalse();
    }

    // ---- Coerência do catálogo ------------------------------------------------------------------

    @Test
    @DisplayName("código da ocorrência diferente do código da regra é recusado (uma ocorrência, um código)")
    void codeMustMatchTheRuleCode() {
        assertThatThrownBy(() -> new ReadingOccurrence("OC-OUTRO", "Ocorrência fictícia",
                rule("OC-REGRA", ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL),
                false, null, false, null, null))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("difere");
    }

    @Test
    @DisplayName("o mapa de modos cobre todos os efeitos de consumo, e os válidos sem leitura são os mesmos da cascata")
    void billingModeCoversEveryConsumptionEffect() {
        assertThat(EnumSet.allOf(ConsumptionEffect.class))
                .allSatisfy(effect -> assertThat(OccurrenceBillingMode.from(effect)).isNotNull());
        for (ConsumptionEffect effect : ConsumptionEffect.values()) {
            assertThat(OccurrenceBillingMode.from(effect).validWithoutReading())
                    .as(effect.name())
                    .isEqualTo(effect.validWithoutReading());
        }
    }
}
