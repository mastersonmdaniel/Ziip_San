package br.com.ziipsan.operations.metering.supervisory;

import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.telemetry.TelemetryInput;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * O envelope de ingestão do supervisório (ED-17): o esquema publicado que o
 * agente do cliente envia por HTTPS. O agente roda na rede do cliente, lê do
 * historiador (OPC UA, MQTT, SQL somente leitura ou CSV agendado), guarda
 * localmente o que não conseguiu enviar e reenvia quando o enlace volta —
 * sem nenhuma porta de entrada na rede do cliente e sem broker em nenhum
 * trecho (REST e arquivo, exceção controlada ao ADR-0010 do ADR-0046).
 *
 * <p><b>Unidirecional, sempre:</b> o envelope declara
 * {@code accessMode = READ_ONLY} e cada amostra declara
 * {@code operation = SAMPLE}. Qualquer outra coisa é recusada na ingestão:
 * o Ziip SAN nunca escreve em CLP, RTU ou IHM, e o envelope não é um canal
 * de comando disfarçado. O desenho segue o modelo de zonas e conduítes da
 * IEC 62443: o agente está na zona do cliente, o conduíte é só esta chamada
 * HTTPS de saída com assinatura.
 *
 * @param schemaVersion versão do esquema publicado (ex.: {@code 1})
 * @param agentId       identificador do agente do cliente
 * @param stationCode   código da estação (ETA, ETE, elevatória, reservatório)
 * @param envelopeId    identificador do envelope no agente (trilha do reenvio)
 * @param signature     HMAC-SHA-256 hexadecimal do envelope, ver {@link SupervisorySignature}
 */
public record SupervisoryEnvelope(String schemaVersion, String agentId, String stationCode,
                                  String accessMode, String envelopeId, OffsetDateTime sentAt,
                                  List<Sample> samples, String signature) {

    /** O único modo de acesso que o contrato admite: leitura na origem. */
    public static final String READ_ONLY = "READ_ONLY";

    public SupervisoryEnvelope {
        TelemetryInput.require(schemaVersion != null && !schemaVersion.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "schemaVersion do envelope é obrigatória");
        TelemetryInput.require(agentId != null && !agentId.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "agentId do envelope é obrigatório");
        TelemetryInput.require(stationCode != null && !stationCode.isBlank(),
                MeteringErrorCode.INVALID_INPUT, "stationCode do envelope é obrigatório");
        TelemetryInput.notNull(sentAt, "sentAt");
        TelemetryInput.require(samples != null && !samples.isEmpty(),
                MeteringErrorCode.INVALID_INPUT, "o envelope precisa trazer ao menos uma amostra");
        samples = List.copyOf(samples);
    }

    /**
     * Uma amostra do envelope.
     *
     * @param sampleKey chave de idempotência da amostra, estável no agente:
     *                  o reenvio da mesma amostra traz a mesma chave e não duplica
     * @param operation sempre {@code SAMPLE}; qualquer outro valor é tentativa
     *                  de escrita e derruba o envelope
     */
    public record Sample(String sampleKey, String tagCode, OffsetDateTime sampledAt,
                         BigDecimal numericValue, String textValue, String unitOfMeasure,
                         String quality, String operation) {

        /** A única operação que o contrato admite numa amostra. */
        public static final String OPERATION_SAMPLE = "SAMPLE";

        public Sample {
            TelemetryInput.require(sampleKey != null && !sampleKey.isBlank(),
                    MeteringErrorCode.INVALID_INPUT, "sampleKey da amostra é obrigatória");
            TelemetryInput.require(tagCode != null && !tagCode.isBlank(),
                    MeteringErrorCode.INVALID_INPUT, "tagCode da amostra é obrigatório");
            TelemetryInput.notNull(sampledAt, "sampledAt");
        }
    }
}
