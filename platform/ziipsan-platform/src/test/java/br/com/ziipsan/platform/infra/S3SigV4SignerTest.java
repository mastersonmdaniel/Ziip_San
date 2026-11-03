package br.com.ziipsan.platform.infra;

import br.com.ziipsan.platform.infra.storage.S3SigV4Signer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.45: SigV4 (sem SDK). Afirma formato, determinismo e sensibilidade ao corpo —
 * nao um vetor externo decorado (ver o javadoc do signer): a prova contra o
 * emulador e o ensaio do ambiente local.
 */
class S3SigV4SignerTest {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

    @Test
    @DisplayName("Authorization tem o formato AWS4-HMAC-SHA256 com escopo e assinatura hex de 64")
    void authorizationFormat() {
        var headers = new S3SigV4Signer("AKIDTEST", "segredo-teste", "us-east-1")
                .sign("GET", "localhost:9000", "/docs/a.txt", new byte[0], NOW);
        assertThat(headers.get("Authorization"))
                .startsWith("AWS4-HMAC-SHA256 Credential=AKIDTEST/20261103/us-east-1/s3/aws4_request, "
                        + "SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=")
                .matches(".*Signature=[0-9a-f]{64}$");
        assertThat(headers.get("x-amz-date")).isEqualTo("20261103T000000Z");
    }

    @Test
    @DisplayName("mesma entrada, mesma assinatura; corpo diferente, assinatura diferente")
    void deterministicAndPayloadSensitive() {
        var signer = new S3SigV4Signer("AKIDTEST", "segredo-teste", "us-east-1");
        var a = signer.sign("PUT", "localhost:9000", "/docs/a.txt", "v1".getBytes(), NOW);
        var b = signer.sign("PUT", "localhost:9000", "/docs/a.txt", "v1".getBytes(), NOW);
        var c = signer.sign("PUT", "localhost:9000", "/docs/a.txt", "v2".getBytes(), NOW);
        assertThat(a.get("Authorization")).isEqualTo(b.get("Authorization"));
        assertThat(a.get("Authorization")).isNotEqualTo(c.get("Authorization"));
    }
}
