package br.com.ziipsan.commercial.collection.pix;

import java.security.SecureRandom;

/**
 * Politica de entropia do segmento da URL do webhook Pix (P3.2, correcao
 * pos-revisao M2). O segmento e a UNICA credencial de rota do callback: e ele
 * que resolve tenant, convenio e PSP. Segmento previsivel ou sequencial anula
 * essa protecao, por isso a exigencia fica registrada aqui, no codigo, e nao
 * so no CHECK de formato do banco (regex 32-64 caracteres na V6_3), que aceita
 * ate um segmento sequencial de 32 caracteres.
 *
 * <p><b>Geracao (a garantia):</b> o cadastro de uma rota nova DEVE usar
 * {@link #generate()} - {@link SecureRandom} sobre um alfabeto de 64 simbolos,
 * {@value #GENERATED_LENGTH} caracteres = 288 bits de entropia. Nunca derivar
 * o segmento de dado cadastral (CNPJ, convenio, sequencia, data) nem usar
 * gerador previsivel ({@code Math.random}, {@code Random} semeado, UUID
 * sequencial).
 *
 * <p><b>Validacao (o piso):</b> {@link #hasRequiredEntropy(String)} nao prova
 * entropia - nenhuma validacao prova; ela recusa o obviamente fraco: fora da
 * faixa 32-64, fora do alfabeto da V6_3, ou com menos de
 * {@value #MIN_DISTINCT_CHARS} caracteres distintos (o que elimina segmento de
 * um so caractere repetido, padrao curto repetido e sequencia obvia curta). O
 * {@link PixCallbackProcessor} aplica este piso a todo callback recebido.
 */
public final class WebhookSegmentPolicy {

    /** Comprimento minimo do segmento (mesma faixa do CHECK da V6_3). */
    public static final int MIN_LENGTH = 32;

    /** Comprimento maximo do segmento (mesma faixa do CHECK da V6_3). */
    public static final int MAX_LENGTH = 64;

    /** Comprimento gerado: 48 simbolos de 64 = 288 bits via SecureRandom. */
    public static final int GENERATED_LENGTH = 48;

    /** Piso de caracteres distintos: abaixo disso o segmento e fraco obvio. */
    public static final int MIN_DISTINCT_CHARS = 10;

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSegmentPolicy() {
    }

    /** Gera um segmento novo para cadastro de rota, com entropia de CSPRNG. */
    public static String generate() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Piso de validacao descrito na classe: formato + variedade minima. */
    public static boolean hasRequiredEntropy(String segment) {
        if (segment == null) {
            return false;
        }
        int length = segment.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = segment.charAt(i);
            boolean allowed = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9' || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return segment.chars().distinct().count() >= MIN_DISTINCT_CHARS;
    }

    /** Como {@link #hasRequiredEntropy(String)}, mas lancando excecao. */
    public static void requireValid(String segment) {
        if (!hasRequiredEntropy(segment)) {
            throw new IllegalArgumentException("segmento de webhook fora da politica de entropia "
                    + "(32-64 caracteres do alfabeto, gerado por CSPRNG, minimo de "
                    + MIN_DISTINCT_CHARS + " caracteres distintos)");
        }
    }
}
