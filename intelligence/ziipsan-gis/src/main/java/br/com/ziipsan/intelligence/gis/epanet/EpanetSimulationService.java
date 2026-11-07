package br.com.ziipsan.intelligence.gis.epanet;

import br.com.ziipsan.intelligence.gis.domain.GisNetworkElement;
import br.com.ziipsan.intelligence.gis.repository.GisNetworkElementRepository;
import br.com.ziipsan.intelligence.gis.repository.GisValidationIssueRepository;
import br.com.ziipsan.intelligence.losses.repository.DmaDistrictRepository;
import br.com.ziipsan.intelligence.losses.domain.DmaDistrict;
import br.com.ziipsan.intelligence.losses.domain.PressureAnalysis;
import br.com.ziipsan.intelligence.losses.domain.WaterBalance;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.core.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpanetSimulationService {

    private final AuditLogRepository auditLogRepository;
    private final DmaDistrictRepository dmaRepository;
    private final GisNetworkElementRepository networkElementRepository;
    private final GisValidationIssueRepository validationIssueRepository;

    // ========================================================================
    // EPANET NETWORK BUILDING
    // ========================================================================

    @Transactional(readOnly = true)
    public EpanetNetwork buildNetworkFromDma(UUID dmaId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        DmaDistrict dma = dmaRepository.findByIdAndTenantId(dmaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("DMA not found"));

        List<GisNetworkElement> elements = networkElementRepository.findByDmaIdAndTenantId(dmaId, tenantId);

        List<ElementSpec> specs = new ArrayList<>();
        List<GisNetworkElement> candidates = new ArrayList<>();
        int withoutHydraulicRole = 0;
        for (GisNetworkElement element : elements) {
            ElementSpec spec = toSpec(element);
            if (spec == null) {
                withoutHydraulicRole++;
            } else {
                specs.add(spec);
                candidates.add(element);
            }
        }

        // Aceite da GIS-03: feição com erro grave (severidade ERROR) ainda aberto na
        // validação contínua fica FORA da exportação e é listada. A exclusão é aviso, não
        // problema do modelo: o restante da rede continua exportável e o .inp não é
        // recusado por causa dela. Se a feição excluída era ponta ou peça de tubulação, o
        // construtor registra o problema de topologia correspondente, como qualquer
        // referência quebrada.
        Map<UUID, List<String>> openErrors = validationIssueRepository.findOpenErrorRulesByFeatureId(
                tenantId, candidates.stream().map(GisNetworkElement::getId).toList());
        List<String> excluded = new ArrayList<>();
        if (!openErrors.isEmpty()) {
            List<ElementSpec> kept = new ArrayList<>();
            for (int i = 0; i < specs.size(); i++) {
                List<String> rules = openErrors.get(candidates.get(i).getId());
                if (rules == null) {
                    kept.add(specs.get(i));
                } else {
                    excluded.add("Feição " + specs.get(i).code() + " excluída da exportação EPANET: "
                            + "erro grave aberto na validação contínua (regras "
                            + String.join(", ", rules) + "). Corrija o problema ou aceite-o na "
                            + "validação para a feição voltar à exportação.");
                }
            }
            specs = kept;
            Collections.sort(excluded);
        }

        EpanetNetwork network = NetworkTopologyBuilder.build(
                "DMA " + dma.getCode() + " - " + dma.getName(),
                "Gerado do cadastro GIS para o DMC " + dma.getCode(),
                specs);
        network.getWarnings().addAll(excluded);
        if (withoutHydraulicRole > 0) {
            network.getWarnings().add(withoutHydraulicRole + " elemento(s) sem papel hidráulico no modelo foram "
                    + "ignorados (hidrantes, medidores, ligações prediais, ...).");
        }
        network.setOptions(createSimulationOptions());
        return network;
    }

    /**
     * Converte um elemento do cadastro no papel hidráulico do EPANET, ou {@code null} se ele não
     * tem representação no modelo.
     *
     * <p>Reservatório e tanque do cadastro são {@code TANK} do EPANET (volume que enche e esvazia).
     * O {@code RESERVOIR} do EPANET é uma fronteira de carga fixa: só poço, nascente e captação
     * entram assim. Os atributos numéricos vêm do JSON {@code properties}; o que não existe lá
     * continua ausente e o construtor de topologia registra como problema.
     */
    private ElementSpec toSpec(GisNetworkElement e) {
        ElementSpec.Kind kind;
        EpanetValve.ValveType valveType = null;
        Map<String, BigDecimal> numbers = new HashMap<>();
        String text = e.getProperties();

        switch (e.getElementType()) {
            case NODE, JUNCTION -> kind = ElementSpec.Kind.JUNCTION;
            case RESERVOIR, TANK -> kind = ElementSpec.Kind.TANK;
            case WELL, SPRING, INTAKE -> kind = ElementSpec.Kind.RESERVOIR;
            case PUMP_STATION -> kind = ElementSpec.Kind.PUMP;
            case PIPE -> kind = ElementSpec.Kind.PIPE;
            case VALVE -> {
                kind = ElementSpec.Kind.VALVE;
                valveType = parseValveType(text);
            }
            case PRESSURE_REDUCING_VALVE -> {
                kind = ElementSpec.Kind.VALVE;
                valveType = EpanetValve.ValveType.PRV;
            }
            default -> {
                return null;
            }
        }

        parseNumbers(text, numbers);
        // Topologia persistida (GIS-03, M4): as pontas do arco vêm da view; só PIPE as tem.
        return new ElementSpec(e.getElementCode(), e.getName(), kind, e.getGeometry(), e.getDiameterMm(),
                e.getLengthM(), e.getMaterial(), valveType, numbers, e.getNode1Code(), e.getNode2Code());
    }

    /** Lê os campos numéricos do JSON de propriedades. {@code elevation} é aceito como {@code elevation_m}. */
    private void parseNumbers(String propertiesJson, Map<String, BigDecimal> out) {
        if (propertiesJson == null || propertiesJson.isBlank()) return;
        try {
            var node = Json.readTree(propertiesJson);
            node.properties().forEach(en -> {
                if (en.getValue().isNumber()) {
                    out.put(en.getKey(), en.getValue().decimalValue());
                }
            });
            if (!out.containsKey("elevation_m") && out.containsKey("elevation")) {
                out.put("elevation_m", out.get("elevation"));
            }
        } catch (Exception ex) {
            log.warn("Propriedades do elemento não são um JSON válido: {}", ex.getMessage());
        }
    }

    /** {@code valve_type} no JSON (PRV, PSV, PBV, FCV, TCV). Ausente = válvula de isolamento. */
    private EpanetValve.ValveType parseValveType(String propertiesJson) {
        if (propertiesJson == null || propertiesJson.isBlank()) return null;
        try {
            var node = Json.readTree(propertiesJson);
            if (node.has("valve_type") && node.get("valve_type").isString()) {
                return EpanetValve.ValveType.valueOf(node.get("valve_type").asString().trim().toUpperCase(Locale.ROOT));
            }
        } catch (Exception ex) {
            log.warn("valve_type inválido: {}", ex.getMessage());
        }
        return null;
    }

    private EpanetSimulationOptions createSimulationOptions() {
        EpanetSimulationOptions options = new EpanetSimulationOptions();
        options.setDuration(24 * 3600); // 24 hours
        options.setHydraulicTimestep(3600); // 1 hour
        options.setQualityTimestep(3600);
        options.setPatternTimestep(3600);
        options.setPatternStart(0);
        options.setReportTimestep(3600);
        options.setReportStart(0);
        options.setStartClockTime(0);
        options.setStatistic(Statistic.NONE);
        options.setReportLinks(LinkReport.ALL);
        options.setReportNodes(NodeReport.ALL);
        options.setUnbalancedMaxTrials(100);
        options.setUnbalancedTolerance(0.01);
        options.setDemandModel(DemandModel.DDA);
        options.setEmitterExponent(0.5);
        options.setAccuracy(BigDecimal.valueOf(0.001));
        options.setMaxTrials(40);
        options.setCheckFrequency(2);
        options.setDampingLimit(BigDecimal.ZERO);
        return options;
    }

    // ========================================================================
    // SIMULATION EXECUTION (WASM)
    // ========================================================================

    @Transactional
    public SimulationResult runSimulation(UUID dmaId, SimulationRequest request) {
        EpanetNetwork network = buildNetworkFromDma(dmaId);

        // Convert network to EPANET input format (.inp)
        String inpContent = generateInpFile(network);

        // Run simulation via EPANET WASM
        // This would call the WASM module in the browser or via a WASM runtime
        // For server-side, we can use epanet-js via GraalVM or call a microservice
        SimulationResult result = runEpanetWasm(inpContent, network, request.baseOptions());

        // Parse results and store
        if (request.storeResults()) {
            storeSimulationResults(dmaId, result);
        }

        return result;
    }

    /** Gera o .inp. Lança {@link InpValidationException} se o modelo não for confiável. */
    private String generateInpFile(EpanetNetwork network) {
        return InpWriter.write(network);
    }

    private SimulationResult runEpanetWasm(String inpContent, EpanetNetwork network, SimulationOptions options) {
        // ===================================================================
        // NOT A HYDRAULIC SOLVER. No EPANET engine is invoked here.
        //
        // A real implementation calls an EPANET WASM module (epanet-js/wa-epanet)
        // with inpContent, which returns pressures, flows, velocities and
        // headlosses per node/link. That runtime is not wired in yet, so this
        // method does only what can be computed honestly from the network
        // itself: structural counts and length aggregation.
        //
        // Node/link hydraulic maps are deliberately left EMPTY and
        // solverExecuted=false. Empty is reported as empty - never as zeros,
        // and never as invented numbers.
        // ===================================================================
        SimulationResult result = new SimulationResult();
        result.setSimulationTime(OffsetDateTime.now());
        result.setSolverExecuted(false);
        result.setDurationSeconds(0);
        result.setNodeResults(new LinkedHashMap<>());
        result.setLinkResults(new LinkedHashMap<>());
        result.setWarnings(new ArrayList<>());

        // Deterministic structural aggregation - valid without a solver.
        result.setNodeCount(network.nodeCount());
        result.setLinkCount(network.linkCount());
        result.setTotalPipeLengthMeters(network.totalPipeLength());

        // O relatório da simulação carrega os avisos da rede — inclusive a lista de
        // feições excluídas por erro grave aberto (GIS-03), para quem lê o resultado
        // saber o que ficou de fora da exportação e por quê.
        result.getWarnings().addAll(network.getWarnings());
        result.getWarnings().add("Hydraulic solution not computed: no EPANET solver "
                + "is configured. Pressures, flows and velocities are unavailable.");
        if (options != null && options.statistic() != Statistic.AVERAGE) {
            result.getWarnings().add("Requested reporting statistic " + options.statistic()
                    + " is ignored until a solver is available.");
        }

        log.info("EPANET run for network '{}': {} nodes, {} links, {} m of pipe. "
                        + "No hydraulic solution computed (solver not configured).",
                network.getTitle(), result.getNodeCount(), result.getLinkCount(),
                result.getTotalPipeLengthMeters());

        return result;
    }

    private void storeSimulationResults(UUID dmaId, SimulationResult result) {
        // Store node pressures, link flows, tank levels, etc.
    }

    // ========================================================================
    // SCENARIO ANALYSIS
    // ========================================================================

    @Transactional(readOnly = true)
    public ScenarioAnalysisResult analyzeScenario(UUID dmaId, ScenarioRequest request) {
        // Run base simulation. ScenarioRequest carries SimulationOptions, while
        // runSimulation takes a SimulationRequest, so wrap the base options into
        // the request runSimulation expects.
        SimulationResult baseResult = runSimulation(dmaId, new SimulationRequest(
                request.baseOptions(), request.scenarioOptions(), request.storeResults()));

        // NOT IMPLEMENTED: applying scenario modifications (pipe break, valve
        // closure, demand change, pump failure) and re-running. Without a real
        // EPANET solver there is nothing meaningful to compare against, so no
        // deltas are invented here - the result reports scenarioModelled=false.
        ScenarioAnalysisResult analysis = new ScenarioAnalysisResult();
        analysis.setBaseResult(baseResult);
        analysis.setSolverExecuted(baseResult.isSolverExecuted());
        log.warn("Scenario analysis for DMA {} ran the base case only; scenario "
                + "modification and comparison are not implemented", dmaId);

        return analysis;
    }

    // ========================================================================
    // HYDRAULIC CALCULATIONS
    // ========================================================================

    public HydraulicIndicators calculateHydraulicIndicators(UUID dmaId, SimulationResult result) {
        HydraulicIndicators indicators = new HydraulicIndicators();

        // Pressure statistics
        List<BigDecimal> pressures = result.getNodeResults().values().stream()
                .map(r -> r.getPressure())
                .filter(Objects::nonNull)
                .toList();

        if (!pressures.isEmpty()) {
            BigDecimal avg = pressures.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(pressures.size()), 4, RoundingMode.HALF_UP);
            BigDecimal min = Collections.min(pressures);
            BigDecimal max = Collections.max(pressures);

            indicators.setAvgPressure(avg);
            indicators.setMinPressure(Collections.min(pressures));
            indicators.setMaxPressure(max);
            indicators.setPressureDeficitCount(
                    (int) pressures.stream().filter(p -> p.compareTo(BigDecimal.valueOf(20)) < 0).count());
            indicators.setPressureExcessCount(
                    (int) pressures.stream().filter(p -> p.compareTo(BigDecimal.valueOf(60)) > 0).count());
        }

        // Velocity statistics
        List<BigDecimal> velocities = result.getLinkResults().values().stream()
                .map(r -> r.getVelocity())
                .filter(Objects::nonNull)
                .toList();

        if (!velocities.isEmpty()) {
            BigDecimal avgVel = velocities.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(velocities.size()), 4, RoundingMode.HALF_UP);
            BigDecimal maxVel = Collections.max(velocities);

            indicators.setAvgVelocity(avgVel);
            indicators.setMaxVelocity(maxVel);
            indicators.setHighVelocityCount(
                    (int) velocities.stream().filter(v -> v.compareTo(BigDecimal.valueOf(2.0)) > 0).count());
        }

        // Headloss
        List<BigDecimal> headlosses = result.getLinkResults().values().stream()
                .map(r -> r.getHeadloss())
                .filter(Objects::nonNull)
                .toList();

        if (!headlosses.isEmpty()) {
            BigDecimal total = headlosses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            indicators.setTotalHeadloss(total);
        }

        return indicators;
    }

    // DTOs
    public record SimulationRequest(
            SimulationOptions baseOptions,
            SimulationOptions scenarioOptions,
            boolean storeResults
    ) {}

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