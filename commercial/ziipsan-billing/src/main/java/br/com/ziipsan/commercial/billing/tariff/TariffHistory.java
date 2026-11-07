package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Histórico tarifário público (P2.29; NR 13 da ANA, arts. 61 e 62 —
 * transparência tarifária): as versões cuja vigência toca a janela dos
 * últimos {@code historyYears} anos, <b>com o método de cálculo de cada
 * uma</b>, para que qualquer reajuste possa ser refeito e conferido por
 * quem lê o histórico. O piso da janela é travado: menos de
 * {@value #MIN_HISTORY_YEARS} anos é recusado, venha do parâmetro
 * {@code tariff.history_years_public} ou de uma chamada direta.
 *
 * <p>Fronteira: esta classe monta o histórico; <b>publicá-lo no portal é
 * da ED-38</b> (ver ED-38), que consome estas entradas.
 */
public final class TariffHistory {

    /** Piso travado da janela do histórico público, em anos (aceite da P2.29). */
    public static final int MIN_HISTORY_YEARS = 5;

    /** Uma versão no histórico, com o metadado que a transparência exige. */
    public record HistoryEntry(UUID versionId, LocalDate validFrom, LocalDate validTo,
                               TariffVersionChangeType changeType, String regulatoryAct,
                               String indexCode, BigDecimal appliedPct,
                               String calculationMethod) {
    }

    /** Versão + metadado, como o chamador os lê do banco (V4_1 + V4_9). */
    public record VersionWithMetadata(TariffVersion version, VersionChangeMetadata metadata) {
    }

    private TariffHistory() {
    }

    /**
     * Entradas do histórico na janela {@code [referenceDate − historyYears, referenceDate]}:
     * entra a versão cuja vigência (inclusive aberta) intersecta a janela.
     * Ordenação cronológica pela vigência.
     *
     * @throws TariffConfigurationException janela abaixo do piso travado, ou
     *                        versão da janela sem o método de cálculo (o
     *                        histórico público não publica versão sem método)
     */
    public static List<HistoryEntry> publicHistory(List<VersionWithMetadata> versions,
                                                   LocalDate referenceDate, int historyYears) {
        if (historyYears < MIN_HISTORY_YEARS) {
            throw new TariffConfigurationException(
                    "Histórico público de " + historyYears + " anos abaixo do piso travado de "
                            + MIN_HISTORY_YEARS + " anos (NR 13, arts. 61 e 62)");
        }
        if (versions == null || referenceDate == null) {
            throw new TariffConfigurationException("Histórico exige as versões e a data de referência");
        }
        LocalDate windowStart = referenceDate.minusYears(historyYears);
        return versions.stream()
                .filter(v -> intersects(v.version(), windowStart, referenceDate))
                .sorted(Comparator.comparing(v -> v.version().validFrom()))
                .map(v -> {
                    VersionChangeMetadata m = v.metadata();
                    String method = m == null ? null : m.calculationMethod();
                    if (method == null || method.isBlank()) {
                        throw new TariffConfigurationException(
                                "Versão " + v.version().id() + " no histórico sem o método de cálculo: "
                                        + "a transparência da NR 13 (arts. 61 e 62) exige o método mantido");
                    }
                    return new HistoryEntry(v.version().id(), v.version().validFrom(), v.version().validTo(),
                            m.changeType(), m.regulatoryAct(), m.indexCode(), m.appliedPct(), method);
                })
                .toList();
    }

    private static boolean intersects(TariffVersion version, LocalDate from, LocalDate to) {
        LocalDate end = version.validTo() == null ? LocalDate.MAX : version.validTo();
        return !end.isBefore(from) && !version.validFrom().isAfter(to);
    }
}
