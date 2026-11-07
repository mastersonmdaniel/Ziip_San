package br.com.ziipsan.intelligence.gis.connection.web;

import br.com.ziipsan.intelligence.gis.connection.AutoServiceLineDtos.GenerationResultView;
import br.com.ziipsan.intelligence.gis.connection.AutoServiceLineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Ramais automáticos (GIS-05, P2.23): a geração cria o pacote
 * {@code AUTO_SERVICE_LINE} já submetido a revisão; a aprovação é a da
 * GIS-04 ({@code /api/v1/gis/change-sets}), por outra pessoa.
 */
@RestController
@RequestMapping(GisServiceLineRoles.BASE)
public class AutoServiceLineController {

    private final AutoServiceLineService service;

    public AutoServiceLineController(AutoServiceLineService service) {
        this.service = service;
    }

    /** Distância máxima vigente do parâmetro gis.service_line_max_distance_m. */
    @GetMapping("/max-distance")
    @PreAuthorize(GisServiceLineRoles.READ)
    public BigDecimal maxDistance() {
        return service.maxDistanceM();
    }

    @PostMapping("/generate")
    @PreAuthorize(GisServiceLineRoles.GENERATE)
    public GenerationResultView generate() {
        return service.generate();
    }
}
