package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.api.ApiException;

/**
 * Regra pura do aceite de problema (§5.5): problema pode ser aceito com
 * justificativa — por exemplo, rede legada abaixo do DN mínimo.
 *
 * <p>Separada do serviço para ser testável sem banco: justificativa é
 * obrigatória e não pode ser só espaços; só problema OPEN pode ser
 * aceito (ACCEPTED não reaceita, RESOLVED não volta por aceite).
 */
public final class GisValidationAcceptance {

    private GisValidationAcceptance() {
    }

    /** Devolve a justificativa aparada, ou lança validação por campo. */
    public static String requireJustification(String justification) {
        if (justification == null || justification.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "justification", "REQUIRED",
                    "A justificativa do aceite é obrigatória."));
        }
        return justification.trim();
    }

    /** Só OPEN aceita; qualquer outro estado é conflito de estado. */
    public static void requireOpen(String status) {
        if (!"OPEN".equals(status)) {
            throw new ApiException.Conflict("problema-nao-aberto",
                    "Só é possível aceitar um problema aberto.");
        }
    }
}
