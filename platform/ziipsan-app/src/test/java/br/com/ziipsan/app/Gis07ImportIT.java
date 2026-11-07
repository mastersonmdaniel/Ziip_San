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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-07 (P3.26): importador do cadastro técnico da V13_11 no
 * PostgreSQL real — conversão assistida registrada, staging sem tocar
 * no cadastro, SRS/grade do IBGE, perfis, limpeza com contagens,
 * relatório ISO 19157, publicação só por pacote IMPORT revisado,
 * reimportação sem duplicar, contexto e anonimização (ADR-0033).
 *
 * <p>Segue EXATAMENTE o padrão do {@code Gis04ChangeSetIT}: migrations
 * do classpath contra schema descartável, transação desfeita no fim.
 * A lista vai até a V13_9 (GIS-04) e salta para a V13_11: a V13_10 é da
 * tarefa paralela P2.23 e, quando pousar, entra nesta lista na
 * integração — a V13_11 não depende dela. Massa sintética em torno de
 * -46, -23; hashes são cadeias sintéticas de 64 hexadecimais.
 */
class Gis07ImportIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql");
    private static final List<String> GIS_MIGRATIONS = List.of(
            "db/migration/tenant/V13_1__gis_classification_catalog.sql",
            "db/migration/tenant/V13_2__gis_features_history_migration.sql",
            "db/migration/tenant/V13_3__gis_responsible_party_redaction.sql",
            "db/migration/tenant/V13_4__gis_catalog_approval.sql",
            "db/migration/tenant/V13_5__gis_check_feature_qualified.sql",
            "db/migration/tenant/V13_6__gis_check_feature_search_path.sql",
            "db/migration/tenant/V13_7__gis_write_history_explicit_columns.sql",
            "db/migration/tenant/V13_8__gis_topology_validation.sql",
            "db/migration/tenant/V13_9__gis_change_sets.sql",
            "db/migration/tenant/V13_11__gis_import_staging.sql");

    private static final UUID AUTHOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final String HASH_D = "d".repeat(64);

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartável GIS-07')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + tenant + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
        return new Scratch(tenant, schema);
    }

    private static void expectFailure(Connection c, Statement st, String sql,
                                      String messagePart) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(messagePart);
        } finally {
            c.rollback(savepoint);
        }
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis07ImportIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        }
    }

    private record Scratch(UUID tenant, String schema) {
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

    private static UUID newBatch(Statement st, UUID tenant, String format, String hash,
                                 String extraColumns, String extraValues) throws SQLException {
        UUID id = UUID.randomUUID();
        st.execute("INSERT INTO gis_import_batch (id, tenant_id, source_format,"
                + " original_file_name, original_sha256" + extraColumns + ") VALUES ('"
                + id + "', '" + tenant + "', '" + format + "', 'origem." + format.toLowerCase()
                + "', '" + hash + "'" + extraValues + ")");
        return id;
    }

    private static UUID corsanProfile(Statement st, UUID tenant) throws SQLException {
        return UUID.fromString(queryString(st,
                "SELECT id FROM gis_import_profile WHERE tenant_id = '" + tenant
                        + "' AND key = 'CORSAN_ANEXO_A'"));
    }

    private static void stageArc(Statement st, UUID tenant, UUID batch, String layer,
                                 String ref, String wkt) throws SQLException {
        st.execute("INSERT INTO gis_import_raw (tenant_id, batch_id, source_layer,"
                + " source_ref, geom) VALUES ('" + tenant + "', '" + batch + "', '"
                + layer + "', '" + ref + "', ST_GeomFromText('" + wkt + "', 4674))");
    }

    // ------------------------------------------------------------- conversão

    @Test
    @DisplayName("DWG sem DXF do ODA e DGN v8 sem convertido são recusados; com hashes, aceitos")
    void assistedConversionIsRecordedAndEnforced() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            expectFailure(c, st,
                    "INSERT INTO gis_import_batch (tenant_id, source_format, original_file_name,"
                            + " original_sha256) VALUES ('" + s.tenant() + "', 'DWG', 'rede.dwg', '"
                            + HASH_A + "')",
                    "ck_gis_import_batch_conversion");
            expectFailure(c, st,
                    "INSERT INTO gis_import_batch (tenant_id, source_format, original_file_name,"
                            + " original_sha256) VALUES ('" + s.tenant() + "', 'DGN_V8', 'rede.dgn', '"
                            + HASH_B + "')",
                    "ck_gis_import_batch_conversion");
            newBatch(st, s.tenant(), "DWG", HASH_A,
                    ", converted_format, converted_sha256, conversion_tool",
                    ", 'DXF', '" + HASH_B + "', 'ODA_FILE_CONVERTER'");
            newBatch(st, s.tenant(), "DGN_V8", HASH_C,
                    ", converted_format, converted_sha256, conversion_tool",
                    ", 'DXF', '" + HASH_D + "', 'ASSISTED_EXTERNAL'");
            assertThat(count(st, "SELECT count(*) FROM gis_import_batch")).isEqualTo(2);
            c.rollback();
        }
    }

    @Test
    @DisplayName("reimportar o mesmo arquivo (mesmo hash) não duplica o lote")
    void sameFileHashDoesNotDuplicateBatch() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            newBatch(st, s.tenant(), "DXF", HASH_A, "", "");
            expectFailure(c, st,
                    "INSERT INTO gis_import_batch (tenant_id, source_format, original_file_name,"
                            + " original_sha256) VALUES ('" + s.tenant() + "', 'DXF', 'rede2.dxf', '"
                            + HASH_A + "')",
                    "uq_gis_import_batch_original_hash");
            c.rollback();
        }
    }

    // ------------------------------------------------------------------- SRS

    @Test
    @DisplayName("sem SRS não sai do recebimento; SAD69 sem grade do IBGE também não")
    void srsAndIbgeGridAreEnforced() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            UUID semSrs = newBatch(st, s.tenant(), "SHP", HASH_A, "", "");
            expectFailure(c, st,
                    "UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + semSrs + "'",
                    "arquivo sem SRS recusado");
            UUID sad69 = newBatch(st, s.tenant(), "SHP", HASH_B,
                    ", source_srs_epsg, srs_source", ", 4618, 'DETECTED'");
            expectFailure(c, st,
                    "UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + sad69 + "'",
                    "grade do IBGE");
            st.execute("UPDATE gis_import_batch SET datum_transform = 'IBGE_GRID',"
                    + " ibge_grid_ref = 'grade registrada no lote de homologacao'"
                    + " WHERE id = '" + sad69 + "'");
            st.execute("UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + sad69 + "'");
            assertThat(queryString(st, "SELECT status FROM gis_import_batch WHERE id = '"
                    + sad69 + "'")).isEqualTo("STAGED");
            c.rollback();
        }
    }

    // ------------------------------------------------- staging e perfis

    @Test
    @DisplayName("staging não toca no cadastro; perfis Corsan e genérico nascem por tenant")
    void stagingDoesNotTouchRegistryAndProfilesAreSeeded() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            assertThat(count(st, "SELECT count(*) FROM gis_import_profile")).isEqualTo(2);
            assertThat(queryString(st, "SELECT feature_table || '/' || feature_type_code"
                    + " FROM gis_cad_layer_map m JOIN gis_import_profile p ON p.id = m.profile_id"
                    + " WHERE p.key = 'CORSAN_ANEXO_A' AND m.source_layer = 'A_REDE'"))
                    .isEqualTo("gis_arc/gis01:WATER_MAIN");
            UUID batch = newBatch(st, s.tenant(), "DXF", HASH_A,
                    ", source_srs_epsg, srs_source", ", 4674, 'DETECTED'");
            st.execute("UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + batch + "'");
            stageArc(st, s.tenant(), batch, "A_REDE", "7A3F",
                    "LINESTRING Z (-46 -23 0, -46.001 -23 0)");
            assertThat(count(st, "SELECT count(*) FROM gis_import_raw")).isEqualTo(1);
            assertThat(count(st, "SELECT count(*) FROM gis_arc")).isZero();
            assertThat(count(st, "SELECT count(*) FROM gis_node")).isZero();
            c.rollback();
        }
    }

    // ---------------------------------------------------------------- limpeza

    @Test
    @DisplayName("limpeza em conjunto conta antes e depois: duplicata e segmento curto saem")
    void cleaningCountsBeforeAndAfter() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            UUID profile = corsanProfile(st, s.tenant());
            UUID batch = newBatch(st, s.tenant(), "DXF", HASH_A,
                    ", profile_id, source_srs_epsg, srs_source",
                    ", '" + profile + "', 4674, 'DETECTED'");
            st.execute("UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + batch + "'");
            String line = "LINESTRING Z (-46 -23 0, -46.001 -23 0)";
            stageArc(st, s.tenant(), batch, "A_REDE", "R1", line);
            // Duplicata geometrica: mesma camada e mesma geometria, com
            // source_ref distinta (a mesma ref nem entra: UNIQUE parcial).
            stageArc(st, s.tenant(), batch, "A_REDE", "R1B", line);
            stageArc(st, s.tenant(), batch, "A_REDE", "R2",
                    "LINESTRING Z (-46 -23 0, -46.000001 -23 0)");
            st.execute("UPDATE gis_import_raw r SET layer_map_id = m.id, status = 'MAPPED'"
                    + " FROM gis_cad_layer_map m WHERE m.profile_id = '" + profile
                    + "' AND m.source_layer = r.source_layer AND r.batch_id = '" + batch + "'");
            st.execute("UPDATE gis_import_batch SET status = 'MAPPED' WHERE id = '" + batch + "'");
            st.execute("SELECT gis_import_clean('" + batch + "')");
            assertThat(queryString(st, "SELECT counts_before ->> 'total'"
                    + " FROM gis_import_batch WHERE id = '" + batch + "'")).isEqualTo("3");
            assertThat(queryString(st, "SELECT counts_after ->> 'duplicates_removed'"
                    + " FROM gis_import_batch WHERE id = '" + batch + "'")).isEqualTo("1");
            assertThat(queryString(st, "SELECT counts_after ->> 'short_segments_removed'"
                    + " FROM gis_import_batch WHERE id = '" + batch + "'")).isEqualTo("1");
            assertThat(count(st, "SELECT count(*) FROM gis_import_raw"
                    + " WHERE status = 'CLEANED'")).isEqualTo(1);
            assertThat(queryString(st, "SELECT status FROM gis_import_batch WHERE id = '"
                    + batch + "'")).isEqualTo("CLEANED");
            c.rollback();
        }
    }

    // -------------------------------------------------------------- publicação

    @Test
    @DisplayName("nada em operação sem pacote IMPORT revisado por outra pessoa")
    void publicationOnlyViaReviewedImportPackage() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            UUID profile = corsanProfile(st, s.tenant());
            UUID batch = newBatch(st, s.tenant(), "DXF", HASH_A,
                    ", profile_id, source_srs_epsg, srs_source",
                    ", '" + profile + "', 4674, 'DETECTED'");
            st.execute("UPDATE gis_import_batch SET profile_id = '" + profile
                    + "' WHERE id = '" + batch + "'");
            st.execute("UPDATE gis_import_batch SET status = 'STAGED' WHERE id = '" + batch + "'");
            stageArc(st, s.tenant(), batch, "A_REDE", "R1",
                    "LINESTRING Z (-46 -23 0, -46.001 -23 0)");
            st.execute("UPDATE gis_import_raw r SET layer_map_id = m.id, status = 'MAPPED'"
                    + " FROM gis_cad_layer_map m WHERE m.profile_id = '" + profile
                    + "' AND m.source_layer = r.source_layer AND r.batch_id = '" + batch + "'");
            st.execute("UPDATE gis_import_batch SET status = 'MAPPED' WHERE id = '" + batch + "'");
            st.execute("SELECT gis_import_clean('" + batch + "')");
            st.execute("INSERT INTO gis_quality_report (tenant_id, batch_id, report)"
                    + " VALUES ('" + s.tenant() + "', '" + batch + "',"
                    + " '{\"completeness\": {\"omission\": 0}}'::jsonb)");
            st.execute("UPDATE gis_import_batch SET status = 'QUALITY_REPORTED'"
                    + " WHERE id = '" + batch + "'");
            String setId = queryString(st,
                    "SELECT gis_import_publish('" + batch + "', '" + AUTHOR + "')::text");
            assertThat(setId).isNotNull();
            assertThat(queryString(st, "SELECT type || '/' || channel || '/' || status"
                    + " FROM gis_change_set WHERE id = '" + setId + "'"))
                    .isEqualTo("IMPORT/IMPORT/SUBMITTED");
            assertThat(count(st, "SELECT count(*) FROM gis_arc"
                    + " WHERE lifecycle_status = 'IN_SERVICE'")).isZero();
            assertThat(count(st, "SELECT count(*) FROM gis_arc"
                    + " WHERE lifecycle_status = 'PLANNED' AND source_batch_id = '"
                    + batch + "'")).isEqualTo(1);
            // Autor não aplica o próprio pacote (GIS-04).
            st.execute("UPDATE gis_change_set SET status = 'IN_REVIEW' WHERE id = '" + setId + "'");
            expectFailure(c, st,
                    "SELECT gis_apply_change_set('" + setId + "', '" + AUTHOR + "')",
                    "autor nao aprova o proprio pacote");
            st.execute("SELECT gis_apply_change_set('" + setId + "', '" + REVIEWER + "')");
            assertThat(count(st, "SELECT count(*) FROM gis_arc"
                    + " WHERE lifecycle_status = 'IN_SERVICE' AND source_batch_id = '"
                    + batch + "'")).isEqualTo(1);
            // Republicar o lote publicado é recusado (sem duplicar).
            expectFailure(c, st,
                    "SELECT gis_import_publish('" + batch + "', '" + AUTHOR + "')",
                    "QUALITY_REPORTED");
            assertThat(count(st, "SELECT count(*) FROM gis_arc WHERE source_batch_id = '"
                    + batch + "'")).isEqualTo(1);
            c.rollback();
        }
    }

    // ------------------------------------------------- contexto e anonimização

    @Test
    @DisplayName("contexto importa; camada pessoal sem método registrado é recusada (ADR-0033)")
    void contextLayersAndAnonymizationRefusal() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch s = scratch(st);
            UUID batch = newBatch(st, s.tenant(), "SHP", HASH_A,
                    ", source_srs_epsg, srs_source", ", 4674, 'DETECTED'");
            st.execute("INSERT INTO gis_context_feature (tenant_id, batch_id, context_kind,"
                    + " name, geom) VALUES ('" + s.tenant() + "', '" + batch + "',"
                    + " 'NEIGHBORHOOD', 'Centro', ST_GeomFromText("
                    + "'POLYGON ((-46 -23, -46.001 -23, -46.001 -23.001, -46 -23.001, -46 -23))', 4674))");
            expectFailure(c, st,
                    "INSERT INTO gis_context_feature (tenant_id, batch_id, context_kind,"
                            + " personal_data, geom) VALUES ('" + s.tenant() + "', '" + batch
                            + "', 'PARCEL', TRUE, ST_GeomFromText('POINT (-46 -23)', 4674))",
                    "ck_gis_context_feature_personal");
            st.execute("INSERT INTO gis_context_feature (tenant_id, batch_id, context_kind,"
                    + " personal_data, anonymization_method, geom) VALUES ('" + s.tenant()
                    + "', '" + batch + "', 'PARCEL', TRUE, 'AGGREGATED_BY_BLOCK',"
                    + " ST_GeomFromText('POINT (-46 -23)', 4674))");
            assertThat(count(st, "SELECT count(*) FROM gis_context_feature")).isEqualTo(2);
            UUID profile = corsanProfile(st, s.tenant());
            expectFailure(c, st,
                    "INSERT INTO gis_cad_layer_map (tenant_id, profile_id, source_layer,"
                            + " target_kind, feature_table, feature_type_code, personal_data)"
                            + " VALUES ('" + s.tenant() + "', '" + profile + "', 'LOTES',"
                            + " 'FEATURE', 'gis_poi', 'gis01:HYDRANT', TRUE)",
                    "ck_gis_cad_layer_map_personal");
            c.rollback();
        }
    }
}
