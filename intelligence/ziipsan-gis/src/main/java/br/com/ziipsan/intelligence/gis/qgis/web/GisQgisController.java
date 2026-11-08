package br.com.ziipsan.intelligence.gis.qgis.web;

import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.EndpointPolicyInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectDownloadInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectLayerInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Administracao do acesso QGIS (GIS-10): pedido de role (estrutura
 * propria — a role em si e materializada pela rotina privilegiada,
 * correcao D6), consulta de roles, validacao da politica do endpoint
 * e ENTREGA do projeto .qgz e do pg_service.conf (correcao D5). A
 * conexao QGIS em si e DIRETA ao endpoint dedicado do banco (§7.1
 * item 5) — nao passa por esta api.
 */
@RestController
@RequestMapping("/api/v1/gis/qgis")
public class GisQgisController {

    private final GisQgisService service;

    public GisQgisController(GisQgisService service) {
        this.service = service;
    }

    @PostMapping("/role-requests")
    @PreAuthorize(GisQgisRoles.ADMIN)
    public RoleRequestView requestRole(@Valid @RequestBody RoleRequestInput in) {
        return service.requestRole(in);
    }

    @GetMapping("/role-requests")
    @PreAuthorize(GisQgisRoles.READ)
    public List<RoleRequestView> listRoleRequests() {
        return service.listRoleRequests();
    }

    @GetMapping("/role-requests/{id}")
    @PreAuthorize(GisQgisRoles.READ)
    public RoleRequestView getRoleRequest(@PathVariable UUID id) {
        return service.getRoleRequest(id);
    }

    @GetMapping("/roles")
    @PreAuthorize(GisQgisRoles.READ)
    public List<RoleView> listRoles() {
        return service.listRoles();
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize(GisQgisRoles.READ)
    public RoleView getRole(@PathVariable UUID id) {
        return service.getRole(id);
    }

    @PostMapping("/endpoint-policy/validate")
    @PreAuthorize(GisQgisRoles.ADMIN)
    public ResponseEntity<Void> validateEndpoint(
            @Valid @RequestBody EndpointPolicyInput in) {
        service.validateEndpointPolicy(in);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/describe")
    @PreAuthorize(GisQgisRoles.READ)
    public ProjectView describeProject(
            @RequestBody List<@Valid ProjectLayerInput> layers) {
        return service.describeProject(layers, "ziipsan-qgis");
    }

    /** Download do .qgz gerado (correcao D5). */
    @PostMapping("/projects/download")
    @PreAuthorize(GisQgisRoles.READ)
    public ResponseEntity<byte[]> downloadProject(
            @Valid @RequestBody ProjectDownloadInput in) {
        byte[] qgz = service.downloadProject(in);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("ziipsan-qgis.qgz", StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(qgz);
    }

    /** Conteudo do pg_service.conf do usuario da sessao (correcao D5). */
    @PostMapping("/projects/pg-service-conf")
    @PreAuthorize(GisQgisRoles.READ)
    public PgServiceConfView pgServiceConf(@Valid @RequestBody PgServiceConfInput in) {
        return service.pgServiceConfForCurrentUser(in);
    }
}
