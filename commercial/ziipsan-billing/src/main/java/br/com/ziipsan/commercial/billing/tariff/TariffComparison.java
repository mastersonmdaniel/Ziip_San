package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comparação entre a tarifa vigente e a proposta (P2.29): regra a regra,
 * preço a preço, com a variação absoluta e percentual. É a leitura que
 * sustenta a decisão sobre a proposta antes da simulação de impacto — não
 * calcula conta nenhuma (isso é do {@link TariffEngine}) e não grava nada.
 *
 * <p>Regra presente só de um lado entra na comparação como regra
 * acrescentada/removida, nunca é ignorada: uma categoria que some ou aparece
 * na proposta é exatamente o que a comparação precisa mostrar.
 *
 * <p>Faixas são emparelhadas pelo {@code toM3} (a faixa aberta é a de
 * {@code toM3} nulo), <b>não</b> pela posição: numa revisão que insira ou
 * remova uma faixa intermediária, emparelhar por posição desalinharia todos
 * os deltas seguintes. Faixa só de um lado entra com o preço do outro lado
 * nulo (acrescentada/removida), como as regras.
 */
public final class TariffComparison {

    /** Variação de uma faixa de preço. {@code deltaPct} nulo = base zero com preço novo (indefinido). */
    public record BlockDelta(Integer toM3, BigDecimal currentPrice, BigDecimal proposedPrice,
                             BigDecimal delta, BigDecimal deltaPct) {
    }

    /** Variação de uma regra de categoria/subcategoria. */
    public record RuleDelta(UUID categoryId, UUID subcategoryId, boolean inCurrent, boolean inProposed,
                            BigDecimal currentMinCharge, BigDecimal proposedMinCharge,
                            BigDecimal minChargeDelta, BigDecimal minChargeDeltaPct,
                            int currentMinConsumption, int proposedMinConsumption,
                            List<BlockDelta> blocks) {
        public RuleDelta {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    /** Variação da regra de tarifa social (percentuais, não preços). */
    public record SocialDelta(BigDecimal currentMinChargeDiscountPct, BigDecimal proposedMinChargeDiscountPct,
                              boolean presentInCurrent, boolean presentInProposed) {
    }

    public record Result(List<RuleDelta> rules, SocialDelta social) {
        public Result {
            rules = List.copyOf(rules);
        }
    }

    private TariffComparison() {
    }

    public static Result compare(TariffVersion current, TariffVersion proposed) {
        if (current == null || proposed == null) {
            throw new TariffConfigurationException("Comparação exige a tarifa vigente e a proposta");
        }
        List<RuleDelta> deltas = new ArrayList<>();
        for (CategoryRule cur : current.rules()) {
            CategoryRule prop = find(proposed, cur.categoryId(), cur.subcategoryId());
            deltas.add(ruleDelta(cur, prop));
        }
        for (CategoryRule prop : proposed.rules()) {
            if (find(current, prop.categoryId(), prop.subcategoryId()) == null) {
                deltas.add(ruleDelta(null, prop));
            }
        }
        SocialDelta social = new SocialDelta(
                current.socialRule() == null ? null : current.socialRule().minimumChargeDiscountPct(),
                proposed.socialRule() == null ? null : proposed.socialRule().minimumChargeDiscountPct(),
                current.socialRule() != null, proposed.socialRule() != null);
        return new Result(deltas, social);
    }

    private static RuleDelta ruleDelta(CategoryRule cur, CategoryRule prop) {
        UUID categoryId = cur != null ? cur.categoryId() : prop.categoryId();
        UUID subcategoryId = cur != null ? cur.subcategoryId() : prop.subcategoryId();
        List<BlockDelta> blocks = new ArrayList<>();
        List<TariffBlock> curBlocks = cur == null ? List.of() : cur.blocks();
        List<TariffBlock> propBlocks = prop == null ? List.of() : prop.blocks();
        // Emparelhamento por toM3 (único dentro da regra: CategoryRule exige
        // faixas estritamente crescentes, com só a última aberta).
        for (TariffBlock cb : curBlocks) {
            TariffBlock pb = findBlock(propBlocks, cb.toM3());
            blocks.add(blockDelta(cb, pb));
        }
        for (TariffBlock pb : propBlocks) {
            if (findBlock(curBlocks, pb.toM3()) == null) {
                blocks.add(blockDelta(null, pb));
            }
        }
        BigDecimal curMin = cur == null ? null : cur.minCharge();
        BigDecimal propMin = prop == null ? null : prop.minCharge();
        return new RuleDelta(categoryId, subcategoryId, cur != null, prop != null, curMin, propMin,
                curMin == null || propMin == null ? null : propMin.subtract(curMin),
                deltaPct(curMin, propMin),
                cur == null ? -1 : cur.minConsumption(), prop == null ? -1 : prop.minConsumption(), blocks);
    }

    private static BlockDelta blockDelta(TariffBlock cb, TariffBlock pb) {
        Integer toM3 = pb != null ? pb.toM3() : cb.toM3();
        BigDecimal curPrice = cb == null ? null : cb.pricePerM3();
        BigDecimal propPrice = pb == null ? null : pb.pricePerM3();
        BigDecimal delta = curPrice == null || propPrice == null ? null : propPrice.subtract(curPrice);
        return new BlockDelta(toM3, curPrice, propPrice, delta, deltaPct(curPrice, propPrice));
    }

    private static TariffBlock findBlock(List<TariffBlock> blocks, Integer toM3) {
        for (TariffBlock b : blocks) {
            if (toM3 == null ? b.toM3() == null : toM3.equals(b.toM3())) {
                return b;
            }
        }
        return null;
    }

    /** Variação percentual com 2 casas, HALF_UP; base zero: 0,00 se o novo também é zero, nulo se o novo é positivo. */
    static BigDecimal deltaPct(BigDecimal current, BigDecimal proposed) {
        if (current == null || proposed == null) {
            return null;
        }
        if (current.signum() == 0) {
            return proposed.signum() == 0 ? Money.ZERO : null;
        }
        return proposed.subtract(current).multiply(Money.HUNDRED)
                .divide(current, 2, RoundingMode.HALF_UP);
    }

    private static CategoryRule find(TariffVersion version, UUID categoryId, UUID subcategoryId) {
        for (CategoryRule r : version.rules()) {
            if (r.categoryId().equals(categoryId)
                    && (subcategoryId == null ? r.subcategoryId() == null : subcategoryId.equals(r.subcategoryId()))) {
                return r;
            }
        }
        return null;
    }
}
