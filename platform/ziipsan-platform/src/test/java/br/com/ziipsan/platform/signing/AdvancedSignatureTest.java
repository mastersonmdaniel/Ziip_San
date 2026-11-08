package br.com.ziipsan.platform.signing;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Um teste do nivel AVANCADO (aceite) + a veda do gov.br da spec. */
class AdvancedSignatureTest {

    private AdvancedDocumentSigner signer(String providerId) {
        return new AdvancedDocumentSigner(
                (context, hash) -> new AdvancedDocumentSigner.AdvancedSeal(providerId, "vinculo-123", "selo-abc"),
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
    }

    @Test
    @DisplayName("avancada: selo do provedor com vinculo univoco assina; provedor vive SO na evidencia (D5)")
    void advancedSignsWithProviderSeal() {
        DocumentSigner.SignatureRecord record = signer("provedor-x")
                .sign(SigningTestSupport.context(SignableDocumentType.INSTALLMENT_TERM));
        assertThat(record.level()).isEqualTo(SignatureLevel.ADVANCED);
        // D5: psc_provider e coluna da QUALIFICADA (V1_14) — na avancada e NULL,
        // e o provedor fica acessivel onde sempre esteve: na evidencia.
        assertThat(record.pscProvider()).isNull();
        assertThat(Json.readTree(record.evidenceJson()).get("providerId").asText()).isEqualTo("provedor-x");
        assertThat(record.signatureFormat()).isNull();
    }

    @Test
    @DisplayName("avancada: provedor gov.br e recusado (restrito a gestor publico e dominio gov.br)")
    void advancedRefusesGovBr() {
        assertThatThrownBy(() -> signer("gov.br").sign(SigningTestSupport.context(SignableDocumentType.INSTALLMENT_TERM)))
                .hasMessageContaining("gov.br");
    }

    @Test
    @DisplayName("avancada (D4): variantes do identificador gov.br sao recusadas; identificadores sem a sequencia govbr passam")
    void govBrVariantsRefusedLookalikesPass() {
        // Variantes provadas pela sonda do revisor + a familia obvia.
        for (String variant : new String[]{"govbr-sso", "govbr2", "assinatura-govbr", "gov-br",
                "GOV.BR", "govbrassinatura", "govbr"}) {
            assertThatThrownBy(() -> signer(variant).sign(SigningTestSupport.context(SignableDocumentType.INSTALLMENT_TERM)))
                    .as("variante %s deve ser recusada", variant)
                    .hasMessageContaining("gov.br");
        }
        // Delimitacao: sem a sequencia contigua "govbr", nao e a marca gov.br.
        for (String legit : new String[]{"provedor-x", "govtech-cloud", "brgov-assinaturas", "signatario-digital"}) {
            assertThat(signer(legit).sign(SigningTestSupport.context(SignableDocumentType.INSTALLMENT_TERM)).level())
                    .as("identificador %s nao pode ser bloqueado", legit)
                    .isEqualTo(SignatureLevel.ADVANCED);
        }
    }

    @Test
    @DisplayName("avancada (D4): identidade gov.br conhecida na porta recusa ANTES de invocar o selo")
    void govBrRefusedBeforeSealing() {
        AtomicBoolean sealInvoked = new AtomicBoolean(false);
        var provider = new AdvancedDocumentSigner.AdvancedSignatureProvider() {
            @Override
            public String providerId() {
                return "govbr-sso";
            }

            @Override
            public AdvancedDocumentSigner.AdvancedSeal seal(
                    DocumentSigner.SignContext context, String contentHash) {
                sealInvoked.set(true);
                return new AdvancedDocumentSigner.AdvancedSeal("govbr-sso", "v", "s");
            }
        };
        var signer = new AdvancedDocumentSigner(provider,
                SigningTestSupport.clock(), SigningTestSupport.ids(), SigningTestSupport.codes());
        assertThatThrownBy(() -> signer.sign(SigningTestSupport.context(SignableDocumentType.INSTALLMENT_TERM)))
                .hasMessageContaining("gov.br");
        assertThat(sealInvoked).isFalse();
    }
}
