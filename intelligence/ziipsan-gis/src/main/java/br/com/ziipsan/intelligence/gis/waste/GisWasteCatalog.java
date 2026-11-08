package br.com.ziipsan.intelligence.gis.waste;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo de resíduos sólidos da GIS-14 (GIS-CADASTRO-TECNICO §4.5),
 * espelho Java das sementes do SQL pendente da ED-65 — o banco é a
 * autoridade (classes/tipos em {@code gis_feature_class}/
 * {@code gis_feature_type} com {@code factory_code gis14:<code>} e
 * {@code attributes_schema} por tipo); esta classe existe para a api
 * falhar cedo e para o catálogo ser testável sem banco.
 *
 * <p>Desenho do §4.5: resíduos NÃO é rede de fluxo — pontos são POI
 * ({@code gis_poi}), setores são ZONE ({@code gis_zone} com
 * {@code zone_type = COLLECTION_SECTOR}, coluna que já existe na
 * V13_2), unidades são STRUCTURE ({@code gis_structure}) e as rotas
 * são objeto operacional próprio ({@code gis_waste_route}, §3.4),
 * nunca feição de cadastro.
 *
 * <p>Decisões registradas (não inventadas em silêncio):
 * <ul>
 *   <li>ECOPOINT: o §4.5 admite "STRUCTURE ou POI". Aqui é STRUCTURE
 *       sempre: {@code gis_structure} aceita {@code geom_point} quando
 *       só o ponto é conhecido (V13_2), e os atributos do ecoponto
 *       (materiais, limite por entrega, horário, capacidade) são de
 *       unidade, não de ponto solto. Um só caminho de gravação.</li>
 *   <li>Volumes de contêiner, capacidades de veículo e o limite de
 *       RCC por entrega são "valor de referência a validar" no
 *       próprio §4.5: entram como listas de referência
 *       ({@link #REFERENCE_TO_VALIDATE}), nunca como norma.</li>
 *   <li>Classe do resíduo: NBR 10004-1/-2:2024 — classe 1 (perigoso)
 *       e classe 2 (não perigoso), como o §4.5 registra (a divisão
 *       I/II A/II B da edição de 2004 foi abandonada na série 2024;
 *       conferir no texto da ABNT segue pendência da P1.28).</li>
 * </ul>
 */
public final class GisWasteCatalog {

    /** Domínio da vertical (§3.4/V13_1: gis_domain WASTE já existe). */
    public static final String DOMAIN = "WASTE";

    /** Classes novas da GIS-14 (as demais papéis já têm classe). */
    public static final String CLASS_WASTE_POINT = "WASTE_POINT";
    public static final String CLASS_WASTE_FACILITY = "WASTE_FACILITY";
    public static final String CLASS_COLLECTION_SECTOR = "COLLECTION_SECTOR";

    /** Frações de resíduo (§4.5, lista waste_fraction do §4.6). */
    public static final Set<String> WASTE_FRACTIONS = Set.of(
            "REJECT", "ORGANIC", "DRY_RECYCLABLE", "GLASS", "MIXED",
            "CONSTRUCTION", "BULKY", "PRUNING", "HEALTHCARE", "HAZARDOUS");

    /** Tipos de coleta do setor (§4.5). */
    public static final Set<String> COLLECTION_KINDS = Set.of(
            "CONVENTIONAL", "SELECTIVE", "HEALTHCARE", "SWEEPING");

    /** Turnos (ponto de coleta e setor, §4.5). */
    public static final Set<String> SHIFTS = Set.of("MORNING", "AFTERNOON", "NIGHT");

    /** Dias da semana do setor (ISO: MON..SUN). */
    public static final Set<String> WEEK_DAYS = Set.of(
            "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    /** Situação do ponto de descarte irregular (§4.5). */
    public static final Set<String> DUMP_STATUSES = Set.of(
            "ACTIVE", "CLEANING", "ELIMINATED", "REINCIDENT");

    /** Tipos de descarte irregular (§4.5). */
    public static final Set<String> DUMP_KINDS = Set.of(
            "CONSTRUCTION", "BULKY", "DOMESTIC", "MIXED");

    /** Classe do resíduo quando classificado (NBR 10004:2024, §4.5). */
    public static final Set<String> WASTE_CLASSES = Set.of("CLASS_1", "CLASS_2");

    /**
     * Volumes de referência do contêiner (§4.5, valor de referência a
     * validar): PE com rodas em litros; caçamba estacionária e
     * semienterrado/subterrâneo em m³. Chave = modelo declarado.
     */
    public static final Map<String, List<Double>> CONTAINER_VOLUMES_BY_MODEL = Map.of(
            "WHEELED_PE", List.of(120.0, 240.0, 360.0, 660.0, 1000.0),
            "STATIONARY_SKIP", List.of(3.0, 4.0, 5.0),
            "SEMI_BURIED", List.of(3.0, 4.0, 5.0),
            "UNDERGROUND", List.of(3.0, 4.0, 5.0));

    /** Unidade do volume por modelo: L nos de rodas, m³ nos demais. */
    public static final Map<String, String> CONTAINER_VOLUME_UNIT = Map.of(
            "WHEELED_PE", "L",
            "STATIONARY_SKIP", "M3",
            "SEMI_BURIED", "M3",
            "UNDERGROUND", "M3");

    /** Veículos da rota (§4.5, valor de referência a validar). */
    public static final Set<String> VEHICLE_KINDS = Set.of(
            "COMPACTOR_15M3", "COMPACTOR_19M3", "BOX_TRUCK", "SKIP_LOADER");

    /** Limite de RCC por entrega no ecoponto: 1 m³ (§4.5, referência). */
    public static final double ECOPOINT_RCC_LIMIT_M3_REFERENCE = 1.0;

    /** Papel físico do tipo: tabela de feição ou objeto operacional. */
    public enum Role { POI, STRUCTURE, ZONE, OPERATIONAL }

    /**
     * Um tipo do §4.5.
     *
     * @param code         código estável do tipo (= sufixo do factory_code)
     * @param label        rótulo do §4.5
     * @param role         papel (tabela de destino)
     * @param classCode    classe em gis_feature_class
     * @param symbolKey    chave do símbolo na biblioteca (ADR-0036):
     *                     a ARTE é da P1.30/§4.3 de ICONES-E-SIMBOLOGIA —
     *                     aqui só a chave (ver GisWasteSymbols)
     * @param routeStop    pode ser parada de rota de coleta
     * @param requiredAttributes atributos obrigatórios do tipo (espelho
     *                     do attributes_schema do SQL pendente)
     */
    public record WasteType(String code, String label, Role role, String classCode,
                            String symbolKey, boolean routeStop,
                            List<String> requiredAttributes) {
        public String factoryCode() {
            return "gis14:" + code;
        }

        /** Tabela física da feição (null no objeto operacional). */
        public String featureTable() {
            return switch (role) {
                case POI -> "gis_poi";
                case STRUCTURE -> "gis_structure";
                case ZONE -> "gis_zone";
                case OPERATIONAL -> null;
            };
        }
    }

    public static final List<WasteType> TYPES = List.of(
            new WasteType("COLLECTION_POINT", "Ponto de coleta", Role.POI,
                    CLASS_WASTE_POINT, "waste-collection-point", true,
                    List.of("waste_fraction", "collection_frequency", "shift")),
            new WasteType("CONTAINER", "Contêiner", Role.POI,
                    CLASS_WASTE_POINT, "waste-container", true,
                    List.of("waste_fraction", "container_model", "volume")),
            new WasteType("DROP_OFF_POINT", "PEV — ponto de entrega voluntária", Role.POI,
                    CLASS_WASTE_POINT, "waste-drop-off-point", true,
                    List.of("accepted_fractions", "responsible_party_kind")),
            new WasteType("ECOPOINT", "Ecoponto", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-ecopoint", true,
                    List.of("accepted_materials")),
            new WasteType("LITTER_BIN", "Lixeira pública", Role.POI,
                    CLASS_WASTE_POINT, "waste-litter-bin", true,
                    List.of("volume_l")),
            new WasteType("ILLEGAL_DUMP", "Ponto de descarte irregular", Role.POI,
                    CLASS_WASTE_POINT, "waste-illegal-dump", false,
                    List.of("identified_on", "dump_kind", "estimated_volume_m3", "dump_status")),
            new WasteType("COLLECTION_SECTOR", "Setor de coleta", Role.ZONE,
                    CLASS_COLLECTION_SECTOR, "waste-collection-sector", false,
                    List.of("collection_kind", "collection_days", "shift", "executor")),
            new WasteType("TRANSFER_STATION", "Área de transbordo", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-transfer-station", false,
                    List.of("capacity_t_per_day")),
            new WasteType("SORTING_FACILITY", "Unidade de triagem", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-sorting-facility", false,
                    List.of("operator_kind")),
            new WasteType("COMPOSTING_SITE", "Compostagem", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-composting-site", false,
                    List.of()),
            new WasteType("SANITARY_LANDFILL", "Aterro sanitário", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-sanitary-landfill", false,
                    List.of()),
            new WasteType("INERT_LANDFILL", "Aterro de inertes", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-inert-landfill", false,
                    List.of()),
            new WasteType("CONSTRUCTION_WASTE_FACILITY",
                    "Área de transbordo e triagem de RCC", Role.STRUCTURE,
                    CLASS_WASTE_FACILITY, "waste-construction-facility", false,
                    List.of()));

    /** A rota em si: objeto operacional (§3.4), não é tipo de feição. */
    public static final String ROUTE_OBJECT = "gis_waste_route";

    private GisWasteCatalog() {
    }

    public static Optional<WasteType> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return TYPES.stream().filter(t -> t.code().equals(code)).findFirst();
    }

    public static WasteType requireType(String code) {
        return byCode(code).orElseThrow(() -> new IllegalArgumentException(
                "tipo de residuo fora do catalogo da GIS-14 (§4.5): " + code));
    }

    /** Tipos que podem ser parada de rota (coleta), na ordem do §4.5. */
    public static List<WasteType> routeStopTypes() {
        return TYPES.stream().filter(WasteType::routeStop).toList();
    }

    /** Volume declarado é um dos volumes de referência do modelo? */
    public static boolean isReferenceVolume(String containerModel, double volume) {
        List<Double> known = CONTAINER_VOLUMES_BY_MODEL.get(containerModel);
        return known != null && known.stream().anyMatch(v -> v == volume);
    }
}
