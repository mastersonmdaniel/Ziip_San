package br.com.sigsan.compliance.lgpd.service;

import br.com.sigsan.compliance.lgpd.domain.ConsentRecord;
import br.com.sigsan.compliance.lgpd.domain.DataSubjectRequest;
import br.com.sigsan.compliance.lgpd.domain.DpiaRecord;
import br.com.sigsan.compliance.lgpd.engine.AnonymizationEngine;
import br.com.sigsan.compliance.lgpd.engine.DataSubjectRightsEngine;
import br.com.sigsan.compliance.lgpd.engine.DpiaEngine;
import br.com.sigsan.compliance.lgpd.engine.RetentionEngine;
import br.com.sigsan.compliance.lgpd.repository.ConsentRecordRepository;
import br.com.sigsan.compliance.lgpd.repository.DataSubjectRequestRepository;
import br.com.sigsan.compliance.lgpd.repository.DpiaRecordRepository;
import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.domain.BaseEntity;
import br.com.sigsan.core.json.Json;
import br.com.sigsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que o serviço grava em {@code audit_log}: JSON válido, sem dado pessoal do titular.
 */
class LgpdEngineServiceTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String IP = "203.0.113.45";
    private static final String USER_AGENT = "Mozilla/5.0 (navegador-do-titular)";
    private static final String CONSENT_TEXT = "Autorizo o uso do meu \"telefone\" para avisos de falta d'agua";

    private ConsentRecordRepository consentRepo;
    private DpiaRecordRepository dpiaRepo;
    private DataSubjectRequestRepository dsrRepo;
    private AuditLogRepository auditRepo;
    private LgpdEngineService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_alpha");
        TenantContextHolder.setTenantId(TENANT);

        consentRepo = mock(ConsentRecordRepository.class);
        dpiaRepo = mock(DpiaRecordRepository.class);
        dsrRepo = mock(DataSubjectRequestRepository.class);
        auditRepo = mock(AuditLogRepository.class);

        when(consentRepo.findByTenantIdAndSubjectIdAndPurposeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(consentRepo.save(any())).thenAnswer(LgpdEngineServiceTest::withId);
        when(dpiaRepo.save(any())).thenAnswer(LgpdEngineServiceTest::withId);
        when(dsrRepo.save(any())).thenAnswer(LgpdEngineServiceTest::withId);

        service = new LgpdEngineService(consentRepo, dpiaRepo, dsrRepo, mock(AnonymizationEngine.class),
                new DpiaEngine(), new DataSubjectRightsEngine(), mock(RetentionEngine.class), auditRepo);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** O repositório real atribui o id no persist; o mock faz o mesmo. */
    private static Object withId(InvocationOnMock inv) {
        BaseEntity e = inv.getArgument(0);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        return e;
    }

    private List<AuditLog> audits() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepo, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private ConsentRecord grant() {
        return service.grantConsent(new LgpdEngineService.GrantConsentRequest(
                UUID.randomUUID(), "CUSTOMER", "avisos operacionais", "OPERATIONAL",
                ConsentRecord.LegalBasis.CONSENT, CONSENT_TEXT, OffsetDateTime.now().plusYears(1),
                IP, USER_AGENT, Map.of("lat", -23.55, "lon", -46.63), Map.of("canal", "portal")));
    }

    @Test
    @DisplayName("consentimento: snapshot e metadado são JSON válido, sem IP, user agent, texto nem geolocalização")
    void consentAuditHasNoPersonalData() {
        ConsentRecord saved = grant();

        AuditLog audit = audits().get(0);
        assertThat(audit.getEntityId()).isEqualTo(saved.getId());
        assertThat(Json.readMap(audit.getMetadata())).isEqualTo(Map.of("action", "GRANT"));

        String json = audit.getNewValues();
        JsonNode node = Json.readTree(json);
        assertThat(node.get("id").asString()).isEqualTo(saved.getId().toString());
        assertThat(node.get("legalBasis").asString()).isEqualTo("CONSENT");
        assertThat(node.get("purpose").asString()).isEqualTo("avisos operacionais");
        assertThat(node.get("consentHash").asString()).matches("[0-9a-f]{64}");

        assertThat(json).doesNotContain(IP).doesNotContain("navegador-do-titular")
                .doesNotContain("telefone").doesNotContain("-23.55").doesNotContain(saved.getSubjectId().toString());
        assertThat(node.has("ipAddress")).isFalse();
        assertThat(node.has("userAgent")).isFalse();
        assertThat(node.has("consentText")).isFalse();
        assertThat(node.has("geolocation")).isFalse();
        assertThat(node.has("subjectId")).isFalse();
    }

    @Test
    @DisplayName("revogação: valores antigo e novo diferem no status e continuam sem dado pessoal")
    void revokeAudit() {
        ConsentRecord saved = grant();
        when(consentRepo.findByIdAndTenantId(saved.getId(), TENANT)).thenReturn(Optional.of(saved));

        service.revokeConsent(saved.getId(), new LgpdEngineService.RevokeConsentRequest("WEB", "nao quero mais, sou o Joao"));

        AuditLog revoke = audits().get(1);
        assertThat(Json.readTree(revoke.getOldValues()).get("status").asString()).isEqualTo("GRANTED");
        assertThat(Json.readTree(revoke.getNewValues()).get("status").asString()).isEqualTo("REVOKED");
        assertThat(revoke.getNewValues()).doesNotContain("Joao").doesNotContain(IP);
    }

    @Test
    @DisplayName("DPIA: snapshot sem a descrição livre do tratamento")
    void dpiaAudit() {
        service.createDpia(new LgpdEngineService.CreateDpiaRequest("Leitura de hidrometro",
                "Leiturista Maria fotografa o medidor", List.of("consumo"), List.of(), List.of("clientes"),
                List.of(), List.of(), "5 anos", Map.of("cifra", "AES")));

        String json = audits().get(0).getNewValues();
        JsonNode node = Json.readTree(json);
        assertThat(node.get("processingActivity").asString()).isEqualTo("Leitura de hidrometro");
        assertThat(node.get("riskLevel").asString()).isEqualTo("NEGLIGIBLE");
        assertThat(json).doesNotContain("Maria");
    }

    @Test
    @DisplayName("solicitação do titular: snapshot sem descrição nem dados da resposta")
    void dsrAudit() {
        DataSubjectRequest dsr = service.createDataSubjectRequest(new LgpdEngineService.CreateDsrRequest(
                UUID.randomUUID(), "CUSTOMER", DataSubjectRequest.RequestType.ACCESS,
                "Quero meus dados, CPF 529.982.247-25", List.of("cadastro"), null, Map.of("canal", "portal")));
        when(dsrRepo.findByIdAndTenantId(dsr.getId(), TENANT)).thenReturn(Optional.of(dsr));

        service.completeDataSubjectRequest(dsr.getId(), new LgpdEngineService.CompleteDsrRequest(
                UUID.randomUUID(), "Enviado por e-mail", Map.of("endereco", "Rua das Aguas, 100")));

        for (AuditLog audit : audits()) {
            for (String values : new String[]{audit.getOldValues(), audit.getNewValues()}) {
                if (values == null) continue;
                JsonNode node = Json.readTree(values); // JSON válido
                assertThat(node.has("description")).isFalse();
                assertThat(node.has("responseData")).isFalse();
                assertThat(values).doesNotContain("529.982.247-25").doesNotContain("Rua das Aguas")
                        .doesNotContain(dsr.getSubjectId().toString());
            }
            assertThat(Json.readMap(audit.getMetadata())).containsKey("action");
        }
        assertThat(Json.readTree(audits().get(1).getNewValues()).get("status").asString()).isEqualTo("COMPLETED");
    }
}
