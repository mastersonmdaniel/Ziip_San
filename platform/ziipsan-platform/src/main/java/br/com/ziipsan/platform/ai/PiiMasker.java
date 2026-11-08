package br.com.ziipsan.platform.ai;

import java.util.regex.Pattern;

/** Mascaramento (SG-700, mascaramento inteligente; ADR-0042): texto enviado ao modelo SEM CPF, CNPJ,
 * telefone, CEP, e-mail, nome nem endereco desnecessarios.
 * CORRECAO ED-43 (M1 da revisao cruzada): os padroes aceitam separadores variaveis (ponto, hifen,
 * barra e ESPACOS, ate 3 entre grupos de digitos), de modo que grafias como "123 456 789 09" ou
 * "48 99999 1234" sao mascaradas como as formas formatadas — a grafia nao contorna a mascara.
 * O detector fail-closed (containsStructuredPii) cobre TODOS os padroes da mascara (CPF, CNPJ,
 * e-mail, telefone com/sem DDD e CEP, em qualquer grafia) e, como segunda camada, varre sequencias
 * de digitos+separadores cujo total de digitos seja 11 (CPF/telefone com DDD) ou 14 (CNPJ): grafia
 * exotica que a mascara nao reconheca NAO passa pelo detector — quem chama transborda (fail-closed),
 * nunca envia o dado cru ao modelo. Simetria entrada/saida: o mesmo detector vigia a resposta. */
public final class PiiMasker {
    private static final String SEP = "[\\s.\\-/]{0,3}";
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}" + SEP + "\\d{3}" + SEP + "\\d{3}" + SEP + "\\d{2}\\b");
    private static final Pattern CNPJ = Pattern.compile("\\b\\d{2}" + SEP + "\\d{3}" + SEP + "\\d{3}" + SEP + "\\d{4}" + SEP + "\\d{2}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\(?\\d{2}\\)?[\\s.\\-]{0,3}9?\\d{4}[\\s.\\-]{0,3}\\d{4}\\b");
    private static final Pattern PHONE_LOCAL = Pattern.compile("\\b9\\d{4}[\\s.\\-]{0,3}\\d{4}\\b");
    private static final Pattern CEP = Pattern.compile("\\b\\d{5}[\\s\\-]{0,3}\\d{3}\\b");
    private static final Pattern DIGIT_RUN = Pattern.compile("[0-9][0-9\\s.\\-/()]*[0-9]");
    private PiiMasker() {}
    public static String mask(String text, java.util.Collection<String> knownSensitiveValues) {
        if (text == null) return "";
        String out = text;
        if (knownSensitiveValues != null) for (String v : knownSensitiveValues)
            if (v != null && v.length() >= 3) out = out.replace(v, "[REMOVIDO]");
        out = CNPJ.matcher(out).replaceAll("[CNPJ]");
        out = CPF.matcher(out).replaceAll("[CPF]");
        out = EMAIL.matcher(out).replaceAll("[EMAIL]");
        out = CEP.matcher(out).replaceAll("[CEP]");
        out = PHONE.matcher(out).replaceAll("[TELEFONE]");
        out = PHONE_LOCAL.matcher(out).replaceAll("[TELEFONE]");
        return out;
    }
    public static boolean containsStructuredPii(String text) {
        if (text == null) return false;
        if (CPF.matcher(text).find() || CNPJ.matcher(text).find() || EMAIL.matcher(text).find()
            || PHONE.matcher(text).find() || PHONE_LOCAL.matcher(text).find() || CEP.matcher(text).find())
            return true;
        return digitRunHasPiiLength(text);
    }
    /** Segunda camada do detector: sequencia continua de digitos e separadores com 11 ou 14 digitos
     * no total e tratada como PII estruturada, qualquer que seja a grafia dos separadores. */
    private static boolean digitRunHasPiiLength(String text) {
        var m = DIGIT_RUN.matcher(text);
        while (m.find()) {
            long digits = m.group().chars().filter(Character::isDigit).count();
            if (digits == 11 || digits == 14) return true;
        }
        return false;
    }
    /** Valor conhecido (nome/endereco da borda) presente no texto — usado na simetria de saida (M2). */
    public static boolean containsKnownValue(String text, java.util.Collection<String> knownSensitiveValues) {
        if (text == null || knownSensitiveValues == null) return false;
        for (String v : knownSensitiveValues) if (v != null && v.length() >= 3 && text.contains(v)) return true;
        return false;
    }
}
