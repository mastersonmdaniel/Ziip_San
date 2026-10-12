package br.com.sigsan.intelligence.quality.lab;

import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.domain.BaseEntity;
import br.com.sigsan.core.json.Json;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;
import br.com.sigsan.intelligence.quality.domain.QualityParameter;
import br.com.sigsan.intelligence.quality.domain.QualitySample;
import br.com.sigsan.intelligence.quality.repository.LabAnalysisMethodRepository;
import br.com.sigsan.intelligence.quality.repository.LabInstrumentRepository;
import br.com.sigsan.intelligence.quality.repository.LabQcSampleRepository;
import br.com.sigsan.intelligence.quality.repository.QualityAnalysisResultRepository;
import br.com.sigsan.intelligence.quality.repository.QualityParameterRepository;
import br.com.sigsan.intelligence.quality.repository.QualitySampleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabServiceTest {

    private static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private LabInstrumentRepository instrumentRepo;
    private QualitySampleRepository sampleRepo;
    private QualityParameterRepository parameterRepo;
    private QualityAnalysisResultRepository resultRepo;
    private AuditLogRepository auditRepo;
    private LabService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_alpha");
        TenantContextHolder.setTenantId(TENANT);
        instrumentRepo = mock(LabInstrumentRepository.class);
        sampleRepo = mock(QualitySampleRepository.class);
        parameterRepo = mock(QualityParameterRepository.class);
        resultRepo = mock(QualityAnalysisResultRepository.class);
        auditRepo = mock(AuditLogRepository.class);
        service = new LabService(instrumentRepo, mock(LabAnalysisMethodRepository.class),
                mock(LabQcSampleRepository.class), parameterRepo, sampleRepo, resultRepo, auditRepo);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static <T extends BaseEntity> T withId(T e) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        return e;
    }

    private AuditLog lastAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("resultado de análise: data em ISO-8601, texto com aspas escapado, sem analista nem observação")
    void analysisResultAudit() {
        QualitySample sample = withId(new QualitySample());
        QualityParameter parameter = withId(new QualityParameter());
        when(sampleRepo.findByIdAndTenantId(sample.getId(), TENANT)).thenReturn(Optional.of(sample));
        when(sampleRepo.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(parameterRepo.findByIdAndTenantId(parameter.getId(), TENANT)).thenReturn(Optional.of(parameter));
        when(parameterRepo.findById(parameter.getId())).thenReturn(Optional.of(parameter));
        when(resultRepo.findBySampleIdAndParameterId(sample.getId(), parameter.getId())).thenReturn(Optional.empty());
        when(resultRepo.save(any())).thenAnswer(inv -> withId(inv.<QualityAnalysisResult>getArgument(0)));

        OffsetDateTime analyzedAt = OffsetDateTime.of(2026, 10, 12, 9, 30, 0, 0, ZoneOffset.ofHours(-3));
        String text = "presença de \"coliformes\" \\ reanalisar";
        UUID analyst = UUID.randomUUID();
        service.recordAnalysisResult(new LabService.RecordAnalysisRequest(sample.getId(), parameter.getId(),
                new BigDecimal("0.35"), text, "mg/L", "SM-4500", "Standard Methods", null, null, null,
                new BigDecimal("5.0"), QualityAnalysisResult.ConformityStatus.CONFORMING,
                QualityAnalysisResult.AnalysisStatus.COMPLETED, analyzedAt, analyst, null, null,
                Map.of(), "coletado pelo tecnico Jose", Map.of()));

        AuditLog audit = lastAudit();
        assertThat(audit.getOldValues()).as("resultado novo não tem valor antigo").isNull();
        assertThat(Json.readMap(audit.getMetadata())).isEqualTo(Map.of("action", "RECORD"));

        String json = audit.getNewValues();
        JsonNode node = Json.readTree(json);
        assertThat(node.get("analyzedAt").isString()).as("data como texto ISO, não número").isTrue();
        assertThat(OffsetDateTime.parse(node.get("analyzedAt").asString()).isEqual(analyzedAt)).isTrue();
        assertThat(node.get("resultText").asString()).isEqualTo(text);
        assertThat(node.get("sampleId").asString()).isEqualTo(sample.getId().toString());
        assertThat(node.get("resultValue").decimalValue()).isEqualByComparingTo("0.35");
        assertThat(node.has("analyzedBy")).isFalse();
        assertThat(json).doesNotContain(analyst.toString()).doesNotContain("Jose");
    }

    @Test
    @DisplayName("instrumento: configuração de conexão (credenciais) fica fora da auditoria")
    void instrumentAuditOmitsConnectionConfig() {
        when(instrumentRepo.save(any())).thenAnswer(inv -> withId(inv.<LabInstrument>getArgument(0)));
        LabInstrument instrument = new LabInstrument();
        instrument.setCode("ICP-01");
        instrument.setName("ICP-OES");
        instrument.setCalibrationDue(OffsetDateTime.of(2027, 1, 31, 0, 0, 0, 0, ZoneOffset.UTC));
        instrument.setConnectionConfig("{\"user\":\"lims\",\"password\":\"s3nh@\"}");

        service.registerInstrument(instrument);

        String json = lastAudit().getNewValues();
        assertThat(Json.readTree(json).get("code").asString()).isEqualTo("ICP-01");
        assertThat(Json.readTree(json).get("calibrationDue").asString()).isEqualTo("2027-01-31T00:00:00Z");
        assertThat(json).doesNotContain("s3nh@").doesNotContain("connectionConfig");
    }
}
