package br.com.ziipsan.operations.metering.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rateio de consumo de condomínio (relatório 02, 3.11), escrito do zero.
 *
 * <p>O imóvel macro (hidrômetro geral) tem micros vinculados. O que o macro registrou e os micros
 * não somaram é rateado.
 * <pre>
 *   aSerRateado = MAX(0, consumoMacro - soma dos consumos faturados dos micros)
 *   existe micro NÃO medido:  porEconomia = aSerRateado DIV economias dos não medidos;
 *                             só os não medidos recebem; os medidos recebem 0
 *   todos medidos:            porEconomia = MIN(aSerRateado DIV economias medidas, incrementoMáximo)
 *   rateio do micro = porEconomia * economias do micro
 * </pre>
 * <b>Arredondamento:</b> a divisão é inteira (DOWN). O resto da divisão e o que o teto de
 * incremento corta <b>não são distribuídos</b>, e cada perda gera aviso; nunca se perde consumo em
 * silêncio.
 *
 * <p><b>Divergência do relatório:</b> o relatório lista também um limite de decremento
 * ({@code parm_nnconsrateiodecrementomax}), mas como {@code aSerRateado} nunca é negativo, o
 * porEconomia nunca é negativo e o decremento não age. Não há parâmetro de decremento aqui.
 *
 * <p>O rateio por <i>valor</i> em reais (que o relatório pede "pelo maior resto") é do faturamento.
 */
public final class CondominiumApportionment {

    private CondominiumApportionment() {
    }

    /**
     * @param id               identificador do micro (devolvido na chave do resultado)
     * @param billedConsumption consumo faturado do micro (m³)
     * @param economies        economias do micro (&gt;= 1)
     * @param metered          o micro tem hidrômetro
     */
    public record Micro(String id, int billedConsumption, int economies, boolean metered) {
        public Micro {
            MeteringInputException.require(id != null && !id.isBlank(), MeteringErrorCode.INVALID_INPUT,
                    "id do micro é obrigatório");
            MeteringInputException.require(billedConsumption >= 0, MeteringErrorCode.INVALID_INPUT,
                    "consumo do micro negativo");
            MeteringInputException.require(economies >= 1, MeteringErrorCode.NO_ECONOMIES, "micro sem economias");
        }
    }

    /**
     * @param macroConsumption          consumo do hidrômetro geral (m³)
     * @param micros                    micros vinculados (pelo menos um)
     * @param maxIncrementPerEconomy    teto de rateio por economia quando todos os micros são medidos
     */
    public record Input(int macroConsumption, List<Micro> micros, int maxIncrementPerEconomy) {
        public Input {
            MeteringInputException.require(macroConsumption >= 0, MeteringErrorCode.INVALID_INPUT,
                    "consumo do macro negativo");
            MeteringInputException.require(micros != null && !micros.isEmpty(), MeteringErrorCode.NO_ECONOMIES,
                    "condomínio sem micros");
            micros = List.copyOf(micros);
            MeteringInputException.require(maxIncrementPerEconomy >= 0, MeteringErrorCode.INVALID_INPUT,
                    "teto de incremento negativo");
        }
    }

    /**
     * @param toBeApportioned consumo a ratear (m³)
     * @param perEconomy      m³ por economia efetivamente rateados
     * @param byMicro         rateio de cada micro (m³), na ordem da entrada
     * @param warnings        perdas por divisão inteira e por teto
     */
    public record Result(int toBeApportioned, int perEconomy, Map<String, Integer> byMicro,
                         List<CalculationWarning> warnings) {
        public Result {
            byMicro = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byMicro));
            warnings = List.copyOf(warnings);
        }
    }

    public static Result apportion(Input input) {
        MeteringInputException.notNull(input, "input");
        long microsTotal = input.micros().stream().mapToLong(Micro::billedConsumption).sum();
        int toBe = (int) Math.max(0, input.macroConsumption() - microsTotal);

        long economiesNotMetered = input.micros().stream().filter(m -> !m.metered()).mapToLong(Micro::economies).sum();
        long economiesMetered = input.micros().stream().filter(Micro::metered).mapToLong(Micro::economies).sum();
        boolean anyNotMetered = economiesNotMetered > 0;

        List<CalculationWarning> warnings = new ArrayList<>();
        long perEconomy;
        if (anyNotMetered) {
            perEconomy = toBe / economiesNotMetered;
            long lost = toBe - perEconomy * economiesNotMetered;
            if (lost > 0) {
                warnings.add(new CalculationWarning(WarningCode.APPORTIONMENT_REMAINDER_NOT_DISTRIBUTED,
                        lost + " m³ do rateio não distribuídos (divisão inteira entre " + economiesNotMetered
                                + " economias)"));
            }
        } else {
            long uncapped = toBe / economiesMetered;
            perEconomy = Math.min(uncapped, input.maxIncrementPerEconomy());
            long lost = toBe - perEconomy * economiesMetered;
            if (perEconomy < uncapped) {
                warnings.add(new CalculationWarning(WarningCode.APPORTIONMENT_CAPPED, "Rateio limitado a "
                        + input.maxIncrementPerEconomy() + " m³ por economia (seriam " + uncapped
                        + "): " + lost + " m³ não distribuídos"));
            } else if (lost > 0) {
                warnings.add(new CalculationWarning(WarningCode.APPORTIONMENT_REMAINDER_NOT_DISTRIBUTED,
                        lost + " m³ do rateio não distribuídos (divisão inteira entre " + economiesMetered
                                + " economias)"));
            }
        }

        Map<String, Integer> byMicro = new LinkedHashMap<>();
        for (Micro micro : input.micros()) {
            boolean receives = anyNotMetered ? !micro.metered() : true;
            byMicro.put(micro.id(), receives ? Math.toIntExact(perEconomy * micro.economies()) : 0);
        }
        return new Result(toBe, (int) perEconomy, byMicro, warnings);
    }
}
