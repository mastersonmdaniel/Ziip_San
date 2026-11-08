package br.com.ziipsan.intelligence.gis.drain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catálogo de drenagem da GIS-13 (GIS-CADASTRO-TECNICO §4.4), espelho
 * Java das sementes do SQL pendente da ED-64 — o banco é a autoridade
 * (classes/tipos em {@code gis_feature_class}/{@code gis_feature_type}
 * com {@code factory_code gis13:<code>} e {@code attributes_schema}
 * por tipo); esta classe existe para a api falhar cedo e para o
 * catálogo ser testável sem banco.
 *
 * <p>Desenho do §4.4: drenagem É rede de fluxo por gravidade sobre o
 * modelo nó-arco da GIS-01 — arcos em {@code gis_arc}, nós em
 * {@code gis_node}, estruturas em {@code gis_structure} e sub-bacias
 * em {@code gis_zone} ({@code zone_type = STORM_SUBCATCHMENT} /
 * {@code STORM_BASIN}, colunas que já existem na V13_2). O domínio é
 * {@code STORM} (V13_1). "Não há NBR de projeto de microdrenagem
 * pública" (§4.4): o catálogo é configurável por tenant e TODOS os
 * domínios numéricos do §4.4 (DN, seções, classes de carga) são
 * "valor de referência a validar" — entram como referência, nunca
 * como norma conferida (a compra das normas da P1.28 segue pendente,
 * como registrou a P1.27).
 *
 * <p>Decisões registradas (não inventadas em silêncio):
 * <ul>
 *   <li>STORM_BASIN (correção D2 da revisão): a tabela do §4.4
 *       enumera 19 códigos; o 20.º tipo, STORM_BASIN ("Bacia de
 *       drenagem", ZONE), NÃO é linha do §4.4 — sua fonte real é a
 *       lista de {@code zone_type} da V13_2 (§3.6.5 do
 *       GIS-CADASTRO-TECNICO), que já traz STORM_BASIN ao lado de
 *       STORM_SUBCATCHMENT. O catálogo é, portanto, 19 tipos do
 *       §4.4 + STORM_BASIN da V13_2/§3.6.5, decisão registrada
 *       aqui, no SQL pendente e no relatório — nunca "20 tipos do
 *       §4.4".</li>
 *   <li>ENERGY_DISSIPATOR: o §4.4 admite "NODE ou STRUCTURE". Aqui é
 *       NODE sempre: o dissipador é singularidade pontual da rede
 *       (degraus/bacia localizados na descarga); um só caminho de
 *       gravação, como o ECOPOINT da ED-65.</li>
 *   <li>OUTFALL de drenagem usa a classe nova STORM_NODE com code
 *       {@code OUTFALL} (o code da tabela do §4.4): a classe OUTFALL
 *       da V13_2 é de domínio SEWER e a unicidade do tipo é por
 *       (tenant, classe, code) — sem colisão e sem misturar
 *       domínios (regra de domínio do §5.3).</li>
 *   <li>HEADWALL (ala e testa) é STRUCTURE e se liga ao arco por
 *       {@code gis_containment} com relação ATTACHES (§4.4/§5.3).</li>
 *   <li>Os atributos comuns de drenagem da NR 12/2025 (§4.4) —
 *       {@code condition_rating} (coluna da feição, V13_2), obstrução
 *       (%), foto (gis_attachment) e data da última inspeção — NÃO
 *       são atributos de tipo: vivem nas colunas/tabelas próprias
 *       (ver GisDrainRules e gis_drain_inspection do SQL pendente).
 *       O n de Manning é do MATERIAL (gis_material.manning_n):
 *       concreto 0,013 e PE 0,010 já vêm da P1.27; canal em terra
 *       0,025 é revestimento do canal (atributo
 *       {@code lining_manning_n} de referência), não material.</li>
 * </ul>
 */
public final class GisDrainCatalog {

    /** Domínio da vertical (V13_1: gis_domain STORM já existe). */
    public static final String DOMAIN = "STORM";

    /** Classes novas da GIS-13 (o domínio STORM não tinha classe na V13_2). */
    public static final String CLASS_STORM_PIPE = "STORM_PIPE";
    public static final String CLASS_STORM_NODE = "STORM_NODE";
    public static final String CLASS_STORM_STRUCTURE = "STORM_STRUCTURE";
    public static final String CLASS_STORM_ZONE = "STORM_ZONE";

    /** Estado de conservação (§3.5/§4.6; coluna condition_rating da V13_2). */
    public static final Set<String> CONDITION_RATINGS = Set.of(
            "GOOD", "FAIR", "POOR", "CRITICAL", "UNKNOWN");

    /**
     * Tipos de boca de lobo (§4.4). Correção D6 da revisão —
     * ACHATAMENTO DECLARADO: o §4.4 lista as características da
     * boca de lobo em dimensões distintas (quantidade: simples/
     * dupla/tripla; grelha: com/sem; abertura: guia chapéu/lateral)
     * e esta entrega as achata num enum único de {@code inlet_kind},
     * espelho do attributes_schema e da lista de valores do SQL
     * pendente — simplificação registrada, não equivalência com a
     * fonte. Modelar as facetas como atributos separados fica para
     * a revisão do catálogo com a §4.3/fonte completa.
     */
    public static final Set<String> INLET_KINDS = Set.of(
            "SIMPLE", "DOUBLE", "TRIPLE",
            "WITH_GRATE", "WITHOUT_GRATE", "CURB_OPENING", "LATERAL");

    /** Classes de carga da grelha (§4.4: A15 a F900, NBR 10160:2026 citada). */
    public static final Set<String> LOAD_CLASSES = Set.of(
            "A15", "B125", "C250", "D400", "E600", "F900");

    /** Seções do canal aberto (§4.4). */
    public static final Set<String> CHANNEL_SECTIONS = Set.of(
            "TRAPEZOIDAL", "RECTANGULAR", "NATURAL");

    /** Revestimentos do canal aberto (§4.4). */
    public static final Set<String> CHANNEL_LININGS = Set.of(
            "CONCRETE", "GABION", "RIPRAP", "GRASS", "EARTH");

    /**
     * n de Manning de referência por revestimento (§4.4: concreto
     * 0,013; canal em terra 0,025 — valores de referência a validar).
     * Gabião/enrocamento/grama NÃO têm valor no §4.4: ausentes do mapa
     * de propósito (sem número inventado).
     */
    public static final Map<String, Double> LINING_MANNING_REFERENCE = Map.of(
            "CONCRETE", 0.013,
            "EARTH", 0.025);

    /**
     * n de Manning de referência do material do conduto, com a
     * fonte de cada valor (correção D6 da revisão): CONCRETE 0,013
     * vem do §4.4; HDPE_STRUCTURED e PVC_STRUCTURED 0,010 NÃO estão
     * no §4.4 — vêm da tabela de materiais do §4.3.1 e do catálogo
     * de fábrica da P1.27 ({@code R__gis_factory_catalog}, manning
     * 0,010). Valores de referência a validar, como todo o §4.4.
     */
    public static final Map<String, Double> MATERIAL_MANNING_REFERENCE = Map.of(
            "CONCRETE", 0.013,
            "HDPE_STRUCTURED", 0.010,
            "PVC_STRUCTURED", 0.010);

    /** Tipos de dissipador de energia (§4.4). */
    public static final Set<String> DISSIPATOR_KINDS = Set.of(
            "STEPS", "STILLING_BASIN", "RIPRAP");

    /** Forma do bueiro (§4.4: tubular ou celular). */
    public static final Set<String> CULVERT_SHAPES = Set.of("PIPE", "BOX");

    /** Modelos de infiltração do SWMM para a sub-bacia (§4.4/SWMM 5.2). */
    public static final Set<String> INFILTRATION_MODELS = Set.of(
            "HORTON", "GREEN_AMPT", "CURVE_NUMBER");

    /** Fontes do monitoramento pluviométrico (parâmetro drain.rain_source). */
    public static final Set<String> RAIN_SOURCES = Set.of(
            "CEMADEN_IMPORT", "OWN_GAUGE");

    /** Métodos de inspeção desta entrega (vídeo é da ED-56, reaproveitável). */
    public static final Set<String> INSPECTION_METHODS = Set.of(
            "VISUAL", "PHOTO_SURVEY", "CCTV_EXTERNAL_REF");

    /**
     * Classes de elemento do plano de limpeza (chaves do parâmetro
     * {@code drain.cleaning_interval_by_class}): só os elementos que
     * o aceite nomeia para OS recorrente de limpeza — bocas de lobo e
     * galerias — mais os demais elementos inspecionáveis do §4.4 que
     * um plano por classe precisa cobrir para ser configurável.
     * NENHUM intervalo é semeado: a periodicidade é do tenant,
     * conforme a NR 12/ANA (ver GisDrainMaintenance).
     */
    public static final Set<String> CLEANING_ELEMENT_CLASSES = Set.of(
            "INLET", "STORM_PIPE", "BOX_CULVERT", "OPEN_CHANNEL",
            "STORM_MANHOLE", "DETENTION_BASIN");

    /** DN de referência da galeria tubular (§4.4: 300 a 1500, a validar). */
    public static final List<Integer> STORM_PIPE_DN_REFERENCE = List.of(
            300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1500);

    /** DN de referência da ligação boca de lobo–PV (§4.4: 300 a 400). */
    public static final List<Integer> INLET_LEAD_DN_REFERENCE = List.of(300, 400);

    /** Papel físico do tipo: tabela de feição da GIS-01. */
    public enum Role { ARC, NODE, STRUCTURE, ZONE }

    /**
     * Um tipo do catálogo da GIS-13 (19 do §4.4 + STORM_BASIN da
     * V13_2/§3.6.5 — decisão D2 no javadoc da classe).
     *
     * @param code         código estável do tipo (= sufixo do factory_code)
     * @param label        rótulo do §4.4
     * @param role         papel (tabela de destino)
     * @param classCode    classe em gis_feature_class
     * @param symbolKey    chave do símbolo na biblioteca (ADR-0036):
     *                     a ARTE é da P1.30/§4.3 de ICONES-E-SIMBOLOGIA —
     *                     aqui só a chave (ver GisDrainSymbols)
     * @param requiredAttributes atributos obrigatórios do tipo (espelho
     *                     do attributes_schema do SQL pendente)
     */
    public record DrainType(String code, String label, Role role, String classCode,
                            String symbolKey, List<String> requiredAttributes) {
        public String factoryCode() {
            return "gis13:" + code;
        }

        /** Tabela física da feição. */
        public String featureTable() {
            return switch (role) {
                case ARC -> "gis_arc";
                case NODE -> "gis_node";
                case STRUCTURE -> "gis_structure";
                case ZONE -> "gis_zone";
            };
        }

        /** zone_type da V13_2 quando o papel é ZONE; null nos demais. */
        public String zoneType() {
            return switch (code) {
                case "STORM_BASIN" -> "STORM_BASIN";
                case "STORM_SUBCATCHMENT" -> "STORM_SUBCATCHMENT";
                default -> null;
            };
        }
    }

    public static final List<DrainType> TYPES = List.of(
            // ---- Arcos (condutos e canais) ----
            new DrainType("STORM_PIPE", "Galeria tubular", Role.ARC,
                    CLASS_STORM_PIPE, "storm-pipe", List.of()),
            new DrainType("BOX_CULVERT", "Galeria celular", Role.ARC,
                    CLASS_STORM_PIPE, "storm-box-culvert",
                    List.of("cell_count")),
            new DrainType("OPEN_CHANNEL", "Canal", Role.ARC,
                    CLASS_STORM_PIPE, "storm-open-channel",
                    List.of("channel_section", "channel_lining")),
            new DrainType("CULVERT", "Bueiro", Role.ARC,
                    CLASS_STORM_PIPE, "storm-culvert",
                    List.of("culvert_shape")),
            new DrainType("INLET_LEAD", "Ligação boca de lobo–PV", Role.ARC,
                    CLASS_STORM_PIPE, "storm-inlet-lead", List.of()),
            new DrainType("GUTTER", "Sarjeta", Role.ARC,
                    CLASS_STORM_PIPE, "storm-gutter", List.of()),
            new DrainType("VALLEY_GUTTER", "Sarjetão", Role.ARC,
                    CLASS_STORM_PIPE, "storm-valley-gutter", List.of()),
            new DrainType("DITCH", "Valeta", Role.ARC,
                    CLASS_STORM_PIPE, "storm-ditch", List.of()),
            // ---- Nós (singularidades) ----
            new DrainType("INLET", "Boca de lobo", Role.NODE,
                    CLASS_STORM_NODE, "storm-inlet",
                    List.of("inlet_kind")),
            new DrainType("GRATE_INLET", "Grelha", Role.NODE,
                    CLASS_STORM_NODE, "storm-grate-inlet",
                    List.of("load_class")),
            new DrainType("STORM_MANHOLE", "PV pluvial", Role.NODE,
                    CLASS_STORM_NODE, "storm-manhole", List.of()),
            new DrainType("STORM_JUNCTION_BOX", "Caixa de ligação ou de passagem",
                    Role.NODE, CLASS_STORM_NODE, "storm-junction-box", List.of()),
            new DrainType("DROP_SHAFT", "Poço de queda", Role.NODE,
                    CLASS_STORM_NODE, "storm-drop-shaft",
                    List.of("drop_height_m")),
            new DrainType("ENERGY_DISSIPATOR", "Dissipador de energia", Role.NODE,
                    CLASS_STORM_NODE, "storm-energy-dissipator",
                    List.of("dissipator_kind")),
            new DrainType("OUTFALL", "Lançamento ou exutório", Role.NODE,
                    CLASS_STORM_NODE, "storm-outfall",
                    List.of("receiving_body")),
            // ---- Estruturas ----
            new DrainType("HEADWALL", "Ala e testa", Role.STRUCTURE,
                    CLASS_STORM_STRUCTURE, "storm-headwall", List.of()),
            new DrainType("DETENTION_BASIN", "Bacia de detenção", Role.STRUCTURE,
                    CLASS_STORM_STRUCTURE, "storm-detention-basin",
                    List.of("volume_m3")),
            new DrainType("RETENTION_BASIN", "Bacia de retenção", Role.STRUCTURE,
                    CLASS_STORM_STRUCTURE, "storm-retention-basin",
                    List.of("volume_m3")),
            // ---- Zonas ----
            // STORM_BASIN: fonte V13_2/§3.6.5 (zone_type), nao §4.4 — decisao D2.
            new DrainType("STORM_BASIN", "Bacia de drenagem", Role.ZONE,
                    CLASS_STORM_ZONE, "storm-basin", List.of()),
            new DrainType("STORM_SUBCATCHMENT", "Sub-bacia", Role.ZONE,
                    CLASS_STORM_ZONE, "storm-subcatchment",
                    List.of("area_ha", "impervious_pct", "slope_pct", "width_m",
                            "infiltration_model", "outlet_ref")));

    private GisDrainCatalog() {
    }

    public static Optional<DrainType> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return TYPES.stream().filter(t -> t.code().equals(code)).findFirst();
    }

    public static DrainType requireType(String code) {
        return byCode(code).orElseThrow(() -> new IllegalArgumentException(
                "tipo de drenagem fora do catalogo da GIS-13 (§4.4): " + code));
    }

    /** Tipos de arco (condutos/canais), na ordem do §4.4. */
    public static List<DrainType> arcTypes() {
        return TYPES.stream().filter(t -> t.role() == Role.ARC).toList();
    }

    /** DN declarado está na série de referência do tipo? (referência, não norma) */
    public static boolean isReferenceDn(String typeCode, int dnMm) {
        return switch (typeCode) {
            case "STORM_PIPE" -> STORM_PIPE_DN_REFERENCE.contains(dnMm);
            case "INLET_LEAD" -> INLET_LEAD_DN_REFERENCE.contains(dnMm);
            default -> true;
        };
    }
}
