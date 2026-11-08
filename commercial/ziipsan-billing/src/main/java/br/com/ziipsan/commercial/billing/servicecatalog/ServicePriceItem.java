package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Item da tabela de preços de serviços (ED-34), com vigência por tenant. O tipo de OS
 * da P1.31 (V9_1, {@code service_types.price_item_ref}) guarda SÓ a referência ({@link #code()});
 * preço, vigência e lançamento vivem aqui.
 *
 * <p>Regras (acceptance ED-34 / Lei 11.445/2007, art. 10-A, II):
 * <ul>
 *   <li>{@code sharingPct} ({@code rev.sharing_pct}) é obrigatório para receita complementar,
 *       acessória ou de projeto associado, e fica em [0,100] sempre que presente;</li>
 *   <li>se {@code homologationRequired} ({@code rev.homologation_required}), o item só é
 *       faturável com {@code homologationRef} registrada (ato/processo da agência — valor
 *       fornecido pelo tenant/agência, nunca inventado aqui);</li>
 *   <li>preço unitário positivo, com escala monetária (Money).</li>
 * </ul>
 */
public record ServicePriceItem(String code, String description, RevenueNature revenueNature,
                               BigDecimal unitPrice, LocalDate validFrom, LocalDate validTo,
                               boolean homologationRequired, String homologationRef,
                               BigDecimal sharingPct) {
    public ServicePriceItem {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do item de preco e obrigatorio");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("descricao do item e obrigatoria");
        Objects.requireNonNull(revenueNature, "natureza da receita e obrigatoria");
        Objects.requireNonNull(unitPrice, "preco unitario e obrigatorio");
        if (unitPrice.signum() <= 0) throw new IllegalArgumentException("preco unitario deve ser positivo: " + unitPrice);
        unitPrice = Money.roundHalfUp(unitPrice);
        Objects.requireNonNull(validFrom, "inicio de vigencia e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invalida");
        if (sharingPct != null && (sharingPct.signum() < 0 || sharingPct.compareTo(Money.HUNDRED) > 0))
            throw new IllegalArgumentException("rev.sharing_pct fora de [0,100]: " + sharingPct);
        if (revenueNature.requiresSharingPct() && sharingPct == null)
            throw new IllegalArgumentException("receita " + revenueNature + " exige rev.sharing_pct "
                    + "(Lei 11.445/2007, art. 10-A, II): item " + code);
        if (homologationRef != null && homologationRef.isBlank())
            throw new IllegalArgumentException("homologationRef em branco nao registra homologacao");
    }

    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    /** Faturável = vigente tratado pela tabela + homologação registrada quando exigida. */
    public boolean billable() {
        return !homologationRequired || homologationRef != null;
    }
}
