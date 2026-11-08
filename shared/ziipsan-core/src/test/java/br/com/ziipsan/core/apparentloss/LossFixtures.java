package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Massa SINTETICA declarada dos testes da ED-95 (nenhum dado real). */
final class LossFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID REVIEWER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 10, 0);

    private LossFixtures() {
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    static PropertyRegistryRecord property(UUID id, String area, PropertyRegistryRecord.PropertyUse use) {
        return new PropertyRegistryRecord(id, TENANT, "INSC-" + id.toString().substring(0, 8),
                bd(area), use, "HAB-1", "ALV-1",
                "base legal declarada pelo ente (massa sintetica de teste)",
                "finalidade declarada pelo ente (massa sintetica de teste)",
                PropertyRegistryRecord.RegistrySource.MUNICIPAL_CONNECTOR, "LOTE-TESTE-1", NOW);
    }

    static UnitFacts unit(UUID unitId, UUID propertyId, UnitFacts.ConnectionStatus status,
                          PropertyRegistryRecord.PropertyUse use, String area, int economies,
                          List<BigDecimal> consumptions, List<BigDecimal> readings) {
        return new UnitFacts(unitId, TENANT, propertyId, UUID.randomUUID(), status, use,
                area == null ? null : bd(area), economies, consumptions, readings,
                null, null, null, null, null, false, null);
    }

    static UnitFacts unitFull(UUID unitId, UUID propertyId,
                              List<BigDecimal> consumptions, List<BigDecimal> readings,
                              String nominal, String peak, Integer cyclesSinceReplacement,
                              String avgBefore, String avgAfter, boolean telemetry, String telemetryFlow) {
        return new UnitFacts(unitId, TENANT, propertyId, UUID.randomUUID(),
                UnitFacts.ConnectionStatus.ACTIVE, PropertyRegistryRecord.PropertyUse.RESIDENTIAL,
                bd("100"), 1, consumptions, readings,
                nominal == null ? null : bd(nominal), peak == null ? null : bd(peak),
                cyclesSinceReplacement,
                avgBefore == null ? null : bd(avgBefore), avgAfter == null ? null : bd(avgAfter),
                telemetry, telemetryFlow == null ? null : bd(telemetryFlow));
    }

    /** fraud.rules completo: as 8 regras habilitadas com limiares de teste. */
    static final String ALL_RULES_JSON = """
            {"zeroConsumption.enabled": true, "zeroConsumption.minConsecutiveZeroCycles": 3,
             "abruptDrop.enabled": true, "abruptDrop.minDropPercent": 50,
             "perEconomy.enabled": true, "perEconomy.maxM3PerEconomy": 25,
             "perArea.enabled": true, "perArea.maxM3PerSquareMeter": 0.5,
             "stalledMeter.enabled": true, "stalledMeter.minConsecutiveIdenticalReadings": 3,
             "undersizedMeter.enabled": true, "undersizedMeter.minPeakToNominalPercent": 90,
             "meterReplacement.enabled": true, "meterReplacement.maxCyclesAfterReplacement": 2,
             "meterReplacement.minDropPercentAfterReplacement": 40,
             "repeatedReading.enabled": true, "repeatedReading.minConsecutiveRepeatedReadings": 3}
            """;

    static LossParameters.FraudRuleThresholds allThresholds() {
        return LossParameters.FraudRuleThresholds.parse(ALL_RULES_JSON);
    }
}
