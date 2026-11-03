package br.com.ziipsan.app.web;

import br.com.ziipsan.app.export.RegistryExportService;
import br.com.ziipsan.commercial.registry.web.RegistryRoles;
import br.com.ziipsan.core.api.ApiException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P1.12: exportação de listas (regra única).
 *
 * <p>O {@code @PreAuthorize} da classe é {@code registry:read} — ele existe
 * para o RoutePermissionTest (toda rota /api/v1 declara uma permissão do
 * catálogo) e para barrar quem nem lê o cadastro. A permissão que decide a
 * exportação é {@code registry:export}, conferida dentro do serviço (ver
 * {@link br.com.ziipsan.commercial.registry.service.PersonalDataAccessService#authorizeExport}):
 * o gate no serviço, e não na anotação, é o que permite registrar a negação
 * (DENIED) em personal_data_access_log — o mesmo modelo do gate de busca por
 * documento da P0.11. Sem {@code registry:export}, a resposta é 403 do serviço.
 *
 * <p>Até o limite síncrono, o CSV volta no corpo (Content-Disposition
 * attachment); acima dele, 202 com o id do arquivo gerado, que aparece em
 * "Arquivos gerados" quando o worker o concluir.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/exports")
@PreAuthorize(RegistryRoles.READ)
public class RegistryExportController {

    private final RegistryExportService service;

    public RegistryExportController(RegistryExportService service) {
        this.service = service;
    }

    public record ExportRequest(String resource, Map<String, Object> filters) {
    }

    public record QueuedResponse(String generatedFileId) {
    }

    @PostMapping
    public ResponseEntity<?> export(@RequestBody ExportRequest request) {
        Map<String, Object> filters = request.filters() == null ? Map.of() : request.filters();
        RegistryExportService.Result result = switch (request.resource()) {
            case "customers" -> service.exportCustomers(filters);
            case "consumer-units" -> service.exportUnits(filters);
            case null, default -> throw new ApiException.BadRequest("invalid-resource",
                    "Recurso de exportação desconhecido: " + request.resource());
        };
        if (result instanceof RegistryExportService.Queued queued) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new QueuedResponse(queued.generatedFileId().toString()));
        }
        RegistryExportService.Sync sync = (RegistryExportService.Sync) result;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(sync.fileName()).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(sync.csv());
    }
}
