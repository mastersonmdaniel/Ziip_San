package br.com.ziipsan.intelligence.gis.drain;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CadastroReportRow;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CleaningPlanInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.CleaningRequestView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.ConservationClassCount;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.DrainFeatureView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.FloodOccurrenceInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.FloodOccurrenceView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.GenerationResult;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.InspectionInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.InspectionView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.PhotoInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.RainImportView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.RainStationInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.SubcatchmentInput;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.SymbolManifestView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainDtos.ValidationIssueView;
import br.com.ziipsan.intelligence.gis.drain.GisDrainMaintenance.CleaningPlanLine;
import br.com.ziipsan.intelligence.gis.drain.GisDrainRules.DrainIssue;
import br.com.ziipsan.intelligence.gis.drain.GisDrainRules.Severity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GIS-13 (drenagem): feições do §4.4 sobre as tabelas da GIS-01
 * (gis_arc/gis_node/gis_structure/gis_zone do domínio STORM),
 * inspeção com conservação/obstrução/foto, sub-bacias SWMM, plano de
 * limpeza por classe com pedidos recorrentes, chuva importada e
 * ocorrência de alagamento ligada à chuva do período, temático de
 * conservação e relatórios GIS-003/GIS-004.
 *
 * <p>Fronteiras declaradas:
 * <ul>
 *   <li>a OS de limpeza em si é do núcleo de OS (ED-36): aqui nasce
 *       o PEDIDO recorrente (gis_drain_cleaning_request); o
 *       {@code service_order_id} é preenchido pela integração —
 *       nenhum tipo de OS é inventado nesta entrega;</li>
 *   <li>a ocorrência de rede é da ED-92: aqui a ocorrência de
 *       alagamento nasce com o marcador de causa
 *       {@link #FLOOD_CAUSE_CODE} e {@code network_occurrence_id}
 *       nulo até a ED-92 existir e a integração ligar os dois
 *       registros — o código da causa é o marcador proposto desta
 *       entrega, a reconciliar com o catálogo de causas da ED-92
 *       quando ele for carregado;</li>
 *   <li>a inspeção por vídeo é da ED-56 (OPE-12): o método
 *       CCTV_EXTERNAL_REF guarda apenas a REFERÊNCIA do arquivo
 *       dela, reaproveitável quando ela existir;</li>
 *   <li>o parâmetro {@code drain.cleaning_interval_by_class} é lido
 *       pelo mecanismo de parâmetros na integração e materializado
 *       nas linhas de gis_drain_cleaning_plan (a tela de plano desta
 *       api grava a mesma tabela); {@code drain.rain_source} é
 *       conferido a cada estação criada;</li>
 *   <li>mudanças na REDE EM OPERAÇÃO passam pelo pacote da GIS-04
 *       (§6.1); este serviço é o cadastro operacional da drenagem,
 *       como o de resíduos da ED-65 — a passagem por pacote é
 *       fiação de integração declarada;</li>
 *   <li>JSON sempre pela classe Json do core.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GisDrainService {

    /**
     * Marcador da causa "alagamento" para o catálogo de causas da
     * ocorrência de rede da ED-92 (a ED-92 ainda não existe: marcador
     * proposto, a reconciliar na integração — ver javadoc da classe).
     */
    public static final String FLOOD_CAUSE_CODE = "ALAGAMENTO";

    private final JdbcTemplate jdbc;

    public GisDrainService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- feições

    @Transactional
    public DrainFeatureView createFeature(DrainFeatureInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        GisDrainCatalog.DrainType type;
        List<DrainIssue> warnings;
        try {
            type = GisDrainCatalog.requireType(in.typeCode());
            warnings = GisDrainRules.ensureFeatureAttributes(type, in.attributes());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("drenagem-invalida", e.getMessage());
        }
        if (type.role() == GisDrainCatalog.Role.ARC) {
            // Correcao D1 da revisao da ED-64: a validacao de
            // declividade/sentido VALE na gravacao. O comprimento e o
            // geodesico calculado da geometria de entrada (nunca 0
            // presumido) e qualquer erro do §5.4 RECUSA a gravacao.
            List<DrainIssue> issues;
            try {
                issues = validateArcData(in, arcLengthM(in), null);
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("drenagem-invalida", e.getMessage());
            }
            List<DrainIssue> errors = issues.stream()
                    .filter(i -> i.severity() == Severity.ERROR).toList();
            if (!errors.isEmpty()) {
                throw new ApiException.BadRequest("drenagem-invalida",
                        errors.get(0).ruleCode() + ": " + errors.get(0).message());
            }
            warnings = concat(warnings, issues);
        }
        UUID typeId = featureTypeId(tenantId, type);
        UUID id = UUID.randomUUID();
        String attributesJson = Json.write(in.attributes());
        String geoJson = Json.write(in.geometryGeoJson());
        try {
            switch (type.role()) {
                case NODE -> jdbc.update("""
                        INSERT INTO gis_node (id, tenant_id, code, feature_type_id,
                                name, lifecycle_status, condition_rating, capture_method,
                                attributes, geom, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                                ST_SetSRID(ST_GeomFromGeoJSON(?), 4674), ?, ?)
                        """, id, tenantId, in.code(), typeId, in.name(),
                        in.lifecycleStatus(), in.conditionRating(), in.captureMethod(),
                        attributesJson, geoJson, userId, userId);
                case ARC -> {
                    UUID node1 = nodeIdByCode(tenantId, in.node1Code());
                    UUID node2 = nodeIdByCode(tenantId, in.node2Code());
                    jdbc.update("""
                            INSERT INTO gis_arc (id, tenant_id, code, feature_type_id,
                                    name, lifecycle_status, condition_rating, capture_method,
                                    attributes, node_1_id, node_2_id,
                                    upstream_invert_m, downstream_invert_m, flow_direction,
                                    geom, created_by, updated_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?,
                                    ?, ?, ?,
                                    ST_SetSRID(ST_GeomFromGeoJSON(?), 4674), ?, ?)
                            """, id, tenantId, in.code(), typeId, in.name(),
                            in.lifecycleStatus(), in.conditionRating(), in.captureMethod(),
                            attributesJson, node1, node2,
                            in.upstreamInvertM(), in.downstreamInvertM(), in.flowDirection(),
                            geoJson, userId, userId);
                }
                case STRUCTURE -> jdbc.update("""
                        INSERT INTO gis_structure (id, tenant_id, code, feature_type_id,
                                name, lifecycle_status, condition_rating, capture_method,
                                attributes, geom, geom_point, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb,
                                CASE WHEN ST_GeometryType(ST_GeomFromGeoJSON(?))
                                          IN ('ST_Polygon', 'ST_MultiPolygon')
                                     THEN ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4674)) END,
                                CASE WHEN ST_GeometryType(ST_GeomFromGeoJSON(?)) = 'ST_Point'
                                     THEN ST_SetSRID(ST_GeomFromGeoJSON(?), 4674) END, ?, ?)
                        """, id, tenantId, in.code(), typeId, in.name(),
                        in.lifecycleStatus(), in.conditionRating(), in.captureMethod(),
                        attributesJson, geoJson, geoJson, geoJson, geoJson, userId, userId);
                case ZONE -> jdbc.update("""
                        INSERT INTO gis_zone (id, tenant_id, code, feature_type_id,
                                name, lifecycle_status, condition_rating, capture_method,
                                attributes, zone_type, derivation, geom,
                                created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'DRAWN',
                                ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(?), 4674)), ?, ?)
                        """, id, tenantId, in.code(), typeId, in.name(),
                        in.lifecycleStatus(), in.conditionRating(), in.captureMethod(),
                        attributesJson, type.zoneType(), geoJson, userId, userId);
            }
        } catch (DataAccessException e) {
            throw new ApiException.BadRequest("drenagem-recusada",
                    "Banco recusou a feicao de drenagem: "
                            + e.getMostSpecificCause().getMessage());
        }
        return getFeature(id);
    }

    public DrainFeatureView getFeature(UUID id) {
        UUID tenantId = tenantId();
        String[] tables = {"gis_node", "gis_arc", "gis_structure", "gis_zone"};
        for (String table : tables) {
            List<DrainFeatureView> rows = jdbc.query("""
                    SELECT f.id, f.code, ft.code AS type_code, f.name,
                           f.lifecycle_status, f.condition_rating, f.attributes::text AS attrs
                      FROM %s f
                      JOIN gis_feature_type ft
                        ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                     WHERE f.id = ? AND f.tenant_id = ?
                    """.formatted(table), (rs, n) -> new DrainFeatureView(
                    rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("type_code"), table, rs.getString("name"),
                    rs.getString("lifecycle_status"), rs.getString("condition_rating"),
                    Json.readMap(rs.getString("attrs"))), id, tenantId);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        }
        throw new ApiException.NotFound("feicao de drenagem nao encontrada: " + id);
    }

    /**
     * Validação de declividade/sentido de um arco SEM gravar (§5.4):
     * o comprimento é o geodésico informado por quem chama e a
     * checagem é a MESMA aplicada na gravação
     * ({@link #createFeature}): declividade
     * ({@code GisDrainRules.checkSlope}) e sentido contra as cotas
     * ({@code GisDrainRules.checkFlowDirection}).
     */
    public List<ValidationIssueView> validateArc(DrainFeatureInput in, double lengthM,
                                                 Double tenantMinSlope) {
        try {
            return validateArcData(in, lengthM, tenantMinSlope).stream()
                    .map(i -> new ValidationIssueView(
                            i.ruleCode(), i.severity().name(), i.message()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("drenagem-invalida", e.getMessage());
        }
    }

    /**
     * Regras de arco do §5.4 sobre os dados de entrada: declividade
     * (com o comprimento dado) e sentido ({@code flow_direction})
     * contra as cotas. As cotas nas pontas da geometria derivam das
     * cotas de montante/jusante conforme o sentido declarado:
     * WITH_GEOMETRY escoa do nó 1 (montante) ao nó 2 (jusante);
     * AGAINST_GEOMETRY, o inverso — a ponta de montante é o nó 2.
     * Sem as duas cotas, nenhuma das regras se aplica (a
     * {@code slope} do banco também nasce nula nesse caso).
     */
    private List<DrainIssue> validateArcData(DrainFeatureInput in, double lengthM,
                                             Double tenantMinSlope) {
        List<DrainIssue> issues = new ArrayList<>();
        if (in.upstreamInvertM() == null || in.downstreamInvertM() == null) {
            return issues;
        }
        if (lengthM > 0) {
            issues.addAll(GisDrainRules.checkSlope(
                    in.upstreamInvertM(), in.downstreamInvertM(), lengthM, tenantMinSlope));
        }
        if (in.flowDirection() != null) {
            boolean against = "AGAINST_GEOMETRY".equals(in.flowDirection());
            double invertAtNode1 = against ? in.downstreamInvertM() : in.upstreamInvertM();
            double invertAtNode2 = against ? in.upstreamInvertM() : in.downstreamInvertM();
            issues.addAll(GisDrainRules.checkFlowDirection(
                    in.flowDirection(), invertAtNode1, invertAtNode2));
        }
        return issues;
    }

    /**
     * Comprimento geodésico (m) do arco a partir da geometria
     * GeoJSON de entrada (EPSG 4674): soma haversiana dos segmentos
     * da LineString/MultiLineString sobre a esfera de raio médio
     * terrestre. É o comprimento usado na validação de declividade
     * da gravação; o {@code length_m} GRAVADO segue sendo o do
     * banco (ST_Length sobre geography, elipsoide, V13_2) — a
     * diferença esfera×elipsoide (&lt; 0,5%) não muda sinal nem
     * nulidade da declividade, que são os erros que recusam.
     *
     * <p>Com as duas cotas informadas e comprimento não
     * determinável (geometria sem ao menos dois pontos distintos),
     * a gravação é RECUSADA: sem comprimento não há como provar o
     * escoamento por gravidade, e gravar em silêncio era o defeito
     * D1 da revisão.
     */
    private static double arcLengthM(DrainFeatureInput in) {
        double length = geometryLengthM(in.geometryGeoJson());
        if (in.upstreamInvertM() != null && in.downstreamInvertM() != null && length <= 0) {
            throw new IllegalArgumentException(
                    "comprimento do arco nao determinavel a partir da geometria: "
                            + "com as cotas de montante e jusante informadas, a "
                            + "validacao de declividade/sentido (§5.4) exige uma "
                            + "geometria de linha com ao menos dois pontos distintos");
        }
        return length;
    }

    private static double geometryLengthM(Object geometry) {
        if (!(geometry instanceof Map<?, ?> map)) {
            return 0;
        }
        Object type = map.get("type");
        Object coordinates = map.get("coordinates");
        if ("LineString".equals(type)) {
            return lineLengthM(coordinates);
        }
        if ("MultiLineString".equals(type) && coordinates instanceof List<?> lines) {
            double total = 0;
            for (Object line : lines) {
                total += lineLengthM(line);
            }
            return total;
        }
        return 0;
    }

    private static double lineLengthM(Object coordinates) {
        if (!(coordinates instanceof List<?> points) || points.size() < 2) {
            return 0;
        }
        double total = 0;
        double[] previous = null;
        for (Object point : points) {
            double[] lonLat = lonLat(point);
            if (lonLat == null) {
                return 0;
            }
            if (previous != null) {
                total += haversineM(previous, lonLat);
            }
            previous = lonLat;
        }
        return total;
    }

    private static double[] lonLat(Object point) {
        if (point instanceof List<?> coords && coords.size() >= 2
                && coords.get(0) instanceof Number lon
                && coords.get(1) instanceof Number lat) {
            return new double[]{lon.doubleValue(), lat.doubleValue()};
        }
        return null;
    }

    private static double haversineM(double[] a, double[] b) {
        double earthRadiusM = 6_371_008.8;
        double dLat = Math.toRadians(b[1] - a[1]);
        double dLon = Math.toRadians(b[0] - a[0]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[1])) * Math.cos(Math.toRadians(b[1]))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusM * Math.asin(Math.sqrt(h));
    }

    // -------------------------------------------------------- foto/inspeção

    @Transactional
    public void attachPhoto(String featureTable, UUID featureId, PhotoInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        ensureFeatureTable(featureTable);
        jdbc.update("""
                INSERT INTO gis_attachment (id, tenant_id, target_feature_table,
                        target_feature_id, attachment_type, document_id, description,
                        created_by, updated_by)
                VALUES (?, ?, ?, ?, 'PHOTO', ?, ?, ?, ?)
                """, UUID.randomUUID(), tenantId, featureTable, featureId,
                in.documentId(), in.description(), userId, userId);
    }

    /**
     * Registra a inspeção e atualiza a feição: condition_rating,
     * obstrução e data da última inspeção são os atributos comuns da
     * NR 12/2025 (§4.4) — a foto da inspeção entra por
     * {@link #attachPhoto} e é referenciada pelo id do anexo.
     */
    @Transactional
    public InspectionView registerInspection(String featureTable, UUID featureId,
                                             InspectionInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        ensureFeatureTable(featureTable);
        try {
            GisDrainRules.ensureInspection(in.inspectedOn(), in.method(),
                    in.conditionRating(), in.obstructionPct(), in.videoRef());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("inspecao-invalida", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_drain_inspection (id, tenant_id, feature_table, feature_id,
                        inspected_on, method, condition_rating, obstruction_pct,
                        photo_attachment_id, video_ref, notes, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, featureTable, featureId, in.inspectedOn(), in.method(),
                in.conditionRating(), in.obstructionPct(), in.photoAttachmentId(),
                in.videoRef(), in.notes(), userId, userId);
        // Atualiza os atributos comuns da feicao (§4.4): conservacao na
        // coluna, obstrucao e ultima inspecao no JSONB. Correcao D5
        // da revisao: inspecao SEM obstrucao informada NAO apaga a
        // obstrucao anteriormente registrada — a chave so entra no
        // objeto quando o valor vem na inspecao.
        jdbc.update("""
                UPDATE %s
                   SET condition_rating = COALESCE(?, condition_rating),
                       attributes = attributes || CASE
                           WHEN ?::numeric IS NOT NULL
                           THEN jsonb_build_object('obstruction_pct', ?::numeric,
                                                   'last_inspection_on', ?::text)
                           ELSE jsonb_build_object('last_inspection_on', ?::text)
                           END,
                       updated_by = ?, updated_at = NOW()
                 WHERE id = ? AND tenant_id = ?
                """.formatted(featureTable), in.conditionRating(), in.obstructionPct(),
                in.obstructionPct(), in.inspectedOn().toString(),
                in.inspectedOn().toString(), userId, featureId, tenantId);
        return new InspectionView(id, featureTable, featureId, in.inspectedOn(), in.method(),
                in.conditionRating(), in.obstructionPct(), in.videoRef());
    }

    public List<InspectionView> inspectionsOf(String featureTable, UUID featureId) {
        UUID tenantId = tenantId();
        ensureFeatureTable(featureTable);
        return jdbc.query("""
                SELECT id, inspected_on, method, condition_rating, obstruction_pct, video_ref
                  FROM gis_drain_inspection
                 WHERE tenant_id = ? AND feature_table = ? AND feature_id = ?
                 ORDER BY inspected_on DESC
                """, (rs, n) -> new InspectionView(
                rs.getObject("id", UUID.class), featureTable, featureId,
                rs.getObject("inspected_on", LocalDate.class), rs.getString("method"),
                rs.getString("condition_rating"),
                rs.getObject("obstruction_pct", Double.class), rs.getString("video_ref")),
                tenantId, featureTable, featureId);
    }

    // ------------------------------------------------------------- sub-bacia

    @Transactional
    public DrainFeatureView createSubcatchment(SubcatchmentInput in) {
        return createFeature(new DrainFeatureInput(in.code(), "STORM_SUBCATCHMENT",
                in.name(), "IN_SERVICE", "DESIGN", null, in.attributes(),
                in.geometryGeoJson(), null, null, null, null, null));
    }

    // -------------------------------------------------------- plano de limpeza

    @Transactional
    public void upsertCleaningPlan(CleaningPlanInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        try {
            new CleaningPlanLine(in.elementClass(), in.intervalMonths(),
                    in.obstructionThresholdPct(), in.includePoorOrCritical());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("plano-invalido", e.getMessage());
        }
        jdbc.update("""
                INSERT INTO gis_drain_cleaning_plan (id, tenant_id, element_class,
                        interval_months, obstruction_threshold_pct,
                        include_poor_or_critical, plan_start, active,
                        created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                ON CONFLICT (tenant_id, element_class) DO UPDATE
                   SET interval_months = EXCLUDED.interval_months,
                       obstruction_threshold_pct = EXCLUDED.obstruction_threshold_pct,
                       include_poor_or_critical = EXCLUDED.include_poor_or_critical,
                       plan_start = EXCLUDED.plan_start, active = TRUE,
                       updated_by = EXCLUDED.updated_by, updated_at = NOW()
                """, UUID.randomUUID(), tenantId, in.elementClass(), in.intervalMonths(),
                in.obstructionThresholdPct(), in.includePoorOrCritical(), in.planStart(),
                userId, userId);
    }

    /**
     * Gera os pedidos recorrentes de limpeza devidos na data: por
     * classe do plano, para cada feição em serviço — calendário
     * (última limpeza concluída + intervalo) ou critério (obstrução/
     * conservação). Classe sem intervalo e sem critério atingido é
     * PULADA com o motivo registrado (nunca vira pedido inventado).
     * Pedido aberto para a mesma feição e vencimento não duplica.
     */
    @Transactional
    public GenerationResult generateCleaningRequests(LocalDate referenceDate) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        List<CleaningPlanLine> plan = jdbc.query("""
                SELECT element_class, interval_months, obstruction_threshold_pct,
                       include_poor_or_critical, plan_start
                  FROM gis_drain_cleaning_plan
                 WHERE tenant_id = ? AND active = TRUE
                """, (rs, n) -> new CleaningPlanLine(rs.getString("element_class"),
                rs.getObject("interval_months", Integer.class),
                rs.getObject("obstruction_threshold_pct", Double.class),
                rs.getBoolean("include_poor_or_critical")), tenantId);
        Map<String, LocalDate> planStarts = jdbc.query("""
                SELECT element_class, plan_start FROM gis_drain_cleaning_plan
                 WHERE tenant_id = ? AND active = TRUE
                """, (rs, n) -> Map.entry(rs.getString("element_class"),
                rs.getObject("plan_start", LocalDate.class)), tenantId)
                .stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue));
        int created = 0;
        List<String> skipped = new ArrayList<>();
        for (CleaningPlanLine line : plan) {
            String table = tableForElementClass(line.elementClass());
            List<Map<String, Object>> features = jdbc.queryForList("""
                    SELECT f.id, f.condition_rating,
                           (f.attributes ->> 'obstruction_pct')::numeric AS obstruction,
                           (SELECT MAX(r.completed_on) FROM gis_drain_cleaning_request r
                             WHERE r.tenant_id = f.tenant_id AND r.feature_id = f.id
                               AND r.status = 'DONE') AS last_cleaned
                      FROM %s f
                      JOIN gis_feature_type ft
                        ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                     WHERE f.tenant_id = ? AND f.lifecycle_status = 'IN_SERVICE'
                       AND ft.code = ?
                    """.formatted(table), tenantId, line.elementClass());
            for (Map<String, Object> f : features) {
                UUID featureId = (UUID) f.get("id");
                LocalDate lastCleaned = f.get("last_cleaned") == null ? null
                        : ((java.sql.Date) f.get("last_cleaned")).toLocalDate();
                Double obstruction = f.get("obstruction") == null ? null
                        : ((Number) f.get("obstruction")).doubleValue();
                String condition = (String) f.get("condition_rating");
                boolean byCriteria = GisDrainMaintenance.isDueByCriteria(
                        line, obstruction, condition);
                var due = GisDrainMaintenance.nextDue(line, lastCleaned,
                        planStarts.get(line.elementClass()));
                boolean byCalendar = due.isPresent() && !due.get().isAfter(referenceDate);
                if (!byCalendar && !byCriteria) {
                    if (due.isEmpty()) {
                        skipped.add(line.elementClass() + "/" + featureId
                                + ": sem intervalo no plano (drain.cleaning_interval_by_class)");
                    }
                    continue;
                }
                LocalDate dueOn = byCalendar ? due.get() : referenceDate;
                String basis = byCalendar && byCriteria ? "CALENDAR_AND_CRITERIA"
                        : byCalendar ? "CALENDAR" : "CRITERIA";
                Integer inserted = jdbc.queryForObject("""
                        INSERT INTO gis_drain_cleaning_request (id, tenant_id, feature_table,
                                feature_id, element_class, due_on, basis, status,
                                created_by, updated_by)
                        SELECT ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?
                         WHERE NOT EXISTS (
                               SELECT 1 FROM gis_drain_cleaning_request r
                                WHERE r.tenant_id = ? AND r.feature_id = ?
                                  AND r.due_on = ? AND r.status IN ('PENDING', 'ORDERED'))
                        RETURNING 1
                        """, Integer.class, UUID.randomUUID(), tenantId, table, featureId,
                        line.elementClass(), dueOn, basis, userId, userId,
                        tenantId, featureId, dueOn);
                if (inserted != null) {
                    created++;
                }
            }
        }
        return new GenerationResult(created, List.copyOf(skipped));
    }

    public List<CleaningRequestView> cleaningRequests(String status) {
        UUID tenantId = tenantId();
        return jdbc.query("""
                SELECT id, feature_table, feature_id, element_class, due_on, basis,
                       service_order_id, status
                  FROM gis_drain_cleaning_request
                 WHERE tenant_id = ? AND (? IS NULL OR status = ?)
                 ORDER BY due_on
                """, (rs, n) -> new CleaningRequestView(
                rs.getObject("id", UUID.class), rs.getString("feature_table"),
                rs.getObject("feature_id", UUID.class), rs.getString("element_class"),
                rs.getObject("due_on", LocalDate.class), rs.getString("basis"),
                rs.getObject("service_order_id", UUID.class), rs.getString("status")),
                tenantId, status, status);
    }

    // ------------------------------------------------------------------ chuva

    @Transactional
    public UUID createRainStation(RainStationInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        try {
            GisDrainRain.ensureRainSource(in.sourceKind());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("chuva-invalida", e.getMessage());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_drain_rain_station (id, tenant_id, code, source_kind,
                        name, source_ref, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, in.code(), in.sourceKind(), in.name(), in.sourceRef(),
                userId, userId);
        return id;
    }

    /**
     * Importa o CSV mensal na estação (Cemaden por download ou
     * pluviômetro próprio): a fonte citada do lote é gravada em cada
     * leitura — chuva sem fonte não entra (ver GisDrainRain).
     */
    @Transactional
    public RainImportView importRain(UUID stationId, String sourceRef, String csvContent) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        String sourceKind = jdbc.queryForObject("""
                SELECT source_kind FROM gis_drain_rain_station
                 WHERE id = ? AND tenant_id = ?
                """, String.class, stationId, tenantId);
        GisDrainRain.RainImportResult result;
        try {
            result = GisDrainRain.importCsv(sourceKind, sourceRef, csvContent);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("chuva-invalida", e.getMessage());
        }
        // Correcao D7 da revisao: a vista conta as leituras GRAVADAS
        // (o retorno de cada INSERT), nao as lidas — duplicada do
        // mesmo instante e descartada pelo ON CONFLICT DO NOTHING e
        // nao entra na contagem nem no total.
        int stored = 0;
        double storedMm = 0;
        for (GisDrainRain.RainReading r : result.readings()) {
            int inserted = jdbc.update("""
                    INSERT INTO gis_drain_rain_reading (id, tenant_id, station_id,
                            read_at, rain_mm, source_ref, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, station_id, read_at) DO NOTHING
                    """, UUID.randomUUID(), tenantId, stationId,
                    java.sql.Timestamp.valueOf(r.readAt()), r.rainMm(), sourceRef,
                    userId, userId);
            if (inserted > 0) {
                stored++;
                storedMm += r.rainMm();
            }
        }
        return new RainImportView(stationId, stored, storedMm, sourceRef);
    }

    // -------------------------------------------------------------- alagamento

    /**
     * Registra a ocorrência de alagamento e a liga à chuva do
     * período: o acumulado é calculado das leituras IMPORTADAS do
     * tenant no período fechado; sem leitura, fica NULL
     * (desconhecido) — nunca zero. A causa ALAGAMENTO é o marcador
     * para o catálogo da ED-92 (ver javadoc da classe).
     */
    @Transactional
    public FloodOccurrenceView registerFloodOccurrence(FloodOccurrenceInput in) {
        UUID tenantId = tenantId();
        UUID userId = userId();
        if (in.periodEnd().isBefore(in.periodStart())) {
            throw new ApiException.BadRequest("alagamento-invalido",
                    "periodo da ocorrencia invertido");
        }
        List<GisDrainRain.RainReading> readings = jdbc.query("""
                SELECT read_at, rain_mm FROM gis_drain_rain_reading
                 WHERE tenant_id = ? AND read_at >= ? AND read_at <= ?
                """, (rs, n) -> new GisDrainRain.RainReading(
                rs.getTimestamp("read_at").toLocalDateTime(), rs.getDouble("rain_mm")),
                tenantId, java.sql.Timestamp.valueOf(in.periodStart()),
                java.sql.Timestamp.valueOf(in.periodEnd()));
        Double rainMm = GisDrainRain.accumulatedMm(readings, in.periodStart(), in.periodEnd());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO gis_drain_flood_occurrence (id, tenant_id, period_start,
                        period_end, feature_table, feature_id, subcatchment_zone_id,
                        rain_mm_in_period, network_cause_code, description,
                        created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, java.sql.Timestamp.valueOf(in.periodStart()),
                java.sql.Timestamp.valueOf(in.periodEnd()), in.featureTable(),
                in.featureId(), in.subcatchmentZoneId(), rainMm, FLOOD_CAUSE_CODE,
                in.description(), userId, userId);
        return new FloodOccurrenceView(id, in.periodStart(), in.periodEnd(), rainMm,
                FLOOD_CAUSE_CODE, null, in.description());
    }

    // ------------------------------------------------- relatórios e temático

    /** GIS-003: cadastro de drenagem — extensão por material e estado. */
    public List<CadastroReportRow> cadastroReport() {
        tenantId();
        return jdbc.query("""
                SELECT material, estado_conservacao, tipo, extensao_m, arcos,
                       obstrucao_media_pct
                  FROM rpt_gis_drenagem_cadastro
                 ORDER BY material, estado_conservacao, tipo
                """, (rs, n) -> new CadastroReportRow(rs.getString("material"),
                rs.getString("estado_conservacao"), rs.getString("tipo"),
                rs.getDouble("extensao_m"), rs.getLong("arcos"),
                rs.getObject("obstrucao_media_pct", Double.class)));
    }

    /** GIS-004/OPE-32: indicadores mensais calculados das contagens (GisDrainMaintenance). */
    public GisDrainMaintenance.DrainIndicators indicators(LocalDate from, LocalDate to) {
        UUID tenantId = tenantId();
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.atTime(23, 59, 59);
        Long floods = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_drain_flood_occurrence
                 WHERE tenant_id = ? AND period_start >= ? AND period_start <= ?
                """, Long.class, tenantId, java.sql.Timestamp.valueOf(fromTs),
                java.sql.Timestamp.valueOf(toTs));
        List<Double> rainPerFlood = jdbc.queryForList("""
                SELECT rain_mm_in_period FROM gis_drain_flood_occurrence
                 WHERE tenant_id = ? AND period_start >= ? AND period_start <= ?
                """, Double.class, tenantId, java.sql.Timestamp.valueOf(fromTs),
                java.sql.Timestamp.valueOf(toTs));
        Long inspections = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_drain_inspection
                 WHERE tenant_id = ? AND inspected_on >= ? AND inspected_on <= ?
                """, Long.class, tenantId, from, to);
        Long due = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_drain_cleaning_request
                 WHERE tenant_id = ? AND due_on >= ? AND due_on <= ?
                """, Long.class, tenantId, from, to);
        Long done = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_drain_cleaning_request
                 WHERE tenant_id = ? AND due_on >= ? AND due_on <= ? AND status = 'DONE'
                """, Long.class, tenantId, from, to);
        Long networkInService = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM gis_arc a JOIN gis_feature_type ft
                          ON ft.id = a.feature_type_id AND ft.tenant_id = a.tenant_id
                         WHERE a.tenant_id = ? AND a.lifecycle_status = 'IN_SERVICE'
                           AND ft.factory_code LIKE 'gis13:%')
                     + (SELECT COUNT(*) FROM gis_node n JOIN gis_feature_type ft
                          ON ft.id = n.feature_type_id AND ft.tenant_id = n.tenant_id
                         WHERE n.tenant_id = ? AND n.lifecycle_status = 'IN_SERVICE'
                           AND ft.factory_code LIKE 'gis13:%')
                     + (SELECT COUNT(*) FROM gis_structure s JOIN gis_feature_type ft
                          ON ft.id = s.feature_type_id AND ft.tenant_id = s.tenant_id
                         WHERE s.tenant_id = ? AND s.lifecycle_status = 'IN_SERVICE'
                           AND ft.factory_code LIKE 'gis13:%')
                """, Long.class, tenantId, tenantId, tenantId);
        Long inspectedDistinct = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT (feature_table, feature_id))
                  FROM gis_drain_inspection
                 WHERE tenant_id = ? AND inspected_on >= ? AND inspected_on <= ?
                """, Long.class, tenantId, from, to);
        // Correcao D4 da revisao: filtro de tenant explicito nas
        // consultas sobre o tematico, como em todas as demais deste
        // metodo (a view e security_invoker/RLS, mas o servico nao
        // depende disso para escopar).
        Long withCondition = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_thematic_drain_condition
                 WHERE tenant_id = ?
                   AND condition_rating IS NOT NULL AND condition_rating <> 'UNKNOWN'
                """, Long.class, tenantId);
        Long poorCritical = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gis_thematic_drain_condition
                 WHERE tenant_id = ?
                   AND condition_rating IN ('POOR', 'CRITICAL')
                """, Long.class, tenantId);
        List<Double> obstructionSamples = jdbc.queryForList("""
                SELECT obstruction_pct FROM gis_drain_inspection
                 WHERE tenant_id = ? AND inspected_on >= ? AND inspected_on <= ?
                   AND obstruction_pct IS NOT NULL
                """, Double.class, tenantId, from, to);
        return GisDrainMaintenance.indicators(
                floods == null ? 0 : floods, rainPerFlood,
                inspections == null ? 0 : inspections,
                networkInService == null ? 0 : networkInService,
                inspectedDistinct == null ? 0 : inspectedDistinct,
                due == null ? 0 : due, done == null ? 0 : done,
                withCondition == null ? 0 : withCondition,
                poorCritical == null ? 0 : poorCritical, obstructionSamples);
    }

    /** Temático de conservação: contagem de feições por condition_rating. */
    public List<ConservationClassCount> conservationThematic() {
        UUID tenantId = tenantId();
        // Mesmo padrao do D4: filtro de tenant explicito sobre a view.
        return jdbc.query("""
                SELECT condition_rating, COUNT(*) AS features
                  FROM gis_thematic_drain_condition
                 WHERE tenant_id = ?
                 GROUP BY condition_rating
                 ORDER BY condition_rating
                """, (rs, n) -> new ConservationClassCount(
                rs.getString("condition_rating"), rs.getLong("features")), tenantId);
    }

    /** Manifesto de símbolos (inventário; arte é da P1.30 — marcador declarado). */
    public List<SymbolManifestView> symbolManifest(Set<String> librarySymbolKeys) {
        List<SymbolManifestView> out = new ArrayList<>();
        for (GisDrainCatalog.DrainType type : GisDrainCatalog.TYPES) {
            for (String state : GisDrainSymbols.SYMBOL_STATES) {
                String key = GisDrainSymbols.symbolKeyFor(type, state);
                out.add(new SymbolManifestView(type.code(), state, key,
                        librarySymbolKeys.contains(key)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------- helpers

    private static String tableForElementClass(String elementClass) {
        return switch (elementClass) {
            case "INLET", "STORM_MANHOLE" -> "gis_node";
            case "STORM_PIPE", "BOX_CULVERT", "OPEN_CHANNEL" -> "gis_arc";
            case "DETENTION_BASIN" -> "gis_structure";
            default -> throw new IllegalArgumentException(
                    "classe de elemento sem tabela: " + elementClass);
        };
    }

    private static void ensureFeatureTable(String featureTable) {
        if (!Set.of("gis_node", "gis_arc", "gis_structure", "gis_zone").contains(featureTable)) {
            throw new ApiException.BadRequest("drenagem-invalida",
                    "tabela de feicao fora das tabelas GIS: " + featureTable);
        }
    }

    private UUID nodeIdByCode(UUID tenantId, String code) {
        if (code == null) {
            return null;
        }
        List<UUID> rows = jdbc.queryForList("""
                SELECT id FROM gis_node WHERE tenant_id = ? AND code = ?
                """, UUID.class, tenantId, code);
        if (rows.isEmpty()) {
            throw new ApiException.BadRequest("drenagem-invalida",
                    "no da ponta nao encontrado: " + code);
        }
        return rows.get(0);
    }

    private UUID featureTypeId(UUID tenantId, GisDrainCatalog.DrainType type) {
        List<UUID> rows = jdbc.queryForList("""
                SELECT id FROM gis_feature_type
                 WHERE tenant_id = ? AND factory_code = ?
                """, UUID.class, tenantId, type.factoryCode());
        if (rows.isEmpty()) {
            throw new ApiException.BadRequest("catalogo-ausente",
                    "tipo da GIS-13 nao carregado para o tenant: " + type.factoryCode()
                            + " (catalogo do §4.4 pendente de aplicacao)");
        }
        return rows.get(0);
    }

    private static List<DrainIssue> concat(List<DrainIssue> a, List<DrainIssue> b) {
        List<DrainIssue> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
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
