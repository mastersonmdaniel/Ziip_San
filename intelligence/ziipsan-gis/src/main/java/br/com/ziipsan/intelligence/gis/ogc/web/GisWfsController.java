package br.com.ziipsan.intelligence.gis.ogc.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcContext;
import br.com.ziipsan.intelligence.gis.ogc.GisOgcQuery;
import br.com.ziipsan.intelligence.gis.ogc.GisWfsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fachada WFS 2.0 (GIS-17): GET com request=GetCapabilities /
 * DescribeFeatureType / GetFeature; POST de Transaction grava no
 * pacote DRAFT do usuário (resposta declara appliedToOperationalNetwork=false).
 *
 * <p>Correções ED-67: o GetFeature propaga a audiência REAL do
 * contexto (D4b — antes o serviço fixava BACKOFFICE e a distinção
 * backoffice × terceiro do §12 não existia neste caminho); recusas
 * de estado/pendência viram problem+json declarado (D13).
 */
@RestController
@RequestMapping(GisOgcRoles.WFS_BASE)
@PreAuthorize(GisOgcRoles.VIEW)
public class GisWfsController {

    private final GisWfsService service;
    public GisWfsController(GisWfsService service){ this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> get(@RequestParam String request,
                                      @RequestParam(required = false) String typeName,
                                      @RequestParam(required = false) String bbox,
                                      @RequestParam(required = false) String count,
                                      @RequestParam(required = false) UUID tenant) {
        try {
            var audience = GisOgcContext.thirdParty() ? GisExportGuard.Audience.THIRD_PARTY
                    : GisExportGuard.Audience.BACKOFFICE;
            String body = switch (request) {
                case "GetCapabilities" -> service.getCapabilities(GisOgcContext.canViewPersonal());
                case "DescribeFeatureType" -> service.describeFeatureType(typeName);
                case "GetFeature" -> service.getFeature(GisOgcContext.tenantId(), tenant, typeName,
                        GisOgcQuery.parse(bbox, null, count, null), GisOgcContext.canViewPersonal(),
                        audience, List.of(), 5);
                default -> throw new IllegalArgumentException("operação WFS não suportada: " + request);
            };
            MediaType mt = "GetFeature".equals(request) ? MediaType.parseMediaType("application/geo+json")
                    : MediaType.APPLICATION_XML;
            return ResponseEntity.ok().contentType(mt).body(body);
        } catch (SecurityException e) { throw new ApiException.Forbidden(e.getMessage()); }
          catch (IllegalArgumentException e) { throw new ApiException.BadRequest("wfs", e.getMessage()); }
          catch (IllegalStateException e) { throw new ApiException.Conflict("wfs-state", e.getMessage()); }
          catch (UnsupportedOperationException e) { throw new ApiException.BadRequest("wfs-pendente", e.getMessage()); }
    }

    @PostMapping(consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> transaction(@RequestBody String xml) {
        try {
            Map<String,Object> out = service.transaction(GisOgcContext.tenantId(), GisOgcContext.userId(),
                    xml, GisOgcContext.canViewPersonal());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Json.write(out));
        } catch (SecurityException e) { throw new ApiException.Forbidden(e.getMessage()); }
          catch (IllegalArgumentException e) { throw new ApiException.BadRequest("wfs-transaction", e.getMessage()); }
          catch (IllegalStateException e) { throw new ApiException.Conflict("wfs-state", e.getMessage()); }
          catch (UnsupportedOperationException e) { throw new ApiException.BadRequest("wfs-pendente", e.getMessage()); }
    }
}
