package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-102: servico do dominio de convenios — registo de instrumentos
 * ligados ao GIS (C1), cronograma (C3) e medicoes (C2) com as regras
 * de consistencia do aceite:
 * <ul>
 *   <li>percentual fisico ACUMULADO nunca excede 100%;</li>
 *   <li>valor ACUMULADO nunca excede o valor total do instrumento;</li>
 *   <li>a medicao nao excede o CRONOGRAMA: o fisico acumulado ate ao
 *       fim do periodo nao pode superar o fisico previsto acumulado
 *       das etapas concluidas ate essa data, e o valor acumulado nao
 *       pode superar o valor previsto acumulado ate essa data;</li>
 *   <li>periodos de medicao nao se sobrepoem e a sequencia e unica
 *       por instrumento; medicao so em instrumento ACTIVE.</li>
 * </ul>
 * Armazenamento em memoria por desenho do pacote (nucleo puro, como
 * as entregas vizinhas); a fiacao JDBC/JPA e pendencia P-ED102-04.
 */
public final class WorksAgreementService {

    private final GisProjectPort gis;
    private final Map<UUID, FundingAgreement> agreements = new LinkedHashMap<>();
    private final Map<UUID, Schedule> schedules = new LinkedHashMap<>();
    private final Map<UUID, List<Measurement>> measurements = new LinkedHashMap<>();

    public WorksAgreementService(GisProjectPort gis) {
        this.gis = Objects.requireNonNull(gis, "porta do GIS (ED-53) e obrigatoria");
    }

    /** C1: regista o instrumento validando cada ligacao no GIS real (porta). */
    public FundingAgreement register(FundingAgreement agreement) {
        Objects.requireNonNull(agreement, "agreement e obrigatorio");
        if (agreements.containsKey(agreement.id()))
            throw new IllegalArgumentException("instrumento ja registrado: " + agreement.id());
        for (FundingAgreement.GisLink link : agreement.gisLinks()) {
            var project = gis.findProject(agreement.tenantId(), link.projectRef())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "projeto do GIS inexistente para o tenant: " + link.projectRef()));
            if (!project.tenantId().equals(agreement.tenantId()))
                throw new IllegalArgumentException("projeto do GIS de outro tenant: " + link.projectRef());
            if (link.packageRef() != null) {
                gis.findPackage(agreement.tenantId(), link.projectRef(), link.packageRef())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "pacote do GIS inexistente para o tenant: " + link.packageRef()));
            }
        }
        agreements.put(agreement.id(), agreement);
        measurements.put(agreement.id(), new ArrayList<>());
        return agreement;
    }

    /** C3: define o cronograma do instrumento (validado contra ele). */
    public Schedule defineSchedule(Schedule schedule) {
        Objects.requireNonNull(schedule, "schedule e obrigatorio");
        FundingAgreement agreement = requireAgreement(schedule.tenantId(), schedule.agreementId());
        schedule.validateAgainst(agreement);
        schedules.put(agreement.id(), schedule);
        return schedule;
    }

    /** C2: regista uma medicao aplicando TODAS as travas de consistencia. */
    public Measurement addMeasurement(Measurement m) {
        Objects.requireNonNull(m, "measurement e obrigatoria");
        FundingAgreement agreement = requireAgreement(m.tenantId(), m.agreementId());
        if (agreement.status() != FundingAgreement.Status.ACTIVE)
            throw new IllegalStateException("medicao so em instrumento ACTIVE (estado atual: " + agreement.status() + ")");
        Schedule schedule = schedules.get(agreement.id());
        if (schedule == null)
            throw new IllegalStateException("medicao exige cronograma definido (C3 antes de C2)");
        List<Measurement> existing = measurements.get(agreement.id());
        for (Measurement e : existing) {
            if (e.sequence() == m.sequence())
                throw new IllegalArgumentException("sequencia de medicao duplicada: " + m.sequence());
            if (!m.periodStart().isAfter(e.periodEnd()) && !e.periodStart().isAfter(m.periodEnd()))
                throw new IllegalArgumentException("periodo da medicao sobrepoe a medicao " + e.sequence());
        }
        BigDecimal accPct = accumulatedPercent(agreement.id()).add(m.physicalPercentPeriod());
        if (accPct.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("percentual fisico acumulado excede 100% (" + accPct + ")");
        BigDecimal accAmt = accumulatedAmount(agreement.id()).add(m.amount());
        if (accAmt.compareTo(agreement.totalValue()) > 0)
            throw new IllegalArgumentException("valor acumulado excede o valor total do instrumento (" + accAmt + ")");
        BigDecimal plannedPct = schedule.plannedPercentThrough(m.periodEnd());
        if (accPct.compareTo(plannedPct) > 0)
            throw new IllegalArgumentException("medicao excede o cronograma: fisico acumulado " + accPct
                    + "% > previsto acumulado " + plannedPct + "% ate " + m.periodEnd());
        BigDecimal plannedAmt = schedule.plannedAmountThrough(m.periodEnd());
        if (accAmt.compareTo(plannedAmt) > 0)
            throw new IllegalArgumentException("medicao excede o cronograma financeiro: acumulado " + accAmt
                    + " > previsto acumulado " + plannedAmt + " ate " + m.periodEnd());
        existing.add(m);
        existing.sort(Comparator.comparingInt(Measurement::sequence));
        return m;
    }

    public BigDecimal accumulatedPercent(UUID agreementId) {
        return measurements.getOrDefault(agreementId, List.of()).stream()
                .map(Measurement::physicalPercentPeriod).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal accumulatedAmount(UUID agreementId) {
        return measurements.getOrDefault(agreementId, List.of()).stream()
                .map(Measurement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** C3: confronto previsto x executado ate a data. */
    public record ProgressView(BigDecimal plannedPercent, BigDecimal executedPercent,
                               BigDecimal plannedAmount, BigDecimal executedAmount) {}

    public ProgressView progressThrough(UUID tenantId, UUID agreementId, java.time.LocalDate date) {
        FundingAgreement agreement = requireAgreement(tenantId, agreementId);
        Schedule schedule = schedules.get(agreement.id());
        if (schedule == null) throw new IllegalStateException("sem cronograma definido");
        BigDecimal execPct = measurements.get(agreement.id()).stream()
                .filter(m -> !m.periodEnd().isAfter(date))
                .map(Measurement::physicalPercentPeriod).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal execAmt = measurements.get(agreement.id()).stream()
                .filter(m -> !m.periodEnd().isAfter(date))
                .map(Measurement::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ProgressView(schedule.plannedPercentThrough(date), execPct,
                schedule.plannedAmountThrough(date), execAmt);
    }

    public FundingAgreement requireAgreement(UUID tenantId, UUID agreementId) {
        FundingAgreement a = agreements.get(agreementId);
        if (a == null) throw new IllegalArgumentException("instrumento inexistente: " + agreementId);
        if (!a.tenantId().equals(tenantId))
            throw new IllegalArgumentException("instrumento de outro tenant");
        return a;
    }

    public List<Measurement> measurementsOf(UUID tenantId, UUID agreementId) {
        requireAgreement(tenantId, agreementId);
        return List.copyOf(measurements.get(agreementId));
    }

    public Schedule scheduleOf(UUID tenantId, UUID agreementId) {
        requireAgreement(tenantId, agreementId);
        return schedules.get(agreementId);
    }
}
