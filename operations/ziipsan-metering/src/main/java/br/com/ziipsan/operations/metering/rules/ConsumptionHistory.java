package br.com.ziipsan.operations.metering.rules;

import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Historico de consumo reconstituido das proprias versoes (P2.5, DAD-14), sem ler
 * o {@code audit_log}: a trilha de auditoria e registro de quem mexeu, nao fonte
 * de historia de faturamento -- e ela redige/omite dado (core V2), entao nem
 * conseguiria devolver os valores.
 *
 * <p>A janela e a de {@code ret.billing_history_months} (catalogo da core V3):
 * piso de 120 meses, so amplia. Aqui o piso e o padrao; um tenant com aceite
 * para mais meses passa o seu valor a {@link #reconstructCurrent(List, YearMonth, int)}.
 */
public final class ConsumptionHistory {

    /** Piso de retencao do historico de leitura e faturamento, em meses (NR 11, art. 22). */
    public static final int HISTORY_MONTHS = 120;

    private ConsumptionHistory() {
    }

    /** Historico vigente ate {@code lastReference}, na janela padrao de {@value #HISTORY_MONTHS} meses. */
    public static List<ConsumptionRecord> reconstructCurrent(List<ConsumptionRecord> allVersions,
                                                             YearMonth lastReference) {
        return reconstructCurrent(allVersions, lastReference, HISTORY_MONTHS);
    }

    /**
     * Uma linha por referencia -- a versao vigente de cada uma -- dentro da janela
     * de {@code months} meses que termina em {@code lastReference} (inclusive),
     * em ordem cronologica. Versoes substituidas nao entram: o historico de
     * faturamento e o que valeu em cada mes; a cadeia completa de uma referencia
     * sai de {@link #versionChain(List, YearMonth)}.
     */
    public static List<ConsumptionRecord> reconstructCurrent(List<ConsumptionRecord> allVersions,
                                                             YearMonth lastReference, int months) {
        Objects.requireNonNull(allVersions, "allVersions");
        Objects.requireNonNull(lastReference, "lastReference");
        if (months < 1) {
            throw new IllegalArgumentException("a janela do historico precisa de ao menos 1 mes");
        }
        YearMonth first = lastReference.minusMonths(months - 1L);
        return allVersions.stream()
                .filter(ConsumptionRecord::isCurrent)
                .filter(r -> {
                    YearMonth ref = r.referenceMonth();
                    return !ref.isBefore(first) && !ref.isAfter(lastReference);
                })
                .sorted(Comparator.comparing(ConsumptionRecord::referenceMonth))
                .toList();
    }

    /** A cadeia inteira de uma referencia, da versao original a vigente. */
    public static List<ConsumptionRecord> versionChain(List<ConsumptionRecord> allVersions, YearMonth reference) {
        Objects.requireNonNull(allVersions, "allVersions");
        Objects.requireNonNull(reference, "reference");
        return allVersions.stream()
                .filter(r -> reference.equals(r.referenceMonth()))
                .sorted(Comparator.comparingInt(ConsumptionRecord::getVersionNo))
                .toList();
    }
}
