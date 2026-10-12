package br.com.sigsan.intelligence.quality.scada;

import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.intelligence.quality.repository.QualityAnalysisResultRepository;
import br.com.sigsan.intelligence.quality.repository.QualityParameterRepository;
import br.com.sigsan.intelligence.quality.repository.QualitySampleRepository;
import br.com.sigsan.intelligence.quality.repository.SamplingPointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScadaIntegrationServiceTest {

    private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String DATA_TOPIC = "sigsan/" + TENANT + "/scada/data/ETA1.CLORO";

    private ScadaTagRepository tagRepo;
    private ScadaDataPointRepository dataRepo;
    private ScadaIntegrationService service;
    private ScadaTag tag;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
        tagRepo = mock(ScadaTagRepository.class);
        dataRepo = mock(ScadaDataPointRepository.class);
        // Construído direto, sem @PostConstruct: nenhum broker envolvido.
        service = new ScadaIntegrationService(tagRepo, dataRepo, mock(QualityParameterRepository.class),
                mock(QualitySampleRepository.class), mock(QualityAnalysisResultRepository.class),
                mock(SamplingPointRepository.class), mock(AuditLogRepository.class));

        tag = new ScadaTag();
        tag.setId(UUID.randomUUID());
        tag.setTenantId(TENANT);
        tag.setTagName("ETA1.CLORO");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void process(String topic, String payload) {
        service.processMqttMessage(topic, payload.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- liga/desliga

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withBean(ScadaTagRepository.class, () -> tagRepo)
                .withBean(ScadaDataPointRepository.class, () -> dataRepo)
                .withBean(QualityParameterRepository.class, () -> mock(QualityParameterRepository.class))
                .withBean(QualitySampleRepository.class, () -> mock(QualitySampleRepository.class))
                .withBean(QualityAnalysisResultRepository.class, () -> mock(QualityAnalysisResultRepository.class))
                .withBean(SamplingPointRepository.class, () -> mock(SamplingPointRepository.class))
                .withBean(AuditLogRepository.class, () -> mock(AuditLogRepository.class))
                .withUserConfiguration(ScadaIntegrationService.class);
    }

    @Test
    @DisplayName("desligado por padrão: sem a propriedade o bean não existe e nada conecta")
    void disabledByDefault() {
        runner().run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ScadaIntegrationService.class));
        runner().withPropertyValues("sigsan.scada.mqtt.enabled=false")
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ScadaIntegrationService.class));
        verifyNoInteractions(tagRepo, dataRepo);
    }

    @Test
    @DisplayName("ligado sem broker configurado: a subida falha, sem broker padrão inventado")
    void enabledRequiresBroker() {
        runner().withPropertyValues("sigsan.scada.mqtt.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .rootCause().hasMessageContaining("sigsan.scada.mqtt.broker"));
    }

    // ---------------------------------------------------------------- mensagens

    @Test
    @DisplayName("mensagem válida: tenant do tópico no contexto durante o processamento e limpo depois")
    void validMessageUsesTopicTenantAndClearsContext() {
        AtomicReference<UUID> tenantDuringLookup = new AtomicReference<>();
        when(tagRepo.findByTenantIdAndTagNameAndStatus(TENANT, "ETA1.CLORO", "ACTIVE")).thenAnswer(inv -> {
            tenantDuringLookup.set(TenantContextHolder.getTenantId());
            return Optional.of(tag);
        });

        process(DATA_TOPIC, "{\"value\": 1.25, \"quality\": 0, \"timestamp\": \"2026-10-12T08:00:00-03:00\"}");

        ArgumentCaptor<ScadaDataPoint> saved = ArgumentCaptor.forClass(ScadaDataPoint.class);
        verify(dataRepo).save(saved.capture());
        ScadaDataPoint point = saved.getValue();
        assertThat(point.getTenantId()).isEqualTo(TENANT);
        assertThat(point.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-10-12T08:00:00-03:00"));
        assertThat(point.getQuality()).as("qualidade da mensagem, não 192").isZero();
        assertThat(point.getRawValue()).isEqualByComparingTo("1.25");

        assertThat(tenantDuringLookup.get()).isEqualTo(TENANT);
        assertThat(TenantContextHolder.getTenantId()).as("contexto limpo depois").isNull();
    }

    @Test
    @DisplayName("tag carregada sob demanda e guardada em cache por (tenant, tag)")
    void tagCacheIsPerTenantAndOnDemand() {
        when(tagRepo.findByTenantIdAndTagNameAndStatus(TENANT, "ETA1.CLORO", "ACTIVE")).thenReturn(Optional.of(tag));
        String payload = "{\"value\": 1, \"quality\": 192, \"timestamp\": \"2026-10-12T08:00:00Z\"}";
        process(DATA_TOPIC, payload);
        process(DATA_TOPIC, payload);
        verify(tagRepo, org.mockito.Mockito.times(1)).findByTenantIdAndTagNameAndStatus(TENANT, "ETA1.CLORO", "ACTIVE");

        UUID other = UUID.fromString("66666666-6666-6666-6666-666666666666");
        process("sigsan/" + other + "/scada/data/ETA1.CLORO", payload);
        verify(tagRepo).findByTenantIdAndTagNameAndStatus(other, "ETA1.CLORO", "ACTIVE");
    }

    @ParameterizedTest(name = "tópico [{0}] descartado sem consultar nada")
    @ValueSource(strings = {
            "sigsan/tenant_alpha/scada/data/ETA1.CLORO",
            "sigsan/not-a-uuid/scada/data/ETA1.CLORO",
            "sigsan/55555555-5555-5555-5555-555555555555/quality/ETA1.CLORO"})
    void invalidTenantInTopicIsDropped(String topic) {
        TenantContextHolder.setTenantId(UUID.randomUUID()); // tenant de outra coisa na thread
        process(topic, "{\"value\": 1, \"quality\": 192, \"timestamp\": \"2026-10-12T08:00:00Z\"}");
        verifyNoInteractions(tagRepo, dataRepo);
    }

    @ParameterizedTest(name = "payload [{0}] rejeitado")
    @ValueSource(strings = {
            "{\"value\": 1.0, \"quality\": 192}",                                   // sem timestamp
            "{\"value\": 1.0, \"timestamp\": \"2026-10-12T08:00:00Z\"}",            // sem quality
            "{\"value\": 1.0, \"quality\": \"boa\", \"timestamp\": \"2026-10-12T08:00:00Z\"}",
            "{\"value\": 1.0, \"quality\": 192, \"timestamp\": \"ontem\"}",
            "{\"value\": 1.0, \"quality\": 192, \"timestamp\": \"2026-10-12T08:00:00\"}", // sem fuso
            "{\"value\": \"alto\", \"quality\": 192, \"timestamp\": \"2026-10-12T08:00:00Z\"}",
            "nao e json",
            ""})
    void incompleteMessageIsRejected(String payload) {
        when(tagRepo.findByTenantIdAndTagNameAndStatus(TENANT, "ETA1.CLORO", "ACTIVE")).thenReturn(Optional.of(tag));
        process(DATA_TOPIC, payload);
        verify(dataRepo, never()).save(any());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("tag desconhecida: descartada, contexto limpo")
    void unknownTagIsDropped() {
        when(tagRepo.findByTenantIdAndTagNameAndStatus(any(), anyString(), anyString())).thenReturn(Optional.empty());
        process(DATA_TOPIC, "{\"value\": 1, \"quality\": 192, \"timestamp\": \"2026-10-12T08:00:00Z\"}");
        verify(dataRepo, never()).save(any());
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("erro no repositório: mensagem perdida é logada e o contexto é limpo mesmo assim")
    void contextIsClearedOnFailure() {
        when(tagRepo.findByTenantIdAndTagNameAndStatus(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("banco fora"));
        process(DATA_TOPIC, "{}");
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    @DisplayName("comando sem tenant no contexto ou sem valor é recusado")
    void publishCommandValidation() {
        assertThatThrownBy(() -> service.publishCommand("ETA1.CLORO", BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sem tenant");
        TenantContextHolder.setTenantId(TENANT);
        assertThatThrownBy(() -> service.publishCommand("ETA1.CLORO", null))
                .isInstanceOf(IllegalArgumentException.class);
        when(tagRepo.findByTenantIdAndTagNameAndStatus(any(), anyString(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publishCommand("NAO.EXISTE", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desconhecida");
    }
}
