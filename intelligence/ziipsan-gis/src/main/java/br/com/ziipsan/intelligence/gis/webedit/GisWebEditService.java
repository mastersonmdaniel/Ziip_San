package br.com.ziipsan.intelligence.gis.webedit;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.webedit.GisDrawnConnectionSync.DrawnConnectionPort;
import br.com.ziipsan.intelligence.gis.webedit.GisGeom.Pt;
import br.com.ziipsan.intelligence.gis.webedit.GisGeom.SplitResult;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.DrawnSyncView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinResultView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinRowView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.LayerPermissionView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.MergeInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.MergeView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.OpenForServiceOrderInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.SplitInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.SplitView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.TransformInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.TransformView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerView;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Editor web completo da GIS-09 (ED-53): transformações e
 * operações topológicas puras (GisGeom), permissões por camada e
 * operação (SG-864/SG-865, tabela gis_layer_permission do SQL em
 * db/pendente), camada do usuário e junção de tabelas
 * (SG-840/SG-841), cadastro de unidades ETA/ETE/elevatórias/
 * reservatório (SG-148..152), projetos e obras (SG-858,
 * TR-1050/TR-1051), pacote aberto por OS que altera a rede
 * (TR-626) e sincronização do ramal desenhado (SG-866/TR-1053).
 *
 * <p>A gravação de feições da rede NÃO acontece aqui: segue a
 * GIS-04 — o editor monta itens no pacote (gis_change_set_item,
 * V13_9) e a aplicação é a da P2.22, com revisão por outra
 * pessoa. Este serviço cuida das superfícies próprias da GIS-09
 * e das operações geométricas sem efeito colateral.
 *
 * <p>Correções da revisão cruzada aplicadas nesta versão:
 * <ul>
 *   <li><b>D1</b> — a guarda camada×operação
 *   ({@link #ensureLayerOperation}) é CHAMADA por toda escrita,
 *   antes de escrever, e todo contrato de escrita carrega
 *   {@code layerId} (ver GisWebEditDtos). Sem permissão na
 *   camada para a operação: 403 e nenhuma escrita.</li>
 *   <li><b>D2</b> — o cadastro de unidades lê o
 *   {@code attributes_schema} real do tipo via
 *   {@link GisFeatureTypeSchemaPort} e valida contra ele; tipo
 *   inexistente no tenant → 404; tipo sem esquema com campos →
 *   recusa explícita (400 esquema-nao-cadastrado). Validação
 *   contra mapa vazio nunca mais acontece.</li>
 *   <li><b>D4</b> — o sync do ramal desenhado confere a
 *   elegibilidade ({@code shouldSync}: só item de gis_connection
 *   de pacote APPLIED) e a máquina de estados
 *   ({@code ensureTransition}) antes de gravar.</li>
 *   <li><b>D5</b> — createProject CRIA e vincula os dois pacotes
 *   (PROJECT e AS_BUILT) de verdade; gis_user_feature tem
 *   caminho de escrita/leitura; a junção (SG-841) é EXECUTÁVEL
 *   ({@link #executeJoin}), não só definição.</li>
 *   <li><b>N1</b> (2ª revisão) — as LEITURAS escopadas a uma
 *   camada também passam pela guarda camada×operação com VIEW,
 *   antes de responder: feições da camada do usuário
 *   ({@link #listUserFeatures}), execução de junção
 *   ({@link #executeJoin}) e a listagem de camadas do usuário
 *   ({@link #listUserLayers} — camada sem VIEW é retida, zero
 *   dados dela). Leitura de projeto é a exceção declarada em
 *   {@link #getProject}: gis_project não tem coluna de camada,
 *   logo a guarda por camada não se aplica ali.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GisWebEditService {

    private static final double DEFAULT_TOLERANCE = 1.0e-6;

    private final JdbcTemplate jdbc;
    private final Optional<DrawnConnectionPort> drawnPort;
    private final GisFeatureTypeSchemaPort schemaPort;

    public GisWebEditService(JdbcTemplate jdbc,
                             Optional<DrawnConnectionPort> drawnPort,
                             GisFeatureTypeSchemaPort schemaPort) {
        this.jdbc = jdbc;
        this.drawnPort = drawnPort;
        this.schemaPort = schemaPort;
    }

    // ------------------------------------------- operações puras

    /** Mover/rotacionar/escalar/mover vértice (SG-838/839, TR-1026/1027). */
    public TransformView transform(TransformInput in) {
        guard(in.layerId(), "EDIT_GEOMETRY");
        List<Pt> shape = parseShape(in.geomWkt());
        Pt pivot = in.pivotX() != null && in.pivotY() != null
                ? new Pt(in.pivotX(), in.pivotY()) : GisGeom.centroid(shape);
        List<Pt> out = switch (in.mode() == null ? "" : in.mode().toUpperCase()) {
            case "MOVE" -> GisGeom.translate(shape,
                    req(in.dx(), "dx"), req(in.dy(), "dy"));
            case "ROTATE" -> GisGeom.rotate(shape, pivot, req(in.degrees(), "degrees"));
            case "SCALE" -> GisGeom.scale(shape, pivot, req(in.factor(), "factor"));
            case "VERTEX" -> GisGeom.moveVertex(shape,
                    in.vertexIndex() == null
                            ? failInt("vertexIndex") : in.vertexIndex(),
                    new Pt(req(in.toX(), "toX"), req(in.toY(), "toY")));
            default -> throw new ApiException.BadRequest(
                    "modo-de-transformacao-invalido",
                    "Modo desconhecido: " + in.mode());
        };
        return new TransformView(toWktLike(in.geomWkt(), out));
    }

    /** Dividir arco / inserir nó que segmenta. */
    public SplitView split(SplitInput in, boolean insertNode) {
        guard(in.layerId(), insertNode ? "INSERT_NODE" : "SPLIT");
        List<Pt> line = GisGeom.parseLine(in.geomWkt());
        double tol = in.tolerance() == null ? DEFAULT_TOLERANCE : in.tolerance();
        Pt at = new Pt(in.atX(), in.atY());
        SplitResult r = insertNode
                ? GisGeom.insertNodeAt(line, at, tol)
                : GisGeom.splitLineAt(line, at, tol);
        return new SplitView(GisGeom.toLineWkt(r.first()),
                GisGeom.toLineWkt(r.second()), GisGeom.toPointWkt(r.node()));
    }

    /** Unir arcos com extremidade comum. */
    public MergeView merge(MergeInput in) {
        guard(in.layerId(), "MERGE");
        double tol = in.tolerance() == null ? DEFAULT_TOLERANCE : in.tolerance();
        List<Pt> merged = GisGeom.mergeLines(
                GisGeom.parseLine(in.firstWkt()),
                GisGeom.parseLine(in.secondWkt()), tol);
        return new MergeView(GisGeom.toLineWkt(merged));
    }

    // --------------------------------- permissões SG-864/SG-865

    public List<LayerPermissionView> myLayerPermissions() {
        // Correção N1 (2ª revisão): esta leitura NÃO passa pela
        // guarda camada×operação — de propósito. Ela devolve
        // SOMENTE as concessões do próprio chamador, filtradas
        // por tenant_id + user_id do contexto (nenhum dado de
        // outro usuário/tenant), e é a superfície de DESCOBERTA
        // das permissões: não é escopada a uma camada (não
        // carrega layerId), e exigir VIEW numa camada para
        // listar as próprias concessões seria circular.
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT layer_id, layer_key, operation
                FROM gis_layer_permission
                WHERE tenant_id = ? AND user_id = ?
                ORDER BY layer_key, operation
                """, tenantId, userId);
        Map<String, List<String>> byLayer = new LinkedHashMap<>();
        Map<String, UUID> layerIds = new LinkedHashMap<>();
        Map<String, String> layerKeys = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String group = row.get("layer_id") + "|" + row.get("layer_key");
            byLayer.computeIfAbsent(group, g -> new ArrayList<>())
                    .add((String) row.get("operation"));
            layerIds.put(group, (UUID) row.get("layer_id"));
            layerKeys.put(group, (String) row.get("layer_key"));
        }
        List<LayerPermissionView> out = new ArrayList<>();
        byLayer.forEach((group, ops) -> out.add(new LayerPermissionView(
                layerIds.get(group), layerKeys.get(group), List.copyOf(ops))));
        return out;
    }

    /**
     * Guarda camada×operação (SG-864/865).
     * Correção D1: chamada por TODA operação de escrita deste
     * serviço (via {@link #guard}) antes de qualquer INSERT/
     * UPDATE — sem linha em gis_layer_permission para
     * (tenant, usuário, camada, operação), a escrita é recusada
     * com 403 e nada é gravado.
     * Correção N1 (2ª revisão): a MESMA guarda é chamada pelas
     * leituras escopadas a uma camada, com a operação VIEW,
     * antes de qualquer dado da camada ser devolvido
     * (listUserFeatures, executeJoin e, por camada listada,
     * listUserLayers).
     */
    public void ensureLayerOperation(UUID layerId, String operation) {
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        Set<String> granted = new HashSet<>(jdbc.queryForList("""
                SELECT operation FROM gis_layer_permission
                WHERE tenant_id = ? AND user_id = ? AND layer_id = ?
                """, String.class, tenantId, userId, layerId));
        try {
            GisWebEditRules.ensureCanOperate(granted, operation);
        } catch (GisWebEditRules.LayerPermissionException e) {
            throw new ApiException.Forbidden(e.getMessage());
        }
    }

    /** Ponto único de fiação da guarda (D1): layerId é obrigatório. */
    private void guard(UUID layerId, String operation) {
        if (layerId == null) {
            throw new ApiException.BadRequest("camada-ausente",
                    "Operação de escrita exige layerId (SG-864/SG-865).");
        }
        ensureLayerOperation(layerId, operation);
    }

    // ------------------------------------- camada do usuário/junção

    /**
     * Cria a camada do usuário (SG-840). A camada NASCE do dono —
     * não há camada prévia contra a qual conferir permissão; o
     * que o serviço faz é registrar a concessão total do dono na
     * gis_layer_permission, para que TODA escrita posterior
     * nesta camada passe pela mesma guarda D1 (inclusive as do
     * próprio dono: revogar a linha revoga a escrita).
     */
    @Transactional
    public UserLayerView createUserLayer(UserLayerInput in) {
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        GisWebEditRules.ensureGeometryFits("USER", in.geometryType());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_user_layer (id, tenant_id, owner_id, name,
                        geometry_type, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, userId, in.name(), in.geometryType(),
                userId, userId);
        for (String operation : GisWebEditRules.OPERATIONS) {
            jdbc.update("""
                    INSERT INTO gis_layer_permission (tenant_id, layer_id,
                            layer_key, user_id, operation, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, tenantId, id, in.name(), userId, operation, userId);
        }
        return new UserLayerView(id, in.name(), in.geometryType(), userId);
    }

    /**
     * Lista as camadas do usuário (SG-840) — correção N1 (2ª
     * revisão): além do filtro tenant + dono, cada camada só é
     * devolvida se o chamador tiver a permissão VIEW nela
     * (guarda camada×operação, o mesmo padrão D1 das escritas).
     * Camada do dono cuja concessão VIEW foi revogada é RETIDA
     * da listagem — zero dados dela (nem nome nem tipo de
     * geometria) — em vez de vazar pela simples posse. O dono
     * recebe todas as operações na criação da camada
     * (createUserLayer), então o caso normal não muda.
     */
    public List<UserLayerView> listUserLayers() {
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        return jdbc.queryForList("""
                SELECT id, name, geometry_type, owner_id FROM gis_user_layer
                WHERE tenant_id = ? AND owner_id = ? ORDER BY name
                """, tenantId, userId).stream()
                .map(row -> new UserLayerView(
                        (UUID) row.get("id"), (String) row.get("name"),
                        (String) row.get("geometry_type"),
                        (UUID) row.get("owner_id")))
                .filter(layer -> hasLayerOperation(layer.id(), "VIEW"))
                .toList();
    }

    /**
     * Versão booleana da guarda, para leituras em COLEÇÃO
     * (listUserLayers): a camada sem a permissão é retida da
     * resposta em vez de derrubar a listagem inteira com 403.
     * Leituras de UMA camada (feições, junção) usam a guarda
     * direta e recusam com 403.
     */
    private boolean hasLayerOperation(UUID layerId, String operation) {
        try {
            ensureLayerOperation(layerId, operation);
            return true;
        } catch (ApiException.Forbidden e) {
            return false;
        }
    }

    /**
     * Escrita de feição na camada do usuário (SG-840) — correção
     * D5: gis_user_feature tinha tabela e nenhum caminho. Exige
     * dono da camada E permissão CREATE na camada (guarda D1);
     * a geometria tem de ser do tipo declarado da camada.
     */
    @Transactional
    public UserFeatureView addUserFeature(UUID layerId, UserFeatureInput in) {
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        UserLayerView layer = requireOwnedUserLayer(tenantId, userId, layerId);
        ensureLayerOperation(layerId, "CREATE");
        String geometryType = geometryTypeOf(in.geomWkt());
        if (!geometryType.equals(layer.geometryType())) {
            throw new ApiException.BadRequest("geometria-incompativel-camada",
                    "Geometria " + geometryType + " incompativel com a camada "
                            + layer.geometryType() + ".");
        }
        parseShape(in.geomWkt());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_user_feature (id, tenant_id, layer_id, geom,
                        attributes, created_by, updated_by)
                VALUES (?, ?, ?, ST_GeomFromText(?, 4674),
                        CAST(? AS jsonb), ?, ?)
                """, id, tenantId, layerId, in.geomWkt(),
                Json.orDefault(in.attributes(), "{}"), userId, userId);
        return new UserFeatureView(id, layerId, in.geomWkt(),
                in.attributes() == null ? Map.of() : in.attributes());
    }

    /**
     * Leitura das feições da própria camada (SG-840) — correção
     * N1 (2ª revisão): leitura escopada a uma camada passa pela
     * guarda camada×operação com VIEW (após a checagem de dono
     * e ANTES da consulta às feições): sem VIEW, 403 e nenhum
     * dado da camada é lido nem devolvido.
     */
    public List<UserFeatureView> listUserFeatures(UUID layerId) {
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        requireOwnedUserLayer(tenantId, userId, layerId);
        ensureLayerOperation(layerId, "VIEW");
        return jdbc.queryForList("""
                SELECT id, ST_AsText(geom) AS geom_wkt,
                       attributes::text AS attributes_json
                FROM gis_user_feature
                WHERE tenant_id = ? AND layer_id = ?
                ORDER BY created_at
                """, tenantId, layerId).stream()
                .map(row -> new UserFeatureView(
                        (UUID) row.get("id"), layerId,
                        (String) row.get("geom_wkt"),
                        Json.readMap((String) row.get("attributes_json"))))
                .toList();
    }

    private UserLayerView requireOwnedUserLayer(UUID tenantId, UUID userId,
                                                UUID layerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, geometry_type, owner_id FROM gis_user_layer
                WHERE tenant_id = ? AND id = ?
                """, tenantId, layerId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Camada do usuário não encontrada.");
        }
        Map<String, Object> row = rows.get(0);
        UserLayerView layer = new UserLayerView(
                (UUID) row.get("id"), (String) row.get("name"),
                (String) row.get("geometry_type"), (UUID) row.get("owner_id"));
        try {
            GisWebEditRules.ensureUserLayerOwnedBy(
                    tenantId, tenantId, layer.ownerId(), userId);
        } catch (GisWebEditRules.LayerPermissionException e) {
            throw new ApiException.Forbidden(e.getMessage());
        }
        return layer;
    }

    @Transactional
    public JoinView createJoin(JoinInput in) {
        guard(in.layerId(), "MANAGE_LAYER");
        GisWebEditRules.ensureJoinAllowed(in.leftTable(), in.rightTable(),
                in.joinType(), in.joinKey());
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_table_join (id, tenant_id, layer_id, left_table,
                        right_table, join_type, join_key, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, in.layerId(), in.leftTable(), in.rightTable(),
                in.joinType(), in.joinKey(), userId, userId);
        return new JoinView(id, in.leftTable(), in.rightTable(),
                in.joinType(), in.joinKey(), in.layerId());
    }

    /**
     * Executa a junção definida (SG-841) — correção D5: aplicar a
     * definição GERA a consulta e devolve as linhas unidas, não
     * fica só a definição gravada. Tabelas/chave/tipo são
     * revalidados contra as listas fechadas antes de montar o
     * SQL (os identificadores interpolados vêm SÓ dessas listas,
     * nunca de texto livre); os dois lados são filtrados pelo
     * tenant_id do contexto — junção nunca mistura tenants.
     * Combinação tabela×chave sem a coluna real no banco falha
     * explicitamente (400 juncao-nao-executavel), nunca finge
     * resultado vazio.
     *
     * <p>Correção N1 (2ª revisão): por ser leitura escopada à
     * camada da definição, a guarda camada×operação com VIEW é
     * conferida contra essa camada ANTES de montar/executar a
     * consulta — sem VIEW, 403 e nenhuma linha é produzida.
     */
    public JoinResultView executeJoin(UUID joinId) {
        UUID tenantId = GisWebEditContext.tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, left_table, right_table, join_type, join_key,
                       layer_id
                FROM gis_table_join WHERE tenant_id = ? AND id = ?
                """, tenantId, joinId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Junção não encontrada.");
        }
        Map<String, Object> def = rows.get(0);
        UUID layerId = (UUID) def.get("layer_id");
        if (layerId == null) {
            throw new ApiException.Conflict("juncao-sem-camada",
                    "Junção sem camada associada não é executável.");
        }
        ensureLayerOperation(layerId, "VIEW");
        String leftTable = (String) def.get("left_table");
        String rightTable = (String) def.get("right_table");
        String joinType = (String) def.get("join_type");
        String joinKey = (String) def.get("join_key");
        GisWebEditRules.ensureJoinAllowed(leftTable, rightTable, joinType, joinKey);
        String keyColumn = switch (joinKey) {
            case "feature_id" -> "id";
            case "code" -> "code";
            case "dma_id" -> "dma_id";
            case "service_connection_id" -> "service_connection_id";
            case "consumer_unit_id" -> "consumer_unit_id";
            default -> throw new ApiException.BadRequest("chave-de-juncao-invalida",
                    "Chave de junção fora da lista: " + joinKey);
        };
        String joinKeyword = "LEFT".equals(joinType) ? "LEFT JOIN" : "JOIN";
        String sql = "SELECT l.id AS left_id, r.id AS right_id, "
                + "l." + keyColumn + "::text AS key_value FROM "
                + leftTable + " l " + joinKeyword + " " + rightTable
                + " r ON r.tenant_id = l.tenant_id AND r." + keyColumn
                + " = l." + keyColumn
                + " WHERE l.tenant_id = ? ORDER BY l.id LIMIT 500";
        List<JoinRowView> joined;
        try {
            joined = jdbc.queryForList(sql, tenantId).stream()
                    .map(row -> new JoinRowView(
                            (UUID) row.get("left_id"),
                            (UUID) row.get("right_id"),
                            (String) row.get("key_value")))
                    .toList();
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("juncao-nao-executavel",
                    "A junção definida não é executável sobre o esquema "
                            + "real (tabela/chave incompatíveis).");
        }
        return new JoinResultView((UUID) def.get("id"), leftTable, rightTable,
                joinType, joinKey, joined);
    }

    // --------------------------- unidades ETA/ETE/elev./reservatório

    /**
     * Cadastro de unidade (SG-148..152): a unidade é uma
     * gis_structure; a criação entra como feição PLANNED no
     * pacote do usuário (GIS-04) — aqui validamos o tipo e
     * devolvemos o contrato; a materialização é item de pacote.
     *
     * <p>Correção D2: os atributos são validados contra o
     * {@code attributes_schema} REAL do tipo (gis_feature_type),
     * lido pela porta — nunca contra um mapa vazio. Tipo
     * inexistente neste tenant → 404; tipo sem esquema com
     * campos cadastrados → 400 esquema-nao-cadastrado (recusa
     * explícita: sem esquema não há o que validar, e fingir
     * validação vazia foi o defeito original).
     */
    public PlantStructureView draftPlantStructure(PlantStructureInput in) {
        guard(in.layerId(), "CREATE");
        GisWebEditRules.ensurePlantStructure(in.structureKind());
        boolean hasGeom = in.geomWkt() != null && !in.geomWkt().isBlank();
        boolean hasPoint = in.geomPointWkt() != null && !in.geomPointWkt().isBlank();
        if (!hasGeom && !hasPoint) {
            throw new ApiException.BadRequest("geometria-ausente",
                    "Unidade exige geometria (poligono ou ponto).");
        }
        if (hasGeom) {
            GisGeom.parsePolygonRing(in.geomWkt());
        }
        if (hasPoint) {
            GisGeom.parsePoint(in.geomPointWkt());
        }
        UUID tenantId = GisWebEditContext.tenantId();
        Optional<Map<String, Object>> schema =
                schemaPort.attributesSchema(tenantId, in.featureTypeId());
        if (schema.isEmpty()) {
            throw new ApiException.NotFound(
                    "Tipo de feição não encontrado neste tenant.");
        }
        Map<String, Object> realSchema = schema.get();
        if (GisFormSchema.formFromSchema(realSchema).fields().isEmpty()) {
            throw new ApiException.BadRequest("esquema-nao-cadastrado",
                    "O tipo de feição não tem attributes_schema com campos "
                            + "cadastrados; sem esquema não há validação de "
                            + "atributos possível (recusa explícita, D2).");
        }
        List<String> problems = GisFormSchema.validate(
                realSchema, in.attributes() == null
                        ? Map.of() : in.attributes());
        if (!problems.isEmpty()) {
            throw new ApiException.BadRequest("atributos-invalidos",
                    String.join("; ", problems));
        }
        return new PlantStructureView(null, in.structureKind(), in.code(),
                in.name(), in.geomWkt(), in.geomPointWkt());
    }

    /** Formulário da classe: gerado do JSON Schema do tipo (GIS-09). */
    public GisFormSchema.FormDescriptor formForSchema(
            Map<String, Object> attributesSchema) {
        return GisFormSchema.formFromSchema(attributesSchema);
    }

    // --------------------------------- projetos, obras e pacotes

    /**
     * Projeto/obra (SG-858, TR-1050/1051) — correção D5: o par
     * PROJECT/AS_BUILT é CRIADO de verdade: dois registros em
     * gis_change_set (DRAFT, canal WEB, project_ref = o projeto),
     * vinculados nas duas colunas do gis_project. Nada de par
     * tautológico com pacotes NULL.
     */
    @Transactional
    public ProjectView createProject(ProjectInput in) {
        guard(in.layerId(), "CREATE");
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        UUID id = UUID.randomUUID();
        UUID projectSetId = UUID.randomUUID();
        UUID asBuiltSetId = UUID.randomUUID();
        // Os tipos conferidos são os MESMOS inseridos logo abaixo.
        GisWebEditRules.ensureProjectPair("PROJECT", "AS_BUILT");
        insertProjectChangeSet(projectSetId, tenantId, userId, "PROJECT", id);
        insertProjectChangeSet(asBuiltSetId, tenantId, userId, "AS_BUILT", id);
        jdbc.update("""
                INSERT INTO gis_project (id, tenant_id, name, description,
                        work_ref, status, project_change_set_id,
                        as_built_change_set_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
                """, id, tenantId, in.name(), in.description(), in.workRef(),
                projectSetId, asBuiltSetId, userId, userId);
        return getProject(id);
    }

    private void insertProjectChangeSet(UUID setId, UUID tenantId, UUID userId,
                                        String type, UUID projectId) {
        jdbc.update("""
                INSERT INTO gis_change_set (id, tenant_id, type, channel,
                        status, author_id, project_ref, created_by, updated_by)
                VALUES (?, ?, ?, 'WEB', 'DRAFT', ?, ?, ?, ?)
                """, setId, tenantId, type, userId, projectId.toString(),
                userId, userId);
    }

    /**
     * Leitura de projeto (SG-858) — correção N1 (2ª revisão),
     * exceção DECLARADA à guarda por camada: a tabela
     * gis_project NÃO tem coluna de camada (ver o SQL em
     * db/pendente: ela referencia os pacotes, não uma camada),
     * portanto a guarda camada×operação (VIEW) não é expressável
     * para projeto sem inventar coluna ou permissão — e nada é
     * inventado aqui. A leitura segue escopada pelo tenant_id
     * do contexto (projeto de outro tenant → 404, sem vazamento),
     * o mesmo filtro com que createProject a reutiliza
     * internamente logo após a guarda CREATE da escrita.
     */
    public ProjectView getProject(UUID id) {
        UUID tenantId = GisWebEditContext.tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, name, status, project_change_set_id,
                       as_built_change_set_id, work_ref
                FROM gis_project WHERE tenant_id = ? AND id = ?
                """, tenantId, id);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Projeto não encontrado.");
        }
        Map<String, Object> row = rows.get(0);
        return new ProjectView(
                (UUID) row.get("id"), (String) row.get("name"),
                (String) row.get("status"),
                (UUID) row.get("project_change_set_id"),
                (UUID) row.get("as_built_change_set_id"),
                (String) row.get("work_ref"));
    }

    /**
     * TR-626: OS que altera a rede abre pacote SERVICE_ORDER.
     * OS sem efeito de rede não abre (devolve vazio) — a decisão
     * "altera a rede" é do chamador (efeito da OS, ED-36/P1.31),
     * registrada na chamada; nenhum pacote é inventado para OS
     * que não toca a rede. Havendo abertura, a guarda D1 (CREATE
     * na camada) é conferida ANTES do INSERT.
     */
    @Transactional
    public Optional<UUID> openChangeSetForServiceOrder(
            OpenForServiceOrderInput in) {
        if (!GisWebEditRules.serviceOrderOpensChangeSet(in.affectsNetwork())) {
            return Optional.empty();
        }
        guard(in.layerId(), "CREATE");
        UUID tenantId = GisWebEditContext.tenantId();
        UUID userId = GisWebEditContext.userId();
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_change_set (id, tenant_id, type, channel,
                        status, author_id, service_order_ref,
                        created_by, updated_by)
                VALUES (?, ?, 'SERVICE_ORDER', 'SERVICE_ORDER', 'DRAFT',
                        ?, ?, ?, ?)
                """, id, tenantId, userId, in.serviceOrderRef(), userId, userId);
        return Optional.of(id);
    }

    // ------------------------------ ramal desenhado → cadastro

    /**
     * SG-866/TR-1053: sincroniza o ramal desenhado aplicado com o
     * cadastro (ED-13) pela porta; idempotente pela feição GIS.
     * Sem a porta ligada (integração), devolve PENDING declarado —
     * nunca finge sincronizado.
     *
     * <p>Correção D4: ANTES de qualquer gravação, confere a
     * elegibilidade real no banco — existe item de pacote desta
     * feição em gis_connection com o pacote em APPLIED
     * ({@code shouldSync})? Sem item elegível: 409 e nada é
     * gravado (não se sincroniza qualquer feição sob demanda). A
     * transição de estado passa por {@code ensureTransition}: de
     * SYNCED é no-op idempotente; de RETIRED para SYNCED é
     * recusada.
     */
    @Transactional
    public DrawnSyncView syncDrawnConnection(UUID layerId,
                                             UUID gisConnectionId,
                                             UUID consumerUnitId,
                                             String domainKey) {
        guard(layerId, "CREATE");
        UUID tenantId = GisWebEditContext.tenantId();
        String key = GisDrawnConnectionSync.idempotencyKey(gisConnectionId);
        GisDrawnConnectionSync.serviceKindForDomain(domainKey);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT s.status AS change_set_status, i.operation AS operation
                FROM gis_change_set_item i
                JOIN gis_change_set s
                  ON s.tenant_id = i.tenant_id AND s.id = i.change_set_id
                WHERE i.tenant_id = ? AND i.feature_id = ?
                  AND i.feature_table = 'gis_connection'
                ORDER BY i.created_at DESC
                """, tenantId, gisConnectionId);
        boolean eligible = items.stream().anyMatch(item ->
                GisDrawnConnectionSync.shouldSync(
                        (String) item.get("change_set_status"),
                        "gis_connection", (String) item.get("operation")));
        if (!eligible) {
            throw new ApiException.Conflict("sincronizacao-nao-elegivel",
                    "Só ramal desenhado de pacote APLICADO sincroniza "
                            + "(SG-866/TR-1053).");
        }
        List<Map<String, Object>> current = jdbc.queryForList("""
                SELECT service_connection_id, status
                FROM gis_drawn_connection_sync
                WHERE tenant_id = ? AND gis_connection_id = ?
                """, tenantId, gisConnectionId);
        String currentStatus = current.isEmpty()
                ? null : (String) current.get(0).get("status");
        if (drawnPort.isEmpty()) {
            if (currentStatus == null) {
                jdbc.update("""
                        INSERT INTO gis_drawn_connection_sync (tenant_id,
                                gis_connection_id, status, idempotency_key)
                        VALUES (?, ?, 'PENDING', ?)
                        ON CONFLICT (tenant_id, gis_connection_id) DO NOTHING
                        """, tenantId, gisConnectionId, key);
                return new DrawnSyncView(gisConnectionId, null, "PENDING", key);
            }
            // Idempotente: devolve o estado já registrado, sem regravar.
            return new DrawnSyncView(gisConnectionId,
                    (UUID) current.get(0).get("service_connection_id"),
                    currentStatus, key);
        }
        if ("SYNCED".equals(currentStatus)) {
            // Idempotente: já sincronizado, não chama a porta de novo.
            return new DrawnSyncView(gisConnectionId,
                    (UUID) current.get(0).get("service_connection_id"),
                    "SYNCED", key);
        }
        try {
            GisDrawnConnectionSync.ensureTransition(
                    currentStatus == null ? "PENDING" : currentStatus, "SYNCED");
        } catch (IllegalStateException e) {
            throw new ApiException.Conflict(
                    "transicao-de-sincronizacao-invalida", e.getMessage());
        }
        UUID serviceConnectionId = drawnPort.get().ensureServiceConnection(
                tenantId, gisConnectionId, consumerUnitId, domainKey);
        jdbc.update("""
                INSERT INTO gis_drawn_connection_sync (tenant_id,
                        gis_connection_id, service_connection_id, status,
                        idempotency_key)
                VALUES (?, ?, ?, 'SYNCED', ?)
                ON CONFLICT (tenant_id, gis_connection_id) DO UPDATE
                SET service_connection_id = EXCLUDED.service_connection_id,
                    status = 'SYNCED', updated_at = NOW()
                """, tenantId, gisConnectionId, serviceConnectionId, key);
        return new DrawnSyncView(gisConnectionId, serviceConnectionId,
                "SYNCED", key);
    }

    // ------------------------------------------------------- internos

    private static String geometryTypeOf(String wkt) {
        String upper = wkt == null ? "" : wkt.trim().toUpperCase(java.util.Locale.ROOT);
        if (upper.startsWith("POLYGON")) {
            return "POLYGON";
        }
        if (upper.startsWith("LINESTRING")) {
            return "LINESTRING";
        }
        if (upper.startsWith("POINT")) {
            return "POINT";
        }
        throw new ApiException.BadRequest("geometria-invalida",
                "WKT não é POINT, LINESTRING nem POLYGON.");
    }

    private static List<Pt> parseShape(String wkt) {
        String upper = wkt == null ? "" : wkt.trim().toUpperCase(java.util.Locale.ROOT);
        if (upper.startsWith("POLYGON")) {
            return GisGeom.parsePolygonRing(wkt);
        }
        if (upper.startsWith("POINT")) {
            return List.of(GisGeom.parsePoint(wkt));
        }
        return GisGeom.parseLine(wkt);
    }

    private static String toWktLike(String originalWkt, List<Pt> shape) {
        String upper = originalWkt == null
                ? "" : originalWkt.trim().toUpperCase(java.util.Locale.ROOT);
        if (upper.startsWith("POLYGON")) {
            return GisGeom.toPolygonWkt(shape);
        }
        if (upper.startsWith("POINT")) {
            return GisGeom.toPointWkt(shape.get(0));
        }
        return GisGeom.toLineWkt(shape);
    }

    private static double req(Double v, String name) {
        if (v == null) {
            throw new ApiException.BadRequest("parametro-ausente",
                    "Parâmetro obrigatório ausente: " + name);
        }
        return v;
    }

    private static int failInt(String name) {
        throw new ApiException.BadRequest("parametro-ausente",
                "Parâmetro obrigatório ausente: " + name);
    }
}
