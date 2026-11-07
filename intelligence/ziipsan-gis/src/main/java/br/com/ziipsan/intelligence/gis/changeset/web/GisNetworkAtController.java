package br.com.ziipsan.intelligence.gis.changeset.web;

import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.NetworkAtView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

/**
 * "Rede na data" (§3.7): {@code GET /api/v1/gis/network-at?date=AAAA-MM-DD}
 * pelo tempo do negócio e {@code ?recorded-at=<instante>} pelo tempo do
 * sistema. Só feições {@code IN_SERVICE} no instante — projetadas e em
 * obra ficam fora do traçado e dos indicadores.
 */
@RestController
@RequestMapping(GisChangeSetRoles.NETWORK_AT_BASE)
@PreAuthorize(GisChangeSetRoles.READ)
public class GisNetworkAtController {

    private final GisChangeSetService service;

    public GisNetworkAtController(GisChangeSetService service) {
        this.service = service;
    }

    @GetMapping
    public NetworkAtView networkAt(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "recorded-at", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant recordedAt) {
        return service.networkAt(date, recordedAt);
    }
}
