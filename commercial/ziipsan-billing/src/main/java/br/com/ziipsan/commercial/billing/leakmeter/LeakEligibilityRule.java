package br.com.ziipsan.commercial.billing.leakmeter;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Limite de faturas beneficiadas em 12 meses (P3.28): o desconto por
 * vazamento nao e ilimitado -- as notas dos perfis ARESC e AGIR da P2.31
 * registram "2 faturas em 12 meses" (AGIR: "2 faturas SEQUENCIAIS em 12
 * meses"); o perfil ARIS nao registra limite e declara sem teto com o nulo
 * JSON explicito de {@code leak.max_bills_per_12_months} (a chave e
 * obrigatoria -- ver {@link LeakMeterParameters}, correcao M1).
 *
 * <p><b>Semantica adotada (declarada):</b> a janela e contada pelas
 * REFERENCIAS das faturas, nao pelas datas de deferimento: contam as
 * faturas ja beneficiadas cuja referencia esta nos 12 meses anteriores a
 * primeira referencia pedida, exclusive ela -- isto e, referencias
 * posteriores a {@code primeira.minusMonths(12)} e anteriores a
 * {@code primeira}. O pedido e elegivel quando
 * {@code jaBeneficiadasNaJanela + pedidas <= teto}.
 *
 * <ul>
 *   <li>Modo {@code SEQUENTIAL_IN_12_MONTHS} (AGIR): alem da contagem, as
 *       referencias pedidas precisam ser meses consecutivos entre si
 *       (2026-01 e 2026-03, com buraco, nao formam o bloco sequencial);</li>
 *   <li>fatura ja beneficiada pedida de novo e inelegivel em qualquer modo
 *       (duplicidade), com o teto ou sem ele;</li>
 *   <li>sem teto configurado ({@code null}), so a duplicidade barra.</li>
 * </ul>
 *
 * <p>Regra pura: o servico a consulta ANTES de abrir qualquer OS.
 */
public final class LeakEligibilityRule {

    /**
     * Decisao de elegibilidade. {@code reason} e {@code null} quando
     * elegivel; quando inelegivel, nomeia a causa (teto, sequencia ou
     * duplicidade) com os numeros conferidos.
     */
    public record Eligibility(boolean eligible, int priorsInWindow, int requestedCount,
                              Integer maxBills, boolean sequentialSatisfied, String reason) {
    }

    private LeakEligibilityRule() {
    }

    public static Eligibility evaluate(LeakMeterParameters parameters,
                                       List<YearMonth> priorDiscountedReferences,
                                       List<YearMonth> requestedReferences) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(priorDiscountedReferences, "priorDiscountedReferences");
        if (requestedReferences == null || requestedReferences.isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa nomear ao menos uma fatura (referencia)");
        }
        List<YearMonth> requested = new ArrayList<>(requestedReferences);
        requested.sort(null);
        Set<YearMonth> requestedSet = new HashSet<>(requested);
        if (requestedSet.size() != requested.size()) {
            throw new IllegalArgumentException("Referencias repetidas no mesmo pedido: " + requestedReferences);
        }
        for (YearMonth prior : priorDiscountedReferences) {
            if (requestedSet.contains(prior)) {
                return new Eligibility(false, 0, requested.size(), parameters.maxBillsPer12Months(), false,
                        "Fatura da referencia " + prior + " ja foi beneficiada por vazamento (duplicidade)");
            }
        }
        YearMonth first = requested.get(0);
        YearMonth windowStartExclusive = first.minusMonths(12);
        int priorsInWindow = 0;
        for (YearMonth prior : priorDiscountedReferences) {
            if (prior.isAfter(windowStartExclusive) && prior.isBefore(first)) {
                priorsInWindow++;
            }
        }
        boolean sequentialSatisfied = true;
        if (parameters.billsLimitMode() == LeakMeterParameters.BillsLimitMode.SEQUENTIAL_IN_12_MONTHS) {
            for (int i = 1; i < requested.size(); i++) {
                if (!requested.get(i).equals(requested.get(i - 1).plusMonths(1))) {
                    sequentialSatisfied = false;
                    break;
                }
            }
            if (!sequentialSatisfied) {
                return new Eligibility(false, priorsInWindow, requested.size(),
                        parameters.maxBillsPer12Months(), false,
                        "Modo sequencial: as referencias pedidas nao sao meses consecutivos: " + requested);
            }
        }
        Integer max = parameters.maxBillsPer12Months();
        if (max != null && priorsInWindow + requested.size() > max) {
            return new Eligibility(false, priorsInWindow, requested.size(), max, sequentialSatisfied,
                    "Limite de " + max + " faturas em 12 meses: " + priorsInWindow
                            + " ja beneficiadas na janela e " + requested.size() + " pedidas");
        }
        return new Eligibility(true, priorsInWindow, requested.size(), max, sequentialSatisfied, null);
    }

    /** Como {@link #evaluate}, mas lanca excecao com o motivo quando inelegivel. */
    public static Eligibility requireEligible(LeakMeterParameters parameters,
                                              List<YearMonth> priorDiscountedReferences,
                                              List<YearMonth> requestedReferences) {
        Eligibility eligibility = evaluate(parameters, priorDiscountedReferences, requestedReferences);
        if (!eligibility.eligible()) {
            throw new IllegalStateException("Revisao por vazamento inelegivel: " + eligibility.reason());
        }
        return eligibility;
    }
}
