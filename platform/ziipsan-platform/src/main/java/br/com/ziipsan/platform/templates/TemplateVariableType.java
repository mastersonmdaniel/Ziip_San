package br.com.ziipsan.platform.templates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Tipo de uma variável do catálogo (P2.30). A tipagem é o que permite a
 * validação do aceite: variável inexistente ou de tipo incompatível recusa
 * o modelo — e valor incompatível na geração recusa a geração, em vez de
 * imprimir lixo no documento.
 *
 * <p>Formatação na renderização (documentada para não surpreender o autor
 * do modelo): TEXT sai como está; NUMBER sai normalizado (sem zeros
 * desnecessários); MONEY sai com 2 casas decimais, sem símbolo — o corpo
 * do modelo escreve o "R$" onde ele pertence; DATE sai em ISO
 * ({@code aaaa-mm-dd}), o formato que o catálogo aceita na entrada; BOOLEAN
 * sai {@code sim}/{@code não}.
 */
public enum TemplateVariableType {

    TEXT,
    NUMBER,
    MONEY,
    DATE,
    BOOLEAN;

    /**
     * Valida e formata o valor para impressão. Lança
     * {@link IllegalArgumentException} com mensagem sem o valor (o valor
     * pode ser dado do titular e não vai para log nem para mensagem de erro
     * que o frontend exiba sem redação).
     */
    public String format(String rawValue, String variableName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Variável '" + variableName + "' sem valor.");
        }
        String value = rawValue.strip();
        try {
            return switch (this) {
                case TEXT -> value;
                case NUMBER -> new BigDecimal(value).stripTrailingZeros().toPlainString();
                case MONEY -> new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
                case DATE -> LocalDate.parse(value).toString();
                case BOOLEAN -> switch (value.toLowerCase()) {
                    case "true", "sim", "s", "yes" -> "sim";
                    case "false", "não", "nao", "n", "no" -> "não";
                    default -> throw new IllegalArgumentException(
                            "Variável '" + variableName + "' com valor incompatível com BOOLEAN.");
                };
            };
        } catch (IllegalArgumentException e) {
            // Reaproveita a mensagem específica de BOOLEAN; os demais erros de
            // parse viram a mensagem genérica de incompatibilidade, sem o valor.
            if (this == TemplateVariableType.BOOLEAN) {
                throw e;
            }
            throw new IllegalArgumentException(
                    "Variável '" + variableName + "' com valor incompatível com " + this + ".");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Variável '" + variableName + "' com valor incompatível com DATE.");
        }
    }
}
