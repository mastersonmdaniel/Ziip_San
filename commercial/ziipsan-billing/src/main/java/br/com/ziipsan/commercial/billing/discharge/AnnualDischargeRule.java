package br.com.ziipsan.commercial.billing.discharge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Regra pura da declaracao anual de quitacao de debitos (P2.24;
 * Lei 12.007/2009; aceite: "gerada na fatura de maio e no mes seguinte a
 * quitacao tardia"). Nenhum numero desta regra e inventado: maio e o mes
 * que o aceite nomeia, e "mes seguinte" e a leitura literal do aceite.
 *
 * <p>Definicoes (todas derivadas do aceite e do vocabulario da P2.5):
 * <ul>
 *   <li>ANO QUITADO: o ano tem ao menos uma conta viva
 *       ({@code BillStatus.live()}) e TODAS as contas vivas do ano estao
 *       {@code PAID}. Conta cancelada, cancelada por retificacao ou com
 *       erro de processamento nao ocupa o ano (e a semantica de
 *       {@code live()} da P2.5, a mesma do indice unico parcial da V4_1).
 *       Ano sem nenhuma conta viva NAO e ano quitado: nao ha fatura do
 *       ano para declarar, e "sem conta" nao prova "sem debito".</li>
 *   <li>QUITACAO DO ANO: a maior data de quitacao entre as contas do ano
 *       (a ultima conta paga e a que completa a quitacao do ano).</li>
 *   <li>CICLO DE MAIO ({@link Trigger#MAY_CYCLE}): quitacao do ano ate
 *       30 de abril do ano seguinte -> a declaracao e devida na fatura
 *       cuja competencia e maio do ano seguinte. Conta de dezembro paga
 *       em janeiro (vencimento normal) cai aqui, nao na quitacao tardia.</li>
 *   <li>QUITACAO TARDIA ({@link Trigger#LATE_SETTLEMENT}): quitacao do
 *       ano em maio do ano seguinte ou depois — o ciclo de maio ja passou
 *       sem o ano quitado -> a declaracao e devida na fatura do MES
 *       SEGUINTE ao da quitacao.</li>
 *   <li>UMA VEZ POR ANO: ano ja declarado ({@code alreadyIssued}) nunca
 *       e devido de novo, nem em maio nem por quitacao tardia.</li>
 * </ul>
 *
 * <p>Calculo a mao (conferido nos testes): ano de 2025 todo pago ate
 * 10/03/2026 -> devido em 2026-05. Ano de 2025 com a ultima conta paga em
 * 15/06/2026 -> devido em 2026-07. Ano de 2025 com uma conta NORMAL ->
 * nao devido em mes nenhum enquanto ela nao for paga.
 */
public final class AnnualDischargeRule {

    private AnnualDischargeRule() {
    }

    /** Como a declaracao ficou devida. */
    public enum Trigger {
        /** Ciclo regular: ano quitado a tempo da fatura de maio do ano seguinte. */
        MAY_CYCLE,
        /** Quitacao tardia: ano quitado depois de o ciclo de maio ter passado. */
        LATE_SETTLEMENT
    }

    /**
     * A declaracao devida de um ano.
     *
     * @param referenceYear      o ano declarado (ano das competencias)
     * @param trigger            o ciclo que a tornou devida
     * @param settledOn          a quitacao do ano (maior data de quitacao das contas do ano)
     * @param dueReferenceMonth  a competencia da fatura em que ela sai (primeiro dia)
     */
    public record DischargeDecision(
            int referenceYear,
            Trigger trigger,
            LocalDate settledOn,
            LocalDate dueReferenceMonth) {
        public DischargeDecision {
            if (settledOn == null || dueReferenceMonth == null || dueReferenceMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException("decisao de quitacao exige quitacao e competencia valida");
            }
        }
    }

    /**
     * A declaracao do ano {@code referenceYear} e devida na fatura de
     * competencia {@code targetReferenceMonth}?
     */
    public static Optional<DischargeDecision> evaluate(int referenceYear, List<AnnualBillFact> facts,
                                                       LocalDate targetReferenceMonth, boolean alreadyIssued) {
        if (facts == null) {
            throw new IllegalArgumentException("facts e obrigatorio");
        }
        if (targetReferenceMonth == null || targetReferenceMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "targetReferenceMonth deve ser o primeiro dia do mes: " + targetReferenceMonth);
        }
        if (alreadyIssued) {
            return Optional.empty();
        }
        LocalDate lastSettledOn = null;
        boolean anyLiveBill = false;
        for (AnnualBillFact fact : facts) {
            if (fact.referenceMonth().getYear() != referenceYear || !fact.countsForYear()) {
                continue;
            }
            anyLiveBill = true;
            if (!fact.settled()) {
                // Debito em aberto no ano: nao ha declaracao enquanto ele existir.
                return Optional.empty();
            }
            if (lastSettledOn == null || fact.settledOn().isAfter(lastSettledOn)) {
                lastSettledOn = fact.settledOn();
            }
        }
        if (!anyLiveBill) {
            return Optional.empty();
        }
        // lastSettledOn nao e nulo aqui: toda conta viva esta PAID e o fato
        // garante a data de quitacao de conta PAID (ver AnnualBillFact).
        LocalDate mayOfNextYear = LocalDate.of(referenceYear + 1, 5, 1);
        LocalDate dueMonth;
        Trigger trigger;
        if (!lastSettledOn.isAfter(LocalDate.of(referenceYear + 1, 4, 30))) {
            dueMonth = mayOfNextYear;
            trigger = Trigger.MAY_CYCLE;
        } else {
            dueMonth = lastSettledOn.plusMonths(1).withDayOfMonth(1);
            trigger = Trigger.LATE_SETTLEMENT;
        }
        if (!dueMonth.equals(targetReferenceMonth)) {
            return Optional.empty();
        }
        return Optional.of(new DischargeDecision(referenceYear, trigger, lastSettledOn, dueMonth));
    }

    /**
     * Todas as declaracoes devidas na competencia alvo, em ordem crescente
     * de ano (um ano por declaracao; dois anos podem vencer no mesmo mes —
     * ex.: maio de um ano e a quitacao tardia de outro).
     */
    public static List<DischargeDecision> evaluateAll(List<AnnualBillFact> facts,
                                                      LocalDate targetReferenceMonth,
                                                      Set<Integer> alreadyIssuedYears) {
        if (facts == null || alreadyIssuedYears == null) {
            throw new IllegalArgumentException("facts e alreadyIssuedYears sao obrigatorios");
        }
        Set<Integer> years = new TreeSet<>();
        for (AnnualBillFact fact : facts) {
            years.add(fact.referenceMonth().getYear());
        }
        List<DischargeDecision> due = new ArrayList<>();
        for (int year : years) {
            evaluate(year, facts, targetReferenceMonth, alreadyIssuedYears.contains(year))
                    .ifPresent(due::add);
        }
        return List.copyOf(due);
    }
}
