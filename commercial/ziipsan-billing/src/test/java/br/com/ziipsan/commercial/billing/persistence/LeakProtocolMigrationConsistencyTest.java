package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guardas textuais do SQL pendente da ED-23: sem numero Flyway, anexo a
 * P3.28 (leak_reviews), nenhuma tabela de desconto/credito propria e
 * nenhum valor de parametro semeado.
 */
class LeakProtocolMigrationConsistencyTest {

    private static String sql() throws IOException {
        String relative = "commercial/ziipsan-billing/src/main/resources/db/pendente/ED-23__leak_review_protocol.sql";
        List<Path> candidates = List.of(
                Path.of(relative),
                Path.of(System.getProperty("user.dir"), "workspace/ziipsan-producao/ed-23/entrega", relative),
                Path.of(System.getProperty("user.home"), "workspace/ziipsan-producao/ed-23/entrega", relative));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return Files.readString(candidate);
            }
        }
        fail("SQL pendente da ED-23 nao localizado nos caminhos candidatos");
        return "";
    }

    @Test
    void sqlIsPendingWithoutFlywayNumber() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("SEM NUMERO Flyway"), "cabecalho deve declarar a pendencia de numeracao");
        assertTrue(!sql.matches("(?s).*V\\d+_\\d+__.*"), "nenhum numero de migration pode ser inventado");
    }

    @Test
    void protocolAttachesToP328AndCreatesNoDiscountOrCreditTable() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("REFERENCES leak_reviews"), "protocolo se anexa ao pedido da P3.28");
        assertTrue(sql.contains("CREATE TABLE leak_review_protocols"));
        assertTrue(sql.contains("CREATE TABLE leak_review_inspections"));
        assertTrue(sql.contains("CREATE TABLE leak_review_reports"));
        assertTrue(!sql.contains("CREATE TABLE leak_discount"), "desconto e da P3.28");
        assertTrue(!sql.contains("CREATE TABLE bill_credits"), "credito/versao de conta e da P2.20/P2.5");
    }

    @Test
    void inspectionAndReportChecksMirrorJavaGuards() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("NOT leak_found OR leak_location IS NOT NULL"));
        assertTrue(sql.contains("conclusion <> 'INCONCLUSIVE' OR inconclusive_reason IS NOT NULL"));
    }

    @Test
    void noParameterValueIsSeeded() throws IOException {
        String sql = sql();
        assertTrue(!sql.contains("INSERT INTO"), "nenhum valor semeado (parametro, desconto, prazo)");
        assertTrue(sql.contains("[A DEFINIR]"), "prazo de fase marcado como a definir, nao inventado");
    }
}
