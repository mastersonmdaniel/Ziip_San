package br.com.ziipsan.platform.signing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Um teste do nivel QUALIFICADO (aceite): PSC em nuvem, PAdES, politica e carimbo RFC 3161. */
class QualifiedSignatureTest {

    private SigningTestSupport.MapParameters configuredParams() {
        var params = new SigningTestSupport.MapParameters();
        params.values.put("sign.psc_provider", "psc-credenciado-teste");
        params.values.put("sign.timestamp_authority", "act-teste");
        return params;
    }

    /** PSC de fixture AMARRADO ao pedido (D9): certificado do signatario e hash do conteudo do pedido. */
    private QualifiedDocumentSigner signer(SigningParameters params) {
        return new QualifiedDocumentSigner(params,
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "politica-declarada-pelo-psc", "usuario-teste", hash, new byte[]{1, 2, 3}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        authority, SigningTestSupport.NOW, hash, SignatureHashes.sha256Hex("token")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
    }

    @Test
    @DisplayName("qualificada: PSC + PAdES + politica declarada + carimbo da ACT configurada")
    void qualifiedSignsWithPscPadesAndTimestamp() {
        DocumentSigner.SignatureRecord record = signer(configuredParams())
                .sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION));
        assertThat(record.level()).isEqualTo(SignatureLevel.QUALIFIED);
        assertThat(record.signatureFormat()).isEqualTo("PAdES");
        assertThat(record.pscProvider()).isEqualTo("psc-credenciado-teste");
        assertThat(record.signaturePolicyId()).isEqualTo("politica-declarada-pelo-psc");
        assertThat(record.timestampAuthority()).isEqualTo("act-teste");
        assertThat(SignatureHashes.isSha256Hex(record.timestampTokenHash())).isTrue();
    }

    @Test
    @DisplayName("qualificada: sem sign.psc_provider ou sem sign.timestamp_authority, recusa")
    void qualifiedRefusesWithoutPscOrAuthority() {
        var signer = signer(new SigningTestSupport.MapParameters());
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("sign.psc_provider");
        var onlyPsc = new SigningTestSupport.MapParameters();
        onlyPsc.values.put("sign.psc_provider", "psc-teste");
        assertThatThrownBy(() -> signer(onlyPsc).sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("sign.timestamp_authority");
    }

    @Test
    @DisplayName("qualificada: carimbo de ACT diferente da configurada e recusado")
    void qualifiedRefusesForeignTimestamp() {
        var signer = new QualifiedDocumentSigner(configuredParams(),
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "pol", "usuario-teste", hash, new byte[]{9}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        "act-DIFERENTE", SigningTestSupport.NOW, hash, SignatureHashes.sha256Hex("t")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("diferente");
    }

    @Test
    @DisplayName("qualificada (D9): certificado de outro titular ou PSC que assinou outro conteudo, recusa")
    void qualifiedRefusesUnboundPscReturn() {
        var foreignCert = new QualifiedDocumentSigner(configuredParams(),
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "pol", "CN=Outra Pessoa", hash, new byte[]{9}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        authority, SigningTestSupport.NOW, hash, SignatureHashes.sha256Hex("t")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> foreignCert.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("nao e do signatario");
        var otherContent = new QualifiedDocumentSigner(configuredParams(),
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "pol", "usuario-teste", SignatureHashes.sha256Hex("outro conteudo"), new byte[]{9}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        authority, SigningTestSupport.NOW, hash, SignatureHashes.sha256Hex("t")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> otherContent.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("nao corresponde ao conteudo");
    }

    @Test
    @DisplayName("qualificada (D9): carimbo que nao amarra o conteudo (messageImprint) ou com instante no futuro, recusa")
    void qualifiedRefusesUnboundOrFutureTimestamp() {
        var wrongImprint = new QualifiedDocumentSigner(configuredParams(),
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "pol", "usuario-teste", hash, new byte[]{9}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        authority, SigningTestSupport.NOW, SignatureHashes.sha256Hex("outro"),
                        SignatureHashes.sha256Hex("t")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> wrongImprint.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("nao amarra");
        var future = new QualifiedDocumentSigner(configuredParams(),
                (context, hash, psc) -> new QualifiedDocumentSigner.PscSignature(
                        "pol", "usuario-teste", hash, new byte[]{9}),
                (hash, authority) -> new QualifiedDocumentSigner.TimestampToken(
                        authority, Instant.parse("2027-01-01T00:00:00Z"), hash,
                        SignatureHashes.sha256Hex("t")),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> future.sign(SigningTestSupport.context(SignableDocumentType.DEBIT_AUTHORIZATION)))
                .hasMessageContaining("futuro");
    }
}
