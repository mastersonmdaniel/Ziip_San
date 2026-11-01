package br.com.sigsan.commercial.billing.consumption;

import br.com.sigsan.commercial.billing.tariff.CategoryRule;
import br.com.sigsan.commercial.billing.tariff.TariffGroup;
import br.com.sigsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.sigsan.commercial.billing.tariff.TariffEngine;
import br.com.sigsan.commercial.billing.tariff.TariffVersion;

import java.util.List;

/**
 * Ajuste do consumo ao múltiplo do número de economias (relatório 02, seção 3.8), feito antes da
 * tarifa: nunca se fatura fração de economia, e o resto "se perde".
 *
 * <pre>
 *   mín   = consumo mínimo da ligação (TariffEngine.minimumConsumption)
 *   exc   = consumo − mín
 *   exc &gt; 0:  porEco = exc DIV E;  ajustado = Σ(porEco × econ(c) + mín(c) × econ(c));  resto = consumo − ajustado
 *   exc ≤ 0:  resto = consumo MOD E
 *   faturado = consumo − resto
 * </pre>
 *
 * <p>Premissa documentada: a especificação não diz como o fator de economias interage com este
 * ajuste. Com fator, {@code mín} usa o fator e {@code ajustado} usa as economias reais, o que pode
 * dar resto fora de {@code [0, E)}. Nesse caso o método <b>falha</b> em vez de devolver um consumo
 * inventado (ver README).
 */
public final class EconomyMultiple {

    private EconomyMultiple() {
    }

    /** Consumo faturado após o ajuste (divisões inteiras truncadas). */
    public static int adjust(int consumption, TariffVersion version, List<TariffGroup> groups) {
        if (consumption < 0) {
            throw new IllegalArgumentException("Consumo negativo: " + consumption);
        }
        int economies = groups.stream().mapToInt(TariffGroup::economies).sum();
        if (economies < 1) {
            throw new IllegalArgumentException("A unidade não tem economias");
        }
        if (economies == 1) {
            return consumption;
        }
        int propertyMinimum = TariffEngine.minimumConsumption(version, groups);
        int excess = consumption - propertyMinimum;
        int rest;
        if (excess > 0) {
            int perEconomy = excess / economies;
            long adjusted = 0;
            for (TariffGroup g : groups) {
                CategoryRule rule = version.ruleFor(g.categoryId(), g.subcategoryId());
                adjusted += (long) perEconomy * g.economies() + (long) rule.minConsumption() * g.economies();
            }
            rest = (int) (consumption - adjusted);
        } else {
            rest = consumption % economies;
        }
        if (rest < 0 || rest >= economies) {
            throw new TariffConfigurationException("O fator de economias torna o ajuste ao múltiplo de economias "
                    + "inconsistente (resto " + rest + " fora de [0, " + economies + ")); revisar a regra do tenant");
        }
        return consumption - rest;
    }
}
