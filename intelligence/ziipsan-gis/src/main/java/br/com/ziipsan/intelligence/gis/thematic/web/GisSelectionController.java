package br.com.ziipsan.intelligence.gis.thematic.web;

import br.com.ziipsan.intelligence.gis.thematic.GisSelectionService;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PolygonInput;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PolygonStatsView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seleção por polígono com estatísticas (TR-1059): o polígono vai
 * no CORPO como GeoJSON (nunca na URL, RNF-52) e a resposta são só
 * os agregados — ligações, economias, consumo médio e área.
 */
@RestController
@RequestMapping(GisThematicRoles.SELECTION_BASE)
@PreAuthorize(GisThematicRoles.VIEW)
public class GisSelectionController {

    private final GisSelectionService service;

    public GisSelectionController(GisSelectionService service) {
        this.service = service;
    }

    @PostMapping("/statistics")
    public PolygonStatsView statistics(@Valid @RequestBody PolygonInput input) {
        return service.statistics(input.polygonGeoJson());
    }
}
