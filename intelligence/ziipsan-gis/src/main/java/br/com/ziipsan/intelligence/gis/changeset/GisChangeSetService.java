package br.com.ziipsan.intelligence.gis.changeset;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ApproveInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ChangeSetView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.CreateInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ItemInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ItemView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.LockInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.LockView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.NetworkAtView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.NetworkFeatureView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.PageView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.PendingReviewView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewDetailView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewView;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pacotes de edição da GIS-04 (§6): CRUD do pacote e dos itens, submissão,
 * revisão por outra pessoa, aprovação que aplica numa só transação (a
 * função {@code gis_apply_change_set} roda na transação desta chamada —
 * qualquer erro desfaz decisão, itens aplicados e histórico juntos),
 * rebase, travas por área e a "rede na data".
 *
 * <p>As guardas do banco (V13_9) são a autoridade: valem para api, QGIS e
 * SQL direto. Este serviço falha cedo com o erro HTTP certo (403 na
 * auto-revisão, 409 no rebase e nos bloqueios de validação) e traduz as
 * recusas do banco para os mesmos códigos quando elas acontecem lá.
 */
@Service
@Transactional(readOnly = true)
public class GisChangeSetService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public GisChangeSetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- pacotes

    @Transactional
    public ChangeSetView create(CreateInput in) {
        UUID tenantId = GisChangeSetContext.tenantId();
        UUID authorId = GisChangeSetContext.userId();
        UUID id = UUID.randomUUID();
        // Domínios validados aqui (correção M4): sem isto, type/channel
        // fora dos CHECKs da V13_9 estouravam como 500 no banco.
        String type = in.type() == null ? "" : in.type().trim().toUpperCase();
        if (!GisChangeSetLifecycle.CHANGE_SET_TYPES.contains(type)) {
            throw new ApiException.BadRequest("tipo-de-pacote-invalido",
                    "Tipo de pacote desconhecido: " + in.type());
        }
        String channel = in.channel() == null ? "" : in.channel().trim().toUpperCase();
        if (!GisChangeSetLifecycle.CHANGE_SET_CHANNELS.contains(channel)) {
            throw new ApiException.BadRequest("canal-de-pacote-invalido",
                    "Canal de pacote desconhecido: " + in.channel());
        }
        String extentWkt = in.extentWkt() == null || in.extentWkt().isBlank()
                ? null : in.extentWkt();
        try {
            jdbc.update("""
                    INSERT INTO gis_change_set (id, tenant_id, type, channel, status,
                            author_id, extent, service_order_ref, project_ref,
                            technical_report, quality_report, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'DRAFT', ?,
                            CASE WHEN ? IS NULL THEN NULL
                                 ELSE ST_GeomFromText(?, 4674) END,
                            ?, ?, ?, ?::jsonb, ?, ?)
                    """, id, tenantId, type, channel, authorId,
                    extentWkt, extentWkt, in.serviceOrderRef(), in.projectRef(),
                    in.technicalReport(),
                    in.qualityReport() == null ? null : Json.write(in.qualityReport()),
                    authorId, authorId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return get(id);
    }

    public ChangeSetView get(UUID id) {
        UUID tenantId = GisChangeSetContext.tenantId();
        List<ChangeSetView> rows = jdbc.query(SELECT_SET + " WHERE cs.id = ? AND cs.tenant_id = ?",
                this::mapSet, id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Pacote de edição não encontrado.");
        }
        return rows.get(0);
    }

    public PageView<ChangeSetView> list(String status, boolean onlyMine, int page, int size) {
        UUID tenantId = GisChangeSetContext.tenantId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        StringBuilder where = new StringBuilder(" WHERE cs.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isBlank()) {
            where.append(" AND cs.status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (onlyMine) {
            where.append(" AND cs.author_id = ?");
            args.add(GisChangeSetContext.userId());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM gis_change_set cs" + where,
                Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<ChangeSetView> content = jdbc.query(SELECT_SET + where
                        + " ORDER BY cs.created_at DESC, cs.id LIMIT ? OFFSET ?",
                this::mapSet, pageArgs.toArray());
        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        return new PageView<>(content, safePage, safeSize, totalElements, totalPages);
    }

    /** Pacotes aguardando revisão — a mesma consulta da P2.28 (PLT-13). */
    public List<PendingReviewView> pendingReview() {
        UUID tenantId = GisChangeSetContext.tenantId();
        return jdbc.query("""
                SELECT change_set_id, type, channel, status, author_id,
                       submitted_at, created_at, item_count
                FROM gis_change_set_pending_review
                WHERE tenant_id = ? ORDER BY created_at
                """, (rs, row) -> new PendingReviewView(
                rs.getObject("change_set_id", UUID.class), rs.getString("type"),
                rs.getString("channel"), rs.getString("status"),
                rs.getObject("author_id", UUID.class), instant(rs, "submitted_at"),
                instant(rs, "created_at"), rs.getLong("item_count")), tenantId);
    }

    // --------------------------------------------------------------- itens

    @Transactional
    public ItemView addItem(UUID changeSetId, ItemInput in) {
        UUID tenantId = GisChangeSetContext.tenantId();
        ChangeSetView set = get(changeSetId);
        ensureAuthor(set);
        String table = in.featureTable().trim();
        if (!GisChangeSetLifecycle.FEATURE_TABLES.contains(table)) {
            throw new ApiException.BadRequest("tabela-de-feicao-invalida",
                    "Tabela de feição desconhecida: " + table);
        }
        String operation = in.operation().trim().toUpperCase();
        FeatureBase base = featureBase(table, in.featureId(), tenantId);
        Long baseVersion = null;
        String baseSnapshot = null;
        if ("INSERT".equals(operation)) {
            if (!"PLANNED".equals(base.lifecycleStatus())) {
                throw new ApiException.Conflict("feicao-nao-planejada",
                        "Item INSERT exige feição PLANNED criada no pacote.");
            }
            if (!changeSetId.equals(base.changeSetId())) {
                throw new ApiException.Conflict("feicao-de-outro-pacote",
                        "Feição PLANNED não pertence a este pacote.");
            }
        } else if ("UPDATE".equals(operation) || "RETIRE".equals(operation)) {
            baseVersion = base.version();
            baseSnapshot = base.snapshotJson();
        } else {
            throw new ApiException.BadRequest("operacao-invalida",
                    "Operação desconhecida: " + operation);
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_change_set_item (id, tenant_id, change_set_id,
                            operation, feature_table, feature_id, base_version,
                            base_snapshot, proposed_state, proposed_geom_wkt,
                            proposed_geom_point_wkt, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                    """, id, tenantId, changeSetId, operation, table, in.featureId(),
                    baseVersion, baseSnapshot,
                    Json.orDefault(in.proposedState(), "{}"),
                    in.proposedGeomWkt(), in.proposedGeomPointWkt(),
                    GisChangeSetContext.userId(), GisChangeSetContext.userId());
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return getItem(changeSetId, id);
    }

    public ItemView getItem(UUID changeSetId, UUID itemId) {
        UUID tenantId = GisChangeSetContext.tenantId();
        List<ItemView> rows = jdbc.query("""
                SELECT id, operation, feature_table, feature_id, base_version,
                       base_snapshot::text AS base_snapshot,
                       proposed_state::text AS proposed_state,
                       proposed_geom_wkt, proposed_geom_point_wkt,
                       validation_result::text AS validation_result
                FROM gis_change_set_item
                WHERE id = ? AND change_set_id = ? AND tenant_id = ?
                """, this::mapItem, itemId, changeSetId, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Item de pacote não encontrado.");
        }
        return rows.get(0);
    }

    public List<ItemView> listItems(UUID changeSetId) {
        UUID tenantId = GisChangeSetContext.tenantId();
        get(changeSetId);
        return jdbc.query("""
                SELECT id, operation, feature_table, feature_id, base_version,
                       base_snapshot::text AS base_snapshot,
                       proposed_state::text AS proposed_state,
                       proposed_geom_wkt, proposed_geom_point_wkt,
                       validation_result::text AS validation_result
                FROM gis_change_set_item
                WHERE change_set_id = ? AND tenant_id = ?
                ORDER BY created_at, id
                """, this::mapItem, changeSetId, tenantId);
    }

    /**
     * Rebase (§6.3): o autor refaz a base do item sobre a versão atual da
     * feição. O estado proposto não muda — quem muda a proposta edita o
     * item em pacote DRAFT/RETURNED.
     */
    @Transactional
    public ItemView rebaseItem(UUID changeSetId, UUID itemId) {
        UUID tenantId = GisChangeSetContext.tenantId();
        ChangeSetView set = get(changeSetId);
        ensureAuthor(set);
        ItemView item = getItem(changeSetId, itemId);
        if ("INSERT".equals(item.operation())) {
            throw new ApiException.Conflict("rebase-sem-base",
                    "Item INSERT não tem versão de base para refazer.");
        }
        FeatureBase base = featureBase(item.featureTable(), item.featureId(), tenantId);
        try {
            jdbc.update("""
                    UPDATE gis_change_set_item
                    SET base_version = ?, base_snapshot = ?::jsonb, updated_at = NOW()
                    WHERE id = ? AND tenant_id = ?
                    """, base.version(), base.snapshotJson(), itemId, tenantId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return getItem(changeSetId, itemId);
    }

    // ------------------------------------------------------------- revisão

    @Transactional
    public ChangeSetView submit(UUID id) {
        ChangeSetView set = get(id);
        ensureAuthor(set);
        transition(id, set.status(), "SUBMITTED");
        jdbc.update("UPDATE gis_change_set SET submitted_at = clock_timestamp() WHERE id = ? AND tenant_id = ?",
                id, GisChangeSetContext.tenantId());
        transition(id, "SUBMITTED", "IN_REVIEW");
        return get(id);
    }

    /** Tela de revisão: pacote, antes/depois por feição, decisões e problemas. */
    public ReviewDetailView reviewDetail(UUID id) {
        UUID tenantId = GisChangeSetContext.tenantId();
        ChangeSetView set = get(id);
        List<ReviewView> reviews = jdbc.query("""
                SELECT id, decision, reviewer_id, reason, created_at
                FROM gis_change_set_review
                WHERE change_set_id = ? AND tenant_id = ? ORDER BY created_at
                """, (rs, row) -> new ReviewView(rs.getObject("id", UUID.class),
                rs.getString("decision"), rs.getObject("reviewer_id", UUID.class),
                rs.getString("reason"), instant(rs, "created_at")), id, tenantId);
        List<Map<String, Object>> issues = jdbc.query("""
                SELECT rule_code, severity, feature_table, feature_id::text AS feature_id,
                       status, message
                FROM gis_validation_issue
                WHERE change_set_id = ? AND tenant_id = ? ORDER BY detected_at
                """, (rs, row) -> Map.<String, Object>of(
                "ruleCode", rs.getString("rule_code"),
                "severity", rs.getString("severity"),
                "featureTable", rs.getString("feature_table"),
                "featureId", rs.getString("feature_id"),
                "status", rs.getString("status"),
                "message", rs.getString("message") == null ? "" : rs.getString("message")),
                id, tenantId);
        return new ReviewDetailView(set, listItems(id), reviews, issues);
    }

    /**
     * Aprova e aplica numa só transação: delega à função do banco, que
     * confere revisor ≠ autor, bloqueios de validação e versão de base de
     * cada item antes de gravar qualquer feição.
     */
    @Transactional
    public ChangeSetView approve(UUID id, ApproveInput in) {
        ChangeSetView set = get(id);
        UUID reviewerId = GisChangeSetContext.userId();
        try {
            GisChangeSetLifecycle.ensureReviewerIsNotAuthor(set.authorId(), reviewerId);
        } catch (GisChangeSetLifecycle.SelfReviewException e) {
            throw new ApiException.Forbidden(e.getMessage());
        }
        try {
            jdbc.queryForObject("SELECT gis_apply_change_set(?, ?, ?)",
                    String.class, id, reviewerId,
                    in == null ? null : in.warningJustification());
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return get(id);
    }

    @Transactional
    public ChangeSetView returnToAuthor(UUID id, ReviewInput in) {
        return decide(id, in, "RETURNED");
    }

    @Transactional
    public ChangeSetView reject(UUID id, ReviewInput in) {
        return decide(id, in, "REJECTED");
    }

    private ChangeSetView decide(UUID id, ReviewInput in, String newStatus) {
        UUID tenantId = GisChangeSetContext.tenantId();
        ChangeSetView set = get(id);
        UUID reviewerId = GisChangeSetContext.userId();
        try {
            GisChangeSetLifecycle.ensureReviewerIsNotAuthor(set.authorId(), reviewerId);
        } catch (GisChangeSetLifecycle.SelfReviewException e) {
            throw new ApiException.Forbidden(e.getMessage());
        }
        if (in == null || in.reason() == null || in.reason().isBlank()) {
            throw new ApiException.BadRequest("motivo-obrigatorio",
                    "Devolução e rejeição exigem o motivo do revisor.");
        }
        String decision = "RETURNED".equals(newStatus) ? "RETURNED" : "REJECTED";
        try {
            jdbc.update("""
                    INSERT INTO gis_change_set_review (id, tenant_id, change_set_id,
                            decision, reviewer_id, reason, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), tenantId, id, decision, reviewerId,
                    in.reason(), reviewerId, reviewerId);
            transition(id, set.status(), newStatus);
            jdbc.update("""
                    UPDATE gis_change_set SET reviewed_by = ?, reviewed_at = clock_timestamp()
                    WHERE id = ? AND tenant_id = ?
                    """, reviewerId, id, tenantId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        return get(id);
    }

    @Transactional
    public ChangeSetView cancel(UUID id) {
        ChangeSetView set = get(id);
        ensureAuthor(set);
        transition(id, set.status(), "CANCELLED");
        return get(id);
    }

    // --------------------------------------------------------------- travas

    @Transactional
    public LockView createLock(UUID changeSetId, LockInput in) {
        UUID tenantId = GisChangeSetContext.tenantId();
        // Correção M3: travar a área é escrita do pacote — só o autor,
        // como em addItem/rebase/cancel. Sem isto, qualquer usuário com
        // papel EDIT travava a área de pacote alheio.
        ChangeSetView set = get(changeSetId);
        ensureAuthor(set);
        // Correção M4: expiração no passado estourava o CHECK do banco
        // como 500; é erro do cliente (400).
        if (in.expiresAt() == null || !in.expiresAt().isAfter(Instant.now())) {
            throw new ApiException.BadRequest("expiracao-da-trava-invalida",
                    "A trava precisa expirar no futuro.");
        }
        UUID id = UUID.randomUUID();
        UUID ownerId = GisChangeSetContext.userId();
        try {
            jdbc.update("""
                    INSERT INTO gis_edit_lock (id, tenant_id, change_set_id, geom,
                            owner_id, expires_at, created_by, updated_by)
                    VALUES (?, ?, ?, ST_GeomFromText(?, 4674), ?, ?, ?, ?)
                    """, id, tenantId, changeSetId, in.geomWkt(), ownerId,
                    Timestamp.from(in.expiresAt()), ownerId, ownerId);
        } catch (DataAccessException e) {
            throw translateDbError(e);
        }
        List<LockView> rows = jdbc.query("""
                SELECT id, change_set_id, ST_AsText(geom) AS geom_wkt, owner_id, expires_at
                FROM gis_edit_lock WHERE id = ? AND tenant_id = ?
                """, this::mapLock, id, tenantId);
        return rows.get(0);
    }

    public List<LockView> listLocks(UUID changeSetId) {
        UUID tenantId = GisChangeSetContext.tenantId();
        get(changeSetId);
        return jdbc.query("""
                SELECT id, change_set_id, ST_AsText(geom) AS geom_wkt, owner_id, expires_at
                FROM gis_edit_lock WHERE change_set_id = ? AND tenant_id = ?
                ORDER BY created_at
                """, this::mapLock, changeSetId, tenantId);
    }

    @Transactional
    public void releaseLock(UUID changeSetId, UUID lockId) {
        UUID tenantId = GisChangeSetContext.tenantId();
        // Correção M3: liberar a trava também é escrita do pacote (autor).
        ensureAuthor(get(changeSetId));
        int removed = jdbc.update(
                "DELETE FROM gis_edit_lock WHERE id = ? AND change_set_id = ? AND tenant_id = ?",
                lockId, changeSetId, tenantId);
        if (removed == 0) {
            throw new ApiException.NotFound("Trava não encontrada.");
        }
    }

    // --------------------------------------------------------- rede na data

    public NetworkAtView networkAt(LocalDate businessDate, Instant recordedAt) {
        GisChangeSetContext.tenantId();
        if ((businessDate == null) == (recordedAt == null)) {
            throw new ApiException.BadRequest("parametro-de-data",
                    "Informe exatamente um de date (data de negócio) ou recorded-at (data de registro).");
        }
        List<NetworkFeatureView> features;
        if (businessDate != null) {
            features = jdbc.query("""
                    SELECT feature_table, feature_id, code, lifecycle_status,
                           snapshot::text AS snapshot
                    FROM gis_network_at_business(?) ORDER BY feature_table, code
                    """, this::mapNetworkFeature, java.sql.Date.valueOf(businessDate));
            return new NetworkAtView("BUSINESS_DATE", businessDate, null, features);
        }
        features = jdbc.query("""
                SELECT feature_table, feature_id, code, lifecycle_status,
                       snapshot::text AS snapshot
                FROM gis_network_at_recorded(?) ORDER BY feature_table, code
                """, this::mapNetworkFeature, Timestamp.from(recordedAt));
        return new NetworkAtView("RECORDED_AT", null, recordedAt, features);
    }

    // ------------------------------------------------------------- internos

    private static final String SELECT_SET = """
            SELECT cs.id, cs.type, cs.channel, cs.status, cs.author_id,
                   ST_AsText(cs.extent) AS extent_wkt, cs.service_order_ref,
                   cs.project_ref, cs.technical_report,
                   cs.quality_report::text AS quality_report,
                   cs.submitted_at, cs.reviewed_by, cs.reviewed_at,
                   cs.applied_at, cs.created_at, cs.updated_at,
                   (SELECT count(*) FROM gis_change_set_item i
                     WHERE i.change_set_id = cs.id AND i.tenant_id = cs.tenant_id) AS item_count
            FROM gis_change_set cs
            """;

    private record FeatureBase(long version, String lifecycleStatus, UUID changeSetId,
                               String snapshotJson) {
    }

    private FeatureBase featureBase(String table, UUID featureId, UUID tenantId) {
        // 'table' ja passou pela lista fechada de FEATURE_TABLES.
        List<FeatureBase> rows = jdbc.query(
                "SELECT version, lifecycle_status, change_set_id, to_jsonb(f)::text AS snap"
                        + " FROM " + table + " f WHERE f.id = ? AND f.tenant_id = ?",
                (rs, row) -> new FeatureBase(rs.getLong("version"),
                        rs.getString("lifecycle_status"),
                        rs.getObject("change_set_id", UUID.class),
                        rs.getString("snap")),
                featureId, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Feição não encontrada em " + table + ".");
        }
        return rows.get(0);
    }

    private void transition(UUID id, String from, String to) {
        if (!GisChangeSetLifecycle.canTransitionChangeSet(from, to)) {
            throw new ApiException.Conflict("transicao-invalida",
                    "Pacote em " + from + " não pode ir para " + to + ".");
        }
        int updated = jdbc.update("""
                UPDATE gis_change_set SET status = ?, updated_at = clock_timestamp()
                WHERE id = ? AND tenant_id = ? AND status = ?
                """, to, id, GisChangeSetContext.tenantId(), from);
        if (updated == 0) {
            throw new ApiException.Conflict("transicao-invalida",
                    "Pacote mudou de estado durante a operação; recarregue.");
        }
    }

    private void ensureAuthor(ChangeSetView set) {
        if (!set.authorId().equals(GisChangeSetContext.userId())) {
            throw new ApiException.Forbidden("Somente o autor edita o pacote.");
        }
    }

    /**
     * Traduz as recusas do banco para o erro HTTP equivalente (correção
     * M4: o mapa cobre TODAS as mensagens nomeadas {@code gis: ...} da
     * V13_9 e cai para o SQLState nas violações sem nome — CHECK vira
     * 400, FK 409, UNIQUE 409. Sem tradução, a recusa vazava como 500:
     * era o caso de decidir pacote em DRAFT, de type/channel fora dos
     * CHECKs e de trava com expiração no passado).
     */
    private RuntimeException translateDbError(DataAccessException e) {
        String message = e.getMostSpecificCause() == null
                ? String.valueOf(e.getMessage())
                : String.valueOf(e.getMostSpecificCause().getMessage());
        if (message.contains("autor nao aprova") || message.contains("autor nao revisa")) {
            return new ApiException.Forbidden(
                    "Autor não revisa o próprio pacote (revisão por outra pessoa, GIS-04).");
        }
        if (message.contains("rebase exigido")) {
            return new ApiException.Conflict("rebase-exigido", message);
        }
        if (message.contains("severidade erro")) {
            return new ApiException.Conflict("validacao-com-erro", message);
        }
        if (message.contains("aviso exige justificativa")) {
            return new ApiException.Conflict("aviso-exige-justificativa", message);
        }
        if (message.contains("travada")) {
            return new ApiException.Conflict("area-travada", message);
        }
        if (message.contains("transicao de pacote invalida")
                || message.contains("transicao de ciclo de vida invalida")) {
            return new ApiException.Conflict("transicao-invalida", message);
        }
        if (message.contains("nao pode ser aprovado")) {
            return new ApiException.Conflict("pacote-nao-aprovavel", message);
        }
        if (message.contains("nao aceita revisao")) {
            return new ApiException.Conflict("pacote-nao-aceita-revisao", message);
        }
        if (message.contains("itens so mudam")) {
            return new ApiException.Conflict("pacote-fechado-para-itens", message);
        }
        if (message.contains("trava exige pacote aberto")) {
            return new ApiException.Conflict("pacote-fechado-para-trava", message);
        }
        if (message.contains("nao pode ser apagad")) {
            return new ApiException.Conflict("registro-imutavel", message);
        }
        if (message.contains("atributos propostos desconhecidos")) {
            return new ApiException.Conflict("atributos-propostos-invalidos", message);
        }
        if (message.contains("ja esta em")) {
            return new ApiException.Conflict("item-inaplicavel", message);
        }
        if (message.contains("nao encontrad")) {
            return new ApiException.NotFound(message);
        }
        String sqlState = sqlState(e);
        if ("23514".equals(sqlState)) {
            return new ApiException.BadRequest("violacao-de-dominio", message);
        }
        if ("23503".equals(sqlState)) {
            return new ApiException.Conflict("referencia-invalida", message);
        }
        if ("23505".equals(sqlState)) {
            return new ApiException.Conflict("registro-duplicado", message);
        }
        return e;
    }

    /** SQLState da causa raiz, se ela for um SQLException. */
    private static String sqlState(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }

    private ChangeSetView mapSet(ResultSet rs, int row) throws SQLException {
        return new ChangeSetView(
                rs.getObject("id", UUID.class), rs.getString("type"),
                rs.getString("channel"), rs.getString("status"),
                rs.getObject("author_id", UUID.class), rs.getString("extent_wkt"),
                rs.getString("service_order_ref"), rs.getString("project_ref"),
                rs.getString("technical_report"),
                rs.getString("quality_report") == null
                        ? Map.of() : Json.readMap(rs.getString("quality_report")),
                instant(rs, "submitted_at"), rs.getObject("reviewed_by", UUID.class),
                instant(rs, "reviewed_at"), instant(rs, "applied_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("item_count"));
    }

    private ItemView mapItem(ResultSet rs, int row) throws SQLException {
        return new ItemView(
                rs.getObject("id", UUID.class), rs.getString("operation"),
                rs.getString("feature_table"), rs.getObject("feature_id", UUID.class),
                rs.getObject("base_version", Long.class),
                rs.getString("base_snapshot") == null
                        ? Map.of() : Json.readMap(rs.getString("base_snapshot")),
                Json.readMap(rs.getString("proposed_state")),
                rs.getString("proposed_geom_wkt"), rs.getString("proposed_geom_point_wkt"),
                rs.getString("validation_result") == null
                        ? Map.of() : Json.readMap(rs.getString("validation_result")));
    }

    private LockView mapLock(ResultSet rs, int row) throws SQLException {
        return new LockView(rs.getObject("id", UUID.class),
                rs.getObject("change_set_id", UUID.class), rs.getString("geom_wkt"),
                rs.getObject("owner_id", UUID.class), instant(rs, "expires_at"));
    }

    private NetworkFeatureView mapNetworkFeature(ResultSet rs, int row) throws SQLException {
        return new NetworkFeatureView(rs.getString("feature_table"),
                rs.getObject("feature_id", UUID.class), rs.getString("code"),
                rs.getString("lifecycle_status"), Json.readMap(rs.getString("snapshot")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
