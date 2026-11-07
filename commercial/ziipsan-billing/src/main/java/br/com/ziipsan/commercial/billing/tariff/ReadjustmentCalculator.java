package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aplica o percentual do reajuste sobre uma estrutura tarifária, gerando a
 * <b>nova versão</b> (P2.29): cada preço (parcela fixa e preço do m³ de cada
 * faixa, NUMERIC(14,4) no banco) é multiplicado por
 * {@code 1 + percentualAplicado/100} e arredondado a 4 casas, HALF_UP — a
 * mesma convenção de arredondamento do {@link TariffEngine} para preços.
 *
 * <p>O que NÃO é reajustado: percentuais. O desconto da tarifa social
 * (percentual sobre a parcela fixa e percentuais das bandas) é política de
 * benefício, não preço: multiplicá-lo pelo índice mudaria o benefício sem
 * ato que o mandasse. Consumo mínimo (m³) e fator de economias também ficam:
 * são grandezas físicas/estruturais, não valores.
 *
 * <p>Classe pura, sem I/O: a versão de origem nunca é alterada (records são
 * imutáveis e o resultado é um objeto novo, com o id novo informado).
 */
public final class ReadjustmentCalculator {

    /** Escala dos preços da tabela tarifária (NUMERIC(14,4) da V4_1). */
    public static final int PRICE_SCALE = 4;

    private ReadjustmentCalculator() {
    }

    /** Um preço reajustado: {@code preço × (1 + pct/100)}, 4 casas, HALF_UP. */
    public static BigDecimal adjustedPrice(BigDecimal price, BigDecimal appliedPct) {
        if (price == null || price.signum() < 0) {
            throw new TariffConfigurationException("Preço ausente ou negativo no reajuste: " + price);
        }
        if (appliedPct == null || appliedPct.compareTo(new BigDecimal("-100")) <= 0) {
            throw new TariffConfigurationException(
                    "Percentual aplicado deve ser informado e maior que -100%: " + appliedPct);
        }
        BigDecimal factor = BigDecimal.ONE.add(appliedPct.movePointLeft(2));
        return price.multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Nova versão = estrutura de {@code source} com todos os preços
     * multiplicados pelo percentual aplicado da {@link ReadjustFormula}.
     *
     * @param newId     id da nova versão (a rastreabilidade vai para as linhas
     *                  da conta quando ela vigorar)
     * @param validFrom início da vigência da nova versão
     * @param validTo   fim da vigência; {@code null} = aberta (o chamador
     *                  fecha a versão anterior — a não sobreposição é da
     *                  EXCLUDE da V4_1 e conferida em
     *                  {@link TariffManagementService})
     * @return o percentual aplicado, embutido na versão devolvida apenas via
     *         preços; quem chama registra o percentual e o método em
     *         {@link VersionChangeMetadata}
     */
    public static TariffVersion apply(TariffVersion source, UUID newId, LocalDate validFrom, LocalDate validTo,
                                      ReadjustFormula formula, BigDecimal indexPct, BigDecimal factorXPct) {
        if (source == null) {
            throw new TariffConfigurationException("Reajuste sem versão de origem");
        }
        if (formula == null) {
            throw new TariffConfigurationException(
                    "Reajuste sem a fórmula (tariff.readjust_formula): a fórmula não se presume");
        }
        BigDecimal appliedPct = formula.appliedPct(indexPct, factorXPct);
        List<CategoryRule> rules = new ArrayList<>();
        for (CategoryRule rule : source.rules()) {
            List<TariffBlock> blocks = new ArrayList<>();
            for (TariffBlock block : rule.blocks()) {
                blocks.add(new TariffBlock(block.toM3(), adjustedPrice(block.pricePerM3(), appliedPct)));
            }
            rules.add(new CategoryRule(rule.categoryId(), rule.subcategoryId(), rule.minConsumption(),
                    adjustedPrice(rule.minCharge(), appliedPct), rule.economyFactor(), blocks));
        }
        return new TariffVersion(newId, validFrom, validTo, rules, source.socialRule());
    }

    /** Percentual aplicado de uma fórmula, sem aplicar (para o método/relato). */
    public static BigDecimal appliedPct(ReadjustFormula formula, BigDecimal indexPct, BigDecimal factorXPct) {
        if (formula == null) {
            throw new TariffConfigurationException(
                    "Reajuste sem a fórmula (tariff.readjust_formula): a fórmula não se presume");
        }
        return formula.appliedPct(indexPct, factorXPct);
    }
}
