package br.com.sigsan.commercial.collection.surcharge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Acréscimos por impontualidade: multa, juros de mora e correção monetária. Serviço puro: sem
 * I/O, sem relógio, sem valor padrão. Toda regra vem da {@link SurchargePolicy} do tenant e o
 * calendário de dias úteis (feriados do município) é injetado.
 *
 * <h3>Regras</h3>
 * <ol>
 *   <li><b>Isenção</b>: pagamento por débito automático e suspensão por situação especial de
 *       cobrança zeram tudo ({@link Exemption}).</li>
 *   <li><b>Vencimento efetivo</b>: com {@code dueDateRollsToBusinessDay}, vencimento em dia não
 *       útil vale o próximo dia útil. Daqui em diante, "vencimento" é o efetivo.</li>
 *   <li><b>Não vencido</b>: data de avaliação igual ou anterior ao vencimento: zero.</li>
 *   <li><b>Carência</b>: se os dias úteis em {@code (vencimento, avaliação]} forem no máximo a
 *       carência, zero. Passada a carência, os acréscimos contam <b>desde o vencimento</b>, não
 *       desde o fim da carência.</li>
 *   <li><b>Multa</b>: {@code principal x percentual}, uma vez. O que já foi cobrado de multa
 *       sobre o mesmo principal é abatido do total da multa (nunca cobra duas vezes; o GSAN
 *       multiplicava {@code (principal - multas já cobradas)} pelo percentual, o que dá outro
 *       valor).</li>
 *   <li><b>Juros</b>: simples, pro rata die, sobre o principal, em dias corridos
 *       ({@link InterestRule.SimplePerDay}).</li>
 *   <li><b>Correção</b>: razão dos fatores do índice entre o mês do pagamento e o do vencimento
 *       ({@link CorrectionRule.ByIndex}).</li>
 * </ol>
 *
 * <h3>Data de avaliação</h3>
 * {@code evaluationDate} é sempre explícita: a data do pagamento, ou a data em que se está
 * calculando a dívida em aberto. O relatório 03 §9 aponta que o GSAN, sem data de pagamento,
 * usava "hoje" e chamava uma função de subtrair dias cujo resultado era descartado, de modo que
 * a data de cálculo nunca era recuada pela carência. Aqui não há "hoje" implícito e a carência é
 * aplicada <b>do mesmo jeito</b> a dívida paga e a dívida em aberto: o que o GSAN pretendia com
 * o recuo não está documentado e não foi reproduzido (decisão pendente do dono, ver README).
 *
 * <h3>Arredondamento</h3>
 * Sem arredondamento intermediário. Multa, juros e correção são arredondados cada um uma única
 * vez, a 2 casas, {@link RoundingMode#HALF_UP}, e o total é a soma dos três valores já
 * arredondados. (O GSAN misturava HALF_UP e HALF_DOWN conforme o módulo.)
 */
public final class SurchargeCalculator {

    public enum Exemption { NONE, PAID_BY_DIRECT_DEBIT, SUSPENDED_BY_SPECIAL_STATUS }

    public enum Outcome { NOT_OVERDUE, WITHIN_GRACE, EXEMPT_DIRECT_DEBIT, EXEMPT_SUSPENDED, CHARGED }

    /**
     * @param principal            valor da dívida sem acréscimos
     * @param dueDate              vencimento original
     * @param evaluationDate       data do pagamento, ou data-base do cálculo da dívida em aberto
     * @param finesAlreadyCharged  multa já cobrada sobre este principal em cálculo anterior
     *                             (abatida da multa devida); zero se nenhuma
     * @param exemption            isenção aplicável
     */
    public record Request(BigDecimal principal, LocalDate dueDate, LocalDate evaluationDate,
                          BigDecimal finesAlreadyCharged, Exemption exemption) {
        public Request {
            if (principal == null || principal.signum() < 0) {
                throw new IllegalArgumentException("O principal é obrigatório e não pode ser negativo");
            }
            if (dueDate == null || evaluationDate == null) {
                throw new IllegalArgumentException("Vencimento e data de avaliação são obrigatórios");
            }
            if (finesAlreadyCharged == null || finesAlreadyCharged.signum() < 0) {
                throw new IllegalArgumentException("Multa já cobrada: informe zero se nenhuma");
            }
            if (exemption == null) {
                throw new IllegalArgumentException("Isenção: informe NONE se nenhuma");
            }
        }

        public static Request of(BigDecimal principal, LocalDate dueDate, LocalDate evaluationDate) {
            return new Request(principal, dueDate, evaluationDate, BigDecimal.ZERO, Exemption.NONE);
        }
    }

    /**
     * @param fine               multa devida agora (já abatida a multa anterior)
     * @param interest           juros de mora
     * @param correction         correção monetária
     * @param outcome            por que o resultado é este (auditável)
     * @param effectiveDueDate   vencimento efetivo usado (após rolagem para dia útil)
     * @param daysOverdue        dias corridos entre o vencimento efetivo e a data de avaliação
     * @param businessDaysLate   dias úteis entre o vencimento efetivo e a data de avaliação
     */
    public record Result(BigDecimal fine, BigDecimal interest, BigDecimal correction, Outcome outcome,
                         LocalDate effectiveDueDate, long daysOverdue, int businessDaysLate) {

        public BigDecimal total() {
            return fine.add(interest).add(correction);
        }
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SurchargePolicy policy;
    private final BusinessCalendar calendar;

    public SurchargeCalculator(SurchargePolicy policy, BusinessCalendar calendar) {
        if (policy == null || calendar == null) {
            throw new IllegalArgumentException("Política e calendário são obrigatórios");
        }
        this.policy = policy;
        this.calendar = calendar;
    }

    public Result calculate(Request req) {
        if (req.exemption() == Exemption.PAID_BY_DIRECT_DEBIT) {
            return zero(Outcome.EXEMPT_DIRECT_DEBIT, req.dueDate(), 0, 0);
        }
        if (req.exemption() == Exemption.SUSPENDED_BY_SPECIAL_STATUS) {
            return zero(Outcome.EXEMPT_SUSPENDED, req.dueDate(), 0, 0);
        }

        LocalDate due = policy.dueDateRollsToBusinessDay()
                ? calendar.nextBusinessDayOnOrAfter(req.dueDate())
                : req.dueDate();
        if (!req.evaluationDate().isAfter(due)) {
            return zero(Outcome.NOT_OVERDUE, due, 0, 0);
        }
        int businessDays = calendar.businessDaysBetween(due, req.evaluationDate());
        long days = ChronoUnit.DAYS.between(due, req.evaluationDate());
        if (businessDays <= policy.graceBusinessDays()) {
            return zero(Outcome.WITHIN_GRACE, due, days, businessDays);
        }

        BigDecimal principal = req.principal();
        BigDecimal fine = fine(principal, req.finesAlreadyCharged());
        BigDecimal interest = interest(principal, days);
        BigDecimal correction = correction(principal, due, req.evaluationDate());
        return new Result(fine, interest, correction, Outcome.CHARGED, due, days, businessDays);
    }

    private BigDecimal fine(BigDecimal principal, BigDecimal alreadyCharged) {
        BigDecimal total = principal.multiply(policy.fine().ratePercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal due = total.subtract(alreadyCharged.setScale(2, RoundingMode.HALF_UP));
        return due.signum() < 0 ? ZERO : due;
    }

    private BigDecimal interest(BigDecimal principal, long days) {
        if (!(policy.interest() instanceof InterestRule.SimplePerDay rule)) {
            return ZERO;
        }
        // principal x taxa/100 x dias / base, com uma única divisão e um único arredondamento.
        BigDecimal numerator = principal.multiply(rule.monthlyPercent()).multiply(BigDecimal.valueOf(days));
        BigDecimal denominator = HUNDRED.multiply(BigDecimal.valueOf(rule.monthBasisDays()));
        BigDecimal interest = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        if (rule.capPercent() != null) {
            BigDecimal cap = principal.multiply(rule.capPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            if (interest.compareTo(cap) > 0) {
                interest = cap;
            }
        }
        return interest;
    }

    private BigDecimal correction(BigDecimal principal, LocalDate due, LocalDate evaluation) {
        if (!(policy.correction() instanceof CorrectionRule.ByIndex rule)) {
            return ZERO;
        }
        YearMonth from = YearMonth.from(due);
        YearMonth to = YearMonth.from(evaluation);
        long months = ChronoUnit.MONTHS.between(from, to);
        if (months <= 0 || months < rule.minMonthsToApply()) {
            return ZERO;
        }
        BigDecimal fromFactor = rule.series().factor(from).orElseThrow(() -> new MissingIndexException(from));
        BigDecimal toFactor = rule.series().factor(to).orElseThrow(() -> new MissingIndexException(to));
        if (fromFactor.signum() <= 0 || toFactor.signum() <= 0) {
            throw new IllegalStateException("Fator de índice não positivo para " + from + " ou " + to);
        }
        BigDecimal correction = principal.multiply(toFactor.subtract(fromFactor))
                .divide(fromFactor, 2, RoundingMode.HALF_UP);
        if (correction.signum() < 0 && !rule.allowNegative()) {
            return ZERO;
        }
        return correction;
    }

    private static Result zero(Outcome outcome, LocalDate due, long days, int businessDays) {
        return new Result(ZERO, ZERO, ZERO, outcome, due, days, businessDays);
    }
}
