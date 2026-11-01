package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Regra de uma categoria (ou subcategoria) dentro de uma versão de tarifa.
 *
 * <p>A parcela fixa cobre os primeiros {@code minConsumption} m³ por economia. Dois modelos cabem
 * aqui, e o tenant escolhe pelos parâmetros:
 * <ul>
 *   <li><b>tarifa por consumo mínimo (com franquia)</b>: {@code minConsumption > 0};</li>
 *   <li><b>tarifa básica (sem franquia)</b>: {@code minConsumption == 0}; a parcela fixa é só a tarifa
 *       básica e todo m³ vai para as faixas.</li>
 * </ul>
 *
 * @param subcategoryId {@code null} = regra da categoria inteira (serve de reserva para subcategorias
 *                      sem regra própria)
 * @param economyFactor "fator de economias" (economias equivalentes); {@code null} = usa o número real
 *                      de economias. Só afeta o consumo mínimo da ligação
 *                      ({@link TariffEngine#minimumConsumption}); ver README
 * @param blocks        faixas em ordem crescente; só a última pode ser aberta
 */
public record CategoryRule(UUID categoryId, UUID subcategoryId, int minConsumption, BigDecimal minCharge,
                           BigDecimal economyFactor, List<TariffBlock> blocks) {

    public CategoryRule {
        if (categoryId == null) {
            throw new TariffConfigurationException("Regra sem categoria");
        }
        if (minConsumption < 0) {
            throw new TariffConfigurationException("Consumo mínimo negativo: " + minConsumption);
        }
        if (minCharge == null || minCharge.signum() < 0) {
            throw new TariffConfigurationException("Tarifa mínima ausente ou negativa na categoria " + categoryId);
        }
        if (economyFactor != null && economyFactor.signum() <= 0) {
            throw new TariffConfigurationException("Fator de economias deve ser positivo: " + economyFactor);
        }
        if (blocks == null) {
            throw new TariffConfigurationException("Faixas ausentes na categoria " + categoryId);
        }
        blocks = List.copyOf(blocks);
        Integer previous = null;
        for (int i = 0; i < blocks.size(); i++) {
            Integer to = blocks.get(i).toM3();
            if (to == null) {
                if (i != blocks.size() - 1) {
                    throw new TariffConfigurationException(
                            "Só a última faixa pode ser aberta (categoria " + categoryId + ")");
                }
            } else {
                if (previous != null && to <= previous) {
                    throw new TariffConfigurationException(
                            "Faixas devem ser estritamente crescentes (categoria " + categoryId + ")");
                }
                previous = to;
            }
        }
    }

    /** Regra da categoria inteira, sem subcategoria. */
    public static CategoryRule forCategory(UUID categoryId, int minConsumption, BigDecimal minCharge,
                                           BigDecimal economyFactor, List<TariffBlock> blocks) {
        return new CategoryRule(categoryId, null, minConsumption, minCharge, economyFactor, blocks);
    }
}
