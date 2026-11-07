package br.com.ziipsan.operations.metering.supervisory;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.SupervisorySample;
import br.com.ziipsan.operations.metering.repository.SupervisorySampleRepository;
import br.com.ziipsan.operations.metering.telemetry.IntegrationCredentialChecker;
import br.com.ziipsan.operations.metering.telemetry.TelemetryScopes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O contrato do supervisório da ED-17: reenvio não duplica (idempotência
 * por amostra), assinatura inválida não entra, e tentativa de escrita —
 * accessMode ou operação — derruba o envelope sem gravar nada.
 */
class SupervisoryIngestionServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CLIENT = "cliente-agente-eta";
    private static final String SECRET = "segredo-de-teste-do-agente";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-07T10:00:00-03:00");

    private SupervisorySampleRepository samples;
    private SupervisoryIngestionService service;
    private final Set<String> storedKeys = new HashSet<>();

    private static SupervisoryEnvelope.Sample sample(String key, String operation) {
        return new SupervisoryEnvelope.Sample(key, "NIVEL-01", AT, new BigDecimal("3.42"),
                null, "m", "GOOD", operation);
    }

    private static SupervisoryEnvelope envelope(String accessMode, List<SupervisoryEnvelope.Sample> list,
                                                String signature) {
        return new SupervisoryEnvelope("1", "agente-eta-central", "ETA-CENTRAL", accessMode,
                "env-0001", AT.plusMinutes(15), list, signature);
    }

    private static SupervisoryEnvelope signed(String accessMode, List<SupervisoryEnvelope.Sample> list) {
        SupervisoryEnvelope unsigned = envelope(accessMode, list, null);
        return envelope(accessMode, list, SupervisorySignature.sign(SECRET, unsigned));
    }

    @BeforeEach
    void setUp() {
        samples = mock(SupervisorySampleRepository.class);
        IntegrationCredentialChecker credentials = mock(IntegrationCredentialChecker.class);
        SupervisoryAgentSecrets secrets = mock(SupervisoryAgentSecrets.class);
        when(credentials.hasScope(TENANT, CLIENT, TelemetryScopes.SUPERVISORY_WRITE)).thenReturn(true);
        when(secrets.sharedSecret(TENANT, "agente-eta-central")).thenReturn(SECRET);
        when(samples.existsByTenantIdAndSampleKey(eq(TENANT), anyString()))
                .thenAnswer(invocation -> storedKeys.contains(invocation.getArgument(1)));
        when(samples.save(any(SupervisorySample.class))).thenAnswer(invocation -> {
            SupervisorySample entity = invocation.getArgument(0);
            storedKeys.add(entity.getSampleKey());
            return entity;
        });
        service = new SupervisoryIngestionService(samples, credentials, secrets, CLOCK);
    }

    @Test
    @DisplayName("o reenvio do mesmo envelope (store-and-forward) não duplica nenhuma amostra")
    void resendDoesNotDuplicateSamples() {
        SupervisoryEnvelope envelope = signed(SupervisoryEnvelope.READ_ONLY, List.of(
                sample("ETA-CENTRAL:NIVEL-01:2026-11-07T10:00", SupervisoryEnvelope.Sample.OPERATION_SAMPLE),
                sample("ETA-CENTRAL:NIVEL-01:2026-11-07T11:00", SupervisoryEnvelope.Sample.OPERATION_SAMPLE)));

        SupervisoryIngestionService.IngestOutcome first = service.ingest(TENANT, CLIENT, envelope);
        SupervisoryIngestionService.IngestOutcome second = service.ingest(TENANT, CLIENT, envelope);

        assertThat(first).isEqualTo(new SupervisoryIngestionService.IngestOutcome(2, 0));
        assertThat(second).isEqualTo(new SupervisoryIngestionService.IngestOutcome(0, 2));
        verify(samples, times(2)).save(any(SupervisorySample.class));
    }

    @Test
    @DisplayName("assinatura que não confere derruba o envelope sem gravar nada")
    void badSignatureIsRejected() {
        SupervisoryEnvelope envelope = envelope(SupervisoryEnvelope.READ_ONLY,
                List.of(sample("K1", SupervisoryEnvelope.Sample.OPERATION_SAMPLE)), "00deadbeef");

        assertThatThrownBy(() -> service.ingest(TENANT, CLIENT, envelope))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(samples, never()).save(any());
    }

    @Test
    @DisplayName("amostra com operação de escrita derruba o envelope inteiro: o Ziip SAN nunca escreve em CLP, RTU ou IHM")
    void writeOperationIsRejected() {
        SupervisoryEnvelope envelope = signed(SupervisoryEnvelope.READ_ONLY, List.of(
                sample("K1", SupervisoryEnvelope.Sample.OPERATION_SAMPLE),
                sample("K2", "WRITE")));

        assertThatThrownBy(() -> service.ingest(TENANT, CLIENT, envelope))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("nunca escreve em CLP, RTU ou IHM");
        verify(samples, never()).save(any());
    }

    @Test
    @DisplayName("accessMode diferente de READ_ONLY é recusado")
    void nonReadOnlyEnvelopeIsRejected() {
        SupervisoryEnvelope envelope = signed("READ_WRITE",
                List.of(sample("K1", SupervisoryEnvelope.Sample.OPERATION_SAMPLE)));

        assertThatThrownBy(() -> service.ingest(TENANT, CLIENT, envelope))
                .isInstanceOf(ApiException.BadRequest.class);
        verify(samples, never()).save(any());
    }

    @Test
    @DisplayName("credencial sem o escopo supervisory:write não ingere")
    void missingScopeIsForbidden() {
        IntegrationCredentialChecker credentials = mock(IntegrationCredentialChecker.class);
        when(credentials.hasScope(any(), any(), any())).thenReturn(false);
        SupervisoryIngestionService denied = new SupervisoryIngestionService(
                samples, credentials, mock(SupervisoryAgentSecrets.class), CLOCK);
        SupervisoryEnvelope envelope = signed(SupervisoryEnvelope.READ_ONLY,
                List.of(sample("K1", SupervisoryEnvelope.Sample.OPERATION_SAMPLE)));

        assertThatThrownBy(() -> denied.ingest(TENANT, CLIENT, envelope))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(samples, never()).save(any());
    }
}
