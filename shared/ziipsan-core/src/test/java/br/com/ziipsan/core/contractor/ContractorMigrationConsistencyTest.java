package br.com.ziipsan.core.contractor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consistencia dominio x SQL pendente da ED-37 (o SQL nao e aplicado nesta VM). */
class ContractorMigrationConsistencyTest {

    static String sql() throws IOException {
        Path p = Path.of("src/main/resources/db/pendente/ED-37__contractors.sql");
        if (!Files.exists(p)) p = Path.of("shared/ziipsan-core/src/main/resources/db/pendente/ED-37__contractors.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("ed37.sql", ""));
        return Files.readString(p);
    }

    @Test void sqlExtendsCertSuppliersWithoutDuplicatingRegistry() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("REFERENCES cert_suppliers"), "perfil/contrato/medicao referenciam o cadastro da P3.31");
        assertFalse(sql.toUpperCase().contains("CREATE TABLE IF NOT EXISTS CERT_SUPPLIERS"), "cadastro da P3.31 nao e recriado");
        for (String table : new String[]{"contractor_profiles", "contractor_contracts", "contractor_penalty_clauses",
                "contractor_penalty_applications", "contractor_measurements", "contractor_measurement_items"})
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "tabela ausente: " + table);
    }

    @Test void sqlSeedsNoFactoryPenalty() throws IOException {
        String sql = sql().toUpperCase();
        assertFalse(sql.contains("INSERT INTO"), "nenhuma clausula/penalidade padrao pode ser semeada (catalogo vazio por desenho)");
    }

    @Test void sqlHasNoOutOfScopeColumns() throws IOException {
        // So o DDL conta: os comentarios do cabecalho DECLARAM a exclusao da
        // ED-62 citando os campos pelo nome — coluna de verdade e o que esta
        // fora das linhas de comentario.
        String ddl = sql().lines().filter(l -> !l.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b).toLowerCase();
        for (String forbidden : new String[]{"empenho", "pncp", "ordem_compra", "budget"})
            assertFalse(ddl.contains(forbidden), "coluna de integracao orcamentaria (ED-62) fora de escopo: " + forbidden);
        // Correcao D6: a trava de escopo tambem cobre os termos da ED-84
        // (obras/projetos) no DDL, nao so os da ED-62.
        for (String forbidden : new String[]{"obra", "projeto"})
            assertFalse(ddl.contains(forbidden), "coluna de obras/projetos (ED-84) fora de escopo: " + forbidden);
    }

    @Test void sqlSpecialtiesCheckRejectsEmptyArray() throws IOException {
        // Correcao D5: array_length('{}', 1) e NULL no PostgreSQL e CHECK com
        // NULL PASSA — array vazio entraria pelo banco. cardinality('{}') = 0,
        // entao o CHECK com cardinality barra o array vazio de verdade.
        String ddl = sql().lines().filter(l -> !l.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b).toLowerCase();
        assertTrue(ddl.contains("cardinality(specialties) > 0"),
                "CHECK de specialties deve usar cardinality(...) > 0 para barrar array vazio");
        assertFalse(ddl.contains("array_length(specialties"),
                "array_length(specialties, 1) > 0 nao barra array vazio no PostgreSQL (NULL passa no CHECK)");
    }

    @Test void sqlCommentDoesNotPromiseIndexThatFileDoesNotCreate() throws IOException {
        // Correcao D1: o cabecalho da medicao prometia "indice unico parcial
        // abaixo" que o arquivo nao cria. O comentario corrigido declara que
        // a trava de dupla medicao vive no dominio e que a de banco fica
        // para a integracao (nota final do arquivo).
        String sql = sql().toLowerCase();
        assertFalse(sql.contains("indice unico parcial abaixo"),
                "o SQL nao pode prometer um indice unico parcial que ele nao cria");
        assertTrue(sql.contains("order_already_measured"),
                "o SQL deve apontar a trava real de dupla medicao, que e a do dominio");
    }

    @Test void sqlIsPendingWithoutInventedFlywayNumber() throws IOException {
        assertTrue(sql().contains("PENDENTE DE NUMERACAO"), "SQL fica em db/pendente sem numero inventado");
    }
}
