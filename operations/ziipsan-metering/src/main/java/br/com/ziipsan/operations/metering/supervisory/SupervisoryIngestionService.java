package br.com.ziipsan.operations.metering.supervisory;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.metering.domain.SupervisorySample;
import br.com.ziipsan.operations.metering.repository.SupervisorySampleRepository;
import br.com.ziipsan.operations.metering.telemetry.IntegrationCredentialChecker;
import br.com.ziipsan.operations.metering.telemetry.TelemetryScopes;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Ingestão do supervisório (ED-17): recebe o envelope do agente do cliente e
 * grava as amostras. As guardas, nesta ordem, são o contrato:
 *
 * <ol>
 *   <li>a credencial de integração tem o escopo {@code supervisory:write}
 *       (camada da P2.26);</li>
 *   <li>o envelope declara {@code accessMode = READ_ONLY} e nenhuma amostra
 *       declara operação diferente de {@code SAMPLE} — tentativa de escrita
 *       em CLP, RTU ou IHM derruba o envelope inteiro, sem gravar nada;</li>
 *   <li>a assinatura HMAC confere com o segredo do agente naquele tenant;</li>
 *   <li>cada amostra entra uma única vez: a {@code sampleKey} já conhecida é
 *       contada como duplicada e não grava de novo — é o que torna seguro o
 *       reenvio do agente quando o enlace volta.</li>
 * </ol>
 *
 * Classe simples, sem anotação, como os demais serviços do projeto.
 */
public class SupervisoryIngestionService {

    private final SupervisorySampleRepository samples;
    private final IntegrationCredentialChecker credentials;
    private final SupervisoryAgentSecrets secrets;
    private final Clock clock;

    public SupervisoryIngestionService(SupervisorySampleRepository samples,
                                       IntegrationCredentialChecker credentials,
                                       SupervisoryAgentSecrets secrets, Clock clock) {
        this.samples = Objects.requireNonNull(samples, "O repositório de amostras é obrigatório");
        this.credentials = Objects.requireNonNull(credentials, "A porta de credenciais é obrigatória");
        this.secrets = Objects.requireNonNull(secrets, "A porta de segredos é obrigatória");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório");
    }

    /** Resultado da ingestão de um envelope: aceitas e duplicadas (reenvio), por contadores nomeados. */
    public record IngestOutcome(int accepted, int duplicates) {
    }

    public IngestOutcome ingest(UUID tenantId, String clientId, SupervisoryEnvelope envelope) {
        Objects.requireNonNull(envelope, "O envelope é obrigatório");
        if (clientId == null || !credentials.hasScope(tenantId, clientId, TelemetryScopes.SUPERVISORY_WRITE)) {
            throw new ApiException.Forbidden(
                    "A credencial de integração não tem o escopo " + TelemetryScopes.SUPERVISORY_WRITE + ".");
        }
        if (!SupervisoryEnvelope.READ_ONLY.equals(envelope.accessMode())) {
            throw new ApiException.BadRequest("supervisory-write-attempt",
                    "A ingestão do supervisório é somente leitura: accessMode precisa ser READ_ONLY.");
        }
        for (SupervisoryEnvelope.Sample sample : envelope.samples()) {
            if (!SupervisoryEnvelope.Sample.OPERATION_SAMPLE.equals(sample.operation())) {
                throw new ApiException.BadRequest("supervisory-write-attempt",
                        "A amostra " + sample.sampleKey() + " declara operação " + sample.operation()
                                + ": o Ziip SAN nunca escreve em CLP, RTU ou IHM.");
            }
        }
        String secret = secrets.sharedSecret(tenantId, envelope.agentId());
        if (secret == null || !SupervisorySignature.verify(secret, envelope)) {
            throw new ApiException.Forbidden("A assinatura do envelope do supervisório não confere.");
        }
        int accepted = 0;
        int duplicates = 0;
        for (SupervisoryEnvelope.Sample sample : envelope.samples()) {
            if (samples.existsByTenantIdAndSampleKey(tenantId, sample.sampleKey())) {
                duplicates++;
                continue;
            }
            SupervisorySample entity = new SupervisorySample();
            entity.setTenantId(tenantId);
            entity.setStationCode(envelope.stationCode());
            entity.setTagCode(sample.tagCode());
            entity.setBucketMonth(YearMonth.from(sample.sampledAt()).atDay(1));
            entity.setSampledAt(sample.sampledAt());
            entity.setNumericValue(sample.numericValue());
            entity.setTextValue(sample.textValue());
            entity.setUnitOfMeasure(sample.unitOfMeasure());
            entity.setQuality(sample.quality());
            entity.setSampleKey(sample.sampleKey());
            entity.setEnvelopeId(envelope.envelopeId());
            entity.setReceivedAt(OffsetDateTime.now(clock));
            samples.save(entity);
            accepted++;
        }
        return new IngestOutcome(accepted, duplicates);
    }
}
