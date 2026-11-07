package br.com.ziipsan.intelligence.gis.publication.web;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.publication.GisMapSearchService;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.BasemapInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.BasemapView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.DirtyStatusView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.FeatureDetailView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.LayerView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.SearchHitView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleOverrideInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.StyleRowView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.SymbolView;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.WmsSourceInput;
import br.com.ziipsan.intelligence.gis.publication.GisPublicationDtos.WmsSourceView;
import br.com.ziipsan.intelligence.gis.publication.GisStyleService;
import br.com.ziipsan.intelligence.gis.publication.GisTileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Superfície do mapa web de consulta da GIS-06 (§10.2/§10.3) sob
 * {@code /api/v1/gis/map}: camadas, documento de estilo MapLibre
 * gerado da fonte única (temas claro e escuro — as cores são tokens
 * resolvidos no navegador), símbolos da biblioteca, mapas base,
 * fontes WMS do tenant, overrides de estilo, identificação e as
 * pesquisas (atributo, endereço, coordenada geográfica e UTM).
 *
 * <p>O documento de estilo é devolvido como texto JSON serializado
 * pela classe {@code Json} do core (a mesma regra das colunas
 * jsonb: nada de serialização manual).
 */
@RestController
@RequestMapping(GisPublicationRoles.MAP_BASE)
@PreAuthorize(GisPublicationRoles.VIEW)
public class GisMapController {

    private final GisStyleService styles;
    private final GisMapSearchService search;
    private final GisTileService tiles;

    public GisMapController(GisStyleService styles, GisMapSearchService search,
                            GisTileService tiles) {
        this.styles = styles;
        this.search = search;
        this.tiles = tiles;
    }

    @GetMapping("/layers")
    public List<LayerView> layers() {
        return styles.layersForCurrentUser();
    }

    /** Estilo MapLibre do tema (light|dark), gerado de gis_style. */
    @GetMapping(value = "/style", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> style(
            @RequestParam(defaultValue = "light") String theme) {
        Map<String, Object> doc = styles.maplibreStyle(theme);
        return ResponseEntity.ok(Json.write(doc));
    }

    @GetMapping("/styles")
    public List<StyleRowView> styleRows() {
        return styles.styleRowsView();
    }

    @PutMapping("/styles/override")
    @PreAuthorize(GisPublicationRoles.STYLE_MANAGE)
    public StyleRowView saveOverride(@Valid @RequestBody StyleOverrideInput in) {
        return styles.saveOverride(in);
    }

    @DeleteMapping("/styles/override/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(GisPublicationRoles.STYLE_MANAGE)
    public void restoreFactory(@PathVariable UUID id) {
        styles.restoreFactory(id);
    }

    @GetMapping("/symbols")
    public List<SymbolView> symbols() {
        return styles.symbols();
    }

    /**
     * Manifesto de símbolos: INVENTÁRIO das chaves publicadas ao
     * tenant (correção A3) — não é o descritor de sprite MapLibre,
     * que só existirá com a arte da P1.30; o documento de estilo
     * não referencia esta rota como sprite até lá.
     */
    @GetMapping(value = "/symbols/sprite", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sprite() {
        return ResponseEntity.ok(Json.write(styles.spriteManifest()));
    }

    @GetMapping("/basemaps")
    public List<BasemapView> basemaps() {
        return styles.basemaps();
    }

    @PostMapping("/basemaps")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisPublicationRoles.STYLE_MANAGE)
    public BasemapView addBasemap(@Valid @RequestBody BasemapInput in) {
        return styles.addBasemap(in);
    }

    @GetMapping("/wms-sources")
    public List<WmsSourceView> wmsSources() {
        return styles.wmsSources();
    }

    @PostMapping("/wms-sources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisPublicationRoles.STYLE_MANAGE)
    public WmsSourceView addWmsSource(@Valid @RequestBody WmsSourceInput in) {
        return styles.addWmsSource(in);
    }

    @DeleteMapping("/wms-sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(GisPublicationRoles.STYLE_MANAGE)
    public void removeWmsSource(@PathVariable UUID id) {
        styles.removeWmsSource(id);
    }

    /** Ficha da feição (identificação ao clicar, §10.3). */
    @GetMapping("/features/{featureTable}/{id}")
    public FeatureDetailView identify(@PathVariable String featureTable,
                                      @PathVariable UUID id) {
        return search.identifyForCurrentUser(featureTable, id);
    }

    @GetMapping("/search/attribute")
    public List<SearchHitView> searchByAttribute(@RequestParam String q) {
        return search.searchByAttributeForCurrentUser(q);
    }

    @GetMapping("/search/address")
    public List<SearchHitView> searchByAddress(@RequestParam String q) {
        return search.searchByAddress(q);
    }

    @GetMapping("/search/near")
    public List<SearchHitView> searchNear(@RequestParam double lon,
                                          @RequestParam double lat,
                                          @RequestParam(defaultValue = "250") double radius) {
        return search.searchNearForCurrentUser(lon, lat, radius);
    }

    @GetMapping("/search/utm")
    public List<SearchHitView> searchByUtm(@RequestParam double easting,
                                           @RequestParam double northing,
                                           @RequestParam int zone,
                                           @RequestParam(defaultValue = "true") boolean southern,
                                           @RequestParam(defaultValue = "250") double radius) {
        return search.searchByUtmForCurrentUser(easting, northing, zone, southern, radius);
    }

    /** Saúde da invalidação de tiles (janela de 1 min do aceite). */
    @GetMapping("/tiles-dirty")
    public DirtyStatusView tilesDirty() {
        return tiles.dirtyStatus();
    }
}
