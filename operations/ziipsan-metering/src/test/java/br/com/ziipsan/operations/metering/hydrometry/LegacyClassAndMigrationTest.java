package br.com.ziipsan.operations.metering.hydrometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

/** Classe antiga A–D: só parque migrado; e o SQL da migração preserva a classe (ED-14). */
class LegacyClassAndMigrationTest {
    @Test @DisplayName("cadastro novo recusa classe antiga; parque migrado aceita a leitura")
    void legacyRules() {
        assertThatThrownBy(() -> LegacyMetrologicalClassRule.requireNewRegistrationFreeOfLegacyClass("B"))
                .isInstanceOf(IllegalArgumentException.class);
        LegacyMetrologicalClassRule.requireNewRegistrationFreeOfLegacyClass(null);
        LegacyMetrologicalClassRule.requireLegacyClassOnlyOnMigratedPark("B", true);
        assertThatThrownBy(() -> LegacyMetrologicalClassRule.requireLegacyClassOnlyOnMigratedPark("B", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("SQL move A–D para legacy_metrological_class, marca migrated_park e trava o uso fora dele")
    void migrationSql() throws Exception {
        String sql = Files.readString(sqlPath());
        assertThat(sql).contains("legacy_metrological_class = metrological_class, migrated_park = TRUE");
        assertThat(sql).contains("metrological_class IN ('A', 'B', 'C', 'D')");
        assertThat(sql).contains("ck_meters_legacy_only_migrated");
        // lacre num lugar só: nenhuma tabela nova da ED-14 tem coluna de lacre
        assertThat(sql.toLowerCase()).doesNotContain("seal_number");
        assertThat(sql).contains("lugar unico");
    }
    static Path sqlPath() {
        Path p = Path.of("operations/ziipsan-metering/src/main/resources/db/pendente/ED-14__metering_hydrometry.sql");
        if (Files.exists(p)) return p;
        return Path.of("src/main/resources/db/pendente/ED-14__metering_hydrometry.sql");
    }
}
