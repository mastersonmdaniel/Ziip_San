package br.com.ziipsan.intelligence.gis.epanet;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.hydraulic.EpanetInpWriter;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.SnapshotRequest;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterArc;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterNode;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicModel.WaterSnapshot;
import br.com.ziipsan.intelligence.gis.hydraulic.HydraulicPublication;
import br.com.ziipsan.intelligence.gis.hydraulic.solver.SolverPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GIS-12 (ED-63) — SUBSTITUI o serviço da GIS-03. Mudanças de fundo
 * (M5/M6 do GIS-CADASTRO-TECNICO §2.3):
 *
 * <ul>
 *   <li>não lê mais a view de compatibilidade {@code gis_network_element}
 *       nem a entidade {@code GisNetworkElement} (ambas removidas pelo SQL
 *       pendente desta entrega): a foto vem de {@code gis_node}/{@code gis_arc}
 *       por zona, estado e data, com o mapeamento tipo → elemento da tabela
 *       {@code gis_hydraulic_mapping} (por tenant);</li>
 *   <li>não devolve mais aviso de "solução não calculada":
 *       {@link #runSimulation} executa o solver real (porta
 *       {@link SolverPort}; produção = biblioteca do EPANET 2.2) sobre a
 *       topologia persistida e publica pelo portão da N7. Sem solver
 *       configurado, a chamada FALHA — resultado inventado ou vazio
 *       disfarçado não existe mais;</li>
 *   <li>a demanda é o consumo faturado das ligações do nó, injetado pela
 *       porta {@link BilledConsumptionSource} (a agregação vive no módulo
 *       comercial/faturamento, GIS-05; o GIS não duplica a consulta).</li>
 * </ul>
 */
@Service
public class EpanetSimulationService {

    /** Consumo faturado agregado por código de nó no período (fonte: faturamento, via GIS-05). */
    public interface BilledConsumptionSource {
        Map<String, BigDecimal> billedVolumeM3ByNode(UUID tenantId, String zoneCode, int periodDays);
    }

    private final JdbcTemplate jdbc;
    private final SolverPort solver;
    private final BilledConsumptionSource consumption;

    public EpanetSimulationService(JdbcTemplate jdbc, SolverPort solver, BilledConsumptionSource consumption) {
        this.jdbc = jdbc;
        this.solver = solver;
        this.consumption = consumption;
    }

    /**
     * Foto da rede de água por zona/estado/data direto das tabelas novas.
     * Data = tempo do negócio: installed_on <= data e (abandoned_on nulo ou > data).
     * Zona = feições do DMA da zona ou contidas no polígono dela.
     */
    @Transactional(readOnly = true)
    public WaterSnapshot loadSnapshot(SnapshotRequest request, int billedPeriodDays) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Map<String, String> mapping = loadMapping(tenantId, "EPANET");

        List<WaterNode> nodes = new ArrayList<>();
        List<Map<String, Object>> nodeRows = jdbc.queryForList("""
                SELECT n.code, n.name, ft.code AS type_code, n.ground_elevation_m, n.rim_elevation_m,
                       n.invert_elevation_m, n.setting,
                       (n.attributes ->> 'head_m')::numeric AS head_m,
                       (n.attributes ->> 'init_level_m')::numeric AS init_level_m,
                       (n.attributes ->> 'min_level_m')::numeric AS min_level_m,
                       (n.attributes ->> 'max_level_m')::numeric AS max_level_m,
                       (n.attributes ->> 'diameter_m')::numeric AS diameter_m,
                       ST_X(n.geom) AS x, ST_Y(n.geom) AS y
                  FROM gis_node n
                  JOIN gis_feature_type ft ON ft.tenant_id = n.tenant_id AND ft.id = n.feature_type_id
                  JOIN gis_zone z ON z.tenant_id = n.tenant_id AND z.code = ?
                 WHERE n.tenant_id = ? AND n.lifecycle_status = ANY (?)
                   AND (n.installed_on IS NULL OR n.installed_on <= ?)
                   AND (n.abandoned_on IS NULL OR n.abandoned_on > ?)
                   AND (n.dma_id = z.dma_district_id OR ST_Contains(z.geom, n.geom))
                """, request.zoneCode(), tenantId,
                request.lifecycleStatuses().toArray(new String[0]), request.asOf(), request.asOf());
        for (Map<String, Object> r : nodeRows) {
            String element = mapping.get(str(r, "type_code"));
            if (element == null) continue; // tipo sem papel hidráulico no mapeamento do tenant
            WaterNode.WaterNodeKind kind = switch (element) {
                case "RESERVOIR" -> WaterNode.WaterNodeKind.RESERVOIR;
                case "TANK" -> WaterNode.WaterNodeKind.TANK;
                default -> WaterNode.WaterNodeKind.JUNCTION;
            };
            nodes.add(new WaterNode(str(r, "code"), str(r, "name"), kind,
                    num(r, "ground_elevation_m"), num(r, "head_m"), num(r, "init_level_m"),
                    num(r, "min_level_m"), num(r, "max_level_m"), num(r, "diameter_m"),
                    num(r, "x"), num(r, "y")));
        }

        List<WaterArc> arcs = new ArrayList<>();
        List<Map<String, Object>> arcRows = jdbc.queryForList("""
                SELECT a.code, a.name, ft.code AS type_code, n1.code AS node1_code, n2.code AS node2_code,
                       COALESCE(a.declared_length_m, a.length_m) AS length_m,
                       ci.dn_mm, m.code AS material_code, m.hazen_williams_c,
                       (a.attributes ->> 'roughness')::numeric AS roughness,
                       (a.attributes ->> 'power_kw')::numeric AS power_kw,
                       (a.attributes ->> 'valve_type') AS valve_type, (a.attributes ->> 'setting')::numeric AS setting, a.operational_status
                  FROM gis_arc a
                  JOIN gis_feature_type ft ON ft.tenant_id = a.tenant_id AND ft.id = a.feature_type_id
                  LEFT JOIN gis_node n1 ON n1.tenant_id = a.tenant_id AND n1.id = a.node_1_id
                  LEFT JOIN gis_node n2 ON n2.tenant_id = a.tenant_id AND n2.id = a.node_2_id
                  LEFT JOIN gis_catalog_item ci ON ci.tenant_id = a.tenant_id AND ci.id = a.catalog_item_id
                  LEFT JOIN gis_material m ON m.tenant_id = a.tenant_id AND m.id = ci.material_id
                  JOIN gis_zone z ON z.tenant_id = a.tenant_id AND z.code = ?
                 WHERE a.tenant_id = ? AND a.lifecycle_status = ANY (?)
                   AND (a.installed_on IS NULL OR a.installed_on <= ?)
                   AND (a.abandoned_on IS NULL OR a.abandoned_on > ?)
                   AND (a.dma_id = z.dma_district_id OR ST_Contains(z.geom, ST_Centroid(a.geom)))
                """, request.zoneCode(), tenantId,
                request.lifecycleStatuses().toArray(new String[0]), request.asOf(), request.asOf());
        Map<String, BigDecimal> hwC = new LinkedHashMap<>();
        for (Map<String, Object> r : arcRows) {
            String element = mapping.get(str(r, "type_code"));
            if (element == null) continue;
            if (str(r, "material_code") != null && num(r, "hazen_williams_c") != null) {
                hwC.put(str(r, "material_code"), num(r, "hazen_williams_c"));
            }
            WaterArc.WaterArcKind kind = switch (element) {
                case "PUMP" -> WaterArc.WaterArcKind.PUMP;
                case "VALVE" -> WaterArc.WaterArcKind.VALVE;
                default -> WaterArc.WaterArcKind.PIPE;
            };
            boolean open = !"CLOSED".equalsIgnoreCase(str(r, "operational_status"));
            arcs.add(new WaterArc(str(r, "code"), str(r, "name"), kind, str(r, "node1_code"), str(r, "node2_code"),
                    num(r, "length_m"), intOrNull(r, "dn_mm"), str(r, "material_code"), num(r, "roughness"),
                    num(r, "power_kw"), str(r, "valve_type"), num(r, "setting"), open));
        }

        Map<String, BigDecimal> billed = consumption == null ? Map.of()
                : consumption.billedVolumeM3ByNode(tenantId, request.zoneCode(), billedPeriodDays);
        return new WaterSnapshot(request, "Rede de agua - zona " + request.zoneCode(),
                nodes, arcs, billed, billedPeriodDays, hwC, List.of());
    }

    /** Gera o .inp do recorte (sem executar). Problemas de modelo viram 422 com a lista. */
    @Transactional(readOnly = true)
    public String exportInp(SnapshotRequest request, int billedPeriodDays) {
        try {
            return EpanetInpWriter.write(loadSnapshot(request, billedPeriodDays));
        } catch (br.com.ziipsan.intelligence.gis.hydraulic.HydraulicValidationException e) {
            throw new ApiException.Validation(String.join(" | ", e.issues()), List.of(), List.of());
        }
    }

    /** Executa o solver REAL e devolve o resultado cru (base da calibração e dos cenários). */
    @Transactional(readOnly = true)
    public SolverPort.SolverResult runCalculation(SnapshotRequest request, int billedPeriodDays) {
        String inp = exportInp(request, billedPeriodDays);
        try {
            return solver.runEpanet(inp);
        } catch (SolverPort.SolverUnavailableException | SolverPort.SolverExecutionException e) {
            throw new ApiException.Validation(e.getMessage(), List.of(), List.of());
        }
    }

    /**
     * Executa e PUBLICA pelo portão da N7: exige solver, versão (do
     * relatório do solver), premissas e grau de confiança — este último
     * vem da calibração (HydraulicCalibration) ou da declaração do
     * cenário; sem ele a publicação é barrada, nunca presumida.
     */
    @Transactional(readOnly = true)
    public HydraulicPublication.PublishedResult runSimulation(SnapshotRequest request, int billedPeriodDays,
                                                              List<String> premises,
                                                              HydraulicPublication.Confidence confidence) {
        SolverPort.SolverResult result = runCalculation(request, billedPeriodDays);
        List<String> allPremises = new ArrayList<>();
        allPremises.add("Topologia persistida gis_node/gis_arc, zona " + request.zoneCode()
                + ", estados " + request.lifecycleStatuses() + ", data " + request.asOf());
        allPremises.add("Demanda do consumo faturado agregado por no, periodo de " + billedPeriodDays + " dias");
        if (premises != null) allPremises.addAll(premises);
        return HydraulicPublication.publish(new HydraulicPublication.PublishedResult(
                result, "EPANET", result.solverVersion(), allPremises, confidence));
    }

    private Map<String, String> loadMapping(UUID tenantId, String model) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT feature_type_code, element_code FROM gis_hydraulic_mapping WHERE tenant_id = ? AND model = ? AND active",
                tenantId, model)) {
            map.put(str(r, "feature_type_code"), str(r, "element_code"));
        }
        return map;
    }

    private static String str(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? null : v.toString();
    }

    private static BigDecimal num(Map<String, Object> r, String key) {
        Object v = r.get(key);
        if (v == null) return null;
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    private static Integer intOrNull(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? null : ((Number) v).intValue();
    }

    /* Enums da seccao [OPTIONS]/[REPORT] do EPANET, declarados na
       entrega original da P2.21 e referenciados por
       EpanetSimulationOptions, InpWriter e os seus testes. A versao
       deste servico que pousou depois nao os trazia; repoe-se a
       declaracao verbatim da P2.21 — correcao-ci-cura-lote-1
       (varredura de compilacao da CI, 2026-11-09). */
    public record SimulationOptions(
            int duration,
            int hydraulicTimestep,
            Statistic statistic,
            DemandModel demandModel,
            BigDecimal accuracy,
            int maxTrials
    ) {
        public SimulationOptions() {
            this(86400, 3600, Statistic.AVERAGE, DemandModel.DDA,
                    new BigDecimal("0.001"), 40);
        }
    }

    public enum Statistic { NONE, AVERAGE, MINIMUM, MAXIMUM, RANGE };
    public enum DemandModel { DDA, PDA };
    public enum LinkReport { NONE, ALL };
    public enum NodeReport { NONE, ALL };
}
