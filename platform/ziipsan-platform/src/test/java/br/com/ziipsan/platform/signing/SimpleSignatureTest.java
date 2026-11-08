package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Um teste do nivel SIMPLES (aceite): evidencias da P1.24 completas e hash conferido. */
class SimpleSignatureTest {

    private SimpleDocumentSigner signerWith(SimpleDocumentSigner.AcceptanceEvidence evidence) {
        return new SimpleDocumentSigner(
                (tenantId, userId, code, version, hash) -> evidence == null ? Optional.empty() : Optional.of(evidence),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
    }

    @Test
    @DisplayName("simples: aceite da P1.24 com hash, data, IP e autenticacao assina e grava as evidencias")
    void simpleSignsFromAcceptanceEvidence() {
        String hash = SignatureHashes.sha256Hex(SigningTestSupport.CONTENT);
        var signer = signerWith(SigningTestSupport.acceptance(hash));
        DocumentSigner.SignatureRecord record = signer.sign(
                SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT));
        assertThat(record.level()).isEqualTo(SignatureLevel.SIMPLE);
        assertThat(record.contentHash()).isEqualTo(hash);
        var evidence = Json.readTree(record.evidenceJson());
        assertThat(evidence.get("ipAddress").asText()).isEqualTo("203.0.113.10");
        assertThat(evidence.get("authMethod").asText()).isEqualTo("PORTAL_PASSWORD_MFA");
        assertThat(evidence.get("acceptedAt").asText()).isEqualTo(SigningTestSupport.NOW.toString());
        // A chave real do aceite (D6) fica registrada na evidencia da assinatura.
        assertThat(evidence.get("userId").asText()).isEqualTo(SigningTestSupport.USER_ID.toString());
        assertThat(evidence.get("documentCode").asText()).isEqualTo(SigningTestSupport.ACCEPTANCE_CODE);
        assertThat(evidence.get("documentVersion").asText()).isEqualTo(SigningTestSupport.ACCEPTANCE_VERSION);
    }

    @Test
    @DisplayName("simples: sem aceite registrado na P1.24, recusa")
    void simpleRefusesWithoutAcceptance() {
        var signer = signerWith(null);
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)))
                .hasMessageContaining("document_acceptance");
    }

    @Test
    @DisplayName("simples: aceite de outro texto (hash diverso) ou evidencia incompleta, recusa")
    void simpleRefusesMismatchedOrIncompleteEvidence() {
        var wrongHash = signerWith(new SimpleDocumentSigner.AcceptanceEvidence(
                SigningTestSupport.USER_ID, SigningTestSupport.ACCEPTANCE_CODE, SigningTestSupport.ACCEPTANCE_VERSION,
                SignatureHashes.sha256Hex("outro texto"), SigningTestSupport.NOW, "203.0.113.10", "PORTAL"));
        assertThatThrownBy(() -> wrongHash.sign(SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)))
                .hasMessageContaining("nao cobre");
        String hash = SignatureHashes.sha256Hex(SigningTestSupport.CONTENT);
        var noIp = signerWith(new SimpleDocumentSigner.AcceptanceEvidence(
                SigningTestSupport.USER_ID, SigningTestSupport.ACCEPTANCE_CODE, SigningTestSupport.ACCEPTANCE_VERSION,
                hash, SigningTestSupport.NOW, null, "PORTAL"));
        assertThatThrownBy(() -> noIp.sign(SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)))
                .hasMessageContaining("incompleta");
    }

    @Test
    @DisplayName("simples (D6): sem a chave real do aceite no contexto (user_id/codigo/versao), recusa; aceite de outro documento nao serve")
    void simpleRequiresRealAcceptanceKey() {
        String hash = SignatureHashes.sha256Hex(SigningTestSupport.CONTENT);
        var signer = signerWith(SigningTestSupport.acceptance(hash));
        // Contexto generico (sem user_id/document_code/document_version): nao ha aceite real para conferir.
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.context(SignableDocumentType.ADHESION_CONTRACT)))
                .hasMessageContaining("chave do aceite");
        // Aceite devolvido e de OUTRA versao do documento: nao cobre este pedido.
        var otherVersion = signerWith(new SimpleDocumentSigner.AcceptanceEvidence(
                SigningTestSupport.USER_ID, SigningTestSupport.ACCEPTANCE_CODE, "1999-01",
                hash, SigningTestSupport.NOW, "203.0.113.10", "PORTAL"));
        assertThatThrownBy(() -> otherVersion.sign(SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)))
                .hasMessageContaining("nao cobre");
    }

    @Test
    @DisplayName("simples (D7): fornecedor de codigo fora do formato de alta entropia e recusado pelo signer")
    void simpleRefusesWeakVerificationCode() {
        String hash = SignatureHashes.sha256Hex(SigningTestSupport.CONTENT);
        var signer = new SimpleDocumentSigner(
                (t, u, c, v, h) -> Optional.of(SigningTestSupport.acceptance(hash)),
                SigningTestSupport.clock(), SigningTestSupport.ids(), () -> "CODIGO-SEQUENCIAL-1");
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.contextForSimple(SignableDocumentType.ADHESION_CONTRACT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VerificationCodes::generate");
    }
}
