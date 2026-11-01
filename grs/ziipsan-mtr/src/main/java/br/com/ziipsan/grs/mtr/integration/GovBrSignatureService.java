package br.com.ziipsan.grs.mtr.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

/**
 * Assinatura digital no padrão ICP-Brasil / Gov.br.
 *
 * IMPORTANTE — IMPLEMENTAÇÃO PENDENTE:
 * Este serviço calcula o resumo criptográfico (SHA-256) do conteúdo e expõe a
 * verificação desse resumo. NÃO emite assinatura digital criptográfica: isso
 * exige um certificado ICP-Brasil válido (arquivo .pfx/.p12), sua senha e a
 * cadeia da ICP-Brasil. Sem certificado não é possível produzir uma assinatura
 * verificável por terceiros, e um XML de assinatura falso invalidaria a
 * comprovação legal do documento.
 *
 * Todo envelope produzido por {@link #sign(byte[])} sai com
 * {@link SignatureStatus#PENDING} e sem assinatura — jamais "ASSINADO".
 */
@Slf4j
@Service
public class GovBrSignatureService {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Calcula o resumo do payload. Usado internamente por sign/verify.
     */
    public String digest(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload não informado para cálculo do resumo");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return bytesToHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de resumo indisponível: " + DIGEST_ALGORITHM, e);
        }
    }

    /**
     * Produz o envelope de assinatura. Sem certificado ICP-Brasil configurado,
     * o envelope sai marcado como PENDENTE e sem assinatura — o hash do conteúdo
     * é preenchido, o que permite a verificação de integridade, mas não prova
     * autoria.
     */
    public SignatureEnvelope sign(byte[] payload) {
        String contentHash = digest(payload);
        log.warn("Assinatura ICP-Brasil não implementada: certificado indisponível. "
                + "Envelope emitido como PENDENTE (hash={})", contentHash);
        return new SignatureEnvelope(
                null,
                null,
                DIGEST_ALGORITHM,
                contentHash,
                null,
                SignatureStatus.PENDING,
                "Certificado ICP-Brasil não configurado: assinatura digital não emitida",
                OffsetDateTime.now()
        );
    }

    /**
     * Assina um conteúdo textual (ex.: JSON do manifesto).
     */
    public SignatureEnvelope sign(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Conteúdo não informado para assinatura");
        }
        return sign(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifica a integridade comparando o resumo recalculado com o informado.
     * Verificação é de INTEGRIDADE (hash), não de autenticidade (assinatura).
     */
    public boolean verify(byte[] payload, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                digest(payload).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Situação da assinatura no envelope. */
    public enum SignatureStatus {
        PENDING,
        SIGNED,
        REVOKED,
        EXPIRED,
        FAILED
    }

    /** Envelope de assinatura. Campos de assinatura ficam nulos enquanto PENDENTE. */
    public record SignatureEnvelope(
            String certificateAlias,
            String signerCpf,
            String digestAlgorithm,
            String contentHash,
            String signatureBase64,
            SignatureStatus status,
            String reason,
            OffsetDateTime signedAt
    ) {}
}