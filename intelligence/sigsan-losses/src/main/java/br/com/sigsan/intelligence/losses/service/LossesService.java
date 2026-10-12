package br.com.sigsan.intelligence.losses.service;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.intelligence.losses.calc.PressureStatistics;
import br.com.sigsan.intelligence.losses.calc.WaterBalanceCalculator;
import br.com.sigsan.intelligence.losses.domain.*;
import br.com.sigsan.intelligence.losses.repository.DmaDistrictRepository;
import br.com.sigsan.intelligence.losses.repository.LeakDetectionRepository;
import br.com.sigsan.intelligence.losses.repository.PressureAnalysisRepository;
import br.com.sigsan.intelligence.losses.repository.WaterBalanceRepository;
import br.com.sigsan.intelligence.quality.domain.QualityParameter;
import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;
import br.com.sigsan.intelligence.quality.scada.ScadaDataPoint;
import br.com.sigsan.core.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LossesService {

    private final WaterBalanceRepository waterBalanceRepository;
    private final DmaDistrictRepository dmaRepository;
    private final LeakDetectionRepository leakRepository;
    private final PressureAnalysisRepository pressureRepository;

    // ========================================================================
    // WATER BALANCE CALCULATION (IWA/AWWA Methodology)
    // ========================================================================

    @Transactional
    public WaterBalance calculateWaterBalance(CalculateWaterBalanceRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        OffsetDateTime periodStart = request.periodStart();
        OffsetDateTime periodEnd = request.periodEnd();

        // Get DMA if specified
        DmaDistrict dma = null;
        if (request.dmaId() != null) {
            dma = dmaRepository.findByIdAndTenantId(request.dmaId(), TenantContextHolder.getTenantId())
                    .orElseThrow(() -> new IllegalArgumentException("DMA not found"));
        }

        // Entradas do balanco. ENQUANTO as entradas forem placeholders, o resultado carrega essa
        // marca (dataQualityGrade e properties) e nao pode ser aprovado.
        WaterBalanceInputs inputs = loadInputs(dma, periodStart, periodEnd);

        WaterBalanceCalculator.Result calc = WaterBalanceCalculator.calculate(
                new WaterBalanceCalculator.Input(
                        inputs.systemInputVolume(), inputs.billedAuthorized(), inputs.unbilledAuthorized(),
                        inputs.unauthorizedConsumption(), inputs.meteringInaccuracies(),
                        periodDays(periodStart, periodEnd), networkOf(dma)));

        if (request.approve() && inputs.placeholder()) {
            throw new IllegalStateException("Balanco hidrico nao pode ser aprovado: as entradas "
                    + "(volume de entrada, consumo faturado, perdas aparentes) ainda sao valores fixos de "
                    + "desenvolvimento, nao dados medidos.");
        }
        if (request.approve() && !calc.consistent()) {
            throw new IllegalStateException("Balanco hidrico nao pode ser aprovado: as medicoes nao "
                    + "fecham o balanco. " + String.join(" ", calc.warnings()));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        if (request.properties() != null) {
            props.putAll(request.properties());
        }
        props.put("warnings", calc.warnings());
        props.put("inputsArePlaceholders", inputs.placeholder());

        // Create water balance record
        WaterBalance wb = new WaterBalance();
        wb.setTenantId(TenantContextHolder.getTenantId());
        wb.setPeriodStart(periodStart);
        wb.setPeriodEnd(periodEnd);
        wb.setFrequency(request.frequency());
        wb.setDmaDistrict(dma);
        wb.setSystemInputVolume(inputs.systemInputVolume());
        wb.setAuthorizedConsumption(calc.authorizedConsumption());
        wb.setBilledAuthorized(inputs.billedAuthorized());
        wb.setUnbilledAuthorized(inputs.unbilledAuthorized());
        wb.setApparentLosses(calc.apparentLosses());
        wb.setUnauthorizedConsumption(inputs.unauthorizedConsumption());
        wb.setMeteringInaccuracies(inputs.meteringInaccuracies());
        wb.setRealLosses(calc.realLosses());
        wb.setNonRevenueWater(calc.nonRevenueWater());
        wb.setNrwPercentage(calc.nonRevenueWaterPercent());
        wb.setInfrastructureLeakageIndex(calc.infrastructureLeakageIndex());
        // UARL anual em m3: litros/dia x 365 / 1000. A coluna chama "annual"; antes recebia m3/dia.
        wb.setUnavoidableAnnualRealLosses(calc.uarlLitersPerDay() == null ? null
                : calc.uarlLitersPerDay().multiply(BigDecimal.valueOf(365))
                        .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
        wb.setDataQualityGrade(inputs.placeholder() ? "PLACEHOLDER_INPUTS" : null);
        wb.setStatus(request.approve() ? "APPROVED" : "DRAFT");
        wb.setCalculatedAt(OffsetDateTime.now());
        // Ainda nao ha usuario corrente no contexto. Gravar o id do tenant aqui atribuiria o
        // calculo a uma entidade que nao e usuario; null e a resposta honesta.
        wb.setCalculatedBy(null);
        wb.setProperties(toJson(props));

        WaterBalance saved = waterBalanceRepository.save(wb);

        return saved;
    }

    /**
     * Entradas do balanco hidrico. {@code placeholder = true} indica valores fixos de
     * desenvolvimento, nao dados medidos.
     */
    private record WaterBalanceInputs(
            BigDecimal systemInputVolume, BigDecimal billedAuthorized, BigDecimal unbilledAuthorized,
            BigDecimal unauthorizedConsumption, BigDecimal meteringInaccuracies, boolean placeholder) {
    }

    /**
     * PLACEHOLDER. Devolve constantes ate existir telemetria (volume de entrada), faturamento
     * (consumo faturado) e estimativas de perdas aparentes. Quando a fonte real existir, esta e a
     * unica funcao a trocar, retornando {@code placeholder = false}.
     */
    private WaterBalanceInputs loadInputs(DmaDistrict dma, OffsetDateTime start, OffsetDateTime end) {
        BigDecimal siv = BigDecimal.valueOf(10000);        // Placeholder: macromedicao na entrada do DMC
        BigDecimal billed = BigDecimal.valueOf(7000);      // Placeholder: consumo faturado do modulo comercial
        BigDecimal unbilled = BigDecimal.valueOf(200);     // Placeholder: combate a incendio, lavagem, etc.
        BigDecimal unauthorized = BigDecimal.valueOf(100); // Placeholder: ligacoes clandestinas, fraude
        BigDecimal metering = billed.multiply(BigDecimal.valueOf(0.02)); // Placeholder: 2% do faturado
        return new WaterBalanceInputs(siv, billed, unbilled, unauthorized, metering, true);
    }

    /** Dados de rede do DMC para o UARL. Campo ausente permanece nulo: o calculador nao inventa. */
    private WaterBalanceCalculator.Network networkOf(DmaDistrict dma) {
        if (dma == null) {
            return WaterBalanceCalculator.Network.unknown();
        }
        // O DmaDistrict ainda nao guarda a extensao de ramal privado (Lp): fica nulo e o
        // calculador avisa que assumiu 0 km.
        return new WaterBalanceCalculator.Network(
                dma.getPipeLengthKm(), dma.getConnectionsCount(), null, dma.getAvgPressureM());
    }

    /** Duracao do periodo em dias, com fracao. */
    private static BigDecimal periodDays(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Periodo invalido: o fim deve ser posterior ao inicio");
        }
        return BigDecimal.valueOf(java.time.Duration.between(start, end).getSeconds())
                .divide(BigDecimal.valueOf(86400), 10, RoundingMode.HALF_UP);
    }

    // ========================================================================
    // LEAK DETECTION
    // ========================================================================

    @Transactional
    public LeakDetection createLeakDetection(CreateLeakRequest request) {
        DmaDistrict dma = null;
        if (request.dmaId() != null) {
            dma = dmaRepository.findByIdAndTenantId(request.dmaId(), TenantContextHolder.getTenantId())
                    .orElseThrow(() -> new IllegalArgumentException("DMA not found"));
        }

        LeakDetection leak = new LeakDetection();
        leak.setTenantId(TenantContextHolder.getTenantId());
        leak.setDetectionCode(generateDetectionCode());
        leak.setLocation(request.location());
        leak.setAddress(request.address() != null ? toJson(request.address()) : "{}");
        leak.setDmaDistrict(dma);
        leak.setDetectionMethod(request.detectionMethod());
        leak.setConfidenceScore(request.confidenceScore());
        leak.setLeakType(request.leakType());
        leak.setEstimatedFlowLps(request.estimatedFlowLps());
        leak.setEstimatedDailyLossM3(request.estimatedDailyLossM3());
        leak.setPipeMaterial(request.pipeMaterial());
        leak.setPipeDiameterMm(request.pipeDiameterMm());
        leak.setPipeAgeYears(request.pipeAgeYears());
        leak.setDepthM(request.depthM());
        leak.setStatus(LeakDetection.LeakStatus.DETECTED);
        leak.setPriority(calculatePriority(request));
        leak.setDetectedAt(OffsetDateTime.now());
        // Sem usuario corrente no contexto ainda: gravar o id do tenant atribuiria a deteccao a uma
        // entidade que nao e usuario.
        leak.setDetectedBy(null);
        leak.setPhotos(request.photos() != null ? toJson(request.photos()) : "[]");
        leak.setNotes(request.notes());
        leak.setProperties(request.properties() != null ? toJson(request.properties()) : "{}");

        LeakDetection saved = leakRepository.save(leak);

        // Trigger alerts if high priority
        if ("HIGH".equals(leak.getPriority()) || "CRITICAL".equals(leak.getPriority())) {
            notifyHighPriorityLeak(saved);
        }

        return saved;
    }

    @Transactional
    public LeakDetection confirmLeak(UUID leakId, ConfirmLeakRequest request) {
        LeakDetection leak = leakRepository.findByIdAndTenantId(leakId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Leak not found"));

        leak.setStatus(LeakDetection.LeakStatus.CONFIRMED);
        leak.setConfirmedAt(OffsetDateTime.now());
        leak.setConfirmedBy(request.confirmedBy());
        leak.setPriority(request.priority());
        leak.setNotes(request.notes());

        LeakDetection saved = leakRepository.save(leak);
        return saved;
    }

    @Transactional
    public LeakDetection repairLeak(UUID leakId, RepairLeakRequest request) {
        LeakDetection leak = leakRepository.findByIdAndTenantId(leakId, TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Leak not found"));

        leak.setStatus(LeakDetection.LeakStatus.REPAIRED);
        leak.setRepairedAt(request.repairedAt() != null ? request.repairedAt() : OffsetDateTime.now());
        leak.setRepairedBy(request.repairedBy());
        leak.setRepairCost(request.repairCost());
        leak.setRepairMethod(request.repairMethod());
        leak.setRepairDurationHours(request.repairDurationHours());
        leak.setWaterSavedM3Day(request.waterSavedM3Day());
        leak.setPhotos(request.photos() != null ? toJson(request.photos()) : "[]");
        leak.setNotes(request.notes());

        LeakDetection saved = leakRepository.save(leak);

        // Update water balance estimates
        updateWaterBalanceAfterRepair(saved);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<LeakDetection> getLeaksByDma(UUID dmaId, LeakDetection.LeakStatus status) {
        if (status != null) {
            return leakRepository.findByDmaDistrictIdAndStatus(dmaId, TenantContextHolder.getTenantId(), status);
        }
        return leakRepository.findByDmaDistrictIdAndTenantId(dmaId, TenantContextHolder.getTenantId());
    }

    @Transactional(readOnly = true)
    public List<LeakDetection> getLeaksByPriority(String priority) {
        return leakRepository.findByTenantIdAndPriorityOrderByDetectedAtDesc(
                TenantContextHolder.getTenantId(), priority);
    }

    // ========================================================================
    // PRESSURE ANALYSIS
    // ========================================================================

    @Transactional
    public PressureAnalysis analyzePressure(PressureAnalysisRequest request) {
        DmaDistrict dma = dmaRepository.findByIdAndTenantId(request.dmaId(), TenantContextHolder.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("DMA not found"));

        // Query SCADA pressure data for period
        List<ScadaDataPoint> pressureData = getPressureData(dma, request.periodStart(), request.periodEnd());

        if (pressureData.isEmpty()) {
            throw new IllegalStateException("No pressure data available for period");
        }

        // Calculate statistics (calculador puro, com teste; ver PressureStatistics)
        PressureStatistics stats = PressureStatistics.of(
                pressureData.stream().map(ScadaDataPoint::getValue).toList());
        BigDecimal avg = stats.mean();
        BigDecimal min = stats.min();
        BigDecimal max = stats.max();
        BigDecimal variance = stats.variance();

        // Minimum Night Flow Analysis
        BigDecimal minNightFlow = calculateMinimumNightFlow(pressureData);

        // Detect anomalies
        Boolean isAnomaly = detectPressureAnomaly(dma, avg, min, max, variance);

        PressureAnalysis analysis = new PressureAnalysis();
        analysis.setTenantId(TenantContextHolder.getTenantId());
        analysis.setDmaDistrict(dma);
        analysis.setTimestamp(OffsetDateTime.now());
        analysis.setAvgPressureM(avg);
        analysis.setMinPressureM(min);
        analysis.setMaxPressureM(max);
        analysis.setPressureVariance(variance);
        analysis.setPressureStdDev(stats.standardDeviation());
        analysis.setMinimumNightFlowLps(minNightFlow);
        analysis.setBackgroundLeakageLps(estimateBackgroundLeakage(minNightFlow));
        analysis.setIsAnomaly(isAnomaly);
        analysis.setAnomalyScore(calculateAnomalyScore(avg, min, max, variance));
        analysis.setAnomalyType(isAnomaly ? "PRESSURE_ANOMALY" : "NORMAL");
        analysis.setProperties(toJson(Map.of("sample_count", stats.count())));

        PressureAnalysis saved = pressureRepository.save(analysis);

        if (saved.getIsAnomaly()) {
            notifyPressureAnomaly(saved);
        }

        return saved;
    }

    private String generateDetectionCode() {
        String date = OffsetDateTime.now().toLocalDate().toString().replace("-", "");
        long count = leakRepository.countByTenantId(TenantContextHolder.getTenantId()) + 1;
        return String.format("LEAK-%s-%05d", date, count);
    }

    private String calculatePriority(CreateLeakRequest request) {
        if (request.estimatedFlowLps() != null && request.estimatedFlowLps().compareTo(BigDecimal.valueOf(5)) > 0) {
            return "CRITICAL";
        }
        if (request.confidenceScore() != null && request.confidenceScore().compareTo(BigDecimal.valueOf(0.9)) > 0) {
            return "HIGH";
        }
        if (request.estimatedDailyLossM3() != null && request.estimatedDailyLossM3().compareTo(BigDecimal.valueOf(50)) > 0) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private void notifyHighPriorityLeak(LeakDetection leak) {
        log.warn("HIGH PRIORITY LEAK: {} Flow: {} L/s", leak.getDetectionCode(), leak.getEstimatedFlowLps());
    }

    private void updateWaterBalanceAfterRepair(LeakDetection leak) {
        // Recalculate water balance for affected DMA
    }

    private void notifyPressureAnomaly(PressureAnalysis analysis) {
        log.warn("PRESSURE ANOMALY in DMA {}: Score {}", analysis.getDmaDistrict().getCode(), analysis.getAnomalyScore());
    }

    private List<ScadaDataPoint> getPressureData(DmaDistrict dma, OffsetDateTime start, OffsetDateTime end) {
        // Query from SCADA integration
        return Collections.emptyList();
    }

    private BigDecimal calculateMinimumNightFlow(List<ScadaDataPoint> data) {
        // Filter for night hours (00:00-05:00)
        // NOT IMPLEMENTED: devolve null (nao calculado). Devolver zero gravaria vazao noturna minima
        // zero, que e um resultado e nao a ausencia dele.
        return null;
    }

    private BigDecimal estimateBackgroundLeakage(BigDecimal minNightFlow) {
        // Background leakage = MNF - legitimate night consumption
        // Legitimate night consumption typically 1-2 L/s per 1000 connections
        // NOT IMPLEMENTED: sem vazao noturna minima nao ha vazamento de fundo a estimar.
        return null;
    }

    private Boolean detectPressureAnomaly(DmaDistrict dma, BigDecimal avg, BigDecimal min, BigDecimal max, BigDecimal variance) {
        if (min != null && dma.getMinPressureM() != null && min.compareTo(dma.getMinPressureM()) < 0) {
            return true;
        }
        if (dma.getAvgPressureM() != null && avg.subtract(dma.getAvgPressureM()).abs().compareTo(BigDecimal.valueOf(5)) > 0) {
            return true;
        }
        return false;
    }

    private BigDecimal calculateAnomalyScore(BigDecimal avg, BigDecimal min, BigDecimal max, BigDecimal variance) {
        // NOT IMPLEMENTED: devolve null (nao calculado). Um escore 0 diria "normal" sem ter avaliado.
        return null;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    // Auditoria: nao ha chamada de aplicacao. As tabelas de tenant tem trigger de auditoria
    // (register_tenant_table, V1_2) que grava em public.audit_log com o tenant e o usuario do
    // contexto. Uma segunda trilha aqui seria duplicada, e a anterior era um metodo vazio.

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Request DTOs
    public record CalculateWaterBalanceRequest(
            UUID dmaId, OffsetDateTime periodStart, OffsetDateTime periodEnd,
            String frequency, boolean approve, Map<String, Object> properties
    ) {}

    public record CreateLeakRequest(
            UUID dmaId, org.locationtech.jts.geom.Point location, Map<String, Object> address,
            LeakDetection.DetectionMethod detectionMethod, BigDecimal confidenceScore,
            LeakDetection.LeakType leakType, BigDecimal estimatedFlowLps,
            BigDecimal estimatedDailyLossM3, String pipeMaterial, Integer pipeDiameterMm,
            Integer pipeAgeYears, BigDecimal depthM, List<String> photos, String notes,
            Map<String, Object> properties
    ) {}

    public record ConfirmLeakRequest(
            UUID confirmedBy, String priority, String notes
    ) {}

    public record RepairLeakRequest(
            OffsetDateTime repairedAt, UUID repairedBy, BigDecimal repairCost,
            String repairMethod, BigDecimal repairDurationHours,
            BigDecimal waterSavedM3Day, List<String> photos, String notes
    ) {}

    public record PressureAnalysisRequest(
            UUID dmaId, OffsetDateTime periodStart, OffsetDateTime periodEnd
    ) {}
}