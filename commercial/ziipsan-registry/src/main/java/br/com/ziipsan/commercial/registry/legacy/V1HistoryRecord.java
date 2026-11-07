package br.com.ziipsan.commercial.registry.legacy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * P2.10: uma linha do histórico mensal da v1 (um mês de uma unidade), como
 * ela vive no staging da V14_2 antes e depois da aplicação. O histórico
 * importado tem no máximo {@link #MAX_MONTHS} meses por unidade — a janela
 * de 120 meses do aceite, contada para trás a partir do mês de corte da
 * migração.
 */
public final class V1HistoryRecord {

    /** Janela do aceite da P2.10: 120 meses de histórico. */
    public static final int MAX_MONTHS = 120;

    public enum Status {
        STAGED, APPLIED, REJECTED
    }

    private final UUID id;
    private final UUID batchId;
    private final UUID stagingUnitId;
    private final String unitLegacyKey;
    private final int sourceRowNo;
    private final LocalDate referenceMonth;
    private final BigDecimal consumptionM3;
    private final Long billedAmountCents;
    private final LocalDate dueDate;
    private final List<LegacyIssue> issues;
    private Status status;

    public V1HistoryRecord(UUID id, UUID batchId, UUID stagingUnitId, String unitLegacyKey,
                           int sourceRowNo, LocalDate referenceMonth, BigDecimal consumptionM3,
                           Long billedAmountCents, LocalDate dueDate, List<LegacyIssue> issues) {
        this.id = id;
        this.batchId = batchId;
        this.stagingUnitId = stagingUnitId;
        this.unitLegacyKey = unitLegacyKey;
        this.sourceRowNo = sourceRowNo;
        this.referenceMonth = referenceMonth;
        this.consumptionM3 = consumptionM3;
        this.billedAmountCents = billedAmountCents;
        this.dueDate = dueDate;
        this.issues = new ArrayList<>(issues == null ? List.of() : issues);
        this.status = Status.STAGED;
    }

    public UUID id() {
        return id;
    }

    public UUID batchId() {
        return batchId;
    }

    public UUID stagingUnitId() {
        return stagingUnitId;
    }

    public String unitLegacyKey() {
        return unitLegacyKey;
    }

    public int sourceRowNo() {
        return sourceRowNo;
    }

    public LocalDate referenceMonth() {
        return referenceMonth;
    }

    public BigDecimal consumptionM3() {
        return consumptionM3;
    }

    public Long billedAmountCents() {
        return billedAmountCents;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public List<LegacyIssue> issues() {
        return List.copyOf(issues);
    }

    public Status status() {
        return status;
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(LegacyIssue::isError);
    }

    public void addIssue(LegacyIssue issue) {
        issues.add(issue);
    }

    public void markApplied() {
        this.status = Status.APPLIED;
    }

    public void markRejected() {
        this.status = Status.REJECTED;
    }
}
