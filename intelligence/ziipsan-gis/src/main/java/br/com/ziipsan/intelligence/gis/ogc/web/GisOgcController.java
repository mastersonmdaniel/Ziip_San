package br.com.ziipsan.intelligence.gis.ogc.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcContext;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcQuery;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * OGC API – Features na api (GIS-17). Sem rota anônima.
 *
 * <p>Correções ED-67: o {@code item} individual recebe a audiência
 * real do contexto (D4a); os agregados e o piso NÃO são fixados
 * aqui — o serviço deriva os agregados da fonte e lê o parâmetro
 * do tenant pela porta quando ligada (D7); recusas de estado/
 * pendência viram problem+json declarado, nunca 500 genérico (D13).
 */
@RestController
@RequestMapping(GisOgcRoles.OGC_BASE)
@PreAuthorize(GisOgcRoles.VIEW)
public class GisOgcController {

    private final GisOgcService service;
    public GisOgcController(GisOgcService service){ this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> landing() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.landingPage());
    }

    @GetMapping(value = "/conformance", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> conformance() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.conformance());
    }

    @GetMapping(value = "/collections", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> collections() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(service.collectionsJson(GisOgcContext.canViewPersonal()));
    }

    @GetMapping(value = "/collections/{id}/items", produces = "application/geo+json")
    public ResponseEntity<String> items(@PathVariable String id,
                                        @RequestParam(required = false) String bbox,
                                        @RequestParam(required = false) String datetime,
                                        @RequestParam(required = false) String limit,
                                        @RequestParam(required = false) String filter,
                                        @RequestParam(required = false) UUID tenant) {
        try {
            var audience = GisOgcContext.thirdParty() ? GisExportGuard.Audience.THIRD_PARTY
                    : GisExportGuard.Audience.BACKOFFICE;
            String body = service.items(GisOgcContext.tenantId(), tenant, id,
                    GisOgcQuery.parse(bbox, datetime, limit, filter),
                    GisOgcContext.canViewPersonal(), audience, List.of(), 5);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/geo+json")).body(body);
        } catch (SecurityException e) { throw new ApiException.Forbidden(e.getMessage()); }
          catch (IllegalArgumentException e) { throw new ApiException.BadRequest("ogc-query", e.getMessage()); }
          catch (IllegalStateException e) { throw new ApiException.Conflict("ogc-state", e.getMessage()); }
          catch (UnsupportedOperationException e) { throw new ApiException.BadRequest("ogc-pendente", e.getMessage()); }
    }

    @GetMapping(value = "/collections/{id}/items/{featureId}", produces = "application/geo+json")
    public ResponseEntity<String> item(@PathVariable String id, @PathVariable String featureId,
                                       @RequestParam(required = false) UUID tenant) {
        try {
            var audience = GisOgcContext.thirdParty() ? GisExportGuard.Audience.THIRD_PARTY
                    : GisExportGuard.Audience.BACKOFFICE;
            String body = service.item(GisOgcContext.tenantId(), tenant, id, featureId,
                    GisOgcContext.canViewPersonal(), audience);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/geo+json")).body(body);
        } catch (SecurityException e) { throw new ApiException.Forbidden(e.getMessage()); }
          catch (IllegalArgumentException e) { throw new ApiException.BadRequest("ogc-item", e.getMessage()); }
          catch (IllegalStateException e) { throw new ApiException.Conflict("ogc-state", e.getMessage()); }
          catch (UnsupportedOperationException e) { throw new ApiException.BadRequest("ogc-pendente", e.getMessage()); }
    }
}
