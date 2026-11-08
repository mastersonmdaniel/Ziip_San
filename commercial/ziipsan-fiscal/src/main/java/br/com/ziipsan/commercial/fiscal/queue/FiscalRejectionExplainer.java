package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageRejectionCode;
import br.com.ziipsan.commercial.fiscal.nfse.NfseRejectionCode;

import java.util.Optional;

/**
 * Explicação da rejeição pelo catálogo da P2.16 (aceite da P2.33: "rejeitadas com a
 * explicação do catálogo da P2.16"): resolve o {@code cStat} nos enums versionados da
 * P2.16 ({@link NfageRejectionCode} / {@link NfseRejectionCode}) e devolve a chave do
 * catálogo de erros da P1.22 ({@code nfage:<cStat>} / {@code nfse:<código>}) e a
 * mensagem oficial. Código fora do enum NÃO ganha explicação inventada: o resultado é
 * vazio e a fila mostra a rejeição sem explicação do catálogo (pendência visível —
 * código novo entra no enum da P2.16 e o teste de cobertura dela cobra a página).
 */
public final class FiscalRejectionExplainer {

    /** @param catalogCode chave da explicação no catálogo da P1.22 (ex.: {@code nfage:204}) */
    public record Explanation(String catalogCode, String officialMessage) {
    }

    private FiscalRejectionExplainer() {
    }

    public static Optional<Explanation> explain(FiscalDocumentKind kind, String rejectionCode) {
        if (rejectionCode == null || rejectionCode.isBlank()) {
            return Optional.empty();
        }
        String code = rejectionCode.trim();
        if (kind == FiscalDocumentKind.NFSE_AVULSA) {
            for (NfseRejectionCode candidate : NfseRejectionCode.values()) {
                if (candidate.code().equals(code)) {
                    return Optional.of(new Explanation(candidate.catalogCode(), candidate.officialMessage()));
                }
            }
            return Optional.empty();
        }
        for (NfageRejectionCode candidate : NfageRejectionCode.values()) {
            if (candidate.code().equals(code)) {
                return Optional.of(new Explanation(candidate.catalogCode(), candidate.officialMessage()));
            }
        }
        return Optional.empty();
    }
}
