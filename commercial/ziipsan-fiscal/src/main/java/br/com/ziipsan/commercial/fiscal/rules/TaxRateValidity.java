package br.com.ziipsan.commercial.fiscal.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Seleção de alíquota por vigência (P1.8; P2.4: sem sobreposição de vigências).
 *
 * <p>Pura de propósito, como o {@link TaxEngine}: a mesma seleção roda no JUnit, no corpus
 * de referência e, mais tarde, no serviço JDBC. A sobreposição é impedida no banco pela
 * restrição de exclusão de {@code tax_rates} (V4_5); aqui ela é detectada como erro em vez
 * de resolvida em silêncio, porque escolher uma das duas alíquotas seria inventar a regra.
 *
 * <p>Alíquota ausente para a competência é erro explícito e a conta não é emitida (P2.4).
 * Alíquotas nunca vêm de constante: vêm das linhas de {@code tax_rates} do tenant.
 */
public final class TaxRateValidity {

    /**
     * Fim do ano de teste da reforma (LC 214/2025; fato normativo do plano, P2.4): em
     * 2026 as alíquotas de CBS e IBS são de teste, destacadas como informativas, sem
     * compor o recolhimento; a partir de 01/01/2027 a CBS plena deixa de ser informativa.
     * A data é normativa, não uma alíquota: as alíquotas continuam vindo só de
     * {@code tax_rates}, nunca de constante (R40, ADR-0002).
     */
    public static final LocalDate TEST_YEAR_END = LocalDate.of(2026, 12, 31);

    private TaxRateValidity() {
    }

    /** Uma linha de {@code tax_rates}, como o repositório a entrega. */
    public record TaxRateRow(String taxCode, String scope, LocalDate validFrom, LocalDate validTo, BigDecimal rate) {
    }

    /**
     * Alíquota vigente de {@code taxCode} na competência. Intervalo fechado
     * [validFrom, validTo]; validTo nulo = vigência aberta.
     *
     * @throws IllegalStateException sem linha vigente, ou com mais de uma (sobreposição)
     */
    public static BigDecimal rateFor(List<TaxRateRow> rows, String taxCode, LocalDate competence) {
        Objects.requireNonNull(rows, "As linhas de aliquota sao obrigatorias");
        Objects.requireNonNull(taxCode, "O codigo do tributo e obrigatorio");
        Objects.requireNonNull(competence, "A competencia e obrigatoria");
        List<TaxRateRow> covering = rows.stream()
                .filter(r -> r.taxCode().equals(taxCode))
                .filter(r -> !competence.isBefore(r.validFrom()))
                .filter(r -> r.validTo() == null || !competence.isAfter(r.validTo()))
                .toList();
        if (covering.isEmpty()) {
            throw new IllegalStateException(
                    "Aliquota ausente para " + taxCode + " na competencia " + competence + ": o documento nao e emitido");
        }
        if (covering.size() > 1) {
            throw new IllegalStateException(
                    "Vigencias sobrepostas para " + taxCode + " na competencia " + competence + ": corrija tax_rates");
        }
        return covering.get(0).rate();
    }

    /**
     * CBS e IBS são informativos só na competência do ano de teste (até 2026-12-31).
     * A partir de 01/01/2027 o flag é falso: a CBS plena compõe o documento e o IBS
     * segue a sua transição por vigência. O flag vem da vigência, não do motor.
     */
    public static boolean cbsIbsInformative(LocalDate competence) {
        Objects.requireNonNull(competence, "A competencia e obrigatoria");
        return !competence.isAfter(TEST_YEAR_END);
    }

    /** Sobreposição de dois intervalos [from, to] fechados; {@code null} = vigência aberta. */
    public static boolean overlaps(LocalDate from1, LocalDate to1, LocalDate from2, LocalDate to2) {
        Objects.requireNonNull(from1, "O inicio da primeira vigencia e obrigatorio");
        Objects.requireNonNull(from2, "O inicio da segunda vigencia e obrigatorio");
        boolean firstBeforeSecondEnd = to2 == null || !from1.isAfter(to2);
        boolean secondBeforeFirstEnd = to1 == null || !from2.isAfter(to1);
        return firstBeforeSecondEnd && secondBeforeFirstEnd;
    }
}
