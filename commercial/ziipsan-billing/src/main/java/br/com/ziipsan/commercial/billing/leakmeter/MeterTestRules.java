package br.com.ziipsan.commercial.billing.leakmeter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regras da afericao de hidrometro (P3.28; parametros {@code meter.test_*}
 * de PARAMETROS-DO-TENANT: "aviso, cobranca so se normal, meses de
 * revisao"; NR 11 art. 38 e a revisao que a afericao alimenta).
 *
 * <ul>
 *   <li><b>Aviso:</b> a afericao so pode acontecer depois de
 *       {@code meter.test_notice_days} dias do aviso ao usuario -- teste
 *       marcado para antes disso e recusado no agendamento, nao no laudo;</li>
 *   <li><b>Cobranca:</b> com {@code meter.test_charge_only_if_normal}
 *       ligado (padrao de fabrica), a afericao so e cobravel quando o laudo
 *       conclui NORMAL; IRREGULAR ou INCONCLUSIVO nao geram cobranca.
 *       Desligado pelo tenant, a cobranca vale para qualquer veredito.
 *       QUANTO se cobra e a tabela de precos do tipo (ED-34) -- esta regra
 *       decide apenas SE cobra;</li>
 *   <li><b>Revisao:</b> hidrometro IRREGULAR abre revisao das faturas dos
 *       ultimos {@code meter.test_review_months} meses, terminando no mes
 *       do ensaio (a revisao em si corre pela BILL_REVIEW/retificacao da
 *       P2.20; esta regra so delimita as referencias).</li>
 * </ul>
 *
 * <p>O veredito e o erro medido vem do LAUDO (ensaio de bancada, tolerancia
 * do laboratorio/INMETRO): esta tarefa registra o veredito e o erro que o
 * laudo declara; a tabela de tolerancia nao e parametro do tenant aqui e
 * nao foi inventada.
 */
public final class MeterTestRules {

    /** Veredito do laudo de afericao, como o laboratorio o declara. */
    public enum MeterVerdict {
        NORMAL,
        IRREGULAR,
        INCONCLUSIVE
    }

    /** Decisao de cobranca da afericao, com o motivo legivel. */
    public record ChargeDecision(boolean chargeable, String reason) {
    }

    private MeterTestRules() {
    }

    /** O ultimo dia de aviso que ainda permite o ensaio em {@code testDate} e o contrario: o primeiro dia de ensaio apos o aviso. */
    public static LocalDate earliestTestDate(LeakMeterParameters parameters, LocalDate noticeDate) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(noticeDate, "noticeDate");
        return noticeDate.plusDays(parameters.meterTestNoticeDays());
    }

    /** O aviso respeitou a antecedencia para o ensaio nesta data? */
    public static boolean isNoticeTimely(LeakMeterParameters parameters, LocalDate noticeDate, LocalDate testDate) {
        Objects.requireNonNull(testDate, "testDate");
        return !testDate.isBefore(earliestTestDate(parameters, noticeDate));
    }

    /** Como {@link #isNoticeTimely}, mas lanca excecao nomeando as datas quando cedo demais. */
    public static void requireTimelyNotice(LeakMeterParameters parameters, LocalDate noticeDate, LocalDate testDate) {
        if (!isNoticeTimely(parameters, noticeDate, testDate)) {
            throw new IllegalArgumentException("Afericao marcada para " + testDate + " sem a antecedencia de "
                    + parameters.meterTestNoticeDays() + " dias do aviso de " + noticeDate
                    + " (primeiro dia possivel: " + earliestTestDate(parameters, noticeDate) + ")");
        }
    }

    /** A afericao e cobravel para este veredito, conforme meter.test_charge_only_if_normal? */
    public static ChargeDecision chargeDecision(LeakMeterParameters parameters, MeterVerdict verdict) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(verdict, "verdict");
        if (!parameters.meterTestChargeOnlyIfNormal()) {
            return new ChargeDecision(true,
                    "Tenant nao condiciona a cobranca ao veredito (meter.test_charge_only_if_normal desligado)");
        }
        return switch (verdict) {
            case NORMAL -> new ChargeDecision(true, "Hidrometro normal: afericao cobravel");
            case IRREGULAR -> new ChargeDecision(false,
                    "Hidrometro irregular: afericao nao cobrada (meter.test_charge_only_if_normal)");
            case INCONCLUSIVE -> new ChargeDecision(false,
                    "Laudo inconclusivo: afericao nao cobrada (meter.test_charge_only_if_normal)");
        };
    }

    /**
     * Referencias das faturas sujeitas a revisao quando o hidrometro esta
     * irregular: os {@code meter.test_review_months} meses que terminam no
     * mes do ensaio, em ordem crescente. Veredito que nao e IRREGULAR nao
     * abre revisao (lista vazia).
     */
    public static List<YearMonth> reviewReferences(LeakMeterParameters parameters, MeterVerdict verdict,
                                                   LocalDate testDate) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(testDate, "testDate");
        if (verdict != MeterVerdict.IRREGULAR) {
            return List.of();
        }
        int months = parameters.meterTestReviewMonths();
        YearMonth last = YearMonth.from(testDate);
        List<YearMonth> references = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            references.add(last.minusMonths(i));
        }
        return List.copyOf(references);
    }
}
