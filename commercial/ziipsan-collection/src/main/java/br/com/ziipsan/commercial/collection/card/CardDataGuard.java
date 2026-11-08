package br.com.ziipsan.commercial.collection.card;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guarda de "nenhum dado de cartao" (ED-28, aceite: "nenhum dado de
 * cartao trafega pelo produto nem e gravado nele, provado por teste
 * sobre requisicoes, logs e banco").
 *
 * <p>O desenho da ED-28 e por CONSTRUCAO: nenhum tipo do pacote
 * {@code card} tem campo para numero de cartao, validade, codigo de
 * seguranca ou nome impresso — o cartao so existe na pagina da
 * credenciadora. Esta guarda e a SEGUNDA linha de defesa, para o caso
 * de um payload (nosso ou da credenciadora) carregar o que nao deve:
 *
 * <ul>
 *   <li><b>PAN</b>: sequencias de 13 a 19 digitos (com espacos, hifens
 *       ou PONTOS opcionais entre eles) que passam no digito de Luhn.
 *       Sequencias mais longas (ex.: identificadores numericos longos
 *       da credenciadora) e numeros que nao passam em Luhn (valores,
 *       datas, referencias) nao sao sinalizados. Escapes unicode
 *       ({@code \\uXXXX}) do texto sao decodificados ANTES da
 *       varredura, para um PAN escapado em JSON nao atravessar;</li>
 *   <li><b>campos proibidos</b>: nomes de campo de dado de cartao em
 *       JSON ou texto — chave com {@code :}/{@code =} ou nome seguido
 *       de valor em texto livre (ex.: {@code cvv 123}).</li>
 * </ul>
 *
 * <p><b>FONTE UNICA DO VOCABULARIO (correcao D1/D2 da revisao de
 * 08/11/2026)</b>: {@link #FORBIDDEN_NAME_TOKENS} e
 * {@link #isForbiddenFieldName(String)} sao O vocabulario proibido do
 * pacote, em PT e EN, na forma normalizada (minusculas, sem
 * separadores). A guarda o usa para montar os padroes de campo, e os
 * testes {@code NoCardDataArchitectureTest} (tipos/fontes) e
 * {@code CardMigrationConsistencyTest} (banco) consomem ESTA MESMA
 * lista — nao existem mais tres listas divergentes.
 *
 * <p>{@link #sanitizeForLog(String)} e a defesa dos logs: decodifica
 * escapes unicode, apaga o valor de qualquer campo proibido e qualquer
 * sequencia longa de digitos vira {@code [REDACTED]} antes de logar,
 * passe ou nao em Luhn — log nao e lugar de numero longo.
 */
public final class CardDataGuard {

    /** Sinal de que um payload carrega dado de cartao: nada se grava nem se baixa. */
    public static final class CardDataDetectedException extends RuntimeException {
        public CardDataDetectedException(String context) {
            super("Dado de cartao detectado em " + context
                    + ": o payload foi recusado inteiro, sem gravacao nem baixa (ED-28)");
        }
    }

    /**
     * Vocabulario proibido CANONICO (PT + EN), na forma normalizada de
     * {@link #normalizeFieldName(String)}: um nome de campo/coluna e
     * proibido se a sua forma normalizada CONTEM um destes tokens
     * (ou e exatamente um de {@link #FORBIDDEN_EXACT_NAMES}). Cobre
     * numero do cartao, codigo de seguranca, validade/expiracao, nome
     * do portador/impresso, trilhas e PIN de cartao.
     */
    public static final List<String> FORBIDDEN_NAME_TOKENS = List.of(
            "cardnumber", "numerocartao", "numerodocartao", "numcartao",
            "creditcard", "cartaocredito",
            "cvv", "cvc", "securitycode", "codigoseguranca", "codigodeseguranca",
            "validade", "expiry", "expiration",
            "cardholder", "holdername", "holder", "nomeimpresso", "titular",
            "track1", "track2", "cardpin");

    /** Nomes que so sao proibidos como nome INTEIRO (evita falso positivo em palavras comuns). */
    public static final List<String> FORBIDDEN_EXACT_NAMES = List.of("pan", "pin");

    /** Forma normalizada de um nome de campo/coluna: minusculas, so letras/digitos. */
    public static String normalizeFieldName(String rawName) {
        return rawName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * O teste de nome usado por TODAS as pernas da prova (guarda,
     * arquitetura e banco): verdadeiro se o nome — em qualquer caixa
     * ou separador ({@code numero_cartao}, {@code numeroCartao},
     * {@code NUMERO_CARTAO}...) — e nome de dado de cartao.
     */
    public static boolean isForbiddenFieldName(String rawName) {
        if (rawName == null) {
            return false;
        }
        String normalized = normalizeFieldName(rawName);
        if (FORBIDDEN_EXACT_NAMES.contains(normalized)) {
            return true;
        }
        for (String token : FORBIDDEN_NAME_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nomes de campo proibidos em TEXTO (payload/log), espelhando
     * {@link #FORBIDDEN_NAME_TOKENS}: variantes PT/EN com separador
     * opcional ou fronteira camelCase. O contexto exigido depois do
     * nome e {@code :}, {@code =} ou um valor em texto livre.
     */
    private static final String FIELD_NAME_REGEX =
            "(?:card_?number|numero_?(?:do_?)?cartao|num_?cartao|credit_?card|cartao_?(?:de_?)?credito"
                    + "|cvv2?|cvc2?|security_?code|codigo_?(?:de_?)?seguranca"
                    + "|validade(?:_?cartao)?|card_?expiry|expiry(?:_?date)?|expiration(?:_?date)?"
                    + "|card_?holder(?:_?name)?|holder_?name|holder|nome_?impresso|titular(?:_?(?:do_?)?cartao)?"
                    + "|track_?[12]|card_?pin|pin|pan)";

    private static final Pattern FORBIDDEN_FIELD_PATTERN = Pattern.compile(
            "(?i)\"?\\b" + FIELD_NAME_REGEX + "\\b\"?(?:\\s*[:=]|\\s+\\S)");

    /** Campo proibido + o seu valor, para a sanitizacao de log apagar o valor junto. */
    private static final Pattern FORBIDDEN_FIELD_VALUE_PATTERN = Pattern.compile(
            "(?i)(\"?\\b" + FIELD_NAME_REGEX + "\\b\"?\\s*(?:[:=]|\\s+))\"?[^\"\\s,}\\]]+");

    /** Sequencia maximal de digitos com espacos/hifens/PONTOS simples intercalados. */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d(?:[.\\- ]?\\d)*");

    /** Escape unicode de JSON ({@code \\uXXXX}), decodificado antes de qualquer varredura. */
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    private CardDataGuard() {
    }

    /** Decodifica escapes {@code \\uXXXX}; o restante do texto passa intacto. */
    static String decodeUnicodeEscapes(String text) {
        Matcher m = UNICODE_ESCAPE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            char c = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(out, Matcher.quoteReplacement(String.valueOf(c)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Verdadeiro se o texto (ja decodificado) carrega um PAN de 13–19 digitos valido em Luhn. */
    private static boolean containsPanDigits(String decodedText) {
        Matcher m = DIGIT_RUN.matcher(decodedText);
        while (m.find()) {
            String digits = m.group().replace(" ", "").replace("-", "").replace(".", "");
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
                return true;
            }
        }
        return false;
    }

    /** Verdadeiro se o texto carrega PAN (Luhn, em qualquer separador ou escapado) — sem olhar nomes de campo. */
    public static boolean containsPan(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsPanDigits(decodeUnicodeEscapes(text));
    }

    /** Verdadeiro se o texto carrega PAN (Luhn) ou algum campo proibido. */
    public static boolean containsCardData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String decoded = decodeUnicodeEscapes(text);
        if (FORBIDDEN_FIELD_PATTERN.matcher(decoded).find()) {
            return true;
        }
        return containsPanDigits(decoded);
    }

    /** Recusa o payload inteiro se houver dado de cartao. */
    public static void assertNoCardData(String text, String context) {
        if (containsCardData(text)) {
            throw new CardDataDetectedException(context);
        }
    }

    /**
     * Versao segura para log: decodifica escapes unicode, apaga o
     * valor de campos proibidos e toda sequencia de 13+ digitos vira
     * {@code [REDACTED]} (com ou sem Luhn); o restante passa intacto.
     */
    public static String sanitizeForLog(String text) {
        if (text == null) {
            return null;
        }
        String masked = FORBIDDEN_FIELD_VALUE_PATTERN.matcher(decodeUnicodeEscapes(text))
                .replaceAll("$1[REDACTED]");
        Matcher m = DIGIT_RUN.matcher(masked);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String digits = m.group().replace(" ", "").replace("-", "").replace(".", "");
            if (digits.length() >= 13) {
                m.appendReplacement(out, "[REDACTED]");
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Digito verificador de Luhn (ISO/IEC 7812-1). */
    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (d < 0 || d > 9) {
                return false;
            }
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return !digits.isEmpty() && sum % 10 == 0;
    }
}
