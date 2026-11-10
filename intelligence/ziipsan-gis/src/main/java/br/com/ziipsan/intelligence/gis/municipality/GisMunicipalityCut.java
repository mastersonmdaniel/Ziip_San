package br.com.ziipsan.intelligence.gis.municipality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * MM-07 — GIS por município: filtro temático de elementos de rede por
 * município (decisão do dono n.º 45; MAPA-DE-PACOTES, MM-07; D-MM-01/02).
 *
 * <p>Classe NOVA, ao lado das classes vigentes do módulo GIS
 * ({@code GisNetworkElement}, serviços EPANET/validação — NENHUM ficheiro
 * vigente é editado por este pacote). É o espelho em memória da view
 * {@code gis_element_municipality} (MM-07__gis_municipality_cut.sql):
 * as duas formas aplicam EXACTAMENTE as mesmas vias e os mesmos estados,
 * e a prova PG do pacote confronta uma com a outra.
 *
 * <p><b>As vias provadas no DDL vigente</b> (ver o cabeçalho do SQL para
 * a transcrição completa):
 * <ul>
 *   <li><b>Via 1 — ramal:</b> {@code gis_connection.service_connection_id}
 *       (FK {@code fk_gis_connection_service_connection}, V13_10/GIS-05)
 *       → {@code service_connections.property_unit_id} (FK
 *       {@code fk_service_connection_property}, V3_14/GIS-05) → UC âncora
 *       → município pela derivação canónica do MM-01
 *       ({@code consumer_unit_municipality} / {@code MunicipalityDerivation}),
 *       consumida aqui como o mapa {@link UnitMunicipality} que o chamador
 *       obtém dessa derivação — esta classe NÃO reimplementa a cadeia
 *       blocks/sectors/localities da UC.</li>
 *   <li><b>Via 2 — zona de setor comercial:</b> só
 *       {@code gis_zone.zone_type = 'COMMERCIAL_SECTOR'}:
 *       {@code commercial_sector_id} → setor → localidade → IBGE (troço
 *       final da cadeia D-MM-02). A coluna NÃO tem FK (V13_2; a FK
 *       prometida para a GIS-05 não veio na V13_10 — pendência
 *       P-MM07-02): valor que não resolve = {@code BROKEN_CHAIN}.</li>
 * </ul>
 *
 * <p><b>O que NÃO é via</b> (declarado, nunca usado em silêncio):
 * <ul>
 *   <li><b>DMA:</b> todas as feições têm {@code dma_id} → {@code dma_district},
 *       mas distrito não determina município (o DDL do distrito, V2_2,
 *       não está nos espelhos locais e {@code blocks.dma_id} é N:1 sem
 *       confinamento a uma localidade). Elemento cuja única ligação é o
 *       DMA = excepção {@code DMA_ONLY_UNRESOLVED} (pendência P-MM07-01).</li>
 *   <li><b>Espacial:</b> {@code ibge_municipalities} (V3_12) não tem
 *       geometria — não há limites municipais em fonte para junção
 *       espacial (pendência P-MM07-03). Nenhuma geometria é lida aqui.</li>
 * </ul>
 *
 * <p><b>Invariante do recorte:</b> cada feição de entrada produz EXACTAMENTE
 * uma {@link CutRow} — derivada ({@code DERIVED}, com IBGE) ou excepção
 * (IBGE nulo, com o motivo em {@link DerivationStatus}). Logo, por tenant:
 * {@code soma(countByMunicipality) + exceptions.size() == total de feições}.
 * Nenhum elemento desaparece no recorte.
 *
 * <p>Classe pura, sem JPA/Spring: opera sobre os identificadores e as
 * ligações que as tabelas de feição da V13_2 já expõem, como a derivação
 * do MM-01. Todas as resoluções são por (tenant_id, id) — uma ligação
 * para outro tenant nunca resolve (a FK composta é parte das vias).
 */
public final class GisMunicipalityCut {

    /** Tabela de feição de origem (V13_2); {@code dbName} é o nome SQL. */
    public enum SourceTable {
        GIS_NODE("gis_node"),
        GIS_ARC("gis_arc"),
        GIS_CONNECTION("gis_connection"),
        GIS_STRUCTURE("gis_structure"),
        GIS_ZONE("gis_zone"),
        GIS_POI("gis_poi");

        private final String dbName;

        SourceTable(String dbName) {
            this.dbName = dbName;
        }

        public String dbName() {
            return dbName;
        }
    }

    /** Via de derivação tentada (a da classe, quando há id de ligação). */
    public enum DerivationVia {
        SERVICE_CONNECTION,
        COMMERCIAL_SECTOR
    }

    /**
     * Estado da derivação de uma feição — lista fechada, espelhada na
     * view SQL. {@code DERIVED} se e só se há IBGE.
     */
    public enum DerivationStatus {
        /** Via resolveu e o município está presente. */
        DERIVED,
        /** Ramal com service_connection_id NULL (coluna anulável, V13_2). */
        NO_SERVICE_CONNECTION,
        /** Id de ligação presente mas sem alvo no tenant. */
        BROKEN_CHAIN,
        /** UC âncora sem linha na derivação do MM-01 (UC sem quadra etc.). */
        NO_UNIT_DERIVATION,
        /** Cadeia íntegra, mas localities.municipality_ibge IS NULL. */
        NULL_MUNICIPALITY,
        /** Zona COMMERCIAL_SECTOR com commercial_sector_id NULL. */
        NO_SECTOR_LINK,
        /** A única ligação territorial é o DMA — não deriva (P-MM07-01). */
        DMA_ONLY_UNRESOLVED,
        /** Nenhuma ligação territorial. */
        NO_TERRITORIAL_LINK
    }

    /** Chave de resolução (tenant, id) — o par das FK compostas das vias. */
    public record ResolutionKey(UUID tenantId, UUID id) {
        public ResolutionKey {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * Feição de entrada: os identificadores e ligações territoriais que a
     * linha da V13_2 expõe. {@code serviceConnectionId} só é lido em
     * GIS_CONNECTION; {@code zoneType}/{@code commercialSectorId} só em
     * GIS_ZONE; em GIS_ZONE, {@code dmaId} traz o id de distrito efectivo
     * (dma_id, ou dma_district_id quando só este está preenchido — para a
     * classificação da excepção basta "tem alguma ligação DMA");
     * {@code dmaId} nunca deriva (só classifica a excepção).
     */
    public record ElementRef(UUID tenantId, SourceTable sourceTable, UUID elementId,
                             String elementCode, UUID dmaId, UUID serviceConnectionId,
                             String zoneType, UUID commercialSectorId) {
        public ElementRef {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sourceTable, "sourceTable");
            Objects.requireNonNull(elementId, "elementId");
        }

        /** Feição sem nenhuma via (node/arc/structure/poi). */
        public static ElementRef simple(UUID tenantId, SourceTable table, UUID elementId,
                                        String elementCode, UUID dmaId) {
            return new ElementRef(tenantId, table, elementId, elementCode, dmaId,
                    null, null, null);
        }

        /** Ramal (gis_connection). */
        public static ElementRef connection(UUID tenantId, UUID elementId, String elementCode,
                                            UUID dmaId, UUID serviceConnectionId) {
            return new ElementRef(tenantId, SourceTable.GIS_CONNECTION, elementId,
                    elementCode, dmaId, serviceConnectionId, null, null);
        }

        /** Zona (gis_zone): dmaDistrictId conta como ligação DMA. */
        public static ElementRef zone(UUID tenantId, UUID elementId, String elementCode,
                                      UUID dmaId, UUID dmaDistrictId, String zoneType,
                                      UUID commercialSectorId) {
            UUID effectiveDma = dmaId != null ? dmaId : dmaDistrictId;
            return new ElementRef(tenantId, SourceTable.GIS_ZONE, elementId, elementCode,
                    effectiveDma, null, zoneType, commercialSectorId);
        }
    }

    /**
     * Resultado da derivação da UC âncora, TAL COMO a view do MM-01 a
     * devolve: {@code inDerivation} = a UC tem linha em
     * {@code consumer_unit_municipality}; {@code municipalityIbge} pode
     * ser nulo com a linha presente (localidade com IBGE NULL).
     */
    public record UnitMunicipality(boolean inDerivation, String municipalityIbge) {
        public static UnitMunicipality derived(String ibge) {
            return new UnitMunicipality(true, Objects.requireNonNull(ibge, "ibge"));
        }

        public static UnitMunicipality nullMunicipality() {
            return new UnitMunicipality(true, null);
        }

        public static UnitMunicipality absent() {
            return new UnitMunicipality(false, null);
        }
    }

    /**
     * Resultado da resolução setor → município (troço final da cadeia):
     * {@code sectorExists} = o id resolve para um setor do tenant;
     * {@code municipalityIbge} pode ser nulo com o setor presente.
     */
    public record SectorMunicipality(boolean sectorExists, String municipalityIbge) {
        public static SectorMunicipality derived(String ibge) {
            return new SectorMunicipality(true, Objects.requireNonNull(ibge, "ibge"));
        }

        public static SectorMunicipality nullMunicipality() {
            return new SectorMunicipality(true, null);
        }

        public static SectorMunicipality absent() {
            return new SectorMunicipality(false, null);
        }
    }

    /**
     * Linha do recorte: a feição, o seu município derivado (nulo nas
     * excepções), o estado e a âncora usada (só na via 1).
     */
    public record CutRow(UUID tenantId, SourceTable sourceTable, UUID elementId,
                         String elementCode, String municipalityIbge,
                         DerivationStatus status, DerivationVia via,
                         UUID consumerUnitId) {
        public boolean isDerived() {
            return status == DerivationStatus.DERIVED;
        }
    }

    private final Map<ResolutionKey, UUID> serviceConnectionAnchor = new LinkedHashMap<>();
    private final Map<ResolutionKey, UnitMunicipality> unitMunicipality = new LinkedHashMap<>();
    private final Map<ResolutionKey, SectorMunicipality> sectorMunicipality = new LinkedHashMap<>();

    /**
     * @param serviceConnectionAnchors (tenant, ligação) → UC âncora
     *        ({@code service_connections.property_unit_id}, V3_14)
     * @param unitMunicipalities (tenant, UC) → resultado da derivação do
     *        MM-01 para essa UC (da view {@code consumer_unit_municipality})
     * @param sectorMunicipalities (tenant, setor) → resolução setor →
     *        município pelo troço sector → locality do registry (V3_1)
     */
    public GisMunicipalityCut(Map<ResolutionKey, UUID> serviceConnectionAnchors,
                              Map<ResolutionKey, UnitMunicipality> unitMunicipalities,
                              Map<ResolutionKey, SectorMunicipality> sectorMunicipalities) {
        this.serviceConnectionAnchor.putAll(serviceConnectionAnchors);
        this.unitMunicipality.putAll(unitMunicipalities);
        this.sectorMunicipality.putAll(sectorMunicipalities);
    }

    /** Recorta UMA feição — exactamente uma linha de saída por feição. */
    public CutRow cut(ElementRef e) {
        Objects.requireNonNull(e, "element");
        return switch (e.sourceTable()) {
            case GIS_CONNECTION -> cutConnection(e);
            case GIS_ZONE -> cutZone(e);
            case GIS_NODE, GIS_ARC, GIS_STRUCTURE, GIS_POI -> noRoute(e);
        };
    }

    /** Recorta todas as feições, preservando a ordem de entrada. */
    public List<CutRow> cutAll(Iterable<ElementRef> elements) {
        List<CutRow> out = new ArrayList<>();
        elements.forEach(e -> out.add(cut(e)));
        return out;
    }

    private CutRow cutConnection(ElementRef e) {
        if (e.serviceConnectionId() == null) {
            return row(e, null, DerivationStatus.NO_SERVICE_CONNECTION, null, null);
        }
        UUID anchor = serviceConnectionAnchor.get(
                new ResolutionKey(e.tenantId(), e.serviceConnectionId()));
        if (anchor == null) {
            return row(e, null, DerivationStatus.BROKEN_CHAIN,
                    DerivationVia.SERVICE_CONNECTION, null);
        }
        UnitMunicipality um = unitMunicipality.get(new ResolutionKey(e.tenantId(), anchor));
        if (um == null || !um.inDerivation()) {
            return row(e, null, DerivationStatus.NO_UNIT_DERIVATION,
                    DerivationVia.SERVICE_CONNECTION, anchor);
        }
        if (um.municipalityIbge() == null) {
            return row(e, null, DerivationStatus.NULL_MUNICIPALITY,
                    DerivationVia.SERVICE_CONNECTION, anchor);
        }
        return row(e, um.municipalityIbge(), DerivationStatus.DERIVED,
                DerivationVia.SERVICE_CONNECTION, anchor);
    }

    private CutRow cutZone(ElementRef e) {
        if ("COMMERCIAL_SECTOR".equals(e.zoneType())) {
            if (e.commercialSectorId() == null) {
                return row(e, null, DerivationStatus.NO_SECTOR_LINK, null, null);
            }
            SectorMunicipality sm = sectorMunicipality.get(
                    new ResolutionKey(e.tenantId(), e.commercialSectorId()));
            if (sm == null || !sm.sectorExists()) {
                return row(e, null, DerivationStatus.BROKEN_CHAIN,
                        DerivationVia.COMMERCIAL_SECTOR, null);
            }
            if (sm.municipalityIbge() == null) {
                return row(e, null, DerivationStatus.NULL_MUNICIPALITY,
                        DerivationVia.COMMERCIAL_SECTOR, null);
            }
            return row(e, sm.municipalityIbge(), DerivationStatus.DERIVED,
                    DerivationVia.COMMERCIAL_SECTOR, null);
        }
        return noRoute(e);
    }

    /** Classes sem via provada: a excepção classifica-se pelo DMA. */
    private CutRow noRoute(ElementRef e) {
        DerivationStatus st = e.dmaId() != null
                ? DerivationStatus.DMA_ONLY_UNRESOLVED
                : DerivationStatus.NO_TERRITORIAL_LINK;
        return row(e, null, st, null, null);
    }

    private CutRow row(ElementRef e, String ibge, DerivationStatus st,
                       DerivationVia via, UUID anchor) {
        return new CutRow(e.tenantId(), e.sourceTable(), e.elementId(),
                e.elementCode(), ibge, st, via, anchor);
    }

    /** Filtro temático: as feições DERIVADAS de um município, na ordem do recorte. */
    public List<CutRow> listByMunicipality(UUID tenantId, String municipalityIbge,
                                           List<CutRow> cut) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(municipalityIbge, "municipalityIbge");
        List<CutRow> out = new ArrayList<>();
        for (CutRow r : cut) {
            if (r.tenantId().equals(tenantId) && r.isDerived()
                    && municipalityIbge.equals(r.municipalityIbge())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Contagens por município (só DERIVED), na ordem de primeira aparição. */
    public Map<String, Long> countByMunicipality(UUID tenantId, List<CutRow> cut) {
        Objects.requireNonNull(tenantId, "tenantId");
        Map<String, Long> out = new LinkedHashMap<>();
        for (CutRow r : cut) {
            if (r.tenantId().equals(tenantId) && r.isDerived()) {
                out.merge(r.municipalityIbge(), 1L, Long::sum);
            }
        }
        return out;
    }

    /**
     * A lista de excepções explícita do tenant: toda a feição sem
     * município derivável, com o motivo — nunca desaparece em silêncio.
     */
    public List<CutRow> exceptions(UUID tenantId, List<CutRow> cut) {
        Objects.requireNonNull(tenantId, "tenantId");
        List<CutRow> out = new ArrayList<>();
        for (CutRow r : cut) {
            if (r.tenantId().equals(tenantId) && !r.isDerived()) {
                out.add(r);
            }
        }
        return out;
    }
}
