package br.com.ziipsan.intelligence.gis.importer;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.BatchView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ContextFeatureInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ContextFeatureView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.CreateBatchInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.LayerMapInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.LayerMapView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.PageView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.ProfileView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.QualityReportInput;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.RawView;
import br.com.ziipsan.intelligence.gis.importer.GisImportDtos.StageRowInput;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Importador do cadastro técnico (GIS-07, §9): abertura do lote com os
 * hashes da conversão assistida, staging em {@code gis_import_raw} (sem
 * tocar no cadastro), mapeamento por perfil do cliente, limpeza em
 * conjunto no banco, relatório ISO 19157 e publicação — que só cria o
 * pacote {@code IMPORT} submetido; aplicar é da GIS-04, após revisão
 * por outra pessoa.
 *
 * <p>As guardas do banco (V13_11) são a autoridade: valem para api,
 * worker e SQL direto. Este serviço falha cedo com o erro HTTP certo e
 * traduz as recusas do banco quando elas acontecem lá. A leitura dos
 * arquivos (GDAL) e as conversões assistidas (ODA/MicroStation) rodam
 * fora daqui — o worker entrega as linhas já transformadas para 4674.
 */
@Service
@Transactional(readOnly = true)
public class GisImportService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public GisImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------- lotes

    @Transactional
    public BatchView createBatch(CreateBatchInput in) {
        UUID tenantId = GisImportContext.tenantId();
        UUID userId = GisImportContext.userId();
        String format = in.sourceFormat().trim().toUpperCase();
        if (!GisImportRules.SOURCE_FORMATS.contains(format)) {
            throw new ApiException.BadRequest("formato-de-origem-invalido",
                    "Formato de origem desconhecido: " + in.sourceFormat());
        }
        try {
            GisImportRules.ensureConversionRecorded(format, in.convertedFormat(),
                    in.originalSha256(), in.convertedSha256(), in.conversionTool());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("conversao-assistida-invalida", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_import_batch (id, tenant_id, profile_id,
                            source_format, original_file_name, original_sha256,
                            converted_format, converted_sha256, conversion_tool,
                            source_srs_epsg, srs_source, srs_declared_by,
                            datum_transform, ibge_grid_ref,
                            control_point_residual_m, snap_tolerance_m,
                            pilot_homologation, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, tenantId, in.profileId(), format, in.originalFileName(),
                    in.originalSha256(), in.convertedFormat(), in.convertedSha256(),
                    in.conversionTool(), in.sourceSrsEpsg(), in.srsSource(),
                    in.srsSource() != null && "OPERATOR_DECLARED".equals(in.srsSource())
                            ? userId : null,
                    in.datumTransform() == null ? "NONE" : in.datumTransform(),
                    in.ibgeGridRef(), in.controlPointResidualM(),
                    in.snapToleranceM() == null
                            ? new java.math.BigDecimal("0.50") : in.snapToleranceM(),
                    in.pilotHomologation(), userId, userId);
        } catch (DuplicateKeyException e) {
            // Reimportação do mesmo arquivo: o lote já existe (hash).
            throw new ApiException.Conflict("lote-ja-importado",
                    "Arquivo já importado neste tenant (mesmo hash do original).");
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return getBatch(id);
    }

    public BatchView getBatch(UUID id) {
        UUID tenantId = GisImportContext.tenantId();
        List<BatchView> rows = jdbc.query(SELECT_BATCH
                        + " WHERE b.id = ? AND b.tenant_id = ?",
                this::mapBatch, id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Lote de importação não encontrado.");
        }
        return rows.get(0);
    }

    public PageView<BatchView> listBatches(String status, int page, int size) {
        UUID tenantId = GisImportContext.tenantId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        StringBuilder where = new StringBuilder(" WHERE b.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isBlank()) {
            where.append(" AND b.status = ?");
            args.add(status.trim().toUpperCase());
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM gis_import_batch b" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<BatchView> content = jdbc.query(SELECT_BATCH + where
                        + " ORDER BY b.created_at DESC, b.id LIMIT ? OFFSET ?",
                this::mapBatch, pageArgs.toArray());
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        return new PageView<>(content, safePage, safeSize, totalElements, totalPages);
    }

    /** RECEIVED -> STAGED: o banco recusa sem SRS/grade (V13_11). */
    @Transactional
    public BatchView markStaged(UUID batchId) {
        BatchView batch = getBatch(batchId);
        // Falha cedo só no caso sem SRS; responsável da escolha, grade do
        // IBGE e resíduo de pontos de controle são conferidos pelo banco
        // na transição (guarda da V13_11) e traduzidos por translateDbError.
        if (batch.sourceSrsEpsg() == null) {
            throw new ApiException.BadRequest("arquivo-sem-srs",
                    "Arquivo sem SRS recusado: declare o SRS de origem ou "
                            + "registre a escolha do operador.");
        }
        transition(batchId, "STAGED");
        return getBatch(batchId);
    }

    // ------------------------------------------------------------ staging

    @Transactional
    public RawView stageRow(UUID batchId, StageRowInput in) {
        UUID tenantId = GisImportContext.tenantId();
        BatchView batch = getBatch(batchId);
        if (!"RECEIVED".equals(batch.status()) && !"STAGED".equals(batch.status())) {
            throw new ApiException.Conflict("lote-nao-recebe-staging",
                    "Staging só em lote RECEIVED/STAGED (atual " + batch.status() + ").");
        }
        UUID id = UUID.randomUUID();
        UUID userId = GisImportContext.userId();
        try {
            jdbc.update("""
                    INSERT INTO gis_import_raw (id, tenant_id, batch_id,
                            source_layer, source_ref, block_name, label_text,
                            attributes, geom, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                            CASE WHEN ? IS NULL THEN NULL
                                 ELSE ST_GeomFromText(?, 4674) END, ?, ?)
                    """, id, tenantId, batchId, in.sourceLayer(), in.sourceRef(),
                    in.blockName(), in.labelText(),
                    Json.orDefault(in.attributes(), "{}"),
                    in.geomWkt(), in.geomWkt(), userId, userId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return getRaw(batchId, id);
    }

    public RawView getRaw(UUID batchId, UUID rawId) {
        UUID tenantId = GisImportContext.tenantId();
        List<RawView> rows = jdbc.query("""
                SELECT id, source_layer, source_ref, block_name, label_text,
                       attributes::text AS attributes, ST_AsText(geom) AS geom_wkt,
                       status, layer_map_id, published_feature_table,
                       published_feature_id
                FROM gis_import_raw
                WHERE id = ? AND batch_id = ? AND tenant_id = ?
                """, this::mapRaw, rawId, batchId, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Linha de staging não encontrada.");
        }
        return rows.get(0);
    }

    public List<RawView> listRaw(UUID batchId, String status) {
        UUID tenantId = GisImportContext.tenantId();
        getBatch(batchId);
        StringBuilder sql = new StringBuilder("""
                SELECT id, source_layer, source_ref, block_name, label_text,
                       attributes::text AS attributes, ST_AsText(geom) AS geom_wkt,
                       status, layer_map_id, published_feature_table,
                       published_feature_id
                FROM gis_import_raw WHERE batch_id = ? AND tenant_id = ?
                """);
        List<Object> args = new ArrayList<>(List.of(batchId, tenantId));
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY created_at, id");
        return jdbc.query(sql.toString(), this::mapRaw, args.toArray());
    }

    /**
     * Aplica o perfil do lote às linhas RAW: cada linha ganha o
     * layer_map da sua camada (FEATURE ou CONTEXT) e vira MAPPED; o lote
     * vai a MAPPED. Linha de camada sem mapa fica RAW e é listada no
     * retorno da contagem — nunca é publicada em silêncio.
     */
    @Transactional
    public BatchView applyMapping(UUID batchId) {
        UUID tenantId = GisImportContext.tenantId();
        BatchView batch = getBatch(batchId);
        if (batch.profileId() == null) {
            throw new ApiException.Conflict("lote-sem-perfil",
                    "Lote sem perfil de mapeamento não pode ser mapeado.");
        }
        jdbc.update("""
                UPDATE gis_import_raw r
                   SET layer_map_id = m.id, status = 'MAPPED',
                       updated_at = clock_timestamp()
                  FROM gis_cad_layer_map m
                 WHERE m.tenant_id = r.tenant_id AND m.profile_id = ?
                   AND m.source_layer = r.source_layer
                   AND r.batch_id = ? AND r.tenant_id = ? AND r.status = 'RAW'
                """, batch.profileId(), batchId, tenantId);
        transition(batchId, "MAPPED");
        return getBatch(batchId);
    }

    /** Limpeza em conjunto no staging (função da V13_11). */
    @Transactional
    public BatchView clean(UUID batchId) {
        getBatch(batchId);
        try {
            jdbc.queryForObject("SELECT gis_import_clean(?)::text",
                    String.class, batchId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return getBatch(batchId);
    }

    /** Associa rótulos ao arco cru mais próximo (texto não interpretado). */
    @Transactional
    public int extractLabels(UUID batchId) {
        getBatch(batchId);
        Integer n = jdbc.queryForObject(
                "SELECT gis_import_extract_labels(?)", Integer.class, batchId);
        return n == null ? 0 : n;
    }

    // ----------------------------------------------------------- contexto

    @Transactional
    public ContextFeatureView addContextFeature(UUID batchId, ContextFeatureInput in) {
        UUID tenantId = GisImportContext.tenantId();
        UUID userId = GisImportContext.userId();
        getBatch(batchId);
        try {
            GisImportRules.ensureAnonymization(in.personalData(), in.anonymizationMethod());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("anonimizacao-nao-registrada", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_context_feature (id, tenant_id, batch_id,
                            context_kind, code, name, personal_data,
                            anonymization_method, attributes, geom,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                            ST_GeomFromText(?, 4674), ?, ?)
                    """, id, tenantId, batchId, in.contextKind(), in.code(), in.name(),
                    in.personalData(), in.anonymizationMethod(),
                    Json.orDefault(in.attributes(), "{}"), in.geomWkt(), userId, userId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        List<ContextFeatureView> rows = jdbc.query("""
                SELECT id, batch_id, context_kind, code, name, personal_data,
                       anonymization_method, ST_AsText(geom) AS geom_wkt
                FROM gis_context_feature WHERE id = ? AND tenant_id = ?
                """, this::mapContext, id, tenantId);
        return rows.get(0);
    }

    public List<ContextFeatureView> listContextFeatures(UUID batchId) {
        UUID tenantId = GisImportContext.tenantId();
        getBatch(batchId);
        return jdbc.query("""
                SELECT id, batch_id, context_kind, code, name, personal_data,
                       anonymization_method, ST_AsText(geom) AS geom_wkt
                FROM gis_context_feature
                WHERE batch_id = ? AND tenant_id = ? ORDER BY created_at, id
                """, this::mapContext, batchId, tenantId);
    }

    // ---------------------------------------------------------- qualidade

    /** Grava o relatório ISO 19157 do lote e avança CLEANED -> QUALITY_REPORTED. */
    @Transactional
    public BatchView saveQualityReport(UUID batchId, QualityReportInput in) {
        UUID tenantId = GisImportContext.tenantId();
        UUID userId = GisImportContext.userId();
        BatchView batch = getBatch(batchId);
        if (in.report() == null || in.report().isEmpty()) {
            throw new ApiException.BadRequest("relatorio-vazio",
                    "Relatório ISO 19157 vazio não é relatório.");
        }
        try {
            jdbc.update("""
                    INSERT INTO gis_quality_report (id, tenant_id, batch_id,
                            report, created_by, updated_by)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (tenant_id, batch_id)
                    DO UPDATE SET report = EXCLUDED.report,
                                  updated_by = EXCLUDED.updated_by,
                                  updated_at = clock_timestamp()
                    """, UUID.randomUUID(), tenantId, batchId,
                    Json.write(in.report()), userId, userId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        if ("CLEANED".equals(batch.status())) {
            transition(batchId, "QUALITY_REPORTED");
        }
        return getBatch(batchId);
    }

    public Map<String, Object> getQualityReport(UUID batchId) {
        UUID tenantId = GisImportContext.tenantId();
        getBatch(batchId);
        List<String> rows = jdbc.query("""
                SELECT report::text FROM gis_quality_report
                WHERE batch_id = ? AND tenant_id = ?
                """, (rs, row) -> rs.getString(1), batchId, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Relatório de qualidade não encontrado.");
        }
        return Json.readMap(rows.get(0));
    }

    // --------------------------------------------------------- publicação

    /**
     * Publica: cria o pacote IMPORT submetido (função da V13_11) e
     * devolve o id do pacote. Aplicar o pacote é da GIS-04, por revisor
     * diferente do autor — nada entra em operação por esta chamada.
     */
    @Transactional
    public UUID publish(UUID batchId) {
        UUID authorId = GisImportContext.userId();
        getBatch(batchId);
        try {
            return jdbc.queryForObject("SELECT gis_import_publish(?, ?)",
                    UUID.class, batchId, authorId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
    }

    // ------------------------------------------------------------- perfis

    public List<ProfileView> listProfiles() {
        UUID tenantId = GisImportContext.tenantId();
        return jdbc.query("""
                SELECT id, key, label, origin, active FROM gis_import_profile
                WHERE tenant_id = ? ORDER BY key
                """, (rs, row) -> new ProfileView(
                rs.getObject("id", UUID.class), rs.getString("key"),
                rs.getString("label"), rs.getString("origin"),
                rs.getBoolean("active")), tenantId);
    }

    public List<LayerMapView> listLayerMaps(UUID profileId) {
        UUID tenantId = GisImportContext.tenantId();
        return jdbc.query("""
                SELECT id, profile_id, source_layer, source_block, text_pattern,
                       target_kind, feature_table, feature_type_code,
                       context_kind, personal_data, anonymization_method
                FROM gis_cad_layer_map
                WHERE profile_id = ? AND tenant_id = ? ORDER BY source_layer, id
                """, this::mapLayerMap, profileId, tenantId);
    }

    @Transactional
    public LayerMapView saveLayerMap(UUID profileId, LayerMapInput in) {
        UUID tenantId = GisImportContext.tenantId();
        UUID userId = GisImportContext.userId();
        try {
            GisImportRules.ensureAnonymization(in.personalData(), in.anonymizationMethod());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("anonimizacao-nao-registrada", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_cad_layer_map (id, tenant_id, profile_id,
                            source_layer, source_block, text_pattern, target_kind,
                            feature_table, feature_type_code, context_kind,
                            personal_data, anonymization_method,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, tenantId, profileId, in.sourceLayer(), in.sourceBlock(),
                    in.textPattern(), in.targetKind(), in.featureTable(),
                    in.featureTypeCode(), in.contextKind(), in.personalData(),
                    in.anonymizationMethod(), userId, userId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        List<LayerMapView> rows = jdbc.query("""
                SELECT id, profile_id, source_layer, source_block, text_pattern,
                       target_kind, feature_table, feature_type_code,
                       context_kind, personal_data, anonymization_method
                FROM gis_cad_layer_map WHERE id = ? AND tenant_id = ?
                """, this::mapLayerMap, id, tenantId);
        return rows.get(0);
    }

    // ------------------------------------------------------------- interno

    private void transition(UUID batchId, String to) {
        UUID tenantId = GisImportContext.tenantId();
        try {
            int updated = jdbc.update("""
                    UPDATE gis_import_batch SET status = ?
                    WHERE id = ? AND tenant_id = ?
                    """, to, batchId, tenantId);
            if (updated == 0) {
                throw new ApiException.NotFound("Lote de importação não encontrado.");
            }
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
    }

    private static final String SELECT_BATCH = """
            SELECT b.id, b.profile_id, b.source_format, b.original_file_name,
                   b.original_sha256, b.converted_format, b.converted_sha256,
                   b.conversion_tool, b.status, b.source_srs_epsg, b.srs_source,
                   b.datum_transform, b.ibge_grid_ref, b.snap_tolerance_m,
                   b.pilot_homologation, b.counts_before::text AS counts_before,
                   b.counts_after::text AS counts_after, b.change_set_id,
                   b.created_at, b.updated_at
            FROM gis_import_batch b
            """;

    private BatchView mapBatch(ResultSet rs, int row) throws SQLException {
        return new BatchView(
                rs.getObject("id", UUID.class),
                rs.getObject("profile_id", UUID.class),
                rs.getString("source_format"), rs.getString("original_file_name"),
                rs.getString("original_sha256"), rs.getString("converted_format"),
                rs.getString("converted_sha256"), rs.getString("conversion_tool"),
                rs.getString("status"), (Integer) rs.getObject("source_srs_epsg"),
                rs.getString("srs_source"), rs.getString("datum_transform"),
                rs.getString("ibge_grid_ref"), rs.getBigDecimal("snap_tolerance_m"),
                rs.getBoolean("pilot_homologation"),
                rs.getString("counts_before") == null
                        ? null : Json.readMap(rs.getString("counts_before")),
                rs.getString("counts_after") == null
                        ? null : Json.readMap(rs.getString("counts_after")),
                rs.getObject("change_set_id", UUID.class),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private RawView mapRaw(ResultSet rs, int row) throws SQLException {
        return new RawView(
                rs.getObject("id", UUID.class), rs.getString("source_layer"),
                rs.getString("source_ref"), rs.getString("block_name"),
                rs.getString("label_text"), Json.readMap(rs.getString("attributes")),
                rs.getString("geom_wkt"), rs.getString("status"),
                rs.getObject("layer_map_id", UUID.class),
                rs.getString("published_feature_table"),
                rs.getObject("published_feature_id", UUID.class));
    }

    private LayerMapView mapLayerMap(ResultSet rs, int row) throws SQLException {
        return new LayerMapView(
                rs.getObject("id", UUID.class), rs.getObject("profile_id", UUID.class),
                rs.getString("source_layer"), rs.getString("source_block"),
                rs.getString("text_pattern"), rs.getString("target_kind"),
                rs.getString("feature_table"), rs.getString("feature_type_code"),
                rs.getString("context_kind"), rs.getBoolean("personal_data"),
                rs.getString("anonymization_method"));
    }

    private ContextFeatureView mapContext(ResultSet rs, int row) throws SQLException {
        return new ContextFeatureView(
                rs.getObject("id", UUID.class), rs.getObject("batch_id", UUID.class),
                rs.getString("context_kind"), rs.getString("code"),
                rs.getString("name"), rs.getBoolean("personal_data"),
                rs.getString("anonymization_method"), rs.getString("geom_wkt"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static ApiException translateDbError(DataAccessException e) {
        String message = rootMessage(e);
        if (message.contains("gis: arquivo sem SRS recusado")
                || message.contains("gis: escolha de SRS")
                || message.contains("gis: SAD69/Corrego Alegre")
                || message.contains("gis: ajuste por pontos de controle")) {
            return new ApiException.BadRequest("srs-invalido", message);
        }
        if (message.contains("gis: lote com camada pessoal")) {
            return new ApiException.BadRequest("anonimizacao-nao-registrada", message);
        }
        if (message.contains("gis: transicao de lote")) {
            return new ApiException.Conflict("transicao-de-lote-invalida", message);
        }
        if (message.contains("gis:")) {
            return new ApiException.Conflict("importacao-recusada", message);
        }
        return new ApiException.BadRequest("importacao-falhou", message);
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
