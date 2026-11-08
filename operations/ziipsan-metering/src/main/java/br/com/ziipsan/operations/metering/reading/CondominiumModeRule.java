package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.CondominiumApportionment;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.util.List;

/**
 * Regra de condomínio configurável nas DUAS modalidades (ED-15;
 * SAMAE 2.10.10), sobre o vínculo do condomínio da P1.3 (macro com micros
 * vinculados) e a cobrança por economia da P2.5 (RAS-13):
 *
 * <ul>
 *   <li>{@link Mode#PER_ECONOMY_FRANCHISE_AND_EXCESS} — franquia por
 *       economia e excedente: o consumo do macro que os micros não
 *       somaram é rateado entre os micros pelo
 *       {@link CondominiumApportionment} já existente (cascata da P2.5);</li>
 *   <li>{@link Mode#INDIVIDUALIZED_METERING} — medição individualizada
 *       (Lei 13.312/2016): cada economia tem o seu hidrômetro; não há
 *       rateio do macro — micro sem hidrômetro nesta modalidade é
 *       configuração recusada, não caso a ratear em silêncio.</li>
 * </ul>
 *
 * <p>O modelo híbrido — tarifa mínima multiplicada pelo número de
 * economias com hidrômetro único — é o vedado pelo STJ (Tema 414): ele
 * existe no enum só para ser <b>recusado na configuração</b>, nunca
 * calculado, com a mesma recusa da {@code EconomyChargeRules} da P2.5.
 * A cobrança em reais de cada modalidade é do faturamento (P2.5); esta
 * regra é a parte de leitura/medição: valida a modalidade do vínculo e
 * produz o rateio de consumo quando a modalidade o tem.
 */
public final class CondominiumModeRule {

    private CondominiumModeRule() {
    }

    /** Modalidade de condomínio configurável no vínculo. */
    public enum Mode {
        PER_ECONOMY_FRANCHISE_AND_EXCESS,
        INDIVIDUALIZED_METERING,
        /** Vedado (STJ, Tema 414): só existe para ser recusado. */
        HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER
    }

    /**
     * Recusa, na configuração, o modelo híbrido vedado e a modalidade
     * ausente — o condomínio não fica sem modalidade por omissão.
     */
    public static void validateConfiguration(Mode mode) {
        if (mode == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "modalidade do condomínio é obrigatória (vínculo da P1.3)");
        }
        if (mode == Mode.HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "Modelo híbrido recusado: tarifa mínima multiplicada pelo número de economias "
                            + "com hidrômetro único é o modelo vedado pelo STJ (Tema 414)");
        }
    }

    /**
     * Rateio de consumo do macro na modalidade por economia. Na medição
     * individualizada não há rateio: cada micro responde pelo seu
     * hidrômetro, e micro não medido é recusado.
     *
     * @param maxIncrementPerEconomy teto de rateio por economia quando
     *        todos os micros são medidos — parâmetro de quem chama
     *        (o mesmo da cascata {@link CondominiumApportionment}); esta
     *        regra não presume teto.
     */
    public static CondominiumApportionment.Result apportion(Mode mode, int macroConsumption,
                                                            List<CondominiumApportionment.Micro> micros,
                                                            int maxIncrementPerEconomy) {
        validateConfiguration(mode);
        if (mode == Mode.INDIVIDUALIZED_METERING) {
            if (micros == null || micros.isEmpty()) {
                throw new MeteringInputException(MeteringErrorCode.NO_ECONOMIES,
                        "condomínio sem micros");
            }
            for (CondominiumApportionment.Micro micro : micros) {
                if (!micro.metered()) {
                    throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                            "medição individualizada (Lei 13.312/2016) exige hidrômetro em cada "
                                    + "economia; micro sem hidrômetro: " + micro.id());
                }
            }
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "medição individualizada não tem rateio do macro: cada economia é faturada "
                            + "pelo seu hidrômetro (cobrança por economia da P2.5)");
        }
        return CondominiumApportionment.apportion(
                new CondominiumApportionment.Input(macroConsumption, micros, maxIncrementPerEconomy));
    }
}
