package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consistencia SQL x codigo da ED-95 (mesmo desenho da ED-24): o SQL
 * pendente declara tabelas/CHECKs com os mesmos valores dos enums e
 * as travas do aceite (decisao humana, recomendacao, backoffice).
 * Resolve os arquivos subindo a partir do diretorio de trabalho e,
 * como ultimo recurso, pelo caminho da entrega local desta VM.
 */
class ApparentLossSqlConsistencyTest {

    private static String sql(String fileName) throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            Path candidate = dir.resolve("src/main/resources/db/pendente/" + fileName);
            if (Files.exists(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }
        String entrega = System.getProperty("ed95.entrega");
        if (entrega != null) {
            Path p = Path.of(entrega, "shared/ziipsan-core/src/main/resources/db/pendente/" + fileName);
            if (Files.exists(p)) return Files.readString(p);
        }
        Path local = Path.of(System.getProperty("user.home"),
                "workspace/ziipsan-producao/ed-95/entrega/shared/ziipsan-core/src/main/resources/db/pendente/" + fileName);
        if (Files.exists(local)) return Files.readString(local);
        fail(fileName + " nao encontrado");
        return "";
    }

    @Test
    void tenantSqlDeclaresTablesEnumChecksAndAcceptanceGuards() throws Exception {
        String sql = sql("ED-95__apparent_loss.sql");
        for (String table : new String[]{"loss_property_registry", "loss_cross_findings",
                "loss_suspicions", "loss_inspection_outcomes", "loss_self_reports"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "tabela ausente: " + table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), "RLS ausente: " + table);
            assertTrue(sql.contains("uq_" + table + "_tenant_id UNIQUE (tenant_id, id)"),
                    "UNIQUE (tenant_id, id) ausente: " + table);
        }
        for (CrossMatch.FindingKind kind : CrossMatch.FindingKind.values())
            assertTrue(sql.contains("'" + kind.name() + "'"), "finding_kind sem " + kind);
        for (FraudRules.Rule rule : FraudRules.Rule.values())
            assertTrue(sql.contains("'" + rule.name() + "'"), "rule_code sem " + rule);
        for (PropertyRegistryRecord.PropertyUse use : PropertyRegistryRecord.PropertyUse.values())
            assertTrue(sql.contains("'" + use.name() + "'"), "declared_use sem " + use);
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, property_id) REFERENCES loss_property_registry (tenant_id, id)"),
                "FK composta achado -> cadastro ausente");
        assertTrue(sql.contains("evaluation_channel = 'BACKOFFICE'"), "trava do backoffice ausente (ADR-0034)");
        assertTrue(sql.contains("recommendation = 'HUMAN_INSPECTION_REVIEW'"), "trava da recomendacao ausente");
        assertTrue(sql.contains("automatic_effect = 'NONE'"), "trava de efeito automatico ausente");
        assertTrue(sql.contains("work_order_id IS NULL OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)"),
                "trava OS-so-com-decisao-humana ausente");
        assertTrue(sql.contains("legal_basis TEXT NOT NULL"), "base legal obrigatoria ausente (CAD-103)");
        assertTrue(sql.contains("sharing_purpose TEXT NOT NULL"), "finalidade obrigatoria ausente (CAD-103)");
        assertTrue(sql.contains("PENDENTE DE NUMERACAO"), "SQL deve declarar a pendencia de numeracao Flyway");
    }

    @Test
    void tenantSqlIndexesAreIdempotentForReapplicationCorrectionD2() throws Exception {
        String sql = sql("ED-95__apparent_loss.sql");
        long indexes = sql.lines().filter(l -> l.contains("CREATE INDEX")).count();
        long idempotent = sql.lines().filter(l -> l.contains("CREATE INDEX IF NOT EXISTS")).count();
        assertEquals(6, indexes, "o SQL tenant declara 6 indices");
        assertEquals(indexes, idempotent,
                "correcao D2: todo CREATE INDEX precisa de IF NOT EXISTS para a reaplicacao sair limpa");
    }

    @Test
    void parameterSqlDeclaresAllKeysWithoutFactoryDefaults() throws Exception {
        String sql = sql("ED-95__core_loss_parameters.sql");
        for (String key : new String[]{LossParameters.PROPERTY_REGISTRY_SYNC,
                LossParameters.UNREGISTERED_BUILDING_REVIEW_DAYS, LossParameters.FRAUD_RULES,
                LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED}) {
            assertTrue(sql.contains("'" + key + "'"), "parametro ausente no SQL: " + key);
        }
        assertFalse(sql.contains("'registry.property_registry_sync', 'TEXT', 'd', '"),
                "registry.property_registry_sync nao pode ter factory_default");
        assertTrue(sql.contains("factory_default NULL"), "SQL de parametros deve declarar a ausencia de fabrica");
    }
}
