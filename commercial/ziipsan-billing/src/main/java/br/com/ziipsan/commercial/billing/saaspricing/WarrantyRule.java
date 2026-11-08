package br.com.ziipsan.commercial.billing.saaspricing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Garantia e correcao de vicios nos valores que o acceptance da ED-10
 * FIXA literalmente: garantia de 12 meses e correcao de vicios em 48 h.
 * Sao constantes COM FONTE (o acceptance), nao parametros inventados.
 */
public final class WarrantyRule {
    private WarrantyRule() {}
    public static final int WARRANTY_MONTHS = 12;
    public static final long DEFECT_CORRECTION_HOURS = 48;

    public static LocalDate warrantyEnd(LocalDate serviceStart) {
        if (serviceStart == null) throw new IllegalArgumentException("inicio do servico e obrigatorio");
        return serviceStart.plusMonths(WARRANTY_MONTHS);
    }
    public static boolean withinWarranty(LocalDate serviceStart, LocalDate onDate) {
        if (serviceStart == null || onDate == null) throw new IllegalArgumentException("inicio e data sao obrigatorios");
        return !onDate.isBefore(serviceStart) && onDate.isBefore(warrantyEnd(serviceStart));
    }
    public static Instant defectCorrectionDeadline(Instant reportedAt) {
        if (reportedAt == null) throw new IllegalArgumentException("instante do relato e obrigatorio");
        return reportedAt.plus(DEFECT_CORRECTION_HOURS, ChronoUnit.HOURS);
    }
    public static boolean correctedInTime(Instant reportedAt, Instant correctedAt) {
        if (correctedAt == null) throw new IllegalArgumentException("instante da correcao e obrigatorio");
        return !correctedAt.isAfter(defectCorrectionDeadline(reportedAt));
    }
}
