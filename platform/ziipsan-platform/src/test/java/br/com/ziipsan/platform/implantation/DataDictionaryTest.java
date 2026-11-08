package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataDictionaryTest {

    private static DataDictionary.ColumnMeta col(String table, String column, int pos, String cls) {
        return new DataDictionary.ColumnMeta(table, column, pos, "character varying", true,
                null, null, cls);
    }

    @Test
    @DisplayName("CSV determinístico: cabeçalho fixo e linhas ordenadas por tabela e posição")
    void deterministicCsv() {
        String csv = DataDictionary.toCsv(List.of(
                col("bills", "id", 2, "ID"),
                col("bills", "tenant_id", 1, "ID"),
                col("bills", "amount", 3, "FIN")));
        String[] lines = csv.split("\n");
        assertThat(lines[0]).isEqualTo(DataDictionary.HEADER);
        assertThat(lines[1]).startsWith("bills;tenant_id;1;");
        assertThat(lines[2]).startsWith("bills;id;2;");
        assertThat(lines[3]).startsWith("bills;amount;3;");
        // Mesma entrada, mesma saída (byte a byte).
        assertThat(DataDictionary.toCsv(List.of(
                col("bills", "amount", 3, "FIN"),
                col("bills", "id", 2, "ID"),
                col("bills", "tenant_id", 1, "ID")))).isEqualTo(csv);
    }

    @Test
    @DisplayName("comentário com ';' sai escapado entre aspas, sem quebrar a linha")
    void escaping() {
        DataDictionary.ColumnMeta c = new DataDictionary.ColumnMeta("t", "c", 1, "text", false,
                null, "nota; com separador", "CT");
        String csv = DataDictionary.toCsv(List.of(c));
        assertThat(csv).contains("\"nota; com separador\"");
        assertThat(csv.split("\n")).hasSize(2);
    }

    @Test
    @DisplayName("coluna sem classificação é listada para a régua da P0.11, não maquiada")
    void unclassifiedListed() {
        List<DataDictionary.ColumnMeta> cols = List.of(
                col("bills", "id", 1, "ID"), col("bills", "notes", 2, null));
        assertThat(DataDictionary.unclassified(cols))
                .extracting(DataDictionary.ColumnMeta::columnName).containsExactly("notes");
        assertThat(DataDictionary.toCsv(cols).split("\n")[2]).endsWith(";");
    }
}
