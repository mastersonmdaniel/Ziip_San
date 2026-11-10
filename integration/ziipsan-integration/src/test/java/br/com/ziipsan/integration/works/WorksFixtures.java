package br.com.ziipsan.integration.works;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fixtures da ED-102: GIS falso pela porta real, instrumento e cronograma validos. */
final class WorksFixtures {
    static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-4222-8222-222222222222");

    static GisProjectPort gis() {
        return new GisProjectPort() {
            public Optional<ProjectView> findProject(UUID tenantId, String projectRef) {
                if (TENANT.equals(tenantId) && "GIS-PRJ-001".equals(projectRef))
                    return Optional.of(new ProjectView(tenantId, projectRef, "Projeto ficticio de teste"));
                return Optional.empty();
            }
            public Optional<PackageView> findPackage(UUID tenantId, String projectRef, String packageRef) {
                if (TENANT.equals(tenantId) && "GIS-PRJ-001".equals(projectRef) && "GIS-PCT-001".equals(packageRef))
                    return Optional.of(new PackageView(tenantId, projectRef, packageRef, "Pacote ficticio de teste"));
                return Optional.empty();
            }
        };
    }

    static FundingAgreement agreement(FundingAgreement.Kind kind) {
        return new FundingAgreement(TENANT, UUID.randomUUID(), kind,
                "REF-FICTICIA-001", "Concedente ficticio de teste",
                new BigDecimal("1000000.00"), new BigDecimal("100000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                FundingAgreement.Status.ACTIVE,
                Set.of(new FundingAgreement.GisLink("GIS-PRJ-001", "GIS-PCT-001")));
    }

    /** Cronograma: 2 etapas de 50% / 500 mil, jan-jun e jul-dez de 2026. */
    static Schedule schedule(UUID agreementId) {
        return new Schedule(TENANT, agreementId, java.util.List.of(
                new Schedule.Stage("ET-1", "Etapa ficticia 1", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30), new BigDecimal("50"), new BigDecimal("500000.00")),
                new Schedule.Stage("ET-2", "Etapa ficticia 2", LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31), new BigDecimal("50"), new BigDecimal("500000.00"))));
    }

    static WorksAgreementService serviceWith(FundingAgreement a) {
        var s = new WorksAgreementService(gis());
        s.register(a);
        s.defineSchedule(schedule(a.id()));
        return s;
    }

    static Measurement measurement(UUID agreementId, int seq, LocalDate start, LocalDate end,
                                   String pct, String amount) {
        return new Measurement(TENANT, agreementId, seq, start, end,
                new BigDecimal(pct), new BigDecimal(amount), Measurement.Status.APPROVED);
    }

    /** Mapeamento declarado completo (generico, NAO oficial) para os testes. */
    static Map<String, String> fullMapping(String prefix) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String f : TransferegovLayoutMapping.CANONICAL_FIELDS) m.put(f, prefix + f);
        return m;
    }
}
