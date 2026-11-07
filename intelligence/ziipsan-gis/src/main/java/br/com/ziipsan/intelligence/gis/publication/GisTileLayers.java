package br.com.ziipsan.intelligence.gis.publication;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de camadas MVT da GIS-06 (§10.1: uma camada por domínio e
 * papel), espelho Java das linhas de fábrica de {@code gis_layer}
 * (V13_12). A tabela é a fonte que a api lê para versão/ativo; este
 * enum é a parte que NÃO é dado de tenant: de qual tabela física sai
 * o tile, o filtro de domínio e a regra de permissão — código, não
 * configuração, porque montar SQL a partir de nome de tabela vindo do
 * banco seria injeção por desenho.
 *
 * <p>Camadas pessoais ({@code personal = true}) são as de imóveis e
 * ligações: só são listadas, estilizadas e servidas para quem tem
 * {@code gis.view-personal} (§6.6/§7.2). A guarda mora no serviço de
 * tiles e no gerador de estilo, e é testada nos dois.
 */
public enum GisTileLayers {

    WATER_ARCS("water-arcs", SourceKind.FEATURE, "gis_arc", "WATER", false, 0),
    WATER_NODES("water-nodes", SourceKind.FEATURE, "gis_node", "WATER", false, 0),
    SEWER_ARCS("sewer-arcs", SourceKind.FEATURE, "gis_arc", "SEWER", false, 0),
    SEWER_NODES("sewer-nodes", SourceKind.FEATURE, "gis_node", "SEWER", false, 0),
    STORM_ARCS("storm-arcs", SourceKind.FEATURE, "gis_arc", "STORM", false, 0),
    STORM_NODES("storm-nodes", SourceKind.FEATURE, "gis_node", "STORM", false, 0),
    STRUCTURES("structures", SourceKind.FEATURE, "gis_structure", null, false, 0),
    ZONES("zones", SourceKind.FEATURE, "gis_zone", null, false, 0),
    POIS("pois", SourceKind.FEATURE, "gis_poi", null, false, 0),
    CONTEXT("context", SourceKind.CONTEXT, "gis_context_feature", null, false, 0),
    /** Ligações/ramais: a partir de z17 (§10.1) e só com gis.view-personal. */
    SERVICE_LINES("service-lines", SourceKind.FEATURE, "gis_connection", null, true, 17),
    /** Imóveis: ponto = consumer_units.coordinates (GIS-05/V13_10); pessoal. */
    PROPERTIES("properties", SourceKind.PROPERTIES, null, null, true, 0);

    public enum SourceKind { FEATURE, CONTEXT, PROPERTIES }

    private final String key;
    private final SourceKind sourceKind;
    private final String featureTable;
    private final String domainKey;
    private final boolean personal;
    private final int tileMinZoom;

    GisTileLayers(String key, SourceKind sourceKind, String featureTable,
                  String domainKey, boolean personal, int tileMinZoom) {
        this.key = key;
        this.sourceKind = sourceKind;
        this.featureTable = featureTable;
        this.domainKey = domainKey;
        this.personal = personal;
        this.tileMinZoom = tileMinZoom;
    }

    public String key() {
        return key;
    }

    public SourceKind sourceKind() {
        return sourceKind;
    }

    public String featureTable() {
        return featureTable;
    }

    public String domainKey() {
        return domainKey;
    }

    public boolean personal() {
        return personal;
    }

    public int tileMinZoom() {
        return tileMinZoom;
    }

    /** Abaixo deste zoom a camada devolve tile vazio (§10.1). */
    public boolean servesAt(int z) {
        return z >= tileMinZoom;
    }

    public static Optional<GisTileLayers> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (GisTileLayers layer : values()) {
            if (layer.key.equals(key)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    /** Camadas visíveis para o chamador (pessoais só com a permissão). */
    public static List<GisTileLayers> visibleFor(boolean canViewPersonal) {
        return List.of(values()).stream()
                .filter(l -> canViewPersonal || !l.personal)
                .toList();
    }
}
