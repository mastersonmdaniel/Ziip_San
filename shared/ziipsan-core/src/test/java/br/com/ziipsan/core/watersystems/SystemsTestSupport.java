package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.certification.governance.RiskRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fixtures sinteticas da ED-79: nenhum valor regulatorio real — codigos e textos genericos de teste. */
final class SystemsTestSupport {
    private SystemsTestSupport() {}

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID TENANT_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID UNIT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    static WaterSystem supplySystem(String code) {
        return new WaterSystem(UUID.randomUUID(), TENANT, code, "Sistema " + code, WaterSystem.Kind.SUPPLY,
                12_000, LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, "Manancial Ficticio", null),
                new WaterSystem.TreatmentInfo(List.of("ETA-01"), List.of(), "tratamento declarado de teste"),
                true);
    }

    static WaterSystem sewageSystem(String code) {
        return new WaterSystem(UUID.randomUUID(), TENANT, code, "Sistema " + code, WaterSystem.Kind.SEWAGE,
                9_000, LocalDate.of(2026, 1, 1), null,
                new WaterSystem.TreatmentInfo(List.of("ETE-01"), List.of(), "tratamento declarado de teste"),
                true);
    }

    static RiskRecord risk(UUID id, UUID tenantId, RiskRecord.Category category, UUID occurrenceId) {
        boolean needsPlan = category == RiskRecord.Category.OPERATIONAL
                || category == RiskRecord.Category.CYBER
                || category == RiskRecord.Category.PROVIDER_CONTINUITY;
        return new RiskRecord(id, tenantId, "risco de teste " + category, RiskRecord.Kind.RISK, category,
                2, 3, needsPlan ? "plano de contingencia de teste" : null, null, occurrenceId,
                LocalDate.of(2027, 1, 1));
    }

    static SystemsPorts.RiskRegistryPort riskPort(Map<UUID, RiskRecord> byId) {
        return (tenantId, riskId) -> {
            RiskRecord r = byId.get(riskId);
            return r != null && r.tenantId().equals(tenantId) ? Optional.of(r) : Optional.empty();
        };
    }

    /** Porta que devolve risco de OUTRO tenant (fiacao errada) — para a guarda RISK_OTHER_TENANT. */
    static SystemsPorts.RiskRegistryPort foreignRiskPort(RiskRecord foreign) {
        return (tenantId, riskId) -> foreign.id().equals(riskId) ? Optional.of(foreign) : Optional.empty();
    }

    static SystemsPorts.FactoryCatalogPort catalogPort(Map<String, Boolean> activeByKey) {
        return new SystemsPorts.FactoryCatalogPort() {
            @Override public boolean typeExists(UUID tenantId, String typeKey) { return activeByKey.containsKey(typeKey); }
            @Override public boolean typeActive(UUID tenantId, String typeKey) { return Boolean.TRUE.equals(activeByKey.get(typeKey)); }
        };
    }

    static AnnualQualityReport.MonthlyQualitySummary summary(int month, String param,
                                                               int planned, int collected, int analyzed,
                                                               int conf, int nonConf, int indet) {
        return new AnnualQualityReport.MonthlyQualitySummary(
                java.time.YearMonth.of(2026, month), param, planned, collected, analyzed, conf, nonConf, indet);
    }
}
