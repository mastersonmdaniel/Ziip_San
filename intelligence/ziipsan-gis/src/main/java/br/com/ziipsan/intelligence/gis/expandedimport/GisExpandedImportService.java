package br.com.ziipsan.intelligence.gis.expandedimport;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.CreateExpandedBatchInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ExpandedBatchView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.GnssPointInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.GnssPointView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchDecisionInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchQueueView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.MatchRequestInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ReconciliationInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.ReconciliationView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.RolloutStageInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.RolloutStageView;
import br.com.ziipsan.intelligence.gis.expandedimport.GisExpandedImportDtos.UnitInput;
import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.MatchResult;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GIS-18 sobre o nucleo da GIS-07: abertura de lote ampliado,
 * casamento de unidades (SG-1349) com fila de revisao, conciliacao
 * base x desenho (SG-1356) e plano de implantacao em tres etapas
 * (SG-1334).
 *
 * <p>Abertura de lote (correcao D3 da revisao cruzada): os formatos
 * da GIS-18 sao abertos AQUI, por {@link #openExpandedBatch}, que
 * aplica {@link GisExpandedImportRules} e grava na mesma tabela
 * {@code gis_import_batch} da GIS-07 (o CHECK de formato ampliado
 * para INP esta no SQL pendente da ED-55). Este servico nao duplica
 * o pipeline de staging/limpeza/publicacao da P3.26: depois de
 * aberto, o lote segue o fluxo da GIS-07. O leitor de INP
 * (EPANET/SWMM) e da GIS-12 (ED-63) e o leitor GDAL (DGN/GNSS) e do
 * worker — a abertura so registra a rota do leitor.
 *
 * <p>JSON sempre pela classe Json do core.
 */
@Service
@Transactional(readOnly = true)
public class GisExpandedImportService {

    private final JdbcTemplate jdbc;

    public GisExpandedImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------- abertura de lote GIS-18

    /**
     * Aceite real de lote ampliado (correcao D3): valida pela regra
     * da GIS-18 e grava o lote RECEIVED em gis_import_batch, com a
     * rota do leitor registrada na resposta. INP sem SRS declarado e
     * recusado aqui (SG-1339) e, sem o SQL pendente da ED-55, tambem
     * pelo CHECK do banco — os dois lados contam a mesma historia.
     */
    @Transactional
    public ExpandedBatchView openExpandedBatch(CreateExpandedBatchInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        String format = in.sourceFormat().trim().toUpperCase();
        try {
            GisExpandedImportRules.ensureExpandedBatch(format,
                    in.convertedFormat(), in.originalSha256(),
                    in.convertedSha256(), in.conversionTool(),
                    in.sourceSrsEpsg(), in.srsSource());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("lote-ampliado-invalido", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_import_batch (id, tenant_id, profile_id,
                            source_format, original_file_name, original_sha256,
                            converted_format, converted_sha256, conversion_tool,
                            source_srs_epsg, srs_source, srs_declared_by,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, tenantId, in.profileId(), format,
                    in.originalFileName(), in.originalSha256(),
                    in.convertedFormat(), in.convertedSha256(), in.conversionTool(),
                    in.sourceSrsEpsg(), in.srsSource(),
                    "OPERATOR_DECLARED".equals(in.srsSource()) ? userId : null,
                    userId, userId);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException.Conflict("lote-ja-importado",
                    "Arquivo ja importado neste tenant (mesmo hash do original).");
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("lote-ampliado-recusado",
                    "Banco recusou o lote ampliado: "
                            + e.getMostSpecificCause().getMessage());
        }
        return getExpandedBatch(id);
    }

    public ExpandedBatchView getExpandedBatch(UUID id) {
        UUID tenantId = tenantId();
        List<ExpandedBatchView> rows = jdbc.query("""
                SELECT id, source_format, original_file_name, status,
                       source_srs_epsg, srs_source
                  FROM gis_import_batch
                 WHERE id = ? AND tenant_id = ?
                """, (rs, n) -> new ExpandedBatchView(
                rs.getObject("id", UUID.class),
                rs.getString("source_format"),
                rs.getString("original_file_name"),
                rs.getString("status"),
                rs.getObject("source_srs_epsg", Integer.class),
                rs.getString("srs_source"),
                routeOf(rs.getString("source_format"))),
                id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Lote de importacao nao encontrado.");
        }
        return rows.get(0);
    }

    /**
     * Ponto GNSS no staging do lote CSV_GNSS (chamador em producao
     * de {@code ensureGnssPoint}, correcao D3): sem metodo de
     * fixacao ou sem precisao positiva, o ponto nao entra; o
     * metadado vai nos atributos da linha crua (JSON via Json).
     */
    @Transactional
    public GnssPointView stageGnssPoint(UUID batchId, GnssPointInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        ExpandedBatchView batch = getExpandedBatch(batchId);
        if (!"CSV_GNSS".equals(batch.sourceFormat())) {
            throw new ApiException.Conflict("lote-nao-gnss",
                    "Pontos GNSS so entram em lote CSV_GNSS (atual "
                            + batch.sourceFormat() + ").");
        }
        try {
            GisExpandedImportRules.ensureGnssPoint(in.fixMethod(), in.accuracyM());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("ponto-gnss-invalido", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = Map.of(
                "gnssFixMethod", in.fixMethod(),
                "gnssAccuracyM", in.accuracyM());
        try {
            jdbc.update("""
                    INSERT INTO gis_import_raw (id, tenant_id, batch_id,
                            source_layer, source_ref, attributes, geom,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb,
                            ST_GeomFromText(?, 4674), ?, ?)
                    """, id, tenantId, batchId, in.sourceLayer(), in.sourceRef(),
                    Json.write(attributes), in.geomWkt(), userId, userId);
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("ponto-gnss-recusado",
                    "Staging recusou o ponto GNSS: "
                            + e.getMostSpecificCause().getMessage());
        }
        return new GnssPointView(id, batchId, in.fixMethod(), in.accuracyM());
    }

    /** Rota do leitor: formatos da GIS-18 tem rota propria; os demais
     *  lotes (GIS-07) seguem o pipeline da P3.26. */
    private static String routeOf(String sourceFormat) {
        return GisExpandedImportRules.isCoveredFormat(sourceFormat)
                ? GisExpandedImportRules.readerRoute(sourceFormat)
                : "GIS-07_PIPELINE";
    }

    // ------------------------------------------------- casamento SG-1349

    @Transactional
    public MatchQueueView enqueueMatch(UUID batchId, MatchRequestInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        List<GisUnitMatching.UnitCandidate> candidates = in.candidates() == null
                ? List.of()
                : in.candidates().stream()
                        .map(u -> new GisUnitMatching.UnitCandidate(
                                u.unitId(), u.registrationKey(), u.address()))
                        .toList();
        MatchResult result = GisUnitMatching.match(
                in.registrationKey(), in.address(), candidates);
        UUID id = UUID.randomUUID();
        // O conjunto avaliado fica gravado no item (correcao D4): e
        // contra ele que a decisao humana MATCHED e validada.
        List<String> evaluatedIds = candidates.stream()
                .map(GisUnitMatching.UnitCandidate::unitId).toList();
        try {
            jdbc.update("""
                    INSERT INTO gis_unit_match_queue (id, tenant_id,
                            batch_id, raw_id, registration_key,
                            normalized_address, candidate_unit_id,
                            candidate_options, match_method, match_status,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """, id, tenantId, batchId, in.rawId(), in.registrationKey(),
                    GisUnitMatching.normalizeAddress(in.address()),
                    result.candidateUnitId() == null ? null
                            : UUID.fromString(result.candidateUnitId()),
                    Json.write(evaluatedIds),
                    result.method().name(), initialStatus(result), userId, userId);
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("casamento-invalido",
                    "Fila de casamento recusou o item: " + e.getMostSpecificCause().getMessage());
        }
        return getMatch(id);
    }

    /**
     * MATCHED automatico so para matricula exata e endereco exato
     * unico (metodo registrado, candidato unico). PREFIX e NONE nascem
     * PENDING_REVIEW; AMBIGUOUS nasce AMBIGUOUS — ambos decididos por
     * pessoa em {@link #decideMatch}.
     */
    private static String initialStatus(MatchResult result) {
        return switch (result.status()) {
            case MATCHED -> "MATCHED";
            case PENDING_REVIEW -> "PENDING_REVIEW";
            case AMBIGUOUS -> "AMBIGUOUS";
        };
    }

    public MatchQueueView getMatch(UUID id) {
        UUID tenantId = tenantId();
        List<MatchQueueView> rows = jdbc.query("""
                SELECT id, batch_id, raw_id, registration_key,
                       normalized_address, candidate_unit_id,
                       match_method, match_status
                  FROM gis_unit_match_queue
                 WHERE id = ? AND tenant_id = ?
                """, (rs, n) -> new MatchQueueView(
                rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("raw_id", UUID.class),
                rs.getString("registration_key"),
                rs.getString("normalized_address"),
                rs.getObject("candidate_unit_id", UUID.class),
                rs.getString("match_method"),
                rs.getString("match_status")), id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Item da fila de casamento nao encontrado.");
        }
        return rows.get(0);
    }

    public List<MatchQueueView> listPendingMatches(UUID batchId) {
        UUID tenantId = tenantId();
        return jdbc.query("""
                SELECT id, batch_id, raw_id, registration_key,
                       normalized_address, candidate_unit_id,
                       match_method, match_status
                  FROM gis_unit_match_queue
                 WHERE batch_id = ? AND tenant_id = ?
                   AND match_status IN ('PENDING_REVIEW', 'AMBIGUOUS')
                 ORDER BY created_at, id
                """, (rs, n) -> new MatchQueueView(
                rs.getObject("id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("raw_id", UUID.class),
                rs.getString("registration_key"),
                rs.getString("normalized_address"),
                rs.getObject("candidate_unit_id", UUID.class),
                rs.getString("match_method"),
                rs.getString("match_status")), batchId, tenantId);
    }

    /** Conjunto avaliado gravado no item (JSON array de ids). */
    private List<String> candidateOptionsOf(UUID id, UUID tenantId) {
        List<String> rows = jdbc.query("""
                SELECT candidate_options::text
                  FROM gis_unit_match_queue
                 WHERE id = ? AND tenant_id = ?
                """, (rs, n) -> rs.getString(1), id, tenantId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return List.of();
        }
        var tree = Json.readTree(rows.get(0));
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        tree.forEach(node -> ids.add(node.asString()));
        return ids;
    }

    /**
     * Decisao humana da fila: MATCHED ou REJECTED, com decisor.
     * MATCHED de item AMBIGUOUS (correcao D4): a pessoa informa o
     * candidato escolhido em {@code candidateUnitId} da decisao; ele
     * e validado contra o conjunto avaliado gravado no item e passa
     * a ser o candidato do item. MATCHED de PENDING_REVIEW pode
     * confirmar o candidato ja registrado ou informar outro do
     * conjunto. REJECTED nao exige candidato.
     */
    @Transactional
    public MatchQueueView decideMatch(UUID id, MatchDecisionInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        String decision = in.decision().trim().toUpperCase();
        if (!decision.equals("MATCHED") && !decision.equals("REJECTED")) {
            throw new ApiException.BadRequest("decisao-invalida",
                    "Decisao da fila e MATCHED ou REJECTED.");
        }
        MatchQueueView current = getMatch(id);
        // REJECTED preserva o candidato registrado (trilha da fila);
        // MATCHED grava o candidato efetivo resolvido abaixo.
        UUID matchedCandidate = current.candidateUnitId();
        if (decision.equals("MATCHED")) {
            String effective;
            try {
                effective = GisUnitMatching.resolveDecisionCandidate(
                        current.candidateUnitId() == null ? null
                                : current.candidateUnitId().toString(),
                        in.candidateUnitId(),
                        candidateOptionsOf(id, tenantId));
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("casamento-sem-candidato",
                        e.getMessage());
            }
            try {
                matchedCandidate = UUID.fromString(effective);
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("casamento-sem-candidato",
                        "Candidato da decisao nao e um identificador de unidade valido.");
            }
        }
        int updated = jdbc.update("""
                UPDATE gis_unit_match_queue
                   SET match_status = ?, candidate_unit_id = ?, decided_by = ?,
                       decided_at = clock_timestamp(), decision_note = ?,
                       updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND tenant_id = ?
                   AND match_status IN ('PENDING_REVIEW', 'AMBIGUOUS')
                """, decision, matchedCandidate, userId, in.note(), userId,
                id, tenantId);
        if (updated == 0) {
            throw new ApiException.Conflict("casamento-ja-decidido",
                    "Item da fila ja decidido ou inexistente.");
        }
        return getMatch(id);
    }

    // ----------------------------------------------- conciliacao SG-1356

    @Transactional
    public ReconciliationView reconcile(UUID batchId, ReconciliationInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        GisUnitMatching.ReconciliationOutcome outcome;
        try {
            outcome = GisUnitMatching.reconcile(in.inBase(), in.inDrawing(),
                    in.baseGeomWkt(), in.drawingGeomWkt(),
                    in.baseAttrDigest(), in.drawingAttrDigest());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("conciliacao-invalida", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> detail = Map.of(
                "baseAttrDigest", String.valueOf(in.baseAttrDigest()),
                "drawingAttrDigest", String.valueOf(in.drawingAttrDigest()));
        jdbc.update("""
                INSERT INTO gis_reconciliation_item (id, tenant_id, batch_id,
                        outcome, base_ref, drawing_ref, detail,
                        created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """, id, tenantId, batchId, outcome.name(), in.baseRef(),
                in.drawingRef(), Json.write(detail), userId, userId);
        return new ReconciliationView(id, batchId, outcome.name(),
                in.baseRef(), in.drawingRef(), null);
    }

    // ------------------------------------------------ implantacao SG-1334

    /**
     * Criacao de etapa do plano (chamador em producao de
     * {@code withinSampleWindow}, correcao D3): etapa de amostra de
     * rede com prazo alem de inicio + 60 dias e recusada na api e,
     * pelo CHECK da tabela, tambem no banco.
     */
    @Transactional
    public RolloutStageView createRolloutStage(RolloutStageInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        if (in.sampleNetwork() && in.startedOn() != null
                && in.sampleDeadlineOn() != null
                && !GisExpandedImportRules.withinSampleWindow(
                        in.startedOn(), in.sampleDeadlineOn())) {
            throw new ApiException.BadRequest("amostra-fora-da-janela",
                    "Amostra de rede: prazo alem de 60 dias do inicio (GIS-18).");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_rollout_plan (id, tenant_id, stage, title,
                            is_sample_network, started_on, sample_deadline_on,
                            created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, tenantId, in.stage(), in.title(), in.sampleNetwork(),
                    in.startedOn() == null ? null
                            : java.sql.Date.valueOf(in.startedOn()),
                    in.sampleDeadlineOn() == null ? null
                            : java.sql.Date.valueOf(in.sampleDeadlineOn()),
                    userId, userId);
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("etapa-recusada",
                    "Banco recusou a etapa: "
                            + e.getMostSpecificCause().getMessage());
        }
        return getRolloutStage(id);
    }

    public RolloutStageView getRolloutStage(UUID id) {
        UUID tenantId = tenantId();
        List<RolloutStageView> rows = jdbc.query("""
                SELECT id, stage, title, status, is_sample_network,
                       started_on, sample_deadline_on, finished_on,
                       training_delivered
                  FROM gis_rollout_plan
                 WHERE id = ? AND tenant_id = ?
                """, this::mapRolloutStage, id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Etapa de implantacao nao encontrada.");
        }
        return rows.get(0);
    }

    private RolloutStageView mapRolloutStage(java.sql.ResultSet rs, int n)
            throws java.sql.SQLException {
        return new RolloutStageView(
                rs.getObject("id", UUID.class),
                rs.getString("stage"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getBoolean("is_sample_network"),
                rs.getDate("started_on") == null ? null
                        : rs.getDate("started_on").toLocalDate(),
                rs.getDate("sample_deadline_on") == null ? null
                        : rs.getDate("sample_deadline_on").toLocalDate(),
                rs.getDate("finished_on") == null ? null
                        : rs.getDate("finished_on").toLocalDate(),
                rs.getBoolean("training_delivered"));
    }

    public List<RolloutStageView> listRolloutStages() {
        UUID tenantId = tenantId();
        return jdbc.query("""
                SELECT id, stage, title, status, is_sample_network,
                       started_on, sample_deadline_on, finished_on,
                       training_delivered
                  FROM gis_rollout_plan
                 WHERE tenant_id = ?
                 ORDER BY stage
                """, this::mapRolloutStage, tenantId);
    }

    private static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisicao");
        }
        return tenantId;
    }

    private static UUID userId() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("sem usuario no contexto da requisicao");
        }
        return userId;
    }
}
