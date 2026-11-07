package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.document.DocumentNumber;

import java.util.regex.Pattern;

/**
 * RNF-52 (P3.3): o documento do consumidor NUNCA aparece em URL (nem como
 * {@code login_hint}), em log da API ou em evento de login do Keycloak.
 *
 * <p>Esta classe e a trava testavel dessa regra: detecta CPF/CNPJ (com ou
 * sem pontuacao) e oferece a redacao para qualquer linha de log que cruze a
 * borda do portal. A fiacao (filtro de log, configuracao do Keycloak) e da
 * integracao; a regra e daqui.
 *
 * <p>Criterio de deteccao (endurecido na revisao cruzada da P3.3): para
 * fins de log/URL, documento NAO precisa ter digito verificador valido —
 * o que o usuario digita no login e o documento dele mesmo com erro de
 * digitacao, e vazaria igual. Por isso sequencias de exatamente 11 ou 14
 * digitos sao tratadas como documento suspeito, e o CNPJ alfanumerico
 * (IN RFB 2.229/2024) e detectado na forma pontuada e na forma corrida
 * validada por {@link DocumentNumber}.
 */
public final class PortalLogSafety {

    private static final Pattern FORMATTED_DOCUMENT =
            Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern FORMATTED_ALNUM_CNPJ =
            Pattern.compile("[0-9A-Za-z]{2}\\.[0-9A-Za-z]{3}\\.[0-9A-Za-z]{3}/[0-9A-Za-z]{4}-\\d{2}");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");
    private static final Pattern ALNUM_TOKEN =
            Pattern.compile("(?<![0-9A-Za-z])[0-9A-Za-z]{14}(?![0-9A-Za-z])");

    private PortalLogSafety() { }

    /** Verdadeiro se o texto contem um documento, formatado ou corrido, valido ou apenas com a forma. */
    public static boolean containsDocument(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (FORMATTED_DOCUMENT.matcher(text).find() || FORMATTED_ALNUM_CNPJ.matcher(text).find()) {
            return true;
        }
        var digits = DIGIT_RUN.matcher(text);
        while (digits.find()) {
            int length = digits.group().length();
            if (length == 11 || length == 14) {
                return true;
            }
        }
        var tokens = ALNUM_TOKEN.matcher(text);
        while (tokens.find()) {
            if (DocumentNumber.kind(tokens.group()) == DocumentNumber.Kind.CNPJ) {
                return true;
            }
        }
        return false;
    }

    /** Recusa URL de login/autenticacao que carregue documento ou login_hint com documento. */
    public static void assertUrlHasNoDocument(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL e obrigatoria");
        }
        if (containsDocument(url)) {
            throw new IllegalArgumentException("URL nao pode conter documento do consumidor (RNF-52)");
        }
        // Documento codificado em percent-encoding foge da varredura corrida:
        // decodifica uma vez e revê (a forma codificada tambem e vazamento).
        try {
            String decoded = java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8);
            if (!decoded.equals(url) && containsDocument(decoded)) {
                throw new IllegalArgumentException("URL nao pode conter documento do consumidor (RNF-52)");
            }
        } catch (IllegalArgumentException malformed) {
            if (malformed.getMessage() != null && malformed.getMessage().contains("RNF-52")) {
                throw malformed;
            }
            // Percent-encoding malformado: nada a decodificar; a varredura corrida ja passou.
        }
    }

    /** Redige documentos de uma linha antes de ela ir para log/evento. */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = FORMATTED_DOCUMENT.matcher(text).replaceAll("[documento redigido]");
        out = FORMATTED_ALNUM_CNPJ.matcher(out).replaceAll("[documento redigido]");
        var digits = DIGIT_RUN.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (digits.find()) {
            int length = digits.group().length();
            String replacement = (length == 11 || length == 14)
                    ? "[documento redigido]" : digits.group();
            digits.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        digits.appendTail(sb);
        out = sb.toString();
        var tokens = ALNUM_TOKEN.matcher(out);
        sb = new StringBuilder();
        while (tokens.find()) {
            String replacement = DocumentNumber.kind(tokens.group()) == DocumentNumber.Kind.CNPJ
                    ? "[documento redigido]" : tokens.group();
            tokens.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        tokens.appendTail(sb);
        return sb.toString();
    }
}
