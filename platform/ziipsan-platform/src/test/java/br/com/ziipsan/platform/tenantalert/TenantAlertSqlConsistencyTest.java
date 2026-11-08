package br.com.ziipsan.platform.tenantalert;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O SQL pendente da ED-07 e o código contam a mesma história: tabelas,
 * valores de kind e índices de deduplicação citados no Java existem no SQL.
 * O arquivo é localizado a partir do diretório de trabalho subindo até a
 * raiz do pacote de entrega (mesmo padrão dos testes de consistência das
 * vizinhas); se a integração mover o SQL para o número definitivo, este
 * teste é atualizado para o novo caminho — nunca afrouxado.
 */
class TenantAlertSqlConsistencyTest {

    private static Path findSql() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            Path candidate = dir.resolve("entrega/db/pendente/V-pendente__ed07_tenant_alerts.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path inModule = dir.resolve("db/pendente/V-pendente__ed07_tenant_alerts.sql");
            if (Files.exists(inModule)) {
                return inModule;
            }
            dir = dir.getParent();
        }
        return null;
    }

    @Test
    void pendingSqlDeclaresTablesKindsAndDeduplication() throws IOException {
        Path sql = findSql();
        assertThat(sql).as("SQL pendente da ED-07 localizado").isNotNull();
        String text = Files.readString(sql);
        assertThat(text).contains("tenant_alert_rules", "tenant_alerts", "tenant_alert_webhook_states");
        assertThat(text).contains("'CATALOG_EVENT'", "'BATCH_FAILURE'");
        assertThat(text).contains("uq_tenant_alerts_source", "uq_tenant_alerts_batch_failure",
                "uq_tenant_alert_rules_active_choice");
        assertThat(text).contains("register_tenant_table('tenant_alert_rules')");
        // Sem número Flyway inventado: o próprio cabeçalho declara a pendência.
        assertThat(text).contains("SEM NÚMERO");
    }
}
