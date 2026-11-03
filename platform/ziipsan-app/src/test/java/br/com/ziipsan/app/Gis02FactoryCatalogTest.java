package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-02 (P1.27): a migration repetível {@code R__gis_factory_catalog.sql} no
 * PostgreSQL real, no mesmo desenho do Gis01MigrationTest (schema descartável,
 * transação desfeita no fim, sem massa legada: a V13_2 converte zero linhas).
 *
 * <p>Provas: reaplicação sem duplicar nem alterar o que o tenant ativou ou
 * criou; nenhum item de fábrica STANDARD (compra P1.28 pendente) e todos
 * REFERENCE_TO_VALIDATE; os 14 tipos acrescidos do manual da ARSESP e do
 * glossário do SINISA; a condição de sucção em pressão nas elevatórias
 * (NBR 12208:2020); item desativado continua válido na feição existente;
 * código de fábrica retirado ganha factory_retired_on sem perder o active;
 * e o CHECK da V13_4 recusando autoaprovação no banco.
 */
class Gis02FactoryCatalogTest {

    private static final java.util.List<String> BASE_MIGRATIONS = java.util.List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql",
            "db/migration/tenant/V13_1__gis_classification_catalog.sql",
            "db/migration/tenant/V13_2__gis_features_history_migration.sql",
            "db/migration/tenant/V13_4__gis_catalog_approval.sql");
    private static final String FACTORY_CATALOG = "db/migration/tenant/R__gis_factory_catalog.sql";

    /** Os 14 tipos acrescidos (ARSESP/SINISA) que faltavam no §4. */
    private static final java.util.List<String> REQUIRED_NEW_TYPES = java.util.List.of(
            "UTS", "AMAZON_WELL", "FLOATING_INTAKE", "DAM", "SLUDGE_PIPELINE",
            "AERIAL_CROSSING", "CANAL", "GALLERY", "TUNNEL", "SURGE_SHAFT",
            "ONE_WAY_FEED_TANK", "HYDROPNEUMATIC_TANK", "PRECONDITIONING_STATION",
            "MOBILE_BOOSTER");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private record Scratch(UUID tenant, String schema) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis02_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : BASE_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static void applyFactoryCatalog(Statement st, Scratch s) throws Exception {
        st.execute(migrationSql(FACTORY_CATALOG, s.tenant()));
        st.execute("SELECT set_config('app.current_tenant', '" + s.tenant() + "', true)");
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis02FactoryCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${tenant_id}", tenant.toString());
        }
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String queryString(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void inScratch(ScratchConsumer test) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = scratch(st);
                applyFactoryCatalog(st, s);
                test.run(c, st, s);
            } finally {
                c.rollback();
            }
        }
    }

    @FunctionalInterface
    private interface ScratchConsumer {
        void run(Connection c, Statement st, Scratch s) throws Exception;
    }

    @Test
    @DisplayName("reaplicação não duplica nem altera o que o tenant ativou ou criou")
    void reapplicationPreservesTenantChoices() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            int typesBefore = count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t);
            int itemsBefore = count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t);
            int matsBefore = count(st, "SELECT count(*) FROM gis_material WHERE tenant_id = " + t);
            int listsBefore = count(st, "SELECT count(*) FROM gis_value_list WHERE tenant_id = " + t);
            assertThat(typesBefore).isGreaterThan(0);
            assertThat(itemsBefore).isEqualTo(261);
            assertThat(matsBefore).isEqualTo(16);
            assertThat(listsBefore).isEqualTo(58);

            // O tenant desativa um item e um tipo de fábrica e cria tipo e item próprios.
            st.execute("UPDATE gis_catalog_item SET active = FALSE WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:ITEM:WATER_MAIN:PVC_PBA:100'");
            st.execute("UPDATE gis_feature_type SET active = FALSE WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:UTS'");
            String pipeClass = queryString(st, "SELECT id FROM gis_feature_class WHERE tenant_id = " + t
                    + " AND code = 'PIPE'");
            // Estado pos-aprovacao fiel ao da API: autor e aprovador distintos
            // (o CHECK da V13_4 recusa APPROVED com autor e sem outro aprovador).
            st.execute("INSERT INTO gis_feature_type (tenant_id, class_id, code, label, origin, active, "
                    + "approval_status, proposed_by, approved_by) VALUES (" + t + ", '" + pipeClass + "', 'TENANT_PIPE', "
                    + "'Tubo do tenant', 'TENANT', TRUE, 'APPROVED', '" + UUID.randomUUID() + "', '"
                    + UUID.randomUUID() + "')");
            String tenantType = queryString(st, "SELECT id FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND code = 'TENANT_PIPE'");
            st.execute("INSERT INTO gis_catalog_item (tenant_id, feature_type_id, dn_mm, origin, active, "
                    + "reference_status, approval_status) VALUES (" + t + ", '" + tenantType + "', 999, "
                    + "'TENANT', TRUE, 'TENANT', 'APPROVED')");

            applyFactoryCatalog(st, s);

            assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t))
                    .isEqualTo(typesBefore + 1);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t))
                    .isEqualTo(itemsBefore + 1);
            assertThat(count(st, "SELECT count(*) FROM gis_material WHERE tenant_id = " + t))
                    .isEqualTo(matsBefore);
            assertThat(count(st, "SELECT count(*) FROM gis_value_list WHERE tenant_id = " + t))
                    .isEqualTo(listsBefore);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:ITEM:WATER_MAIN:PVC_PBA:100' AND active = FALSE"))
                    .isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:UTS' AND active = FALSE")).isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND code = 'TENANT_PIPE' AND origin = 'TENANT'")).isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND dn_mm = 999 AND origin = 'TENANT'")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("nenhum item de fábrica é STANDARD: compra P1.28 pendente, tudo REFERENCE_TO_VALIDATE")
    void noFactoryItemIsStandard() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND origin = 'FACTORY' AND reference_status = 'STANDARD'")).isEqualTo(0);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND origin = 'FACTORY' AND reference_status = 'REFERENCE_TO_VALIDATE'"))
                    .isEqualTo(261);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND origin = 'FACTORY' AND reference_status NOT IN ('REFERENCE_TO_VALIDATE')"))
                    .isEqualTo(0);
        });
    }

    @Test
    @DisplayName("os 14 tipos acrescidos existem, com classes novas, e o booster guarda a sucção em pressão")
    void requiredNewTypesAndBoosterSuction() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            for (String code : REQUIRED_NEW_TYPES) {
                assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                        + " AND code = '" + code + "' AND origin = 'FACTORY' AND approval_status = 'APPROVED'"))
                        .as("tipo de fábrica %s", code).isEqualTo(1);
            }
            for (String clazz : java.util.List.of("DAM", "SURGE_PROTECTION", "BASIN")) {
                assertThat(count(st, "SELECT count(*) FROM gis_feature_class WHERE tenant_id = " + t
                        + " AND code = '" + clazz + "' AND origin = 'FACTORY'")).isEqualTo(1);
            }
            // Booster: schema exige suction_pressurized = true.
            String boosterSchema = queryString(st, "SELECT attributes_schema FROM gis_feature_type "
                    + "WHERE tenant_id = " + t + " AND code = 'BOOSTER'");
            assertThat(boosterSchema).contains("suction_pressurized");
            assertThat(boosterSchema).contains("\"enum\": [true]");
            String mobileSchema = queryString(st, "SELECT attributes_schema FROM gis_feature_type "
                    + "WHERE tenant_id = " + t + " AND code = 'MOBILE_BOOSTER'");
            assertThat(mobileSchema).contains("suction_pressurized");
            // Elevatórias da V13_2 ganham a propriedade sem perder o active.
            for (String code : java.util.List.of("WATER_PUMP_STATION", "SEWAGE_LIFT_STATION")) {
                String schema = queryString(st, "SELECT attributes_schema FROM gis_feature_type "
                        + "WHERE tenant_id = " + t + " AND code = '" + code + "'");
                assertThat(schema).as("schema de %s", code).contains("suction_pressurized");
                assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                        + " AND code = '" + code + "' AND active = TRUE")).isEqualTo(1);
            }
        });
    }

    @Test
    @DisplayName("item desativado some da criação e continua válido na feição existente")
    void deactivatedItemStillValidOnExistingFeatures() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            String itemId = queryString(st, "SELECT id FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:ITEM:HYDRANT_PILLAR::100'");
            String typeId = queryString(st, "SELECT id FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND code = 'HYDRANT_PILLAR'");
            assertThat(itemId).isNotNull();
            st.execute("INSERT INTO gis_node (tenant_id, code, feature_type_id, catalog_item_id, "
                    + "capture_method, geom) VALUES (" + t + ", 'H1', '" + typeId + "', '" + itemId + "', "
                    + "'DIGITIZED', ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))");
            // O tenant desativa o item: a feição continua lá, referenciando-o.
            st.execute("UPDATE gis_catalog_item SET active = FALSE WHERE id = '" + itemId + "'");
            assertThat(count(st, "SELECT count(*) FROM gis_node WHERE tenant_id = " + t
                    + " AND code = 'H1' AND catalog_item_id = '" + itemId + "'")).isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE id = '" + itemId
                    + "' AND active = FALSE")).isEqualTo(1);
            // ...e some da lista de usáveis (APPROVED + active), que a tela usa na criação.
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE id = '" + itemId
                    + "' AND active = TRUE AND approval_status = 'APPROVED'")).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("código de fábrica retirado ganha factory_retired_on sem perder o active")
    void retiredFactoryCodeKeepsActive() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            String pipeClass = queryString(st, "SELECT id FROM gis_feature_class WHERE tenant_id = " + t
                    + " AND code = 'PIPE'");
            st.execute("INSERT INTO gis_feature_type (tenant_id, class_id, code, label, origin, "
                    + "factory_code, active, approval_status) VALUES (" + t + ", '" + pipeClass + "', "
                    + "'FAKE_TYPE', 'Tipo falso de fábrica', 'FACTORY', 'gis02:FAKE_TYPE', TRUE, 'APPROVED')");
            String fakeType = queryString(st, "SELECT id FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND code = 'FAKE_TYPE'");
            st.execute("INSERT INTO gis_catalog_item (tenant_id, feature_type_id, dn_mm, origin, "
                    + "factory_code, active, reference_status) VALUES (" + t + ", '" + fakeType + "', 123, "
                    + "'FACTORY', 'gis02:ITEM:FAKE', TRUE, 'REFERENCE_TO_VALIDATE')");
            applyFactoryCatalog(st, s);
            assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:FAKE_TYPE' AND factory_retired_on IS NOT NULL "
                    + "AND active = TRUE")).isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_catalog_item WHERE tenant_id = " + t
                    + " AND factory_code = 'gis02:ITEM:FAKE' AND factory_retired_on IS NOT NULL "
                    + "AND active = TRUE")).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("CHECK da V13_4: autor não aprova o próprio tipo nem o próprio item")
    void selfApprovalRefusedByCheck() throws Exception {
        inScratch((c, st, s) -> {
            String t = "'" + s.tenant() + "'";
            UUID author = UUID.randomUUID();
            String pipeClass = queryString(st, "SELECT id FROM gis_feature_class WHERE tenant_id = " + t
                    + " AND code = 'PIPE'");
            st.execute("INSERT INTO gis_feature_type (tenant_id, class_id, code, label, origin, active, "
                    + "approval_status, proposed_by) VALUES (" + t + ", '" + pipeClass + "', 'SELF_TYPE', "
                    + "'Tipo do autor', 'TENANT', FALSE, 'PENDING', '" + author + "')");
            var savepoint = c.setSavepoint();
            try {
                st.execute("UPDATE gis_feature_type SET approval_status = 'APPROVED', approved_by = '"
                        + author + "', approved_at = NOW() WHERE tenant_id = " + t + " AND code = 'SELF_TYPE'");
                assertThat(false).as("autoaprovação deveria violar o CHECK").isTrue();
            } catch (SQLException e) {
                assertThat(e.getSQLState()).isEqualTo("23514");
            } finally {
                c.rollback(savepoint);
            }
            // Outro aprovador passa.
            st.execute("UPDATE gis_feature_type SET approval_status = 'APPROVED', approved_by = '"
                    + UUID.randomUUID() + "', approved_at = NOW(), active = TRUE WHERE tenant_id = " + t
                    + " AND code = 'SELF_TYPE'");
            assertThat(count(st, "SELECT count(*) FROM gis_feature_type WHERE tenant_id = " + t
                    + " AND code = 'SELF_TYPE' AND approval_status = 'APPROVED' AND active = TRUE"))
                    .isEqualTo(1);
        });
    }
}
