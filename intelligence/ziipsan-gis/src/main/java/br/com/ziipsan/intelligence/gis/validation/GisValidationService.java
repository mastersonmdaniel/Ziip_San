package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ArcFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectivityRule;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.ConnectionFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.MetaFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.NodeFeature;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.ExistingIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.IssueKey;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.Plan;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validação contínua sobre áreas sujas (GIS-03, P2.21, §5.5).
 *
 * <p>Para cada {@code gis_dirty_area} PENDING do tenant: carrega as
 * feições que intersectam o envelope (com um salto de vizinhança — arcos
 * dos nós da área e nós dos arcos da área — porque conectividade se mede
 * na ponta de fora também), roda as validações geométricas em SQL
 * (PostGIS) e o motor puro ({@link GisValidationEngine}), reconcilia com
 * {@code gis_validation_issue} e marca a área DONE.
 *
 * <p>Reconciliação (decisão pura em {@link GisValidationReconciliation},
 * correção I1+I2 da revisão): problema novo abre OPEN; problema OPEN cuja
 * condição some na revalidação vira RESOLVED; problema ACCEPTED cuja
 * condição some também vira RESOLVED, mantendo justificativa e
 * {@code accepted_*} (só {@code status} e {@code resolved_at} mudam);
 * problema ACCEPTED cuja condição persiste continua ACCEPTED, sem
 * duplicar nem reabrir; condição que volta após RESOLVED abre OPEN novo.
 * Dois problemas detectados com a mesma chave na mesma rodada abrem um
 * só, valendo a primeira severidade detectada. Os problemas existentes
 * são carregados pelos ids das feições do retrato da área (não mais por
 * interseção de envelope): problema de feição fora do retrato não é
 * resolvido por esta rodada.
 *
 * <p>Idempotência (exigência da P1.9): processar área já DONE é
 * no-op; reprocessar o lote não duplica problemas.
 *
 * <p>O tenant é parâmetro explícito em todos os métodos — o worker
 * chama com o tenant da mensagem da outbox, sem ThreadLocal (P1.9).
 *
 * <p>Não implementado aqui, por desenho do §5.5: recálculo de zonas
 * derivadas e de setores de manobra e marcação de tiles sujos são das
 * frentes donas dessas tabelas (GIS-11 e GIS-06); esta frente só valida
 * e grava problemas.
 */
@Service
public class GisValidationService {

    /** Tipo da mensagem da outbox que pede a validação de uma área suja (ou a varredura do tenant). */
    public static final String DIRTY_AREA_MESSAGE_TYPE = "gis.dirty-area";

    /** Resultado de um processamento: áreas concluídas, problemas abertos e resolvidos. */
    public record RunSummary(int areasProcessed, int issuesOpened, int issuesResolved) {
    }

    /** Tabelas de feição e a expressão da geometria de cada uma (lista fechada, nunca entrada de usuário). */
    private static final Map<String, String> GEOMETRY_OF = Map.of(
            "gis_node", "geom",
            "gis_arc", "geom",
            "gis_connection", "geom",
            "gis_structure", "COALESCE(geom, geom_point)",
            "gis_zone", "geom",
            "gis_poi", "geom");

    private final JdbcTemplate jdbc;

    public GisValidationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Processa todas as áreas PENDING do tenant, da mais antiga para a mais nova. */
    @Transactional
    public RunSummary processPendingAreas(UUID tenantId) {
        List<UUID> pending = jdbc.queryForList(
                "SELECT id FROM gis_dirty_area WHERE tenant_id = ? AND status = 'PENDING' ORDER BY created_at",
                UUID.class, tenantId);
        int opened = 0;
        int resolved = 0;
        for (UUID areaId : pending) {
            RunSummary one = processAreaInternal(tenantId, areaId);
            opened += one.issuesOpened();
            resolved += one.issuesResolved();
        }
        return new RunSummary(pending.size(), opened, resolved);
    }

    /** Processa uma área; área inexistente ou já DONE devolve zero (idempotente). */
    @Transactional
    public RunSummary processDirtyArea(UUID tenantId, UUID dirtyAreaId) {
        return processAreaInternal(tenantId, dirtyAreaId);
    }

    // ------------------------------------------------------------------ núcleo

    private RunSummary processAreaInternal(UUID tenantId, UUID areaId) {
        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM gis_dirty_area WHERE id = ? AND tenant_id = ? AND status = 'PENDING'",
                Integer.class, areaId, tenantId);
        if (pending == null || pending == 0) {
            return new RunSummary(0, 0, 0);
        }
        // Reivindica a área (PENDING -> PROCESSING -> DONE, ciclo da
        // V13_8). Falha no meio desfaz a transação inteira: a área volta
        // a PENDING fora dela e a mensagem da outbox é tentada de novo —
        // FAILED/process_note da V13_8 fica para o runner do worker
        // registrar por fora da transação que falhou.
        jdbc.update("UPDATE gis_dirty_area SET status = 'PROCESSING' "
                + "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'", areaId, tenantId);

        SnapshotData data = loadSnapshot(tenantId, areaId);
        Set<DetectedIssue> detected = new java.util.LinkedHashSet<>();
        detected.addAll(geometricChecks(tenantId, areaId, data));
        detected.addAll(GisValidationEngine.detect(new GisValidationEngine.Snapshot(
                data.nodes(), data.arcs(), data.connections(), data.metas(),
                data.rules(), data.containedSourceNodeIds())));

        // A ordem de `detected` e a ordem de deteccao (geometricos, depois
        // o motor): e ela que decide a "primeira severidade" da chave
        // duplicada na rodada, na decisao pura de reconciliacao.
        List<ExistingIssue> existing = loadExistingIssues(tenantId, data);
        Plan plan = GisValidationReconciliation.reconcile(existing, List.copyOf(detected));

        int opened = 0;
        for (DetectedIssue issue : plan.toOpen()) {
            insertIssue(tenantId, areaId, issue);
            opened++;
        }
        int resolved = 0;
        for (ExistingIssue old : plan.toResolve()) {
            // OPEN ou ACCEPTED cuja condicao sumiu: so status/resolved_at
            // mudam -- justificativa e accepted_* do aceite sao preservados.
            resolved += jdbc.update("UPDATE gis_validation_issue SET status = 'RESOLVED', resolved_at = now() "
                    + "WHERE id = ? AND tenant_id = ? AND status IN ('OPEN', 'ACCEPTED')", old.id(), tenantId);
        }

        jdbc.update("UPDATE gis_dirty_area SET status = 'DONE', processed_at = now() "
                + "WHERE id = ? AND tenant_id = ? AND status = 'PROCESSING'", areaId, tenantId);
        return new RunSummary(1, opened, resolved);
    }

    /** Retrato carregado da área, já com a vizinhança de um salto. */
    private record SnapshotData(List<NodeFeature> nodes, List<ArcFeature> arcs,
                                List<ConnectionFeature> connections, List<MetaFeature> metas,
                                List<ConnectivityRule> rules, Set<UUID> containedSourceNodeIds) {
    }

    private SnapshotData loadSnapshot(UUID tenantId, UUID areaId) {
        List<NodeFeature> nodes = new ArrayList<>(loadNodesInArea(tenantId, areaId));
        List<ArcFeature> arcs = new ArrayList<>(loadArcsInArea(tenantId, areaId));
        // Vizinhança de um salto: arcos incidentes aos nós da área e nós
        // das pontas dos arcos da área (a ponta de fora decide contagem,
        // domínio, tê do hidrante e componente).
        Set<UUID> nodeIds = nodes.stream().map(NodeFeature::id).collect(Collectors.toSet());
        Set<UUID> arcIds = arcs.stream().map(ArcFeature::id).collect(Collectors.toSet());
        arcs.addAll(loadArcsOfNodes(tenantId, nodeIds, arcIds));
        arcIds.addAll(arcs.stream().map(ArcFeature::id).toList());
        Set<UUID> endpointIds = new HashSet<>();
        arcs.forEach(a -> {
            if (a.node1Id() != null) {
                endpointIds.add(a.node1Id());
            }
            if (a.node2Id() != null) {
                endpointIds.add(a.node2Id());
            }
        });
        endpointIds.removeAll(nodeIds);
        nodes.addAll(loadNodesByIds(tenantId, endpointIds));
        nodeIds.addAll(endpointIds);

        List<ConnectionFeature> connections = loadConnections(tenantId, areaId);
        List<MetaFeature> metas = loadMetas(tenantId, areaId);
        List<ConnectivityRule> rules = loadRules(tenantId);
        Set<UUID> containedSources = loadContainedSourceNodes(tenantId, nodeIds);
        return new SnapshotData(nodes, arcs, connections, metas, rules, containedSources);
    }

    // ------------------------------------------------------------------ carga

    private static final String NODE_COLUMNS = """
            n.id, n.feature_type_id, ft.code AS type_code, fc.code AS class_code,
            fc.domain, fc.topology_role, ft.terminal_config::text AS terminal_config,
            n.lifecycle_status, n.catalog_item_id, ci.dn_mm, ci.dn2_mm,
            m.code AS material_code, COALESCE(m.legacy_only, false) AS legacy_only,
            n.normal_status, n.operational_status, n.invert_elevation_m,
            n.capture_method, n.accuracy_status, n.change_set_id
            """;

    private static final String NODE_FROM = """
            FROM gis_node n
            JOIN gis_feature_type ft ON ft.id = n.feature_type_id AND ft.tenant_id = n.tenant_id
            JOIN gis_feature_class fc ON fc.id = ft.class_id AND fc.tenant_id = n.tenant_id
            LEFT JOIN gis_catalog_item ci ON ci.id = n.catalog_item_id AND ci.tenant_id = n.tenant_id
            LEFT JOIN gis_material m ON m.id = ci.material_id AND m.tenant_id = n.tenant_id
            """;

    private NodeFeature mapNode(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new NodeFeature(
                rs.getObject("id", UUID.class), rs.getObject("feature_type_id", UUID.class),
                rs.getString("type_code"), rs.getString("class_code"), rs.getString("domain"),
                rs.getString("topology_role"), rs.getString("terminal_config"),
                rs.getString("lifecycle_status"), rs.getObject("catalog_item_id", UUID.class),
                (Integer) rs.getObject("dn_mm"), (Integer) rs.getObject("dn2_mm"),
                rs.getString("material_code"), rs.getBoolean("legacy_only"),
                rs.getString("normal_status"), rs.getString("operational_status"),
                rs.getBigDecimal("invert_elevation_m"),
                rs.getString("capture_method"), rs.getString("accuracy_status"),
                rs.getObject("change_set_id", UUID.class));
    }

    private List<NodeFeature> loadNodesInArea(UUID tenantId, UUID areaId) {
        return jdbc.query("SELECT " + NODE_COLUMNS + NODE_FROM + """
                WHERE n.tenant_id = ?
                  AND ST_Intersects(n.geom, (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?))
                """, this::mapNode, tenantId, areaId, tenantId);
    }

    private List<NodeFeature> loadNodesByIds(UUID tenantId, Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        return jdbc.query("SELECT " + NODE_COLUMNS + NODE_FROM
                + "WHERE n.tenant_id = ? AND n.id IN (" + placeholders + ")",
                this::mapNode, args.toArray());
    }

    private static final String ARC_COLUMNS = """
            a.id, a.feature_type_id, ft.code AS type_code, fc.code AS class_code, fc.domain,
            a.lifecycle_status, a.node_1_id, a.node_2_id, ci.dn_mm,
            m.code AS material_code, COALESCE(m.legacy_only, false) AS legacy_only,
            a.network_function, a.pressurized, a.flow_direction,
            a.upstream_invert_m, a.downstream_invert_m,
            a.capture_method, a.accuracy_status, a.change_set_id
            """;

    private static final String ARC_FROM = """
            FROM gis_arc a
            JOIN gis_feature_type ft ON ft.id = a.feature_type_id AND ft.tenant_id = a.tenant_id
            JOIN gis_feature_class fc ON fc.id = ft.class_id AND fc.tenant_id = a.tenant_id
            LEFT JOIN gis_catalog_item ci ON ci.id = a.catalog_item_id AND ci.tenant_id = a.tenant_id
            LEFT JOIN gis_material m ON m.id = ci.material_id AND m.tenant_id = a.tenant_id
            """;

    private ArcFeature mapArc(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ArcFeature(
                rs.getObject("id", UUID.class), rs.getObject("feature_type_id", UUID.class),
                rs.getString("type_code"), rs.getString("class_code"), rs.getString("domain"),
                rs.getString("lifecycle_status"),
                rs.getObject("node_1_id", UUID.class), rs.getObject("node_2_id", UUID.class),
                (Integer) rs.getObject("dn_mm"), rs.getString("material_code"), rs.getBoolean("legacy_only"),
                rs.getString("network_function"), (Boolean) rs.getObject("pressurized"),
                rs.getString("flow_direction"), rs.getBigDecimal("upstream_invert_m"),
                rs.getBigDecimal("downstream_invert_m"),
                rs.getString("capture_method"), rs.getString("accuracy_status"),
                rs.getObject("change_set_id", UUID.class));
    }

    private List<ArcFeature> loadArcsInArea(UUID tenantId, UUID areaId) {
        return jdbc.query("SELECT " + ARC_COLUMNS + ARC_FROM + """
                WHERE a.tenant_id = ?
                  AND ST_Intersects(a.geom, (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?))
                """, this::mapArc, tenantId, areaId, tenantId);
    }

    private List<ArcFeature> loadArcsOfNodes(UUID tenantId, Set<UUID> nodeIds, Set<UUID> alreadyLoaded) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }
        String placeholders = nodeIds.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(nodeIds);
        args.addAll(nodeIds);
        return jdbc.query("SELECT " + ARC_COLUMNS + ARC_FROM
                        + "WHERE a.tenant_id = ? AND (a.node_1_id IN (" + placeholders
                        + ") OR a.node_2_id IN (" + placeholders + "))",
                this::mapArc, args.toArray())
                .stream().filter(a -> !alreadyLoaded.contains(a.id())).toList();
    }

    private List<ConnectionFeature> loadConnections(UUID tenantId, UUID areaId) {
        return jdbc.query("""
                SELECT c.id, ft.code AS type_code, fc.code AS class_code, fc.domain,
                       c.lifecycle_status, c.arc_id, c.tap_node_id, c.service_connection_id,
                       ci.dn_mm, m.code AS material_code, COALESCE(m.legacy_only, false) AS legacy_only,
                       c.capture_method, c.accuracy_status, c.change_set_id
                FROM gis_connection c
                JOIN gis_feature_type ft ON ft.id = c.feature_type_id AND ft.tenant_id = c.tenant_id
                JOIN gis_feature_class fc ON fc.id = ft.class_id AND fc.tenant_id = c.tenant_id
                LEFT JOIN gis_catalog_item ci ON ci.id = c.catalog_item_id AND ci.tenant_id = c.tenant_id
                LEFT JOIN gis_material m ON m.id = ci.material_id AND m.tenant_id = c.tenant_id
                WHERE c.tenant_id = ?
                  AND ST_Intersects(c.geom, (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?))
                """, (rs, row) -> new ConnectionFeature(
                rs.getObject("id", UUID.class), rs.getString("type_code"), rs.getString("class_code"),
                rs.getString("domain"), rs.getString("lifecycle_status"),
                rs.getObject("arc_id", UUID.class), rs.getObject("tap_node_id", UUID.class),
                rs.getObject("service_connection_id", UUID.class),
                (Integer) rs.getObject("dn_mm"), rs.getString("material_code"), rs.getBoolean("legacy_only"),
                rs.getString("capture_method"), rs.getString("accuracy_status"),
                rs.getObject("change_set_id", UUID.class)), tenantId, areaId, tenantId);
    }

    private List<MetaFeature> loadMetas(UUID tenantId, UUID areaId) {
        List<MetaFeature> metas = new ArrayList<>();
        for (String table : List.of("gis_structure", "gis_zone", "gis_poi")) {
            String geom = GEOMETRY_OF.get(table);
            metas.addAll(jdbc.query("""
                    SELECT f.id, ci.dn_mm, COALESCE(m.legacy_only, false) AS legacy_only,
                           f.capture_method, f.accuracy_status, f.change_set_id
                    FROM %s f
                    LEFT JOIN gis_catalog_item ci ON ci.id = f.catalog_item_id AND ci.tenant_id = f.tenant_id
                    LEFT JOIN gis_material m ON m.id = ci.material_id AND m.tenant_id = f.tenant_id
                    WHERE f.tenant_id = ?
                      AND ST_Intersects(%s, (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?))
                    """.formatted(table, geom), (rs, row) -> new MetaFeature(
                    table, rs.getObject("id", UUID.class), (Integer) rs.getObject("dn_mm"),
                    rs.getBoolean("legacy_only"), rs.getString("capture_method"),
                    rs.getString("accuracy_status"), rs.getObject("change_set_id", UUID.class)),
                    tenantId, areaId, tenantId));
        }
        return metas;
    }

    private List<ConnectivityRule> loadRules(UUID tenantId) {
        return jdbc.query("""
                SELECT ft1.code AS from_type, fc1.code AS from_class,
                       ft2.code AS to_type, fc2.code AS to_class,
                       r.relation, r.min_arcs, r.max_arcs, r.condition::text AS condition, r.severity
                FROM gis_connectivity_rule r
                LEFT JOIN gis_feature_type ft1 ON ft1.id = r.from_type_id AND ft1.tenant_id = r.tenant_id
                LEFT JOIN gis_feature_class fc1 ON fc1.id = r.from_class_id AND fc1.tenant_id = r.tenant_id
                LEFT JOIN gis_feature_type ft2 ON ft2.id = r.to_type_id AND ft2.tenant_id = r.tenant_id
                LEFT JOIN gis_feature_class fc2 ON fc2.id = r.to_class_id AND fc2.tenant_id = r.tenant_id
                WHERE r.tenant_id = ?
                """, (rs, row) -> new ConnectivityRule(
                rs.getString("from_type"), rs.getString("from_class"),
                rs.getString("to_type"), rs.getString("to_class"),
                rs.getString("relation"), (Integer) rs.getObject("min_arcs"),
                (Integer) rs.getObject("max_arcs"), rs.getString("condition"),
                rs.getString("severity")), tenantId);
    }

    /** Nós contidos em estrutura-fonte (reservatório, poço, captação, elevatória) via gis_containment. */
    private Set<UUID> loadContainedSourceNodes(UUID tenantId, Set<UUID> nodeIds) {
        if (nodeIds.isEmpty()) {
            return Set.of();
        }
        String sourceCodes = GisValidationEngine.SOURCE_TYPE_CODES.stream()
                .map(c -> "'" + c + "'").collect(Collectors.joining(","));
        String placeholders = nodeIds.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(nodeIds);
        return new HashSet<>(jdbc.queryForList("""
                SELECT c.contained_id FROM gis_containment c
                JOIN gis_structure s ON s.id = c.container_id AND s.tenant_id = c.tenant_id
                JOIN gis_feature_type ft ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
                WHERE c.tenant_id = ? AND c.contained_table = 'gis_node' AND c.relation = 'CONTAINS'
                  AND ft.code IN (%s) AND c.contained_id IN (%s)
                """.formatted(sourceCodes, placeholders), UUID.class, args.toArray()));
    }

    // ------------------------------------------------------------------ PostGIS

    /**
     * Validações geométricas do §5.4 em SQL, restritas às feições da área:
     * geometria inválida em todas as tabelas, autointerseção de arco e
     * arcos duplicados ou sobrepostos (pares dentro da área expandida).
     */
    private List<DetectedIssue> geometricChecks(UUID tenantId, UUID areaId, SnapshotData data) {
        List<DetectedIssue> issues = new ArrayList<>();
        for (String table : GEOMETRY_OF.keySet()) {
            String geom = GEOMETRY_OF.get(table);
            jdbc.query("""
                    SELECT f.id, f.change_set_id FROM %s f
                    WHERE f.tenant_id = ?
                      AND ST_Intersects(%s, (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?))
                      AND NOT ST_IsValid(%s)
                    """.formatted(table, geom, geom), (rs, row) -> {
                issues.add(new DetectedIssue("INVALID_GEOMETRY", GisValidationEngine.ERROR, table,
                        rs.getObject("id", UUID.class), null, rs.getObject("change_set_id", UUID.class)));
                return null;
            }, tenantId, areaId, tenantId);
        }

        Set<UUID> arcIds = data.arcs().stream().map(ArcFeature::id).collect(Collectors.toSet());
        if (!arcIds.isEmpty()) {
            String placeholders = arcIds.stream().map(i -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>();
            args.add(tenantId);
            args.addAll(arcIds);
            jdbc.query("SELECT id, change_set_id FROM gis_arc WHERE tenant_id = ? AND id IN ("
                    + placeholders + ") AND NOT ST_IsSimple(geom)", (rs, row) -> {
                issues.add(new DetectedIssue("SELF_INTERSECTING_ARC", GisValidationEngine.ERROR, "gis_arc",
                        rs.getObject("id", UUID.class), null, rs.getObject("change_set_id", UUID.class)));
                return null;
            }, args.toArray());

            jdbc.query("""
                    SELECT a.id AS id_a, b.id AS id_b, a.change_set_id
                    FROM gis_arc a JOIN gis_arc b
                      ON b.tenant_id = a.tenant_id AND b.id > a.id
                    WHERE a.tenant_id = ? AND a.id IN (%s) AND b.id IN (%s)
                      AND (ST_Equals(a.geom, b.geom) OR ST_Overlaps(a.geom, b.geom)
                           OR ST_Contains(a.geom, b.geom) OR ST_Within(a.geom, b.geom))
                    """.formatted(placeholders, placeholders), (rs, row) -> {
                issues.add(new DetectedIssue("DUPLICATE_OR_OVERLAPPING_ARC", GisValidationEngine.ERROR,
                        "gis_arc", rs.getObject("id_a", UUID.class), rs.getObject("id_b", UUID.class),
                        rs.getObject("change_set_id", UUID.class)));
                return null;
            }, duplicateArgs(tenantId, arcIds));
        }
        return issues;
    }

    private Object[] duplicateArgs(UUID tenantId, Set<UUID> arcIds) {
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(arcIds);
        args.addAll(arcIds);
        return args.toArray();
    }

    // ------------------------------------------------------------------ reconciliação

    /**
     * Problemas OPEN/ACCEPTED das feições do retrato (correção I2 da
     * revisão: por id de feição, agrupados pela tabela da feição — não
     * mais por interseção com o envelope da área, que trazia problema de
     * feição vizinha fora do retrato e o resolvia por engano quando a
     * condição dela não era redetectada nesta rodada). RESOLVED não é
     * carregado: condição que volta depois de resolvida abre OPEN novo
     * (ver {@link GisValidationReconciliation}).
     */
    private List<ExistingIssue> loadExistingIssues(UUID tenantId, SnapshotData data) {
        Map<String, Set<UUID>> idsByTable = new HashMap<>();
        data.nodes().forEach(n -> idsByTable.computeIfAbsent("gis_node", k -> new HashSet<>()).add(n.id()));
        data.arcs().forEach(a -> idsByTable.computeIfAbsent("gis_arc", k -> new HashSet<>()).add(a.id()));
        data.connections().forEach(c -> idsByTable.computeIfAbsent("gis_connection", k -> new HashSet<>()).add(c.id()));
        data.metas().forEach(m -> idsByTable.computeIfAbsent(m.featureTable(), k -> new HashSet<>()).add(m.id()));
        List<ExistingIssue> existing = new ArrayList<>();
        for (Map.Entry<String, Set<UUID>> entry : idsByTable.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String placeholders = entry.getValue().stream().map(i -> "?").collect(Collectors.joining(","));
            List<Object> args = new ArrayList<>();
            args.add(tenantId);
            args.add(entry.getKey());
            args.addAll(entry.getValue());
            existing.addAll(jdbc.query("SELECT id, rule_code, feature_table, feature_id, feature2_id, status "
                    + "FROM gis_validation_issue "
                    + "WHERE tenant_id = ? AND status IN ('OPEN', 'ACCEPTED') "
                    + "AND feature_table = ? AND feature_id IN (" + placeholders + ")",
                    (rs, row) -> new ExistingIssue(
                            rs.getObject("id", UUID.class),
                            new IssueKey(rs.getString("rule_code"), rs.getString("feature_table"),
                                    rs.getObject("feature_id", UUID.class), rs.getObject("feature2_id", UUID.class)),
                            rs.getString("status")), args.toArray()));
        }
        return existing;
    }

    /**
     * Tabela da segunda feição (a V13_8 exige feature2_table e feature2_id
     * juntos ou nenhum): sai da regra — par de arcos, arco × nó ou
     * ramal × arco. Problema sem segunda feição devolve nulo.
     */
    private static String feature2TableOf(DetectedIssue issue) {
        if (issue.feature2Id() == null) {
            return null;
        }
        return switch (issue.ruleCode()) {
            case "DUPLICATE_OR_OVERLAPPING_ARC", "CONNECTION_TO_TRANSMISSION" -> "gis_arc";
            case "DOMAIN_MISMATCH" -> "gis_node";
            default -> issue.featureTable();
        };
    }

    private void insertIssue(UUID tenantId, UUID areaId, DetectedIssue issue) {
        String geom = GEOMETRY_OF.get(issue.featureTable());
        jdbc.update("""
                INSERT INTO gis_validation_issue
                    (id, tenant_id, rule_code, severity, feature_table, feature_id,
                     feature2_table, feature2_id,
                     change_set_id, envelope, status, detected_at, message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,
                    COALESCE(
                        (SELECT ST_Envelope(%s) FROM %s f WHERE f.id = ? AND f.tenant_id = ?),
                        (SELECT envelope FROM gis_dirty_area WHERE id = ? AND tenant_id = ?)),
                    'OPEN', now(), ?)
                """.formatted(geom, issue.featureTable()),
                UUID.randomUUID(), tenantId, issue.ruleCode(), issue.severity(), issue.featureTable(),
                issue.featureId(), feature2TableOf(issue), issue.feature2Id(), issue.changeSetId(),
                issue.featureId(), tenantId, areaId, tenantId, issue.message());
    }
}
