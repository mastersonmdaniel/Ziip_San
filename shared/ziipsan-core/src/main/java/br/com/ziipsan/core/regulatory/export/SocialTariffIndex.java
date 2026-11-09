package br.com.ziipsan.core.regulatory.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Indice de Tarifa Social (ITS) mensal (ED-97, CAD-92): economias
 * residenciais BENEFICIADAS sobre economias residenciais ATIVAS, no mes.
 * Formula literal do aceite; escala 4, HALF_UP (padrao de arredondamento
 * dos indicadores do projeto). Denominador zero e recusado — indice sem
 * base nao se inventa.
 *
 * <p>Correcao D2 (REVISAO-ED-97): os invariantes valem POR CONSTRUCAO,
 * no construtor canonico — nao so na fabrica {@link #of}: denominador
 * positivo, beneficiadas em [0, ativas] e valor coerente com a razao
 * (escala 4, HALF_UP). Um ITS materializado por outra via (ex.: leitura
 * de persistencia) com indice &gt; 1 ou valor divergente da razao e
 * recusado na construcao.</p>
 */
public record SocialTariffIndex(YearMonth month, long benefitedResidentialEconomies,
                                long activeResidentialEconomies, BigDecimal value) {

    public SocialTariffIndex {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(value, "value");
        if (activeResidentialEconomies <= 0)
            throw new IllegalArgumentException("ITS sem economias ativas no mes " + month);
        if (benefitedResidentialEconomies < 0 || benefitedResidentialEconomies > activeResidentialEconomies)
            throw new IllegalArgumentException("beneficiadas fora de [0, ativas]: "
                    + benefitedResidentialEconomies + "/" + activeResidentialEconomies);
        BigDecimal expected = BigDecimal.valueOf(benefitedResidentialEconomies)
                .divide(BigDecimal.valueOf(activeResidentialEconomies), 4, RoundingMode.HALF_UP);
        if (!expected.equals(value))
            throw new IllegalArgumentException("valor do ITS incoerente com beneficiadas/ativas: "
                    + value + " != " + expected);
    }

    public static SocialTariffIndex of(YearMonth month, long benefited, long active) {
        if (active <= 0) throw new IllegalArgumentException("ITS sem economias ativas no mes " + month);
        if (benefited < 0 || benefited > active)
            throw new IllegalArgumentException("beneficiadas fora de [0, ativas]: " + benefited + "/" + active);
        BigDecimal v = BigDecimal.valueOf(benefited)
                .divide(BigDecimal.valueOf(active), 4, RoundingMode.HALF_UP);
        return new SocialTariffIndex(month, benefited, active, v);
    }
}
