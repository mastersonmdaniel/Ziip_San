package br.com.ziipsan.operations.metering.supervisory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A assinatura do envelope do supervisório (ED-17): HMAC-SHA-256, em
 * hexadecimal minúsculo, sobre a forma canônica do envelope — o mesmo
 * mecanismo de assinatura da P2.26 para os webhooks de saída.
 *
 * <p>Forma canônica: os campos do cabeçalho e, por amostra, chave, tag,
 * instante e valores, unidos por {@code |} e {@code ;} na ordem do envelope.
 * A comparação é em tempo constante ({@link MessageDigest#isEqual}). O
 * segredo é por tenant e agente e vive no cofre (porta
 * {@link SupervisoryAgentSecrets}); esta classe nunca o guarda.
 */
public final class SupervisorySignature {

    private SupervisorySignature() {
    }

    public static String canonical(SupervisoryEnvelope envelope) {
        StringBuilder sb = new StringBuilder();
        sb.append(envelope.schemaVersion()).append('|')
                .append(envelope.agentId()).append('|')
                .append(envelope.stationCode()).append('|')
                .append(envelope.accessMode()).append('|')
                .append(envelope.envelopeId()).append('|')
                .append(envelope.sentAt());
        for (SupervisoryEnvelope.Sample sample : envelope.samples()) {
            // A forma canônica cobre a amostra INTEIRA (também unidade e
            // qualidade): campo fora da assinatura é campo adulterável sem
            // quebrar o HMAC.
            sb.append(';')
                    .append(sample.sampleKey()).append('|')
                    .append(sample.tagCode()).append('|')
                    .append(sample.sampledAt()).append('|')
                    .append(sample.numericValue() == null ? "" : sample.numericValue().toPlainString()).append('|')
                    .append(sample.textValue() == null ? "" : sample.textValue()).append('|')
                    .append(sample.unitOfMeasure() == null ? "" : sample.unitOfMeasure()).append('|')
                    .append(sample.quality() == null ? "" : sample.quality()).append('|')
                    .append(sample.operation() == null ? "" : sample.operation());
        }
        return sb.toString();
    }

    public static String sign(String secret, SupervisoryEnvelope envelope) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(envelope).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 indisponível para assinar o envelope", e);
        }
    }

    /** Verdade se a assinatura do envelope confere com o segredo do agente naquele tenant. */
    public static boolean verify(String secret, SupervisoryEnvelope envelope) {
        if (secret == null || envelope.signature() == null) {
            return false;
        }
        String expected = sign(secret, envelope);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                envelope.signature().getBytes(StandardCharsets.UTF_8));
    }
}
