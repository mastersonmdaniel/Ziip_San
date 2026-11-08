package br.com.ziipsan.intelligence.gis.thematic.web;

import br.com.ziipsan.intelligence.gis.thematic.GisServiceOrderMapService;
import br.com.ziipsan.intelligence.gis.thematic.GisServiceOrderMapService.OrderFilters;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ServiceOrderPoint;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Camada de OS no mapa (SG-491, TR-534): OS solicitadas e
 * programadas por padrão ({@code statuses} default OPEN,SCHEDULED),
 * com filtro por endereço ({@code address}), equipe
 * ({@code crewUserId}) e tipo de serviço ({@code serviceTypeCode}),
 * e a exportação analítica do TR-1061 em CSV.
 */
@RestController
@RequestMapping(GisThematicRoles.ORDERS_BASE)
@PreAuthorize(GisThematicRoles.VIEW)
public class GisServiceOrderMapController {

    private final GisServiceOrderMapService service;

    public GisServiceOrderMapController(GisServiceOrderMapService service) {
        this.service = service;
    }

    @GetMapping
    public List<ServiceOrderPoint> orders(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String serviceTypeCode,
            @RequestParam(required = false) UUID crewUserId,
            @RequestParam(required = false) String address) {
        return service.orders(filters(statuses, serviceTypeCode, crewUserId, address));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String serviceTypeCode,
            @RequestParam(required = false) UUID crewUserId,
            @RequestParam(required = false) String address) {
        String csv = service.exportCsv(filters(statuses, serviceTypeCode, crewUserId, address));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"os-no-mapa.csv\"")
                .body(csv);
    }

    private OrderFilters filters(List<String> statuses, String serviceTypeCode,
                                 UUID crewUserId, String address) {
        List<String> effective = (statuses == null || statuses.isEmpty())
                ? List.of("OPEN", "SCHEDULED") : statuses;
        return new OrderFilters(effective, serviceTypeCode, crewUserId, address);
    }
}
