package br.com.ziipsan.platform.signing;

import java.util.Locale;

/**
 * Nivel de assinatura eletronica (P3.34; Lei 14.063/2020, art. 4o): simples,
 * avancada e qualificada. Os NOMES sao os valores do parametro
 * {@code sign.min_level.<document_type>} (classe d) e do CHECK de
 * {@code document_signatures.level} na V1_14 — mudar um nome aqui exige mudar
 * os dois. A ordem de forca e a da lei: SIMPLE < ADVANCED < QUALIFIED.
 */
public enum SignatureLevel {

    SIMPLE(1),
    ADVANCED(2),
    QUALIFIED(3);

    private final int rank;

    SignatureLevel(int rank) {
        this.rank = rank;
    }

    /** Verdadeiro quando este nivel atende ou supera o minimo exigido. */
    public boolean satisfies(SignatureLevel minimum) {
        if (minimum == null) {
            throw new IllegalArgumentException("nivel minimo e obrigatorio");
        }
        return this.rank >= minimum.rank;
    }

    /**
     * Le o valor do parametro {@code sign.min_level.*}. Valor fora dos tres
     * nomes da spec e recusado — nunca convertido num nivel "parecido".
     */
    public static SignatureLevel parseParameterValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("valor de sign.min_level vazio");
        }
        try {
            return SignatureLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "valor de sign.min_level fora de SIMPLE, ADVANCED, QUALIFIED: recusado", e);
        }
    }
}
