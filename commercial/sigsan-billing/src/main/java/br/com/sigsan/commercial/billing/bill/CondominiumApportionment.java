package br.com.sigsan.commercial.billing.bill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rateio do consumo de condomínio (relatório 02, seção 3.11): o hidrômetro geral (macro) mede mais
 * que a soma dos individuais (micros); a diferença é rateada entre as economias.
 *
 * <pre>
 *   aRatear = MAX(0, consumoMacro − Σ consumoFaturado dos micros)
 *   há micro NÃO medido:  rateia SÓ entre os não medidos:  porEconomia = aRatear DIV economiasNãoMedidas
 *   todos medidos:        porEconomia = aRatear DIV economias, limitado a incrementoMáximo
 *   rateio(micro)  = porEconomia × economias do micro      (micro medido, havendo não medido: 0)
 * </pre>
 * A divisão é inteira: o resto de m³ não é rateado.
 *
 * <p>Premissas documentadas: (1) {@code aRatear} nunca é negativo, como o caso RC-02 do levantamento
 * exige; por isso o parâmetro de decremento máximo do legado é inalcançável e não é modelado;
 * (2) o rateio por <b>valor</b> (R$) é outra operação: use {@code Apportionment.proportional}.
 */
public final class CondominiumApportionment {

    /** Unidade ligada ao condomínio. */
    public record Micro(UUID id, int billedConsumption, int economies, boolean metered) {
        public Micro {
            if (id == null || billedConsumption < 0 || economies < 1) {
                throw new IllegalArgumentException("Micro inválido: " + id);
            }
        }
    }

    /** Parcela rateada a um micro. */
    public record Share(UUID microId, int consumption) {
    }

    private CondominiumApportionment() {
    }

    /**
     * @param maxIncrementPerEconomy limite do rateio por economia quando todos os micros são medidos
     *                               (parâmetro do tenant; obrigatório)
     */
    public static List<Share> apportion(int macroConsumption, List<Micro> micros, Integer maxIncrementPerEconomy) {
        if (micros.isEmpty()) {
            throw new IllegalArgumentException("Condomínio sem unidades");
        }
        if (maxIncrementPerEconomy == null || maxIncrementPerEconomy < 0) {
            throw new IllegalArgumentException("O incremento máximo de rateio por economia deve ser informado");
        }
        int billed = micros.stream().mapToInt(Micro::billedConsumption).sum();
        int toApportion = Math.max(0, macroConsumption - billed);
        boolean anyNotMetered = micros.stream().anyMatch(m -> !m.metered());

        int perEconomy;
        if (anyNotMetered) {
            int economies = micros.stream().filter(m -> !m.metered()).mapToInt(Micro::economies).sum();
            perEconomy = toApportion / economies;
        } else {
            int economies = micros.stream().mapToInt(Micro::economies).sum();
            perEconomy = Math.min(toApportion / economies, maxIncrementPerEconomy);
        }

        List<Share> shares = new ArrayList<>();
        for (Micro m : micros) {
            boolean receives = !anyNotMetered || !m.metered();
            shares.add(new Share(m.id(), receives ? perEconomy * m.economies() : 0));
        }
        return shares;
    }
}
