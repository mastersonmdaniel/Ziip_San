package br.com.ziipsan.intelligence.gis.waste.web;

import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.CleanupRequestView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.PhotoInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.PropertySectorLinkView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.RouteInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.RouteView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SectorInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SectorView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.SymbolManifestView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.WastePointInput;
import br.com.ziipsan.intelligence.gis.waste.GisWasteDtos.WastePointView;
import br.com.ziipsan.intelligence.gis.waste.GisWasteImportLayers;
import br.com.ziipsan.intelligence.gis.waste.GisWasteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GIS-14: resíduos sólidos — pontos/unidades, descarte irregular
 * (foto + pedido de OS de limpeza), setores, vínculo imóvel↔setor
 * (leitura da ED-25), rotas sobre o grafo de logradouros, camadas
 * de importação (SG-1340, parte) e manifesto de símbolos.
 */
@RestController
@RequestMapping("/api/v1/gis/waste")
public class GisWasteController {

    private final GisWasteService service;

    public GisWasteController(GisWasteService service) {
        this.service = service;
    }

    @PostMapping("/points")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisWasteRoles.EDIT)
    public WastePointView createPoint(@Valid @RequestBody WastePointInput in) {
        return service.createPoint(in);
    }

    @GetMapping("/points/{id}")
    @PreAuthorize(GisWasteRoles.READ)
    public WastePointView getPoint(@PathVariable UUID id) {
        return service.getPoint(id);
    }

    /** Pontos coletáveis fora de setor (§5.4: aviso de qualidade). */
    @GetMapping("/points/outside-sector")
    @PreAuthorize(GisWasteRoles.READ)
    public List<UUID> pointsOutsideSector() {
        return service.pointsOutsideSector();
    }

    @PostMapping("/points/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisWasteRoles.EDIT)
    public void attachPhoto(@PathVariable UUID id, @Valid @RequestBody PhotoInput in) {
        service.attachPhoto(id, in);
    }

    /** Pedido de OS de limpeza: exige foto e situação ACTIVE/REINCIDENT. */
    @PostMapping("/points/{id}/cleanup-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisWasteRoles.EDIT)
    public CleanupRequestView requestCleanup(@PathVariable UUID id) {
        return service.requestCleanup(id);
    }

    @PostMapping("/sectors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisWasteRoles.EDIT)
    public SectorView createSector(@Valid @RequestBody SectorInput in) {
        return service.createSector(in);
    }

    /** (Re)calcula o vínculo imóvel↔setor pelo ponto do imóvel. */
    @PostMapping("/properties/{propertyUnitId}/sector-link")
    @PreAuthorize(GisWasteRoles.EDIT)
    public PropertySectorLinkView linkProperty(@PathVariable UUID propertyUnitId) {
        return service.linkPropertyToSector(propertyUnitId);
    }

    /** Vínculo vigente do imóvel — a leitura que a taxa da ED-25 consome. */
    @GetMapping("/properties/{propertyUnitId}/sector-link")
    @PreAuthorize(GisWasteRoles.READ)
    public PropertySectorLinkView sectorForProperty(@PathVariable UUID propertyUnitId) {
        return service.sectorForProperty(propertyUnitId);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisWasteRoles.EDIT)
    public RouteView createRoute(@Valid @RequestBody RouteInput in) {
        return service.createRoute(in);
    }

    /** Camadas de resíduos do perfil genérico de importação (SG-1340, parte). */
    @GetMapping("/import-layers")
    @PreAuthorize(GisWasteRoles.READ)
    public List<GisWasteImportLayers.WasteLayerMap> importLayers() {
        return GisWasteImportLayers.GENERIC_PROFILE_LAYERS;
    }

    /**
     * Manifesto de símbolos (inventário de chaves por tipo × estado).
     * A biblioteca informada é a da GIS-06 na integração; sem arte da
     * P1.30, presentInLibrary vem falso — marcador declarado.
     */
    @GetMapping("/symbols")
    @PreAuthorize(GisWasteRoles.READ)
    public List<SymbolManifestView> symbols() {
        return service.symbolManifest(Set.of());
    }
}
