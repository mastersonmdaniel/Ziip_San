package br.com.ziipsan.core.inspection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatorio de unidades com fraude (aceite da ED-24). Funcao pura sobre os
 * registros de fraude e os autos: uma linha por unidade que tem AO MENOS
 * uma fraude registrada, com contagens por situacao, tipos encontrados,
 * autos emitidos, debito efetivamente lancado e recursos pendentes.
 * Unidade sem fraude nao entra. A ordem e deterministica (mais fraudes
 * primeiro; desempate pelo id da unidade) para o relatorio ser reproduzivel.
 *
 * <p>Contagem por situacao (correcao D6 da revisao cruzada da ED-24):
 * {@code fraudCount} e o total de registros da unidade e a linha expoe os
 * tres contadores — {@code confirmedCount}, {@code suspectedCount} e
 * {@code dismissedCount} — de modo que
 * confirmed + suspected + dismissed == fraudCount, sem que fraudes
 * descartadas (DISMISSED) inflem o total sem explicacao na propria linha.
 *
 * <p>Fora daqui, por ordem do aceite: cruzamento com o cadastro imobiliario
 * municipal e analitica de fraude/perdas aparentes (ED-95).
 */
public final class FraudReport {

    private FraudReport() {}

    public record UnitFraudRow(UUID consumerUnitId, int fraudCount, int confirmedCount, int suspectedCount,
                               int dismissedCount,
                               Map<FraudRecord.FraudType, Integer> countByType,
                               int noticesIssued, BigDecimal debitLaunchedTotal, int pendingAppeals) {
        public UnitFraudRow {
            Objects.requireNonNull(consumerUnitId, "unidade e obrigatoria");
            if (fraudCount <= 0) throw new IllegalArgumentException("linha de relatorio exige ao menos uma fraude");
            countByType = countByType == null ? Map.of() : Map.copyOf(countByType);
            Objects.requireNonNull(debitLaunchedTotal, "total de debito lancado e obrigatorio");
        }
    }

    public record Report(List<UnitFraudRow> rows, int unitsWithFraud, int totalFrauds,
                         BigDecimal totalDebitLaunched) {
        public Report {
            rows = rows == null ? List.of() : List.copyOf(rows);
            Objects.requireNonNull(totalDebitLaunched, "total de debito do relatorio e obrigatorio");
        }
    }

    public static Report build(List<FraudRecord> frauds, List<InfractionLifecycle.Case> notices) {
        Objects.requireNonNull(frauds, "lista de fraudes e obrigatoria (use lista vazia)");
        Objects.requireNonNull(notices, "lista de autos e obrigatoria (use lista vazia)");

        Map<UUID, List<InfractionLifecycle.Case>> noticesByUnit = new LinkedHashMap<>();
        for (InfractionLifecycle.Case notice : notices) {
            noticesByUnit.computeIfAbsent(notice.consumerUnitId(), k -> new ArrayList<>()).add(notice);
        }

        Map<UUID, List<FraudRecord>> fraudsByUnit = new LinkedHashMap<>();
        for (FraudRecord fraud : frauds) {
            fraudsByUnit.computeIfAbsent(fraud.consumerUnitId(), k -> new ArrayList<>()).add(fraud);
        }

        List<UnitFraudRow> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<FraudRecord>> entry : fraudsByUnit.entrySet()) {
            List<FraudRecord> unitFrauds = entry.getValue();
            int confirmed = 0;
            int suspected = 0;
            int dismissed = 0;
            Map<FraudRecord.FraudType, Integer> byType = new EnumMap<>(FraudRecord.FraudType.class);
            for (FraudRecord fraud : unitFrauds) {
                if (fraud.status() == FraudRecord.FraudStatus.CONFIRMED) confirmed++;
                if (fraud.status() == FraudRecord.FraudStatus.SUSPECTED) suspected++;
                if (fraud.status() == FraudRecord.FraudStatus.DISMISSED) dismissed++;
                byType.merge(fraud.type(), 1, Integer::sum);
            }
            List<InfractionLifecycle.Case> unitNotices = noticesByUnit.getOrDefault(entry.getKey(), List.of());
            BigDecimal unitDebit = BigDecimal.ZERO;
            int pendingAppeals = 0;
            for (InfractionLifecycle.Case notice : unitNotices) {
                if (notice.debitState() == InfractionLifecycle.DebitState.LAUNCHED) {
                    unitDebit = unitDebit.add(notice.amount());
                }
                if (notice.status() == InfractionLifecycle.NoticeStatus.UNDER_APPEAL) pendingAppeals++;
            }
            totalDebit = totalDebit.add(unitDebit);
            rows.add(new UnitFraudRow(entry.getKey(), unitFrauds.size(), confirmed, suspected, dismissed,
                    byType, unitNotices.size(), unitDebit, pendingAppeals));
        }
        rows.sort(Comparator.comparingInt(UnitFraudRow::fraudCount).reversed()
                .thenComparing(row -> row.consumerUnitId().toString()));
        return new Report(rows, rows.size(), frauds.size(), totalDebit);
    }
}
