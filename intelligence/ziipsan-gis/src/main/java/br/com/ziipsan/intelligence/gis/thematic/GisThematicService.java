package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ClassDef;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.LegendEntry;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PublicCellView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PublicThematicView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicDataView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicInput;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicPoint;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Temáticos da GIS-08: CRUD das definições do tenant, renderização
 * de pontos no backoffice e agregado do canal público.
 *
 * <p>Segurança (o aceite e o §12):
 * <ul>
 *   <li>fonte pessoal no backoffice exige {@code gis.view-personal}
 *       ANTES de qualquer consulta (403), como a camada pessoal da
 *       GIS-06 — nem a definição some: ela é listada, mas os dados
 *       não são servidos;</li>
 *   <li>fora do backoffice, fonte pessoal só sai agregada por
 *       quadra ou setor com pelo menos
 *       {@code gis.public_aggregation_min_units} unidades (piso 5,
 *       classe c, reaplicado no {@link GisThematicRules}); o SQL
 *       público não seleciona ponto nem id de unidade;</li>
 *   <li>OS nunca é publicável, nem agregada (dado operacional com
 *       posição de unidade); rede e precisão são de ativos e saem
 *       integrais no canal público;</li>
 *   <li>definição de fábrica é imutável pela API (o tenant cria a
 *       sua com outra chave ou desativa a de fábrica); o marcador
 *       {@code personal} vem da FONTE, nunca do corpo;</li>
 *   <li>temático que exige configuração (SG-877 a SG-879 sem tipos
 *       de OS do tenant; SG-882 sem faixas) recusa com 422 e a
 *       explicação — nunca rende vazio fingindo que não há dado.</li>
 * </ul>
 *
 * <p>Os fornecedores de permissão/parâmetro são injetáveis para o
 * teste unitário exercitar as guardas sem montar SecurityContext
 * nem o catálogo de parâmetros.
 */
@Service
@Transactional(readOnly = true)
public class GisThematicService {

    /** Teto de pontos por renderização: guarda de desenho, não regra de negócio. */
    static final int MAX_POINTS = 20_000;

    private final JdbcTemplate jdbc;
    private final BooleanSupplier canViewPersonal;
    private final Supplier<Integer> minUnitsConfigured;

    public GisThematicService(JdbcTemplate jdbc) {
        this(jdbc, GisThematicContext::canViewPersonal, null);
    }

    GisThematicService(JdbcTemplate jdbc, BooleanSupplier canViewPersonal,
                       Supplier<Integer> minUnitsConfigured) {
        this.jdbc = jdbc;
        this.canViewPersonal = canViewPersonal;
        this.minUnitsConfigured = minUnitsConfigured != null
                ? minUnitsConfigured : this::readMinUnitsParameter;
    }

    // ------------------------------------------------------------------ CRUD

    public List<ThematicView> list() {
        UUID tenantId = GisThematicContext.tenantId();
        return jdbc.query(SELECT_ALL + " WHERE tenant_id = ? ORDER BY key",
                (rs, row) -> toView(rs), tenantId);
    }

    public ThematicView get(String key) {
        return findRow(key).toView();
    }

    @Transactional
    public ThematicView create(ThematicInput input) {
        UUID tenantId = GisThematicContext.tenantId();
        Validated validated = validate(input);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO gis_thematic_map
                        (id, tenant_id, key, name, description, source_kind,
                         category_field, measure_field, personal, aggregation,
                         classes_json, filter_json, origin, active, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb,
                            'TENANT', TRUE, ?)
                    """,
                    id, tenantId, input.key(), input.name(), input.description(),
                    validated.source().name(), input.categoryField(), input.measureField(),
                    validated.source().personal(), validated.aggregation(),
                    GisThematicRules.writeClasses(validated.classes()),
                    validated.filterJson(), GisThematicContext.userId());
        } catch (DuplicateKeyException e) {
            throw new ApiException.Conflict("thematic-key-in-use",
                    "Já existe um temático com a chave " + input.key() + " neste tenant.");
        }
        return get(input.key());
    }

    @Transactional
    public ThematicView update(String key, ThematicInput input) {
        ThematicRow existing = findRow(key);
        if ("FACTORY".equals(existing.origin())) {
            throw new ApiException.Forbidden(
                    "Temático de fábrica é imutável: crie um temático do tenant com outra chave.");
        }
        Validated validated = validate(input);
        jdbc.update("""
                UPDATE gis_thematic_map
                SET name = ?, description = ?, source_kind = ?,
                    category_field = ?, measure_field = ?, personal = ?,
                    aggregation = ?, classes_json = ?::jsonb,
                    filter_json = ?::jsonb, updated_by = ?, updated_at = NOW()
                WHERE tenant_id = ? AND key = ? AND origin = 'TENANT'
                """,
                input.name(), input.description(), validated.source().name(),
                input.categoryField(), input.measureField(), validated.source().personal(),
                validated.aggregation(), GisThematicRules.writeClasses(validated.classes()),
                validated.filterJson(), GisThematicContext.userId(),
                GisThematicContext.tenantId(), key);
        return get(key);
    }

    @Transactional
    public void setActive(String key, boolean active) {
        int updated = jdbc.update("""
                UPDATE gis_thematic_map SET active = ?, updated_at = NOW()
                WHERE tenant_id = ? AND key = ?
                """, active, GisThematicContext.tenantId(), key);
        if (updated == 0) {
            throw new ApiException.NotFound("Temático não encontrado.");
        }
    }

    @Transactional
    public void delete(String key) {
        ThematicRow existing = findRow(key);
        if ("FACTORY".equals(existing.origin())) {
            throw new ApiException.Forbidden(
                    "Temático de fábrica não é excluído: desative-o ou crie um do tenant.");
        }
        jdbc.update("DELETE FROM gis_thematic_map WHERE tenant_id = ? AND key = ? AND origin = 'TENANT'",
                GisThematicContext.tenantId(), key);
    }

    // ------------------------------------------------------------------ backoffice

    /**
     * Pontos + legenda automática do temático no backoffice.
     * {@code categoryCode} é o filtro por categoria do SG-883 (só
     * fontes de unidade têm category_code).
     */
    public ThematicDataView render(String key, String categoryCode) {
        ThematicRow row = findRow(key);
        if (!row.active()) {
            throw new ApiException.NotFound("Temático não encontrado ou inativo.");
        }
        GisThematicSources source = row.source();
        if (source.personal() && !canViewPersonal.getAsBoolean()) {
            throw new ApiException.Forbidden(
                    "Temático pessoal: exige a permissão gis.view-personal.");
        }
        requireConfigured(row);
        Map<String, Object> filter = Json.readMap(row.filterJson());
        var built = GisThematicSql.pointsSql(source, row.categoryField(), row.measureField(),
                filter, categoryCode, MAX_POINTS + 1);
        List<Object> args = withTenant(built.args(), GisThematicContext.tenantId());
        List<ThematicPoint> points = jdbc.query(built.sql(), (rs, n) -> {
            String category = rs.getString("category");
            BigDecimal measure = rs.getBigDecimal("measure");
            if (row.measureField() != null && row.categoryField() == null) {
                category = GisThematicRules.classifyNumeric(row.classes(), measure);
            }
            return new ThematicPoint(rs.getString("id"), rs.getDouble("lon"),
                    rs.getDouble("lat"), category, measure);
        }, args.toArray());
        boolean truncated = points.size() > MAX_POINTS;
        if (truncated) {
            points = points.subList(0, MAX_POINTS);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ThematicPoint point : points) {
            if (point.category() != null) {
                counts.merge(point.category(), 1L, Long::sum);
            }
        }
        List<LegendEntry> legend = GisThematicRules.legend(row.classes(), counts);
        return new ThematicDataView(row.key(), points, legend, truncated);
    }

    // ------------------------------------------------------------------ público

    /**
     * Canal fora do backoffice autenticado (§12, item 3): pessoal só
     * agregado com o mínimo; rede/precisão integrais; OS recusada.
     * O tenant vem do contexto que a borda/portal estabeleceu para a
     * publicação — nunca de parâmetro da requisição.
     */
    public PublicThematicView renderPublic(String key) {
        ThematicRow row = findRow(key);
        if (!row.active()) {
            throw new ApiException.NotFound("Temático não encontrado ou inativo.");
        }
        GisThematicSources source = row.source();
        if (!source.publishable()) {
            throw new ApiException.Forbidden(
                    "Temático operacional: não é publicável fora do backoffice.");
        }
        requireConfigured(row);
        UUID tenantId = GisThematicContext.tenantId();
        if (!source.personal()) {
            // Ativos (rede/precisão): publicáveis integrais. O DTO
            // público reaproveita a célula com a contagem por
            // categoria sobre o tenant inteiro, sem ponto — o ponto
            // público de rede é o tile da GIS-06, não este canal.
            // Nenhum mínimo de unidades é aplicado neste ramo (não há
            // supressão de célula pessoal): minUnitsApplied sai nulo,
            // nunca 0 fingindo um mínimo; e o truncamento da contagem
            // (teto MAX_POINTS) é propagado, nunca escondido.
            ThematicDataView data = renderPublicAssets(row);
            Map<String, Long> counts = new LinkedHashMap<>();
            data.legend().forEach(e -> counts.put(e.key(), e.count()));
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            PublicCellView cell = new PublicCellView("TENANT", tenantId.toString(),
                    "Tenant", total, data.legend(), null);
            return new PublicThematicView(row.key(), null, List.of(cell), 0,
                    data.truncated());
        }
        int minUnits = GisThematicRules.effectiveMinUnits(minUnitsConfigured.get());
        Map<String, Object> filter = Json.readMap(row.filterJson());
        var aggregate = GisThematicSql.publicAggregateSql(source, row.categoryField(),
                filter, row.aggregation(), minUnits);
        List<Object> args = withTenant(aggregate.args(), tenantId);
        Map<String, PublicCellAccum> cells = new LinkedHashMap<>();
        jdbc.query(aggregate.sql(), rs -> {
            String cellId = rs.getString("cell_id");
            long unitCount = rs.getLong("unit_count");
            String categoryValue = rs.getString("category_value");
            long categoryCount = rs.getLong("category_count");
            PublicCellAccum accum = cells.computeIfAbsent(cellId,
                    id -> new PublicCellAccum(id, unitCount));
            accum.categories.add(new LegendEntry(categoryValue,
                    labelFor(row, categoryValue), null, categoryCount));
        }, args.toArray());
        var countBuilt = GisThematicSql.publicCellCountSql(source, filter, row.aggregation());
        Long totalCells = jdbc.queryForObject(countBuilt.sql(), Long.class,
                withTenant(countBuilt.args(), tenantId).toArray());
        long suppressed = (totalCells == null ? 0 : totalCells) - cells.size();
        List<PublicCellView> views = new ArrayList<>();
        for (PublicCellAccum accum : cells.values()) {
            views.add(new PublicCellView(row.aggregation(), accum.cellId,
                    cellLabel(row.aggregation(), accum.cellId, tenantId),
                    accum.unitCount, List.copyOf(accum.categories),
                    sectorGeoJson(row.aggregation(), accum.cellId, tenantId)));
        }
        // O SQL agregado não tem LIMIT: truncated é falso de verdade.
        return new PublicThematicView(row.key(), minUnits, List.copyOf(views), suppressed,
                false);
    }

    private ThematicDataView renderPublicAssets(ThematicRow row) {
        Map<String, Object> filter = Json.readMap(row.filterJson());
        var built = GisThematicSql.pointsSql(row.source(), row.categoryField(),
                row.measureField(), filter, null, MAX_POINTS + 1);
        List<Object> args = withTenant(built.args(), GisThematicContext.tenantId());
        Map<String, Long> counts = new LinkedHashMap<>();
        // A consulta pede MAX_POINTS + 1 linhas: a linha extra só
        // serve de sinal — as contagens param em MAX_POINTS e o
        // excesso vira truncated = true na resposta, como no render
        // do backoffice (nunca contagem subcontada em silêncio).
        long[] seen = {0};
        jdbc.query(built.sql(), rs -> {
            seen[0]++;
            if (seen[0] > MAX_POINTS) {
                return;
            }
            String category = rs.getString("category");
            if (category != null) {
                counts.merge(category, 1L, Long::sum);
            }
        }, args.toArray());
        return new ThematicDataView(row.key(), List.of(),
                GisThematicRules.legend(row.classes(), counts), seen[0] > MAX_POINTS);
    }

    // ------------------------------------------------------------------ apoio

    private record Validated(GisThematicSources source, List<ClassDef> classes,
                             String filterJson, String aggregation) {
    }

    private Validated validate(ThematicInput input) {
        GisThematicSources source = GisThematicSources.byKind(input.sourceKind())
                .orElseThrow(() -> ApiException.Validation.of(new ApiException.FieldError(
                        "sourceKind", "unknown-source",
                        "Fonte fora da lista da GIS-08: " + input.sourceKind())));
        List<ClassDef> classes = input.classes() == null ? List.of() : input.classes();
        String filterJson = input.filterJson() == null || input.filterJson().isBlank()
                ? "{}" : input.filterJson();
        // Cópia mutável: validateDefinition devolve List.copyOf (imutável)
        // e as guardas de agregação abaixo acrescentam problemas nela.
        List<String> problems = new ArrayList<>(GisThematicRules.validateDefinition(
                input.key(), source, input.categoryField(), input.measureField(),
                classes, filterJson));
        String aggregation = input.aggregation() == null || input.aggregation().isBlank()
                ? (source.personal() ? "SECTOR" : "NONE") : input.aggregation();
        if (source.personal() && !aggregation.equals("BLOCK") && !aggregation.equals("SECTOR")) {
            problems.add("fonte pessoal exige aggregation BLOCK ou SECTOR");
        }
        if (!aggregation.equals("NONE") && !aggregation.equals("BLOCK")
                && !aggregation.equals("SECTOR")) {
            problems.add("aggregation fora de NONE/BLOCK/SECTOR: " + aggregation);
        }
        if (!problems.isEmpty()) {
            throw ApiException.Validation.of(problems.stream()
                    .map(p -> new ApiException.FieldError("thematic", "invalid-definition", p))
                    .toArray(ApiException.FieldError[]::new));
        }
        return new Validated(source, classes, filterJson, aggregation);
    }

    private void requireConfigured(ThematicRow row) {
        if (GisThematicRules.requiresConfiguration(row.source(), row.categoryField(),
                row.measureField(), row.classes(), row.filterJson())) {
            throw new ApiException.Validation("Temático exige configuração do tenant "
                    + "antes do primeiro uso (ver a descrição do cenário).",
                    List.of(new ApiException.FieldError("thematic", "requires-configuration",
                            "Defina os códigos de tipo de OS ou as faixas do temático.")),
                    List.of());
        }
    }

    private String labelFor(ThematicRow row, String categoryKey) {
        if (categoryKey == null) {
            return null;
        }
        return row.classes().stream().filter(c -> categoryKey.equals(c.key()))
                .map(ClassDef::label).findFirst().orElse(categoryKey);
    }

    private String cellLabel(String aggregation, String cellId, UUID tenantId) {
        if ("SECTOR".equals(aggregation)) {
            String name = jdbc.queryForObject(
                    "SELECT name FROM commercial_sectors WHERE tenant_id = ? AND id = ?::uuid",
                    String.class, tenantId, cellId);
            return name == null ? cellId : name;
        }
        Integer code = jdbc.queryForObject(
                "SELECT code FROM blocks WHERE tenant_id = ? AND id = ?::uuid",
                Integer.class, tenantId, cellId);
        return code == null ? cellId : "Quadra " + code;
    }

    private String sectorGeoJson(String aggregation, String cellId, UUID tenantId) {
        if (!"SECTOR".equals(aggregation)) {
            return null;
        }
        List<String> geo = jdbc.query("""
                SELECT ST_AsGeoJSON(z.geom) FROM gis_zone z
                WHERE z.tenant_id = ? AND z.zone_type = 'COMMERCIAL_SECTOR'
                  AND z.commercial_sector_id = ?::uuid
                LIMIT 1
                """, (rs, n) -> rs.getString(1), tenantId, cellId);
        return geo.isEmpty() ? null : geo.get(0);
    }

    /** Lê gis.public_aggregation_min_units do catálogo de parâmetros (core V9_6). */
    private Integer readMinUnitsParameter() {
        List<String> values = jdbc.query("""
                SELECT CAST(v.value AS text) FROM parameter_versions v
                WHERE v.tenant_id = ? AND v.definition_key = 'gis.public_aggregation_min_units'
                  AND v.status = 'APPROVED'
                  AND v.valid_from <= CURRENT_DATE
                  AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)
                  AND EXISTS (SELECT 1 FROM parameter_acceptances a
                              WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id
                                AND a.content_hash = v.content_hash)
                ORDER BY v.valid_from DESC LIMIT 1
                """, (rs, n) -> rs.getString(1), GisThematicContext.tenantId());
        if (values.isEmpty() || values.get(0) == null) {
            return null;
        }
        String raw = values.get(0).trim().replace("\"", "");
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Object> withTenant(List<Object> args, UUID tenantId) {
        List<Object> out = new ArrayList<>(args);
        int idx = out.indexOf("__TENANT__");
        if (idx >= 0) {
            out.set(idx, tenantId);
        }
        return out;
    }

    private static final String SELECT_ALL = """
            SELECT id, key, name, description, source_kind, category_field,
                   measure_field, personal, aggregation, classes_json::text AS classes_json,
                   filter_json::text AS filter_json, origin, active
            FROM gis_thematic_map
            """;

    private ThematicRow findRow(String key) {
        UUID tenantId = GisThematicContext.tenantId();
        List<ThematicRow> rows = jdbc.query(SELECT_ALL + " WHERE tenant_id = ? AND key = ?",
                (rs, n) -> toRow(rs), tenantId, key);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Temático não encontrado.");
        }
        return rows.get(0);
    }

    private ThematicView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return toRow(rs).toView();
    }

    private ThematicRow toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        // Lido fora da lambda: rs.getString lança SQLException checada,
        // que o fornecedor de orElseThrow não pode declarar.
        String sourceKind = rs.getString("source_kind");
        return new ThematicRow(
                rs.getObject("id", UUID.class), rs.getString("key"), rs.getString("name"),
                rs.getString("description"),
                GisThematicSources.byKind(sourceKind).orElseThrow(
                        () -> new IllegalStateException("source_kind desconhecido: "
                                + sourceKind)),
                rs.getString("category_field"), rs.getString("measure_field"),
                rs.getBoolean("personal"), rs.getString("aggregation"),
                GisThematicRules.parseClasses(rs.getString("classes_json")),
                rs.getString("filter_json"), rs.getString("origin"), rs.getBoolean("active"));
    }

    private static final class PublicCellAccum {
        private final String cellId;
        private final long unitCount;
        private final List<LegendEntry> categories = new ArrayList<>();

        private PublicCellAccum(String cellId, long unitCount) {
            this.cellId = cellId;
            this.unitCount = unitCount;
        }
    }

    record ThematicRow(UUID id, String key, String name, String description,
                       GisThematicSources source, String categoryField, String measureField,
                       boolean personal, String aggregation, List<ClassDef> classes,
                       String filterJson, String origin, boolean active) {

        ThematicView toView() {
            return new ThematicView(id, key, name, description, source.name(), categoryField,
                    measureField, personal, aggregation, classes, filterJson, origin, active,
                    GisThematicRules.requiresConfiguration(source, categoryField, measureField,
                            classes, filterJson));
        }
    }
}
