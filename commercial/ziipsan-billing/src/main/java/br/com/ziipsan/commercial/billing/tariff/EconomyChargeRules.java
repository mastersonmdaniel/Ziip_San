package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Modelo de cobrança por economia (P2.5/RAS-13).
 *
 * <ul>
 *   <li>{@link Model#PER_ECONOMY_FRANCHISE_AND_EXCESS}: franquia por economia e
 *       excedente repartido por economia — é o modelo que o {@link TariffEngine}
 *       já calcula ({@code categoriesFor}): cada economia paga a parcela fixa
 *       (que cobre a franquia) e a sua parte do excedente nas faixas.</li>
 *   <li>{@link Model#HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER}: o modelo
 *       híbrido vedado pelo STJ (Tema 414) — tarifa mínima multiplicada pelo
 *       número de economias em unidade/condomínio de hidrômetro único,
 *       combinada com a medição única. A configuração é <b>recusada</b>, nunca
 *       calculada. O condomínio completo é da ED-15 e não está aqui.</li>
 *   <li>{@link Model#INDIVIDUALIZED_METERING}: medição individualizada
 *       (Lei 13.312/2016): cada economia tem o seu hidrômetro e a sua conta de
 *       tarifa; o total da unidade é a soma das medições individuais.</li>
 * </ul>
 */
public final class EconomyChargeRules {

    public enum Model {
        PER_ECONOMY_FRANCHISE_AND_EXCESS,
        HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER,
        INDIVIDUALIZED_METERING
    }

    /** Uma medição individualizada: o grupo (1 economia) e o consumo do seu hidrômetro. */
    public record MeteredEconomy(TariffGroup group, int consumption) {
        public MeteredEconomy {
            if (group == null || group.economies() != 1) {
                throw new IllegalArgumentException(
                        "Medição individualizada exige grupo de exatamente 1 economia por hidrômetro");
            }
            if (consumption < 0) {
                throw new IllegalArgumentException("Consumo negativo: " + consumption);
            }
        }
    }

    private EconomyChargeRules() {
    }

    /**
     * Recusa, na configuração, o modelo híbrido vedado pelo STJ (Tema 414).
     *
     * @throws TariffConfigurationException se o modelo for o híbrido
     */
    public static void validateConfiguration(Model model) {
        if (model == null) {
            throw new TariffConfigurationException("Modelo de cobrança por economia é obrigatório");
        }
        if (model == Model.HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER) {
            throw new TariffConfigurationException(
                    "Modelo híbrido recusado: tarifa mínima multiplicada pelo número de economias "
                            + "com hidrômetro único é o modelo vedado pelo STJ (Tema 414)");
        }
    }

    /**
     * Total da medição individualizada (Lei 13.312/2016): soma da tarifa de cada
     * hidrômetro, calculada pelo {@link TariffEngine} como unidade de 1 economia.
     * Exige o modelo {@link Model#INDIVIDUALIZED_METERING} já validado.
     */
    public static BigDecimal individualizedTotal(TariffSchedule schedule, LocalDate from, LocalDate to,
                                                 List<MeteredEconomy> meters) {
        validateConfiguration(Model.INDIVIDUALIZED_METERING);
        if (meters == null || meters.isEmpty()) {
            throw new IllegalArgumentException("Medição individualizada sem nenhum hidrômetro");
        }
        BigDecimal total = Money.ZERO;
        for (MeteredEconomy meter : meters) {
            total = total.add(TariffEngine.calculate(schedule, from, to,
                    List.of(meter.group()), meter.consumption()).net());
        }
        return total;
    }
}
