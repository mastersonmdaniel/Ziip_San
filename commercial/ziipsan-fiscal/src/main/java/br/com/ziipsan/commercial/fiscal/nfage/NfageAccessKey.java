package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.core.document.DocumentNumber;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Chave de acesso da NFAg-e, modelo 75: 44 posições numéricas no leiaute consagrado dos
 * documentos fiscais eletrônicos — cUF (2) + AAMM (4) + CNPJ do emitente (14) + modelo
 * (2, sempre "75") + série (3) + número (9) + tipo de emissão (1) + código numérico (8)
 * + dígito verificador (1, módulo 11, pesos 2..9 da direita para a esquerda).
 *
 * <p>Classe pura, sem relógio e sem leitura de dados: quem chama informa cada campo
 * (série e número vêm de {@code fiscal_series}, V4_5/DAD-18 — numeração sem lacuna é do
 * dicionário, não desta classe).
 *
 * <p><b>Limite declarado (condicional ao MOC definitivo):</b> a chave é numérica, logo o
 * CNPJ do emitente que entra nela precisa ser numérico de 14 dígitos. O CNPJ
 * alfanumérico (P1.39/RAS-03) é validado normalmente como documento do emitente e do
 * destinatário no serviço de emissão, mas <b>não</b> pode compor esta chave enquanto o
 * MOC definitivo do modelo 75 não disser como (ou se) ele entra na chave; tentar montar
 * a chave com CNPJ alfanumérico é erro explícito, nunca uma conversão silenciosa.
 */
public final class NfageAccessKey {

    public static final String MODEL = "75";
    public static final int LENGTH = 44;

    /** Componentes de uma chave válida, na ordem do leiaute. */
    public record Parsed(int ufCode, YearMonth competence, String issuerCnpj, int series,
                         long number, int emissionType, String numericCode, int checkDigit) {
    }

    private NfageAccessKey() {
    }

    /**
     * Monta a chave de 44 posições com o DV calculado.
     *
     * @param ufCode        código da UF do emitente (2 dígitos)
     * @param competence    competência AAMM da emissão
     * @param issuerCnpj    CNPJ numérico do emitente, válido pela P1.39
     * @param series        série fiscal (0..999, sai com 3 dígitos)
     * @param number        número do documento (1..999999999, sai com 9 dígitos)
     * @param emissionType  tipo de emissão (1 dígito; contingência usa o seu próprio tipo)
     * @param numericCode   código numérico de 8 dígitos (informado por quem emite)
     * @throws IllegalArgumentException se qualquer campo estiver fora do leiaute —
     *                                  ausência ou excesso de dado é erro, nunca ajuste silencioso
     */
    public static String build(int ufCode, YearMonth competence, String issuerCnpj,
                               int series, long number, int emissionType, String numericCode) {
        if (ufCode < 10 || ufCode > 99) {
            throw new IllegalArgumentException("cUF da chave exige 2 dígitos");
        }
        Objects.requireNonNull(competence, "Competência (AAMM) da chave é obrigatória");
        String cnpj = DocumentNumber.normalize(issuerCnpj);
        if (cnpj.length() != 14 || !cnpj.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException(
                    "A chave de acesso do modelo 75 exige CNPJ do emitente numérico de 14 dígitos; "
                            + "CNPJ alfanumérico é condicional ao MOC definitivo (ver javadoc da classe)");
        }
        if (!DocumentNumber.isValidCnpj(cnpj)) {
            throw new IllegalArgumentException("CNPJ do emitente inválido (validação P1.39)");
        }
        if (series < 0 || series > 999) {
            throw new IllegalArgumentException("Série da chave fora do leiaute (3 dígitos)");
        }
        if (number < 1 || number > 999_999_999L) {
            throw new IllegalArgumentException("Número da chave fora do leiaute (9 dígitos, a partir de 1)");
        }
        if (emissionType < 0 || emissionType > 9) {
            throw new IllegalArgumentException("Tipo de emissão da chave exige 1 dígito");
        }
        if (numericCode == null || !numericCode.matches("\\d{8}")) {
            throw new IllegalArgumentException("Código numérico da chave exige exatamente 8 dígitos");
        }
        String base = String.format("%02d%02d%02d%s%s%03d%09d%d%s",
                ufCode, competence.getYear() % 100, competence.getMonthValue(),
                cnpj, MODEL, series, number, emissionType, numericCode);
        return base + checkDigit(base);
    }

    /** DV módulo 11 das 43 primeiras posições: pesos 2..9, da direita para a esquerda. */
    public static int checkDigit(String first43) {
        if (first43 == null || first43.length() != 43 || !first43.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("O DV é calculado sobre exatamente 43 dígitos");
        }
        int sum = 0;
        int weight = 2;
        for (int i = first43.length() - 1; i >= 0; i--) {
            sum += (first43.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    /** Verdadeiro somente para chave de 44 dígitos, modelo 75 e DV correto. */
    public static boolean isValid(String key) {
        if (key == null || key.length() != LENGTH || !key.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        if (!MODEL.equals(key.substring(20, 22))) {
            return false;
        }
        return key.charAt(43) - '0' == checkDigit(key.substring(0, 43));
    }

    /**
     * Decompõe uma chave válida nos seus campos.
     *
     * @throws IllegalArgumentException se a chave não for válida (tamanho, modelo ou DV)
     */
    public static Parsed parse(String key) {
        if (!isValid(key)) {
            throw new IllegalArgumentException("Chave de acesso da NFAg-e inválida (44 dígitos, modelo 75, DV módulo 11)");
        }
        int year = 2000 + Integer.parseInt(key.substring(2, 4));
        int month = Integer.parseInt(key.substring(4, 6));
        return new Parsed(
                Integer.parseInt(key.substring(0, 2)),
                YearMonth.of(year, month),
                key.substring(6, 20),
                Integer.parseInt(key.substring(22, 25)),
                Long.parseLong(key.substring(25, 34)),
                key.charAt(34) - '0',
                key.substring(35, 43),
                key.charAt(43) - '0');
    }
}
