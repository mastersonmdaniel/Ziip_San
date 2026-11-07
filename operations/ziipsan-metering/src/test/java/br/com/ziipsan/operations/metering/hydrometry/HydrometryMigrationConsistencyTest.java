package br.com.ziipsan.operations.metering.hydrometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Consistência do SQL pendente da ED-14 com o aceite (precedente: testes de migração do projeto). */
class HydrometryMigrationConsistencyTest {
    @Test @DisplayName("SQL traz catálogos, travas de armazenagem/baixa, OS na movimentação e verificação calculada")
    void sqlCoversAcceptance() throws Exception {
        String sql = Files.readString(LegacyClassAndMigrationTest.sqlPath());
        for (String needle : new String[]{"meter_brands", "meter_models", "accuracy_class IN (1, 2)",
                "ratio_r BETWEEN 10 AND 800", "meter_lots", "service_connection_id", "install_service_order_id",
                "remove_service_order_id", "removal_destination IN ('AVAILABLE', 'SCRAP')",
                "ck_meters_storage_required", "ck_meters_writeoff_reason", "ck_meters_old_class_only_migrated",
                "fk_movement_storage", "fk_movement_installation", "remove_service_order_id IS NOT NULL", "meter_movements",
                "service_order_id IS NOT NULL", "meter_verifications", "next_verification_on",
                "EXTERNAL_LAB", "meter_bench_test_results", "register_tenant_table('meter_models')"}) {
            assertThat(sql).as(needle).contains(needle);
        }
        assertThat(sql).contains("657/2025 esta SUSPENSA");
    }
    @Test @DisplayName("catálogo de parâmetros fixa teto 7 (só reduz), volume por classe e amostragem")
    void parameterCatalog() throws Exception {
        Path p = Path.of("operations/ziipsan-metering/src/main/resources/db/pendente/ED-14__core_parameter_catalog.sql");
        if (!Files.exists(p)) p = Path.of("src/main/resources/db/pendente/ED-14__core_parameter_catalog.sql");
        String sql = Files.readString(p);
        assertThat(sql).contains("meter.verification_interval_years").contains("MAX_VALUE', '7'")
                .contains("meter.replace_by_volume_m3_by_class").contains("meter.sample_plan_pct");
    }
}
