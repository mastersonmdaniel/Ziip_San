package br.com.ziipsan.commercial.billing.tariff;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cópia de uma estrutura tarifária existente como ponto de partida da nova
 * versão (P2.29): mesmos preços, regras e regra social, com id e vigência
 * novos. A cópia é profunda nos objetos (novas {@link CategoryRule} e
 * {@link TariffBlock}), para que a proposta editada a partir dela nunca seja
 * confundida com a versão de origem — os dois objetos passam a ter vidas
 * independentes, ainda que os records já sejam imutáveis.
 */
public final class TariffVersionCopier {

    private TariffVersionCopier() {
    }

    public static TariffVersion copy(TariffVersion source, UUID newId, LocalDate validFrom, LocalDate validTo) {
        if (source == null) {
            throw new TariffConfigurationException("Cópia sem versão de origem");
        }
        List<CategoryRule> rules = new ArrayList<>();
        for (CategoryRule rule : source.rules()) {
            List<TariffBlock> blocks = new ArrayList<>();
            for (TariffBlock block : rule.blocks()) {
                blocks.add(new TariffBlock(block.toM3(), block.pricePerM3()));
            }
            rules.add(new CategoryRule(rule.categoryId(), rule.subcategoryId(), rule.minConsumption(),
                    rule.minCharge(), rule.economyFactor(), blocks));
        }
        SocialTariffRule social = source.socialRule() == null ? null
                : new SocialTariffRule(source.socialRule().minimumChargeDiscountPct(), source.socialRule().bands());
        return new TariffVersion(newId, validFrom, validTo, rules, social);
    }
}
