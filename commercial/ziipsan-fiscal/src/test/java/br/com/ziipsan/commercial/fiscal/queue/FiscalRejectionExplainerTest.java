package br.com.ziipsan.commercial.fiscal.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicação da rejeição pelo catálogo da P2.16 (P2.33): código conhecido resolve; desconhecido NÃO é inventado. */
class FiscalRejectionExplainerTest {

    @Test
    @DisplayName("cStat 204 da NFAg-e resolve para a chave nfage:204 e a mensagem oficial do MOC (P2.16)")
    void knownNfageCode() {
        // Conferido à mão no enum da P2.16: C204 = "Duplicidade de NFAg", catalogCode nfage:204.
        var explanation = FiscalRejectionExplainer.explain(FiscalDocumentKind.NFAGE, "204");
        assertThat(explanation).isPresent();
        assertThat(explanation.get().catalogCode()).isEqualTo("nfage:204");
        assertThat(explanation.get().officialMessage()).isEqualTo("Duplicidade de NFAg");
    }

    @Test
    @DisplayName("código fora do catálogo da P2.16 devolve vazio — a fila mostra a rejeição sem explicação, nunca texto inventado")
    void unknownCodeHasNoExplanation() {
        assertThat(FiscalRejectionExplainer.explain(FiscalDocumentKind.NFAGE, "123")).isEmpty();
        assertThat(FiscalRejectionExplainer.explain(FiscalDocumentKind.NFAGE, null)).isEmpty();
        assertThat(FiscalRejectionExplainer.explain(FiscalDocumentKind.NFAGE, " ")).isEmpty();
    }

    @Test
    @DisplayName("código da NFS-e resolve no enum da NFS-e da P2.16, com a chave nfse:")
    void knownNfseCode() {
        var explanation = FiscalRejectionExplainer.explain(FiscalDocumentKind.NFSE_AVULSA, "E0010");
        assertThat(explanation).isPresent();
        assertThat(explanation.get().catalogCode()).isEqualTo("nfse:E0010");
    }
}
