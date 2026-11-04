package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyReadersTest {

    @Test
    @DisplayName("CSV com ';', aspas com delimitador dentro e linhas em branco")
    void csvReader() {
        String csv = """
                matricula;setor;nome
                1001;10;"Silva; Fulano"

                1002;20;Outro
                """;
        List<LegacySourceReader.LegacyRow> rows = new CsvLegacyReader(csv, ';').read();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("matricula")).isEqualTo("1001");
        assertThat(rows.get(0).get("nome")).isEqualTo("Silva; Fulano");
        assertThat(rows.get(0).get("SETOR")).isEqualTo("10");
        assertThat(rows.get(1).rowNo()).isEqualTo(4);
    }

    @Test
    @DisplayName("CSV com mais campos que o cabeçalho é erro (delimitador errado)")
    void csvExtraFieldsFails() {
        assertThatThrownBy(() -> new CsvLegacyReader("a;b\n1;2;3\n", ';').read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mais campos");
    }

    @Test
    @DisplayName("CSV com menos campos completa com null")
    void csvMissingFieldsBecomeNull() {
        List<LegacySourceReader.LegacyRow> rows = new CsvLegacyReader("a;b;c\n1;2\n", ';').read();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("c")).isNull();
    }

    @Test
    @DisplayName("dump SQL: multi-tupla, aspas escapadas, NULL e outras sentenças ignoradas")
    void sqlDumpReader() {
        String dump = """
                -- comentário
                CREATE TABLE cadastro (matricula TEXT, nome TEXT);
                INSERT INTO cadastro (matricula, nome, saldo) VALUES
                  ('1001', 'Fulano ''O'' Silva', NULL),
                  ('1002', 'Outro', 12345);
                INSERT INTO outra (x) VALUES (1);
                """;
        List<LegacySourceReader.LegacyRow> rows = new SqlDumpLegacyReader(dump, "cadastro").read();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("nome")).isEqualTo("Fulano 'O' Silva");
        assertThat(rows.get(0).get("saldo")).isNull();
        assertThat(rows.get(1).get("saldo")).isEqualTo("12345");
    }

    @Test
    @DisplayName("dump SQL sem filtro de tabela lê todos os INSERTs")
    void sqlDumpReaderWithoutTableFilter() {
        String dump = "INSERT INTO a (x) VALUES (1); INSERT INTO b (y) VALUES (2);";
        assertThat(new SqlDumpLegacyReader(dump, null).read()).hasSize(2);
    }

    @Test
    @DisplayName("dump SQL com expressão é recusado, não avaliado")
    void sqlDumpExpressionFails() {
        assertThatThrownBy(() -> new SqlDumpLegacyReader(
                "INSERT INTO a (x) VALUES (now());", "a").read())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("leitor em memória numera as linhas a partir de 1")
    void inMemoryReader() {
        List<LegacySourceReader.LegacyRow> rows = new InMemoryLegacyReader(List.of(
                java.util.Map.of("a", "1"), java.util.Map.of("a", "2"))).read();
        assertThat(rows).extracting(LegacySourceReader.LegacyRow::rowNo).containsExactly(1, 2);
    }
}
