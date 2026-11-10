package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-102: cronograma fisico-financeiro de um convenio — etapas com
 * prazo, percentual fisico previsto e valor previsto. Invariantes do
 * cronograma (validadas na construcao): as etapas somam 100% do fisico
 * e exatamente o valor total do instrumento; prazos dentro da vigencia.
 * O confronto previsto x executado e feito pelo
 * {@link WorksAgreementService}.
 */
public record Schedule(UUID tenantId, UUID agreementId, List<Stage> stages) {

    public record Stage(
            String stageRef,
            String description,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            BigDecimal plannedPercent,
            BigDecimal plannedAmount) {
        public Stage {
            if (stageRef == null || stageRef.isBlank())
                throw new IllegalArgumentException("stageRef e obrigatorio");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("description da etapa e obrigatoria");
            Objects.requireNonNull(plannedStart, "plannedStart e obrigatorio");
            Objects.requireNonNull(plannedEnd, "plannedEnd e obrigatorio");
            if (plannedEnd.isBefore(plannedStart))
                throw new IllegalArgumentException("plannedEnd nao pode ser anterior a plannedStart");
            Objects.requireNonNull(plannedPercent, "plannedPercent e obrigatorio");
            if (plannedPercent.signum() <= 0
                    || plannedPercent.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("plannedPercent de etapa tem de estar em (0, 100]");
            Objects.requireNonNull(plannedAmount, "plannedAmount e obrigatorio");
            if (plannedAmount.signum() < 0)
                throw new IllegalArgumentException("plannedAmount nao pode ser negativo");
        }
    }

    public Schedule {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(agreementId, "agreementId e obrigatorio");
        if (stages == null || stages.isEmpty())
            throw new IllegalArgumentException("o cronograma tem de ter ao menos uma etapa");
        stages = List.copyOf(stages);
        long distinct = stages.stream().map(Stage::stageRef).distinct().count();
        if (distinct != stages.size())
            throw new IllegalArgumentException("stageRef duplicado no cronograma");
    }

    /** Valida o cronograma contra o instrumento: 100% e valor total. */
    public void validateAgainst(FundingAgreement agreement) {
        Objects.requireNonNull(agreement, "agreement e obrigatorio");
        if (!tenantId.equals(agreement.tenantId()) || !agreementId.equals(agreement.id()))
            throw new IllegalArgumentException("cronograma de outro tenant/instrumento");
        BigDecimal pct = stages.stream().map(Stage::plannedPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pct.compareTo(new BigDecimal("100")) != 0)
            throw new IllegalArgumentException("o cronograma tem de somar 100% do fisico (soma atual: " + pct + ")");
        BigDecimal amt = stages.stream().map(Stage::plannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amt.compareTo(agreement.totalValue()) != 0)
            throw new IllegalArgumentException("o cronograma tem de somar o valor total do instrumento (soma atual: " + amt + ")");
        for (Stage s : stages) {
            if (s.plannedStart().isBefore(agreement.startDate()) || s.plannedEnd().isAfter(agreement.endDate()))
                throw new IllegalArgumentException("etapa " + s.stageRef() + " fora da vigencia do instrumento");
        }
    }

    /** Percentual fisico previsto acumulado ate a data (inclusive). */
    public BigDecimal plannedPercentThrough(LocalDate date) {
        return stages.stream().filter(s -> !s.plannedEnd().isAfter(date))
                .map(Stage::plannedPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Valor previsto acumulado ate a data (inclusive). */
    public BigDecimal plannedAmountThrough(LocalDate date) {
        return stages.stream().filter(s -> !s.plannedEnd().isAfter(date))
                .map(Stage::plannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
