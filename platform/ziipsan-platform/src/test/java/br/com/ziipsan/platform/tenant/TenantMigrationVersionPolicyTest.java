package br.com.ziipsan.platform.tenant;

import br.com.ziipsan.platform.tenant.TenantMigrationVersionPolicy.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.12: a política da sequência global — parse e ordenação de versões, e a
 * recusa de migration nova menor ou igual à maior publicada, abaixo de V100,
 * ilegível ou duplicada. A maior publicada usada aqui é a do corte real
 * (27.1, a V27_1 de asset, conferida no repo), não um número de exemplo.
 */
class TenantMigrationVersionPolicyTest {

    private static final Set<String> PUBLISHED = Set.of(
            "V1_8__org_units_and_scopes.sql",
            "V6_5__payment_refunds_and_unidentified_queue.sql",
            "V15_1__reporting_core_and_views.sql",
            "V21_3__certification_governance.sql",
            "V27_1__asset_registry.sql");
    private static final MigrationVersion MAX_PUBLISHED = TenantMigrationVersionPolicy.maxVersion(PUBLISHED);

    @Test
    @DisplayName("maior publicada do corte é 27.1 e a ordenação é numérica por segmento")
    void orderingAndMax() {
        assertThat(MAX_PUBLISHED.toString()).isEqualTo("27.1");
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("V6_5__x.sql").orElseThrow()
                .compareTo(TenantMigrationVersionPolicy.versionOfFileName("V15_1__x.sql").orElseThrow()))
                .isLessThan(0);
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("V100__x.sql").orElseThrow()
                .compareTo(MAX_PUBLISHED)).isGreaterThan(0);
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("V100_1__x.sql").orElseThrow()
                .compareTo(TenantMigrationVersionPolicy.versionOfFileName("V100__x.sql").orElseThrow()))
                .isGreaterThan(0);
        // Segmento que falta vale zero: 100 == 100.0 (ordenação do Flyway).
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("V100__x.sql").orElseThrow()
                .compareTo(new MigrationVersion(List.of(100, 0)))).isEqualTo(0);
    }

    @Test
    @DisplayName("arquivo já publicado nunca é violação, mesmo sendo de faixa baixa ou ilegível")
    void publishedFilesAreHistory() {
        assertThat(TenantMigrationVersionPolicy.checkAll(
                List.of("V1_8__org_units_and_scopes.sql", "V15_X__ed46_complete_catalog.sql",
                        "R__data_classification.sql"),
                Set.of("V1_8__org_units_and_scopes.sql", "V15_X__ed46_complete_catalog.sql",
                        "V27_1__asset_registry.sql"))).isEmpty();
    }

    @Test
    @DisplayName("CI recusa versão menor ou igual à maior publicada")
    void refusesVersionAtOrBelowMaxPublished() {
        assertThatThrownBy(() -> TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V27_1__outra_coisa.sql", PUBLISHED, MAX_PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor ou igual");
        assertThatThrownBy(() -> TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V15_2__relatorio_novo.sql", PUBLISHED, MAX_PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor ou igual");
        assertThat(TenantMigrationVersionPolicy.checkAll(
                List.of("V27_1__asset_registry.sql", "V21_4__faixa_nova.sql"), PUBLISHED))
                .isNotEmpty();
    }

    @Test
    @DisplayName("CI recusa faixa nova abaixo de V100 mesmo acima da maior publicada de mentira")
    void refusesBelowGlobalStart() {
        // Mesmo que a maior publicada fosse baixa, faixa nova < 100 não entra:
        // a sequência global começa em V100.
        Set<String> smallPublished = Set.of("V1_1__create_tenant_schema.sql");
        MigrationVersion smallMax = TenantMigrationVersionPolicy.maxVersion(smallPublished);
        assertThatThrownBy(() -> TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V28_1__faixa_nova.sql", smallPublished, smallMax))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abaixo de V100");
    }

    @Test
    @DisplayName("CI aceita a sequência global: V100 e depois sempre a próxima maior")
    void acceptsGlobalSequence() {
        TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V100__global_sequence_start.sql", PUBLISHED, MAX_PUBLISHED);
        Set<String> publishedAfter100 = Set.of("V27_1__asset_registry.sql", "V100__global_sequence_start.sql");
        MigrationVersion maxAfter100 = TenantMigrationVersionPolicy.maxVersion(publishedAfter100);
        assertThat(maxAfter100.toString()).isEqualTo("100");
        TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V100_1__proxima.sql", publishedAfter100, maxAfter100);
        TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V101__seguinte.sql", publishedAfter100, maxAfter100);
        // Mas repetir o 100 depois de ele publicar é recusado (<= maior publicada).
        assertThatThrownBy(() -> TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V100__repetida.sql", publishedAfter100, maxAfter100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CI recusa versão ilegível e versão duplicada entre migrations novas")
    void refusesUnreadableAndDuplicate() {
        assertThatThrownBy(() -> TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V101_X__ilegivel.sql", PUBLISHED, MAX_PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ilegível");
        assertThat(TenantMigrationVersionPolicy.checkAll(
                List.of("V27_1__asset_registry.sql", "V101__a.sql", "V101__b.sql"), PUBLISHED))
                .isNotEmpty();
    }

    @Test
    @DisplayName("repeatable e arquivo comum não são migration versionada")
    void repeatableIsNotVersioned() {
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("R__data_classification.sql")).isEmpty();
        assertThat(TenantMigrationVersionPolicy.looksVersioned("R__data_classification.sql")).isFalse();
        assertThat(TenantMigrationVersionPolicy.looksVersioned("catalog-seed.frag.sql")).isFalse();
        assertThat(TenantMigrationVersionPolicy.looksVersioned("V15_X__ed46_complete_catalog.sql")).isTrue();
        assertThat(TenantMigrationVersionPolicy.versionOfFileName("V15_X__ed46_complete_catalog.sql")).isEmpty();
    }
}
