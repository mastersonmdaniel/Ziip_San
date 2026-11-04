package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P1.40: o parser lê o modelo (aspas, CRLF, vírgula) e recusa o ilegível, sem adivinhar. */
class ImportCsvParserTest {

    @Test
    @DisplayName("campos com aspas, vírgula dentro de aspas e CRLF viram linhas com número")
    void quotedFieldsAndCrlf() {
        ImportCsvParser.ParsedCsv csv = ImportCsvParser.parse(
                "code,name,notes\r\n"
                        + "A1,\"Nome, com virgula\",\"nota \"\"citada\"\"\"\r\n"
                        + "A2,Simples,ok\r\n");
        assertThat(csv.header()).containsExactly("code", "name", "notes");
        assertThat(csv.rows()).hasSize(2);
        assertThat(csv.rows().get(0).number()).isEqualTo(1);
        assertThat(csv.rows().get(0).values()).containsEntry("name", "Nome, com virgula");
        assertThat(csv.rows().get(0).values()).containsEntry("notes", "nota \"citada\"");
        assertThat(csv.rows().get(1).values()).containsEntry("code", "A2");
    }

    @Test
    @DisplayName("linhas vazias no fim são ignoradas; valores são aparados")
    void trailingBlankLinesIgnoredAndValuesTrimmed() {
        ImportCsvParser.ParsedCsv csv = ImportCsvParser.parse("code , name\n  A1 ,  Nome  \n\n");
        assertThat(csv.rows()).hasSize(1);
        assertThat(csv.rows().get(0).values()).containsEntry("code", "A1").containsEntry("name", "Nome");
    }

    @Test
    @DisplayName("arquivo vazio, cabeçalho repetido e linha com campo a mais/menos são recusados")
    void malformedFilesRejected() {
        assertThatThrownBy(() -> ImportCsvParser.parse("  \n"))
                .isInstanceOf(ImportCsvParser.ParseException.class);
        assertThatThrownBy(() -> ImportCsvParser.parse("code,code\nA1,A2\n"))
                .isInstanceOf(ImportCsvParser.ParseException.class);
        assertThatThrownBy(() -> ImportCsvParser.parse("code,name\nA1\n"))
                .isInstanceOf(ImportCsvParser.ParseException.class);
        assertThatThrownBy(() -> ImportCsvParser.parse("code,name\nA1,Nome,extra\n"))
                .isInstanceOf(ImportCsvParser.ParseException.class);
        assertThatThrownBy(() -> ImportCsvParser.parse("code,name\nA1,\"sem fechar\n"))
                .isInstanceOf(ImportCsvParser.ParseException.class);
    }

    @Test
    @DisplayName("quebra de linha dentro de aspas não termina o registro")
    void newlineInsideQuotes() {
        ImportCsvParser.ParsedCsv csv = ImportCsvParser.parse("code,notes\nA1,\"linha1\nlinha2\"\n");
        assertThat(csv.rows()).hasSize(1);
        assertThat(csv.rows().get(0).values()).containsEntry("notes", "linha1\nlinha2");
    }

    @Test
    @DisplayName("modelo gerado do esquema: cabeçalho na ordem + linha de exemplo, com escape")
    void templateFromSchema() {
        ImportSchema schema = new CategoryImportSchema();
        String csv = ImportTemplate.csv(schema);
        assertThat(csv).startsWith("code,name,abbreviation,minimum_consumption,economies_soft_limit,"
                + "requires_special_permission,public_entity,active\r\n");
        assertThat(csv).contains("RESIDENCIAL,Residencial,RES,10,5,nao,nao,sim\r\n");
        // Reparse do próprio modelo: o exemplo tem de passar no parser.
        ImportCsvParser.ParsedCsv parsed = ImportCsvParser.parse(csv);
        assertThat(parsed.header()).containsExactly(schema.columns().stream()
                .map(ImportColumn::name).toArray(String[]::new));
        assertThat(parsed.rows()).hasSize(1);
    }
}
