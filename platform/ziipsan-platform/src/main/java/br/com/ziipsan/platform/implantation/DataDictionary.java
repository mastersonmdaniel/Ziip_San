package br.com.ziipsan.platform.implantation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dicionário de dados gerado do schema (ED-09): no CI, a cada build.
 *
 * <p>O aceite admite XLSX, CSV ou PDF; esta entrega gera <b>CSV</b> (o formato
 * legível por máquina e por pessoa, sem dependência de biblioteca de planilha
 * no módulo da plataforma). Quem chama lê os metadados de
 * {@code information_schema.columns} (e a classificação de dado da P0.11,
 * {@code data_classification}) e entrega a lista; esta classe só ordena,
 * escapa e monta — não lê banco e não inventa coluna: coluna sem metadado
 * não aparece, e coluna sem classificação sai com a classificação vazia para
 * o teste de CI da P0.11 a recusar, não maquiada aqui.
 *
 * <p>Determinismo: a saída é ordenada por (tabela, posição ordinal) e usa
 * sempre {@code \n}: o mesmo schema gera o mesmo arquivo, byte a byte, para
 * o diff do CI ser limpo.
 */
public final class DataDictionary {

    private DataDictionary() {
    }

    /** Coluna do schema, como lida de information_schema + data_classification. */
    public record ColumnMeta(String tableName, String columnName, int ordinalPosition,
                             String dataType, boolean nullable, String defaultValue,
                             String comment, String dataClass) {
        public ColumnMeta {
            if (tableName == null || tableName.isBlank()
                    || columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("Tabela e coluna são obrigatórias no dicionário.");
            }
            if (dataType == null || dataType.isBlank()) {
                throw new IllegalArgumentException(
                        "O tipo da coluna é obrigatório: " + tableName + "." + columnName);
            }
        }
    }

    public static final String HEADER =
            "tabela;coluna;posicao;tipo;aceita_nulo;valor_padrao;comentario;classe_de_dado";

    /** Monta o CSV completo (cabeçalho + uma linha por coluna). */
    public static String toCsv(List<ColumnMeta> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("A lista de colunas é obrigatória.");
        }
        List<ColumnMeta> sorted = new ArrayList<>(columns);
        sorted.sort(Comparator.comparing(ColumnMeta::tableName)
                .thenComparingInt(ColumnMeta::ordinalPosition)
                .thenComparing(ColumnMeta::columnName));
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (ColumnMeta c : sorted) {
            out.append(escape(c.tableName())).append(';')
                    .append(escape(c.columnName())).append(';')
                    .append(c.ordinalPosition()).append(';')
                    .append(escape(c.dataType())).append(';')
                    .append(c.nullable() ? "SIM" : "NAO").append(';')
                    .append(escape(c.defaultValue() == null ? "" : c.defaultValue())).append(';')
                    .append(escape(c.comment() == null ? "" : c.comment())).append(';')
                    .append(escape(c.dataClass() == null ? "" : c.dataClass()))
                    .append('\n');
        }
        return out.toString();
    }

    /** Colunas sem classificação de dado (a P0.11 recusa coluna nova sem classe). */
    public static List<ColumnMeta> unclassified(List<ColumnMeta> columns) {
        if (columns == null) {
            throw new IllegalArgumentException("A lista de colunas é obrigatória.");
        }
        List<ColumnMeta> out = new ArrayList<>();
        for (ColumnMeta c : columns) {
            if (c.dataClass() == null || c.dataClass().isBlank()) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** Escape CSV com ';': envolve em aspas quando há ';', '"' ou quebra de linha. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")
                || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
