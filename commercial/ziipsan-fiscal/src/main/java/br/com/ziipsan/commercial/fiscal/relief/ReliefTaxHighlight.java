package br.com.ziipsan.commercial.fiscal.relief;

import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * DESTAQUE de CBS/IBS "por fora" (F-05 CORRIGIDO pelas fontes primarias —
 * NT 2.3 e 6.8.1): CBS/IBS NAO sao "item informativo". Sao tributos que nao
 * integram a propria base, sao acrescidos ao valor da operacao e devem ser
 * DESTACADOS na fatura e no documento fiscal sobre a base desonerada, sem
 * homologacao do montante pela reguladora.
 *
 * <p><b>Correccao R-04 (REVISAO-PA-02) — FONTE UNICA do destaque:</b> esta
 * classe e a UNICA produtora do destaque CBS/IBS (montante + flag) na
 * emissao fiscal. O emissor vivo ({@code FiscalIssuanceService}) consome
 * {@link #cbsIbsForCompetence} para as linhas CBS/IBS do documento e para
 * os montantes do XML — nao calcula CBS/IBS por nenhuma outra via. A
 * decisao "informativo ou destaque" deriva de UMA primitiva de vigencia,
 * {@link TaxRateValidity#cbsIbsInformative(LocalDate)} (P2.4): competencia
 * ate 31/12/2026 (ano de teste) ⇒ {@code informative = true}; competencia a
 * partir de 01/01/2027 ⇒ {@code informative = false} (destaque por fora).
 * Nao ha segunda via que possa divergir nem somar de novo: o total da
 * conta NUNCA absorve o destaque (o motor fiscal vivo exclui CBS/IBS do
 * liquido) e o valor a pagar e total + Σ destaques, contados UMA vez.
 *
 * <p>As aliquotas vem sempre de parametro do tenant ({@code tax_rates});
 * a NT nao publica aliquota de CBS/IBS — P-02-11. Nenhum literal de
 * aliquota vive aqui.
 */
public final class ReliefTaxHighlight {

    /** Linha de tributo destacado por fora (ou informativo, no ano de teste). */
    public record HighlightedTax(String taxCode, BigDecimal rateFraction, BigDecimal base,
                                 BigDecimal amount, boolean informative) {
        public HighlightedTax {
            Objects.requireNonNull(taxCode, "codigo do tributo e obrigatorio");
            Objects.requireNonNull(rateFraction, "aliquota e obrigatoria");
            Objects.requireNonNull(base, "base e obrigatoria");
            Objects.requireNonNull(amount, "montante destacado e obrigatorio");
        }
    }

    private ReliefTaxHighlight() {
    }

    /** Montante destacado de um tributo pos-corte: base desonerada x aliquota (fraccao). */
    public static HighlightedTax highlight(String taxCode, BigDecimal rateFraction, BigDecimal relievedBase) {
        return highlight(taxCode, rateFraction, relievedBase, false);
    }

    private static HighlightedTax highlight(String taxCode, BigDecimal rateFraction, BigDecimal base,
                                            boolean informative) {
        Objects.requireNonNull(rateFraction, "aliquota e obrigatoria");
        Objects.requireNonNull(base, "base e obrigatoria");
        if (rateFraction.signum() < 0 || rateFraction.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("aliquota (fraccao) fora de [0, 1]: " + rateFraction);
        }
        BigDecimal amount = base.multiply(rateFraction).setScale(2, RoundingMode.HALF_UP);
        return new HighlightedTax(taxCode, rateFraction, base, amount, informative);
    }

    /** Destaques de CBS e IBS sobre a base desonerada (pos-corte: informative = false). */
    public static List<HighlightedTax> cbsIbs(BigDecimal relievedBase, BigDecimal cbsRateFraction,
                                              BigDecimal ibsRateFraction) {
        return List.of(highlight("CBS", cbsRateFraction, relievedBase),
                highlight("IBS", ibsRateFraction, relievedBase));
    }

    /**
     * A VIA UNICA de destaque para a emissao fiscal (R-04): montante e flag
     * decididos aqui, uma so vez, a partir da competencia. O flag vem de
     * {@link TaxRateValidity#cbsIbsInformative(LocalDate)} — a mesma
     * primitiva que a P2.4 ja usava — pelo que 2026 continua informativo e
     * 2027+ e destaque por fora, sem dupla contagem possivel entre vias.
     */
    public static List<HighlightedTax> cbsIbsForCompetence(BigDecimal base, BigDecimal cbsRateFraction,
                                                           BigDecimal ibsRateFraction,
                                                           LocalDate competence) {
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        boolean informative = TaxRateValidity.cbsIbsInformative(competence);
        return List.of(highlight("CBS", cbsRateFraction, base, informative),
                highlight("IBS", ibsRateFraction, base, informative));
    }

    /**
     * Valor a pagar = total da conta (INALTERADO) + destaques por fora.
     * O total da conta nunca absorve o destaque: sao grandezas separadas.
     * Linhas informativas (ano de teste) NAO somam ao valor a pagar.
     */
    public static BigDecimal payableTotal(BigDecimal billTotal, List<HighlightedTax> highlights) {
        Objects.requireNonNull(billTotal, "total da conta e obrigatorio");
        BigDecimal payable = billTotal;
        for (HighlightedTax tax : highlights) {
            if (!tax.informative()) {
                payable = payable.add(tax.amount());
            }
        }
        return payable;
    }
}
