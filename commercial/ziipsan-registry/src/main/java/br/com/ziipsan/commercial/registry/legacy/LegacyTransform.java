package br.com.ziipsan.commercial.registry.legacy;

import br.com.ziipsan.core.document.DocumentNumber;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Transformações do vocabulário do mapeamento (P1.2).
 *
 * <p>Regras que valem para todas: entrada nula ou só espaços vira "ausente"
 * (null) — a obrigatoriedade é decidida pelo mapeamento, não aqui; nenhuma
 * mensagem de erro carrega o valor bruto (um valor bruto numa mensagem de
 * erro vira dado pessoal em log e em staging).
 *
 * <p>Documento (P1.39): {@link #document} normaliza (maiúsculas, sem
 * pontuação), classifica (CPF/CNPJ/UNKNOWN) e valida o dígito verificador
 * pelo {@link DocumentNumber} — inclusive o CNPJ alfanumérico (12 posições
 * alfanuméricas + 2 dígitos). O valor normalizado NÃO sai daqui: o
 * resultado carrega só a espécie, a máscara de exibição e a validade. Quem
 * grava nunca vê o documento, então o staging nunca o persiste.
 */
public final class LegacyTransform {

    private LegacyTransform() {
    }

    /** Resultado da transformação de documento: espécie, máscara e validade. Nunca o número. */
    public record DocumentValue(String kind, String masked, boolean valid) {
    }

    /** Aplica uma transformação a um valor bruto. Devolve null quando ausente. */
    public static Object apply(LegacyMapping.Transform transform, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        return switch (transform.name()) {
            case "NONE" -> raw;
            case "TRIM" -> value;
            case "UPPER" -> value.toUpperCase(java.util.Locale.ROOT);
            case "INTEGER" -> parseInteger(value);
            case "DECIMAL_CENTS" -> parseDecimalCents(value);
            case "DATE" -> parseDate(value, transform.argument());
            case "DOCUMENT" -> document(value);
            default -> throw new IllegalArgumentException("Transformação desconhecida: " + transform.name());
        };
    }

    static Long parseInteger(String value) {
        try {
            return Long.parseLong(value.replace(" ", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inteiro inválido.");
        }
    }

    /**
     * Dinheiro em centavos, exato: aceita "1234.56" e "1.234,56" (padrão
     * brasileiro, ponto de milhar e vírgula decimal). Mais de 2 casas
     * decimais é erro, não arredondamento — arredondar saldo de legado
     * quebraria a conciliação por centavos.
     */
    static Long parseDecimalCents(String value) {
        String normalized = value.replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        }
        try {
            BigDecimal decimal = new BigDecimal(normalized);
            return decimal.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Valor monetário inválido ou com mais de 2 casas decimais.");
        }
    }

    static LocalDate parseDate(String value, String pattern) {
        String format = pattern == null || pattern.isBlank() ? "yyyy-MM-dd" : pattern;
        // STRICT + 'y' (ano da era) exige era e rejeita até "05/03/2020":
        // 'u' (ano proléptico) não exige, e STRICT continua recusando
        // datas impossíveis (31/02 segue inválido, como o teste exige).
        String strictFormat = format.replace("yyyy", "uuuu");
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(strictFormat)
                    .withResolverStyle(ResolverStyle.STRICT);
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Data inválida para o formato " + format + ".");
        }
    }

    static DocumentValue document(String value) {
        String normalized = DocumentNumber.normalize(value);
        if (DocumentNumber.isValidCpf(normalized)) {
            return new DocumentValue("CPF", DocumentNumber.mask(normalized), true);
        }
        if (DocumentNumber.isValidCnpj(normalized)) {
            return new DocumentValue("CNPJ", DocumentNumber.mask(normalized), true);
        }
        String kind = normalized.length() == 11 ? "CPF"
                : normalized.length() == 14 ? "CNPJ" : "UNKNOWN";
        return new DocumentValue(kind, DocumentNumber.mask(normalized), false);
    }
}
