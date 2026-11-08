package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Retrato factual de uma unidade consumidora e da sua ligacao/ramal
 * para o cruzamento (OPE-37) e para as regras de suspeita (GES-25).
 * E um RETRATO montado pelas portas a partir dos donos de cada fato
 * (cadastro da P1.3, ligacao da P2.23, hidrometria da ED-14,
 * telemetria da ED-17): esta entrega nao recadastra unidade nem
 * ligacao — so le o retrato.
 *
 * <p>Listas cronologicas (mais antiga primeiro): consumos mensais em
 * m3 e leituras absolutas do hidrometro. Campos de telemetria sao
 * opcionais por desenho do aceite ("com dados da telemetria da ED-17
 * quando houver"): {@code telemetryAvailable=false} ou fluxo nulo =
 * sem telemetria, e as regras que dependem dela nao disparam.
 *
 * <p>Campos da troca de hidrometro (ED-14): ciclos desde a troca e os
 * consumos medios antes/depois declarados pelo retrato da
 * hidrometria — a regra de suspeita nao reconstroi o historico da
 * troca, consome o fato.
 */
public record UnitFacts(
        UUID unitId, UUID tenantId, UUID linkedPropertyId, UUID connectionId,
        ConnectionStatus connectionStatus, PropertyRegistryRecord.PropertyUse categoryUse,
        BigDecimal declaredBuiltAreaSquareMeters, int economies,
        List<BigDecimal> monthlyConsumptionsM3, List<BigDecimal> meterReadings,
        BigDecimal meterNominalFlowM3PerHour, BigDecimal observedPeakFlowM3PerHour,
        Integer cyclesSinceMeterReplacement, BigDecimal avgConsumptionBeforeReplacementM3,
        BigDecimal avgConsumptionAfterReplacementM3,
        boolean telemetryAvailable, BigDecimal telemetryPeriodFlowM3) {

    /** Situacao da ligacao/ramal no retrato (catalogo existente, ADR-0034). */
    public enum ConnectionStatus {
        ACTIVE, CUT, INACTIVE, NONE
    }

    public UnitFacts {
        Objects.requireNonNull(unitId, "unidade consumidora e obrigatoria");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(connectionStatus, "situacao da ligacao e obrigatoria");
        Objects.requireNonNull(categoryUse, "categoria/uso da unidade e obrigatorio");
        if (economies <= 0)
            throw new IllegalArgumentException("economias da unidade deve ser maior que zero: " + economies);
        if (declaredBuiltAreaSquareMeters != null && declaredBuiltAreaSquareMeters.signum() < 0)
            throw new IllegalArgumentException("area declarada da unidade nao pode ser negativa");
        monthlyConsumptionsM3 = monthlyConsumptionsM3 == null ? List.of() : List.copyOf(monthlyConsumptionsM3);
        meterReadings = meterReadings == null ? List.of() : List.copyOf(meterReadings);
        if (monthlyConsumptionsM3.stream().anyMatch(Objects::isNull)
                || meterReadings.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("consumo/leitura nula no retrato da unidade");
        if (cyclesSinceMeterReplacement != null && cyclesSinceMeterReplacement < 0)
            throw new IllegalArgumentException("ciclos desde a troca nao pode ser negativo");
    }

    /** Ultimo consumo mensal (ciclo mais recente), quando houver. */
    public java.util.Optional<BigDecimal> lastConsumptionM3() {
        if (monthlyConsumptionsM3.isEmpty()) return java.util.Optional.empty();
        return java.util.Optional.of(monthlyConsumptionsM3.get(monthlyConsumptionsM3.size() - 1));
    }
}
