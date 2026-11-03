package br.com.ziipsan.compliance.lgpd.service;

import br.com.ziipsan.compliance.lgpd.domain.ConsentRecord;
import br.com.ziipsan.compliance.lgpd.engine.AnonymizationEngine;
import br.com.ziipsan.compliance.lgpd.engine.DataSubjectRightsEngine;
import br.com.ziipsan.compliance.lgpd.engine.DpiaEngine;
import br.com.ziipsan.compliance.lgpd.engine.RetentionEngine;
import br.com.ziipsan.compliance.lgpd.repository.ConsentRecordRepository;
import br.com.ziipsan.compliance.lgpd.repository.DataSubjectRequestRepository;
import br.com.ziipsan.compliance.lgpd.repository.DpiaRecordRepository;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0.17: a chave do cache de consentimento é montada do TenantContextHolder, e o evict
 * usa a mesma chave da leitura (tenant + subject + purpose).
 *
 * <p>A chave antiga ({@code #tenantId}) não existia como parâmetro de grantConsent nem de
 * getActiveConsent, então o evict nunca limpava a entrada lida — e, sem tenant na chave,
 * dois tenants com mesmo subject e purpose colidiriam. O teste prova os dois lados com
 * cache real (proxy Spring + SimpleCacheManager) e repositório em memória por tenant:
 * cada tenant concede e lê o valor NOVO, sem enxergar o cache do outro.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LgpdEngineServiceCacheTest.Config.class)
class LgpdEngineServiceCacheTest {

    private static final UUID TENANT_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TENANT_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SUBJECT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String PURPOSE = "avisos operacionais";

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(new ConcurrentMapCache("consents")));
            return manager;
        }

        @Bean
        ConsentRecordRepository consentRecordRepository() {
            return mock(ConsentRecordRepository.class);
        }

        @Bean
        DpiaRecordRepository dpiaRecordRepository() {
            return mock(DpiaRecordRepository.class);
        }

        @Bean
        DataSubjectRequestRepository dataSubjectRequestRepository() {
            return mock(DataSubjectRequestRepository.class);
        }

        @Bean
        AuditLogRepository auditLogRepository() {
            return mock(AuditLogRepository.class);
        }

        @Bean
        LgpdEngineService lgpdEngineService(ConsentRecordRepository consents,
                                            DpiaRecordRepository dpias,
                                            DataSubjectRequestRepository dsrs,
                                            AuditLogRepository audit) {
            return new LgpdEngineService(consents, dpias, dsrs, mock(AnonymizationEngine.class),
                    new DpiaEngine(), mock(DataSubjectRightsEngine.class), mock(RetentionEngine.class), audit);
        }
    }

    @Autowired
    private LgpdEngineService service;

    @Autowired
    private ConsentRecordRepository consentRepo;

    /** "Banco" em memória, particionado por tenant — o mock lê e grava aqui. */
    private final Map<String, ConsentRecord> store = new ConcurrentHashMap<>();

    private static String key(UUID tenantId, UUID subjectId, String purpose) {
        return tenantId + "|" + subjectId + "|" + purpose;
    }

    @BeforeEach
    void setUp() {
        store.clear();
        when(consentRepo.findByTenantIdAndSubjectIdAndPurposeAndStatusAndDeletedAtIsNull(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    ConsentRecord found = store.get(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
                    return found != null && found.getStatus() == ConsentRecord.ConsentStatus.GRANTED
                            ? Optional.of(found) : Optional.empty();
                });
        when(consentRepo.save(any())).thenAnswer(inv -> {
            ConsentRecord consent = inv.getArgument(0);
            if (consent.getId() == null) {
                consent.setId(UUID.randomUUID());
            }
            store.put(key(consent.getTenantId(), consent.getSubjectId(), consent.getPurpose()), consent);
            return consent;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void asTenant(UUID tenantId, String schema) {
        TenantContextHolder.setTenant(schema);
        TenantContextHolder.setTenantId(tenantId);
    }

    private LgpdEngineService.GrantConsentRequest grantRequest(String consentText) {
        return new LgpdEngineService.GrantConsentRequest(
                SUBJECT, "CUSTOMER", PURPOSE, "OPERATIONAL",
                ConsentRecord.LegalBasis.CONSENT, consentText, OffsetDateTime.now().plusYears(1),
                "203.0.113.45", "teste", Map.of(), Map.of());
    }

    @Test
    @DisplayName("P0.17: dois tenants concedem e leem o valor novo, sem vazar cache entre tenants")
    void twoTenantsGrantAndReadFreshValue() {
        // Tenant A lê vazio (o vazio fica cacheado), concede e lê o valor NOVO: o evict
        // da concessão limpou exatamente a chave da leitura. Sem o evict pela mesma chave,
        // a segunda leitura devolveria o vazio cacheado.
        asTenant(TENANT_A, "tenant_a");
        assertThat(service.getActiveConsent(SUBJECT, PURPOSE)).isEmpty();
        service.grantConsent(grantRequest("texto A"));
        assertThat(service.getActiveConsent(SUBJECT, PURPOSE)).isPresent()
                .get().satisfies(consent -> {
                    assertThat(consent.getTenantId()).isEqualTo(TENANT_A);
                    assertThat(consent.getConsentText()).isEqualTo("texto A");
                });

        // Tenant B, mesmo subject e purpose: não enxerga o cache de A (chave com tenant).
        asTenant(TENANT_B, "tenant_b");
        assertThat(service.getActiveConsent(SUBJECT, PURPOSE)).isEmpty();
        service.grantConsent(grantRequest("texto B"));
        assertThat(service.getActiveConsent(SUBJECT, PURPOSE)).isPresent()
                .get().satisfies(consent -> {
                    assertThat(consent.getTenantId()).isEqualTo(TENANT_B);
                    assertThat(consent.getConsentText()).isEqualTo("texto B");
                });

        // De volta a A: a concessão de B não evictou nem sobrescreveu a entrada de A.
        asTenant(TENANT_A, "tenant_a");
        assertThat(service.getActiveConsent(SUBJECT, PURPOSE)).isPresent()
                .get().satisfies(consent -> {
                    assertThat(consent.getTenantId()).isEqualTo(TENANT_A);
                    assertThat(consent.getConsentText()).isEqualTo("texto A");
                });
    }
}
