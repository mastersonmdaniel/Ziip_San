package br.com.ziipsan.commercial.collection.surcharge;

import br.com.ziipsan.commercial.collection.surcharge.SurchargeCalculator.Exemption;
import br.com.ziipsan.commercial.collection.surcharge.SurchargeCalculator.Outcome;
import br.com.ziipsan.commercial.collection.surcharge.SurchargeCalculator.Request;
import br.com.ziipsan.commercial.collection.surcharge.SurchargeCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Casos calculados à mão. Convenção dos exemplos: multa 2%, juros 1% ao mês simples pro rata die
 * com mês de 30 dias, sem correção, salvo quando dito. Cada componente é arredondado uma vez, a 2
 * casas, HALF_UP; o total é a soma dos três já arredondados.
 *
 * <p>Calendário de 2026 usado (confere-se no teste {@code calendarSanity}): 05/10 segunda, 09/10
 * sexta, 10/10 sábado, 11/10 domingo, 12/10 segunda, que nos exemplos entra como feriado injetado
 * (fixture de teste; o módulo não traz feriado nenhum).
 */
class SurchargeCalculatorTest {

    private static final LocalDate MON_05_OCT = LocalDate.of(2026, 10, 5);
    private static final LocalDate FRI_09_OCT = LocalDate.of(2026, 10, 9);
    private static final LocalDate SAT_10_OCT = LocalDate.of(2026, 10, 10);
    private static final LocalDate MON_12_OCT = LocalDate.of(2026, 10, 12);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static BusinessCalendar noHolidays() {
        return BusinessCalendar.of(List.of());
    }

    private static BusinessCalendar withOct12Holiday() {
        return BusinessCalendar.of(List.of(MON_12_OCT));
    }

    private static SurchargePolicy policy(int grace, boolean roll) {
        return new SurchargePolicy(grace, roll, FineRule.of(d("2.00")),
                InterestRule.simplePerDay(d("1.00"), 30, null), CorrectionRule.none());
    }

    private static Result calc(SurchargePolicy p, BusinessCalendar c, String principal, LocalDate due, LocalDate eval) {
        return new SurchargeCalculator(p, c).calculate(Request.of(d(principal), due, eval));
    }

    @Test
    @DisplayName("sanidade do calendário usado nos exemplos")
    void calendarSanity() {
        assertThat(MON_05_OCT.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(FRI_09_OCT.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(SAT_10_OCT.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(MON_12_OCT.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    @DisplayName("1 dia de atraso: multa 2,00; juros 100 x 1% x 1/30 = 0,0333 -> 0,03; total 2,03")
    void oneDayLate() {
        Result r = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, MON_05_OCT.plusDays(1));
        assertThat(r.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(r.fine()).isEqualByComparingTo("2.00");
        assertThat(r.interest()).isEqualByComparingTo("0.03");
        assertThat(r.correction()).isEqualByComparingTo("0.00");
        assertThat(r.total()).isEqualByComparingTo("2.03");
        assertThat(r.daysOverdue()).isEqualTo(1);
    }

    @Test
    @DisplayName("10 dias: juros 100 x 1% x 10/30 = 0,3333 -> 0,33; total 2,33")
    void tenDaysLate() {
        Result r = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, MON_05_OCT.plusDays(10));
        assertThat(r.interest()).isEqualByComparingTo("0.33");
        assertThat(r.total()).isEqualByComparingTo("2.33");
    }

    @Test
    @DisplayName("30 dias, 250,75: multa 5,015 -> 5,02 (HALF_UP); juros 2,5075 -> 2,51; total 7,53")
    void thirtyDaysRounding() {
        Result r = calc(policy(0, false), noHolidays(), "250.75", MON_05_OCT, MON_05_OCT.plusDays(30));
        assertThat(r.fine()).isEqualByComparingTo("5.02");
        assertThat(r.interest()).isEqualByComparingTo("2.51");
        assertThat(r.total()).isEqualByComparingTo("7.53");
    }

    @Test
    @DisplayName("arredondamento HALF_UP no empate exato: 10,00 x 1,5% x 1/30 = 0,005 -> 0,01 (HALF_EVEN daria 0,00)")
    void halfUpOnExactTie() {
        SurchargePolicy p = new SurchargePolicy(0, false, FineRule.none(),
                InterestRule.simplePerDay(d("1.5"), 30, null), CorrectionRule.none());
        Result r = calc(p, noHolidays(), "10.00", MON_05_OCT, MON_05_OCT.plusDays(1));
        assertThat(r.interest()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("juros não arredondam em etapas: 3 dias de 100,50 a 1% = 0,1005 -> 0,10")
    void singleRoundingOfInterest() {
        Result r = calc(policy(0, false), noHolidays(), "100.50", MON_05_OCT, MON_05_OCT.plusDays(3));
        assertThat(r.interest()).isEqualByComparingTo("0.10");
        assertThat(r.fine()).isEqualByComparingTo("2.01"); // 100,50 x 2% = 2,01
    }

    @Test
    @DisplayName("pagamento no vencimento ou antes: nada a cobrar")
    void notOverdue() {
        Result onDue = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, MON_05_OCT);
        Result before = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, MON_05_OCT.minusDays(3));
        for (Result r : List.of(onDue, before)) {
            assertThat(r.outcome()).isEqualTo(Outcome.NOT_OVERDUE);
            assertThat(r.total()).isEqualByComparingTo("0.00");
            assertThat(r.daysOverdue()).isZero();
        }
    }

    @Test
    @DisplayName("carência de 3 dias úteis: quinta (3º dia útil) é grátis; sexta (4º) cobra desde o vencimento, com 4 dias de juros")
    void graceInBusinessDays() {
        SurchargePolicy p = policy(3, false);
        Result thursday = calc(p, noHolidays(), "100.00", MON_05_OCT, LocalDate.of(2026, 10, 8));
        assertThat(thursday.outcome()).isEqualTo(Outcome.WITHIN_GRACE);
        assertThat(thursday.total()).isEqualByComparingTo("0.00");
        assertThat(thursday.businessDaysLate()).isEqualTo(3);

        Result friday = calc(p, noHolidays(), "100.00", MON_05_OCT, FRI_09_OCT);
        assertThat(friday.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(friday.businessDaysLate()).isEqualTo(4);
        assertThat(friday.daysOverdue()).isEqualTo(4);
        assertThat(friday.fine()).isEqualByComparingTo("2.00");
        assertThat(friday.interest()).isEqualByComparingTo("0.13"); // 100 x 1% x 4/30 = 0,1333
    }

    @Test
    @DisplayName("fim de semana e feriado municipal não consomem carência: sexta 09/10, carência 1, feriado 12/10")
    void weekendAndHolidayDoNotCountTowardsGrace() {
        SurchargePolicy p = policy(1, false);
        BusinessCalendar cal = withOct12Holiday();
        // (sex 09, seg 12] = sáb, dom, seg feriado -> 0 dias úteis
        assertThat(calc(p, cal, "100.00", FRI_09_OCT, MON_12_OCT).outcome()).isEqualTo(Outcome.WITHIN_GRACE);
        // terça 13: 1 dia útil, ainda dentro da carência de 1
        assertThat(calc(p, cal, "100.00", FRI_09_OCT, LocalDate.of(2026, 10, 13)).outcome()).isEqualTo(Outcome.WITHIN_GRACE);
        // quarta 14: 2 dias úteis, cobra; 5 dias corridos desde sexta
        Result wed = calc(p, cal, "100.00", FRI_09_OCT, LocalDate.of(2026, 10, 14));
        assertThat(wed.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(wed.businessDaysLate()).isEqualTo(2);
        assertThat(wed.daysOverdue()).isEqualTo(5);
        assertThat(wed.interest()).isEqualByComparingTo("0.17"); // 100 x 1% x 5/30 = 0,1667
    }

    @Test
    @DisplayName("sem o feriado cadastrado, a mesma data cobra: o calendário é do tenant")
    void withoutHolidayTheSameDateIsCharged() {
        Result r = calc(policy(1, false), noHolidays(), "100.00", FRI_09_OCT, LocalDate.of(2026, 10, 13));
        assertThat(r.outcome()).isEqualTo(Outcome.CHARGED); // seg 12 e ter 13 = 2 dias úteis > 1
    }

    @Test
    @DisplayName("vencimento em sábado rola para o próximo dia útil quando a política manda")
    void dueDateRollsForward() {
        SurchargePolicy roll = policy(0, true);
        BusinessCalendar cal = withOct12Holiday();
        // sáb 10 -> dom 11 não é útil -> seg 12 feriado -> ter 13
        Result onRolledDay = calc(roll, cal, "100.00", SAT_10_OCT, LocalDate.of(2026, 10, 13));
        assertThat(onRolledDay.outcome()).isEqualTo(Outcome.NOT_OVERDUE);
        assertThat(onRolledDay.effectiveDueDate()).isEqualTo(LocalDate.of(2026, 10, 13));
        Result nextDay = calc(roll, cal, "100.00", SAT_10_OCT, LocalDate.of(2026, 10, 14));
        assertThat(nextDay.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(nextDay.daysOverdue()).isEqualTo(1);
        assertThat(nextDay.businessDaysLate()).isEqualTo(1);
    }

    @Test
    @DisplayName("sem rolagem, a contagem de dias úteis também protege o pagamento na segunda útil")
    void withoutRollingWeekendIsStillNotCounted() {
        // vencimento sáb 10, sem feriado: seg 12 é o 1º dia útil; com carência 0 já cobra
        Result r = calc(policy(0, false), noHolidays(), "100.00", SAT_10_OCT, MON_12_OCT);
        assertThat(r.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(r.daysOverdue()).isEqualTo(2);
        // com a segunda feriado, nenhum dia útil entre sáb e seg: dentro da carência 0
        Result holiday = calc(policy(0, false), withOct12Holiday(), "100.00", SAT_10_OCT, MON_12_OCT);
        assertThat(holiday.outcome()).isEqualTo(Outcome.WITHIN_GRACE);
    }

    @Test
    @DisplayName("dívida em aberto (sem pagamento): a carência vale igual, com data de avaliação explícita")
    void unpaidDebtUsesSameGraceAsPaid() {
        SurchargePolicy p = policy(2, false);
        // seg 05 + 2 dias úteis (ter, qua) = qua 07 grátis; qui 08 cobra com 3 dias corridos
        assertThat(calc(p, noHolidays(), "100.00", MON_05_OCT, LocalDate.of(2026, 10, 7)).outcome())
                .isEqualTo(Outcome.WITHIN_GRACE);
        Result thu = calc(p, noHolidays(), "100.00", MON_05_OCT, LocalDate.of(2026, 10, 8));
        assertThat(thu.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(thu.daysOverdue()).isEqualTo(3);
        assertThat(thu.interest()).isEqualByComparingTo("0.10"); // 100 x 1% x 3/30
    }

    @Test
    @DisplayName("débito automático e situação especial de cobrança isentam tudo, mesmo com 100 dias de atraso")
    void exemptions() {
        SurchargeCalculator calc = new SurchargeCalculator(policy(0, false), noHolidays());
        Result dd = calc.calculate(new Request(d("100.00"), MON_05_OCT, MON_05_OCT.plusDays(100),
                BigDecimal.ZERO, Exemption.PAID_BY_DIRECT_DEBIT));
        assertThat(dd.outcome()).isEqualTo(Outcome.EXEMPT_DIRECT_DEBIT);
        assertThat(dd.total()).isEqualByComparingTo("0.00");
        Result susp = calc.calculate(new Request(d("100.00"), MON_05_OCT, MON_05_OCT.plusDays(100),
                BigDecimal.ZERO, Exemption.SUSPENDED_BY_SPECIAL_STATUS));
        assertThat(susp.outcome()).isEqualTo(Outcome.EXEMPT_SUSPENDED);
        assertThat(susp.total()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("multa já cobrada é abatida, nunca cobrada de novo, e não fica negativa")
    void alreadyChargedFine() {
        SurchargeCalculator calc = new SurchargeCalculator(policy(0, false), noHolidays());
        LocalDate eval = MON_05_OCT.plusDays(10);
        assertThat(calc.calculate(new Request(d("100.00"), MON_05_OCT, eval, d("2.00"), Exemption.NONE)).fine())
                .isEqualByComparingTo("0.00");
        assertThat(calc.calculate(new Request(d("100.00"), MON_05_OCT, eval, d("0.50"), Exemption.NONE)).fine())
                .isEqualByComparingTo("1.50");
        assertThat(calc.calculate(new Request(d("100.00"), MON_05_OCT, eval, d("5.00"), Exemption.NONE)).fine())
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("teto de juros: 300 dias de 100,00 a 1% dariam 10,00; teto de 5% limita a 5,00")
    void interestCap() {
        LocalDate eval = MON_05_OCT.plusDays(300);
        SurchargePolicy capped = new SurchargePolicy(0, false, FineRule.none(),
                InterestRule.simplePerDay(d("1.00"), 30, d("5.00")), CorrectionRule.none());
        SurchargePolicy uncapped = new SurchargePolicy(0, false, FineRule.none(),
                InterestRule.simplePerDay(d("1.00"), 30, null), CorrectionRule.none());
        assertThat(calc(capped, noHolidays(), "100.00", MON_05_OCT, eval).interest()).isEqualByComparingTo("5.00");
        assertThat(calc(uncapped, noHolidays(), "100.00", MON_05_OCT, eval).interest()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("sem multa e sem juros declarados: só a correção, e valor zero não cobra nada")
    void declaredAbsenceOfRules() {
        SurchargePolicy none = new SurchargePolicy(0, false, FineRule.none(), InterestRule.none(), CorrectionRule.none());
        Result r = calc(none, noHolidays(), "500.00", MON_05_OCT, MON_05_OCT.plusDays(90));
        assertThat(r.outcome()).isEqualTo(Outcome.CHARGED);
        assertThat(r.total()).isEqualByComparingTo("0.00");
        assertThat(calc(policy(0, false), noHolidays(), "0.00", MON_05_OCT, MON_05_OCT.plusDays(5)).total())
                .isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ correção

    private static SurchargePolicy withCorrection(CorrectionRule rule) {
        return new SurchargePolicy(0, false, FineRule.none(), InterestRule.none(), rule);
    }

    private static final IndexSeries SERIES = IndexSeries.of(Map.of(
            YearMonth.of(2026, 10), d("100.0000000000"),
            YearMonth.of(2026, 11), d("100.7000000000"),
            YearMonth.of(2026, 12), d("101.5000000000")));

    @Test
    @DisplayName("correção: 200,00 de out/2026 a dez/2026 = 200 x (101,5 - 100) / 100 = 3,00")
    void correctionByIndex() {
        Result r = calc(withCorrection(CorrectionRule.byIndex(SERIES, 0, false)), noHolidays(), "200.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 12, 10));
        assertThat(r.correction()).isEqualByComparingTo("3.00");
        assertThat(r.total()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("correção em um mês só: 200 x 0,7 / 100 = 1,40")
    void correctionOneMonth() {
        Result r = calc(withCorrection(CorrectionRule.byIndex(SERIES, 0, false)), noHolidays(), "200.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 25));
        assertThat(r.correction()).isEqualByComparingTo("1.40");
    }

    @Test
    @DisplayName("mesmo mês do vencimento: sem correção e sem consultar o índice (série vazia não quebra)")
    void sameMonthSkipsLookup() {
        CorrectionRule empty = CorrectionRule.byIndex(IndexSeries.of(Map.of()), 0, false);
        Result r = calc(withCorrection(empty), noHolidays(), "200.00", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 28));
        assertThat(r.correction()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("mínimo de meses: com minMonthsToApply 3 e só 2 meses decorridos, não corrige")
    void minimumMonthsToApply() {
        LocalDate due = LocalDate.of(2026, 10, 20);
        LocalDate eval = LocalDate.of(2026, 12, 10);
        assertThat(calc(withCorrection(CorrectionRule.byIndex(SERIES, 3, false)), noHolidays(), "200.00", due, eval)
                .correction()).isEqualByComparingTo("0.00");
        assertThat(calc(withCorrection(CorrectionRule.byIndex(SERIES, 2, false)), noHolidays(), "200.00", due, eval)
                .correction()).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("índice em queda de 100 para 99,5: sem permissão a correção é 0; com permissão é 200 x -0,5 / 100 = -1,00")
    void negativeCorrection() {
        IndexSeries falling = IndexSeries.of(Map.of(YearMonth.of(2026, 10), d("100.0"), YearMonth.of(2026, 11), d("99.5")));
        LocalDate due = LocalDate.of(2026, 10, 20);
        LocalDate eval = LocalDate.of(2026, 11, 25);
        assertThat(calc(withCorrection(CorrectionRule.byIndex(falling, 0, false)), noHolidays(), "200.00", due, eval)
                .correction()).isEqualByComparingTo("0.00");
        assertThat(calc(withCorrection(CorrectionRule.byIndex(falling, 0, true)), noHolidays(), "200.00", due, eval)
                .correction()).isEqualByComparingTo("-1.00"); // 200 x -0,5 / 100
    }

    @Test
    @DisplayName("correção arredonda uma vez: 10,00 x (100,05 - 100) / 100 = 0,005 -> 0,01")
    void correctionRoundingHalfUp() {
        IndexSeries s = IndexSeries.of(Map.of(YearMonth.of(2026, 10), d("100"), YearMonth.of(2026, 11), d("100.05")));
        Result r = calc(withCorrection(CorrectionRule.byIndex(s, 0, false)), noHolidays(), "10.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 25));
        assertThat(r.correction()).isEqualByComparingTo("0.01");
    }

    @Test
    @DisplayName("fator ausente nunca é substituído em silêncio: lança com o mês faltante")
    void missingIndexFails() {
        CorrectionRule rule = CorrectionRule.byIndex(IndexSeries.of(Map.of(YearMonth.of(2026, 10), d("100"))), 0, false);
        assertThatThrownBy(() -> calc(withCorrection(rule), noHolidays(), "200.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 12, 10)))
                .isInstanceOfSatisfying(MissingIndexException.class,
                        e -> assertThat(e.month()).isEqualTo(YearMonth.of(2026, 12)));
        CorrectionRule noStart = CorrectionRule.byIndex(IndexSeries.of(Map.of(YearMonth.of(2026, 12), d("100"))), 0, false);
        assertThatThrownBy(() -> calc(withCorrection(noStart), noHolidays(), "200.00",
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 12, 10)))
                .isInstanceOfSatisfying(MissingIndexException.class,
                        e -> assertThat(e.month()).isEqualTo(YearMonth.of(2026, 10)));
    }

    @Test
    @DisplayName("tudo junto: 1.000,00, 60 dias (05/10 a 04/12), multa 20,00 + juros 20,00 + correção 12,00 = 52,00")
    void allComponentsTogether() {
        SurchargePolicy p = new SurchargePolicy(0, false, FineRule.of(d("2.00")),
                InterestRule.simplePerDay(d("1.00"), 30, null),
                CorrectionRule.byIndex(IndexSeries.of(Map.of(
                        YearMonth.of(2026, 10), d("100.00"), YearMonth.of(2026, 12), d("101.20"))), 0, false));
        Result r = calc(p, noHolidays(), "1000.00", MON_05_OCT, LocalDate.of(2026, 12, 4));
        assertThat(r.daysOverdue()).isEqualTo(60); // 26 de outubro + 30 de novembro + 4 de dezembro
        assertThat(r.fine()).isEqualByComparingTo("20.00");
        assertThat(r.interest()).isEqualByComparingTo("20.00");
        assertThat(r.correction()).isEqualByComparingTo("12.00"); // 1000 x 1,20 / 100
        assertThat(r.total()).isEqualByComparingTo("52.00");
    }

    @Test
    @DisplayName("juros pro rata die em mês de 31 dias de base: 100,00 a 1% por 31 dias é exatamente 1,00")
    void monthBasisIsAParameter() {
        SurchargePolicy p = new SurchargePolicy(0, false, FineRule.none(),
                InterestRule.simplePerDay(d("1.00"), 31, null), CorrectionRule.none());
        assertThat(calc(p, noHolidays(), "100.00", MON_05_OCT, MON_05_OCT.plusDays(31)).interest())
                .isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("não há juros 'por mês de calendário': 29 dias ao dia 29 de outubro não cobram um mês inteiro")
    void noCalendarMonthJumps() {
        // 05/10 -> 04/11 cruza o mês de calendário, mas são 30 dias: 100 x 1% x 30/30 = 1,00, nem 0 nem 2
        Result r = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, LocalDate.of(2026, 11, 4));
        assertThat(r.interest()).isEqualByComparingTo("1.00");
        // um dia antes, 29 dias: 0,9667 -> 0,97 (juros contínuos, sem salto na virada do mês)
        Result before = calc(policy(0, false), noHolidays(), "100.00", MON_05_OCT, LocalDate.of(2026, 11, 3));
        assertThat(before.interest()).isEqualByComparingTo("0.97");
    }

    // ------------------------------------------------------------------ travas

    @Test
    @DisplayName("trava legal: multa de 2,00% passa, 2,01% é recusada, zero passa")
    void fineCapIsTwoPercent() {
        assertThat(FineRule.of(d("2.00")).ratePercent()).isEqualByComparingTo("2.00");
        assertThat(FineRule.none().ratePercent()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> FineRule.of(d("2.01"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acima do teto");
        assertThatThrownBy(() -> FineRule.of(d("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FineRule.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(LegalLimits.MAX_FINE_PERCENT).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("teto próprio até 2% não exige norma; acima de 2% só com aceite reforçado (P0.20, classe b)")
    void fineCapOverride() {
        assertThat(FineRule.withCapOverride(d("1.00"), d("1.50"), null).ratePercent()).isEqualByComparingTo("1.00");
        assertThatThrownBy(() -> FineRule.withCapOverride(d("2.00"), d("1.50"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FineRule.withCapOverride(d("11.00"), d("10.00"), "Contrato"))
                .isInstanceOf(IllegalArgumentException.class);
        // Justificativa em texto livre, sozinha, NÃO basta mais acima de 2%.
        assertThatThrownBy(() -> FineRule.withCapOverride(d("5.00"), d("10.00"), "Contrato de concessão cl. 12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("withAboveLegalCap");
        assertThatThrownBy(() -> new FineRule(d("5.00"), d("10.00"), "Contrato de concessão cl. 12"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fee.fine_pct acima de 2%: aceite reforçado completo passa; cada requisito faltando é recusado")
    void fineAboveLegalCapRequiresFullApproval() {
        var proposer = java.util.UUID.randomUUID();
        var approver = java.util.UUID.randomUUID();
        var approval = new FineRule.FineCapApproval(
                "Contrato de concessão cl. 12", proposer, approver, "aceite-0001", true);
        assertThat(FineRule.withAboveLegalCap(d("5.00"), d("10.00"), approval).ratePercent())
                .isEqualByComparingTo("5.00");
        assertThatThrownBy(() -> FineRule.withAboveLegalCap(d("5.00"), d("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FineRule.FineCapApproval(
                "  ", proposer, approver, "aceite-0001", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("norma citada");
        assertThatThrownBy(() -> new FineRule.FineCapApproval(
                "Contrato", proposer, proposer, "aceite-0001", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não aprova");
        assertThatThrownBy(() -> new FineRule.FineCapApproval(
                "Contrato", proposer, approver, "  ", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("aceite formal");
        assertThatThrownBy(() -> new FineRule.FineCapApproval(
                "Contrato", proposer, approver, "aceite-0001", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("alerta de risco");
        assertThatThrownBy(() -> FineRule.withAboveLegalCap(d("1.00"), d("1.50"), approval))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fee.interest_mode DAILY_RATE_WITH_CAP: SC 0,033% ao dia, simples, 30 dias sobre 1000 = 9,90")
    void interestDailyRateWithAgencyCap() {
        assertThatThrownBy(() -> InterestRule.dailyRateWithAgencyCap(
                d("0.034"), LegalLimits.SC_INTEREST_DAILY_PERCENT, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("teto da agência");
        var policy = new SurchargePolicy(0, false, FineRule.none(),
                InterestRule.dailyRateWithAgencyCap(
                        LegalLimits.SC_INTEREST_DAILY_PERCENT, LegalLimits.SC_INTEREST_DAILY_PERCENT, null),
                CorrectionRule.none());
        var result = new SurchargeCalculator(policy, BusinessCalendar.of(java.util.List.of()))
                .calculate(Request.of(d("1000"), MON_05_OCT, MON_05_OCT.plusDays(30)));
        assertThat(result.interest()).isEqualByComparingTo("9.90");
        assertThat(result.fine()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("nenhuma regra tem valor padrão: política, juros e requisição exigem tudo")
    void noImplicitDefaults() {
        assertThatThrownBy(() -> new SurchargePolicy(-1, false, FineRule.none(), InterestRule.none(), CorrectionRule.none()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurchargePolicy(0, false, null, InterestRule.none(), CorrectionRule.none()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurchargePolicy(0, false, FineRule.none(), null, CorrectionRule.none()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurchargePolicy(0, false, FineRule.none(), InterestRule.none(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestRule.simplePerDay(null, 30, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestRule.simplePerDay(d("1"), 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestRule.simplePerDay(d("1"), 32, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectionRule.byIndex(null, 0, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorrectionRule.byIndex(SERIES, -1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Request(null, MON_05_OCT, MON_05_OCT, BigDecimal.ZERO, Exemption.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Request(d("1"), MON_05_OCT, null, BigDecimal.ZERO, Exemption.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Request(d("1"), MON_05_OCT, MON_05_OCT, null, Exemption.NONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Request(d("1"), MON_05_OCT, MON_05_OCT, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurchargeCalculator(null, noHolidays())).isInstanceOf(IllegalArgumentException.class);
    }
}
