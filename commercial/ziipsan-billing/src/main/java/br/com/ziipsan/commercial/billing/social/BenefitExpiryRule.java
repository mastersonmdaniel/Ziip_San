package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Vencimento de benefício (P2.5/CAD-88): a regra e o cálculo do marco — a
 * <b>mensagem</b> de aviso é do motor de mensagens da P2.32 e não está aqui.
 *
 * <pre>
 *   benefício válido até 15/03/2026 → aplicado nas competências até 2026-03
 *   competência seguinte (2026-04) → NÃO aplicado, ainda que o restante do
 *                                    enquadramento continue igual
 *   marco do aviso = data de vencimento − benefit.expiry_notice_days (classe d)
 * </pre>
 */
public final class BenefitExpiryRule {

    private BenefitExpiryRule() {
    }

    /** A competência em que o benefício ainda vale: a do mês do vencimento. */
    public static YearMonth lastApplicableCompetence(LocalDate benefitValidUntil) {
        if (benefitValidUntil == null) {
            throw new IllegalArgumentException("Data de vencimento do benefício é obrigatória");
        }
        return YearMonth.from(benefitValidUntil);
    }

    /**
     * O benefício se aplica na competência? Vencido, deixa de ser aplicado na
     * competência <b>seguinte</b> ao vencimento (na do vencimento, ainda vale).
     */
    public static boolean appliesIn(YearMonth competence, LocalDate benefitValidUntil) {
        if (competence == null) {
            throw new IllegalArgumentException("Competência é obrigatória");
        }
        return !competence.isAfter(lastApplicableCompetence(benefitValidUntil));
    }

    /**
     * Marco do aviso de vencimento: {@code vencimento − expiryNoticeDays}
     * ({@code benefit.expiry_notice_days}). A P2.32 emite o aviso a partir
     * desta data; aqui só se calcula o marco.
     */
    public static LocalDate noticeStartDate(LocalDate benefitValidUntil, int expiryNoticeDays) {
        if (benefitValidUntil == null) {
            throw new IllegalArgumentException("Data de vencimento do benefício é obrigatória");
        }
        if (expiryNoticeDays < 0) {
            throw new IllegalArgumentException(
                    "benefit.expiry_notice_days não pode ser negativo: " + expiryNoticeDays);
        }
        return benefitValidUntil.minusDays(expiryNoticeDays);
    }

    /** A data está na janela do aviso, do marco até o vencimento (inclusive)? */
    public static boolean noticeWindowOpen(LocalDate date, LocalDate benefitValidUntil, int expiryNoticeDays) {
        if (date == null) {
            throw new IllegalArgumentException("Data é obrigatória");
        }
        return !date.isBefore(noticeStartDate(benefitValidUntil, expiryNoticeDays))
                && !date.isAfter(benefitValidUntil);
    }
}
