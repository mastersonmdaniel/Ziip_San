package br.com.ziipsan.commercial.collection.dunning.campaign;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Programa de regularizacao por ato do ente (CAD-73), como campanha em
 * {@code campaign.*}: vigencia, ato normativo citado, aprovacao dupla,
 * publico-alvo por faixa de atraso e categoria, abatimento sobre multa,
 * juros e correcao, parcelas, termo de adesao, perda do beneficio no
 * descumprimento e relatorio de adesao e recuperacao.
 *
 * <p>Abatimento do PRINCIPAL fora do limite do perfil e recusado JA NA
 * CRIACAO da campanha — pelo caminho de criacao
 * {@link #createWithinPrincipalLimit} (correcao D6 da revisao cruzada:
 * antes a recusa so acontecia sob demanda, em {@code discountsFor}) — e
 * revalidado na adesao. O construtor canonico e o caminho de
 * REIDRATACAO da persistencia (linha ja criada e validada).
 */
public record RegularizationCampaign(
        UUID id,
        String name,
        LocalDate validFrom,
        LocalDate validTo,
        String normativeAct,
        UUID proposedBy,
        UUID approvedBy,
        int targetMinOverdueDays,
        int targetMaxOverdueDays,
        Set<String> targetCategories,
        BigDecimal fineDiscountPct,
        BigDecimal interestDiscountPct,
        BigDecimal correctionDiscountPct,
        BigDecimal principalDiscountPct,
        int maxInstallments,
        String adhesionTermTemplateCode,
        boolean benefitLostOnDefault) {

    public RegularizationCampaign {
        if (id == null || name == null || name.isBlank()
                || validFrom == null || validTo == null || validTo.isBefore(validFrom)
                || normativeAct == null || normativeAct.isBlank()
                || adhesionTermTemplateCode == null || adhesionTermTemplateCode.isBlank()) {
            throw new IllegalArgumentException(
                    "campanha exige nome, vigencia, ATO NORMATIVO CITADO e modelo do termo de adesao");
        }
        if (proposedBy == null || approvedBy == null) {
            throw new IllegalArgumentException("campanha exige proponente e aprovador (aprovacao dupla)");
        }
        if (proposedBy.equals(approvedBy)) {
            throw new IllegalArgumentException("aprovacao dupla: quem aprova nao pode ser quem propoe");
        }
        targetCategories = targetCategories == null ? Set.of() : Set.copyOf(targetCategories);
        for (BigDecimal pct : List.of(fineDiscountPct, interestDiscountPct,
                correctionDiscountPct, principalDiscountPct)) {
            if (pct == null || pct.signum() < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("percentual de abatimento deve estar entre 0 e 100");
            }
        }
        if (targetMinOverdueDays < 0 || targetMaxOverdueDays < targetMinOverdueDays || maxInstallments < 1) {
            throw new IllegalArgumentException("faixa de atraso e maximo de parcelas invalidos");
        }
    }

    /**
     * Caminho de CRIACAO da campanha (D6): constroi e valida o teto de
     * abatimento do principal contra o limite do perfil
     * ({@code inst.principal_discount_limit_pct}) NO ATO — uma campanha
     * fora do limite nem nasce, nao apenas falha ao calcular desconto.
     */
    public static RegularizationCampaign createWithinPrincipalLimit(
            UUID id, String name, LocalDate validFrom, LocalDate validTo, String normativeAct,
            UUID proposedBy, UUID approvedBy, int targetMinOverdueDays, int targetMaxOverdueDays,
            Set<String> targetCategories, BigDecimal fineDiscountPct, BigDecimal interestDiscountPct,
            BigDecimal correctionDiscountPct, BigDecimal principalDiscountPct, int maxInstallments,
            String adhesionTermTemplateCode, boolean benefitLostOnDefault,
            BigDecimal principalDiscountLimitPct) {
        var campaign = new RegularizationCampaign(id, name, validFrom, validTo, normativeAct,
                proposedBy, approvedBy, targetMinOverdueDays, targetMaxOverdueDays, targetCategories,
                fineDiscountPct, interestDiscountPct, correctionDiscountPct, principalDiscountPct,
                maxInstallments, adhesionTermTemplateCode, benefitLostOnDefault);
        campaign.requireWithinPrincipalLimit(principalDiscountLimitPct);
        return campaign;
    }

    /** Vigente na data (intervalo fechado). */
    public boolean activeAt(LocalDate date) {
        return date != null && !date.isBefore(validFrom) && !date.isAfter(validTo);
    }

    /** Publico-alvo: faixa de atraso (dias) e categoria da unidade. */
    public boolean targets(int overdueDays, String category) {
        return overdueDays >= targetMinOverdueDays && overdueDays <= targetMaxOverdueDays
                && (targetCategories.isEmpty() || targetCategories.contains(category));
    }

    /** Recusa o abatimento do principal acima do limite do perfil (inst.principal_discount_limit_pct). */
    public void requireWithinPrincipalLimit(BigDecimal principalDiscountLimitPct) {
        if (principalDiscountPct.compareTo(principalDiscountLimitPct) > 0) {
            throw new ApiException.Validation(
                    "Campanha com abatimento do principal de " + principalDiscountPct
                            + "% FORA DO LIMITE do perfil (" + principalDiscountLimitPct + "%): recusada.",
                    List.of(), List.of());
        }
    }

    /** Componentes de um debito para o calculo do abatimento da campanha. */
    public record DebtComponents(BigDecimal principal, BigDecimal fine, BigDecimal interest, BigDecimal correction) {
        public DebtComponents {
            if (principal == null || fine == null || interest == null || correction == null
                    || principal.signum() < 0 || fine.signum() < 0
                    || interest.signum() < 0 || correction.signum() < 0) {
                throw new IllegalArgumentException("componentes do debito sao obrigatorios e nao negativos");
            }
        }

        public BigDecimal total() {
            return principal.add(fine).add(interest).add(correction);
        }
    }

    /** Abatimentos calculados pela campanha sobre um debito. */
    public record DiscountResult(BigDecimal fineDiscount, BigDecimal interestDiscount,
                                 BigDecimal correctionDiscount, BigDecimal principalDiscount,
                                 BigDecimal discountedTotal) {
        public BigDecimal totalDiscount() {
            return fineDiscount.add(interestDiscount).add(correctionDiscount).add(principalDiscount);
        }
    }

    private static BigDecimal pctOf(BigDecimal base, BigDecimal pct) {
        return base.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /** Aplica os percentuais da campanha (multa, juros, correcao e principal limitado). */
    public DiscountResult discountsFor(DebtComponents debt, BigDecimal principalDiscountLimitPct) {
        requireWithinPrincipalLimit(principalDiscountLimitPct);
        BigDecimal fineDiscount = pctOf(debt.fine(), fineDiscountPct);
        BigDecimal interestDiscount = pctOf(debt.interest(), interestDiscountPct);
        BigDecimal correctionDiscount = pctOf(debt.correction(), correctionDiscountPct);
        BigDecimal principalDiscount = pctOf(debt.principal(), principalDiscountPct);
        BigDecimal discounted = debt.total()
                .subtract(fineDiscount).subtract(interestDiscount)
                .subtract(correctionDiscount).subtract(principalDiscount);
        return new DiscountResult(fineDiscount, interestDiscount, correctionDiscount,
                principalDiscount, discounted);
    }

    /** Adesao registrada: termo gerado/assinado e o acordo criado a partir dela. */
    public record Adherence(UUID unitId, UUID customerId, LocalDate adheredAt,
                            UUID agreementId, BigDecimal recoveredAmount, boolean defaulted) {
        public Adherence {
            if (unitId == null || customerId == null || adheredAt == null || agreementId == null
                    || recoveredAmount == null || recoveredAmount.signum() < 0) {
                throw new IllegalArgumentException("adesao exige unidade, titular, data, acordo e recuperado");
            }
        }

        /** Perda do beneficio: so existe se a campanha a preve E houve descumprimento. */
        public boolean benefitLost(RegularizationCampaign campaign) {
            return campaign.benefitLostOnDefault() && defaulted;
        }
    }

    /** Relatorio de adesao e recuperacao da campanha. */
    public record Report(int adherents, BigDecimal totalRecovered, int defaults, int benefitsLost) {
    }

    public static Report report(RegularizationCampaign campaign, List<Adherence> adherences) {
        BigDecimal recovered = adherences.stream().map(Adherence::recoveredAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int defaults = (int) adherences.stream().filter(Adherence::defaulted).count();
        int lost = (int) adherences.stream().filter(a -> a.benefitLost(campaign)).count();
        return new Report(adherences.size(), recovered, defaults, lost);
    }
}
