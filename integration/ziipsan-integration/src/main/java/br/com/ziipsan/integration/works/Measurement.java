package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-102: medicao de obra de um convenio/contrato de repasse —
 * periodo, percentual fisico do periodo e valor do periodo. Os
 * acumulados e as travas contra cronograma e valor do contrato sao
 * garantidos pelo {@link WorksAgreementService} (regras que cruzam
 * medicoes) e espelhados no SQL pendente onde a trava e por linha.
 */
public record Measurement(
        UUID tenantId,
        UUID agreementId,
        int sequence,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal physicalPercentPeriod,
        BigDecimal amount,
        Status status) {

    public enum Status { DRAFT, APPROVED }

    public Measurement {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(agreementId, "agreementId e obrigatorio");
        if (sequence < 1) throw new IllegalArgumentException("sequence da medicao comeca em 1");
        Objects.requireNonNull(periodStart, "periodStart e obrigatorio");
        Objects.requireNonNull(periodEnd, "periodEnd e obrigatorio");
        if (periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodEnd nao pode ser anterior a periodStart");
        Objects.requireNonNull(physicalPercentPeriod, "physicalPercentPeriod e obrigatorio");
        if (physicalPercentPeriod.signum() < 0
                || physicalPercentPeriod.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("physicalPercentPeriod tem de estar entre 0 e 100");
        Objects.requireNonNull(amount, "amount e obrigatorio");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount nao pode ser negativo");
        Objects.requireNonNull(status, "status e obrigatorio");
    }
}
