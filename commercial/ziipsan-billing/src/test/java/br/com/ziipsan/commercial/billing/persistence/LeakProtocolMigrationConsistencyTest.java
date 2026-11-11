package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardas textuais do SQL pendente da ED-23: sem numero Flyway, anexo a
 * P3.28 (leak_reviews), nenhuma tabela de desconto/credito propria e
 * nenhum valor de parametro semeado.
 */
class LeakProtocolMigrationConsistencyTest {

    private static String sql() throws IOException {
        // Cura (lote correcao-cura-billing-metering, P-R19A-BILL-01): o SQL e lido
        // como RECURSO do proprio modulo (/db/pendente/... no classpath), o mesmo
        // padrao do Ed22MigrationConsistencyTest. Os caminhos candidatos antigos
        // resolviam a partir da raiz do repo ou de uma pasta de entrega externa e
        // nunca resolviam com o cwd do surefire (directorio do modulo): os 4
        // testes falhavam sem ler o SQL. Recurso ausente continua a falhar.
        try (InputStream in = LeakProtocolMigrationConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-23__leak_review_protocol.sql")) {
            assertTrue(in != null, "SQL pendente da ED-23 no classpath do modulo");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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
