package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * E4 — Criterio de seleccao de contas para cessao a empresa terceirizada.
 *
 * <p>Objecto de valor EXPLICITO (ADR-0038): dias minimos de atraso e valor
 * minimo sao obrigatorios e declarados por quem selecciona — sem eles, a
 * construcao recusa (nenhum criterio presumido). Valor maximo e exclusoes
 * (por divida e por UC) sao opcionais MAS, quando declarados, valem
 * integralmente; conjuntos de exclusao nulos valem "sem exclusoes
 * declaradas" apenas porque o chamador os entrega vazios/nulos de forma
 * explicita neste objecto — nunca um criterio escondido no codigo.
 *
 * <p>A seleccao ({@link #matches}) e PURA: nao le relogio (a data de
 * referencia e entregue), nao escreve nada.
 */
public record AssignmentCriteria(Integer minDaysOverdue, BigDecimal minAmount, BigDecimal maxAmount,
                                 Set<UUID> excludedDebtIds, Set<UUID> excludedUnitIds) {

    public AssignmentCriteria {
        if (minDaysOverdue == null)
            throw new ThirdPartyCollectionException("CRITERIA_MIN_DAYS_REQUIRED",
                    "criterio exige os dias minimos de atraso declarados (ADR-0038): ausencia e recusa");
        if (minDaysOverdue < 0)
            throw new ThirdPartyCollectionException("CRITERIA_MIN_DAYS_INVALID",
                    "dias minimos de atraso nao podem ser negativos: " + minDaysOverdue);
        if (minAmount == null)
            throw new ThirdPartyCollectionException("CRITERIA_MIN_AMOUNT_REQUIRED",
                    "criterio exige o valor minimo declarado (ADR-0038): ausencia e recusa");
        if (minAmount.signum() < 0)
            throw new ThirdPartyCollectionException("CRITERIA_MIN_AMOUNT_INVALID",
                    "valor minimo nao pode ser negativo: " + minAmount);
        if (maxAmount != null && maxAmount.compareTo(minAmount) < 0)
            throw new ThirdPartyCollectionException("CRITERIA_RANGE_INVERTED",
                    "valor maximo (" + maxAmount + ") inferior ao valor minimo (" + minAmount + ")");
        excludedDebtIds = excludedDebtIds == null ? Set.of() : Set.copyOf(excludedDebtIds);
        excludedUnitIds = excludedUnitIds == null ? Set.of() : Set.copyOf(excludedUnitIds);
    }

    /** Seleccao pura sobre uma candidata, na data de referencia entregue. */
    public boolean matches(ThirdPartyPorts.CandidateDebt candidate, LocalDate asOf) {
        if (candidate == null || asOf == null) return false;
        if (excludedDebtIds.contains(candidate.debtId())) return false;
        if (excludedUnitIds.contains(candidate.unitId())) return false;
        long daysOverdue = ChronoUnit.DAYS.between(candidate.dueDate(), asOf);
        if (daysOverdue < minDaysOverdue) return false;
        if (candidate.amount().compareTo(minAmount) < 0) return false;
        return maxAmount == null || candidate.amount().compareTo(maxAmount) <= 0;
    }
}
