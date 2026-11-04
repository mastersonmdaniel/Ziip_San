package br.com.ziipsan.app.templates;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modelos de documento e mensagem da P2.30. O portão de leitura é
 * {@code template:read}; escrita e aprovação têm portões próprios por
 * método (a separação proponente ≠ aprovador e autor ≠ aprovador vive no
 * serviço, por ator, não por perfil). A spec do OpenAPI é regenerada na
 * integração.
 */
@RestController
@RequestMapping("/api/v1/templates")
@Validated
@PreAuthorize("hasAuthority('template:read')")
public class DocumentTemplateController {

    private final DocumentTemplateService service;

    public DocumentTemplateController(DocumentTemplateService service) {
        this.service = service;
    }

    /** Modelos de fábrica (código, nunca editáveis): o tenant copia e ajusta. */
    @GetMapping("/factory")
    public List<DocumentTemplateService.FactoryView> listFactory() {
        return service.listFactory();
    }

    @PostMapping("/factory/{key}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('template:write')")
    public DocumentTemplateService.TemplateView copyFactory(@PathVariable String key,
                                                            @Valid @RequestBody DocumentTemplateService.CopyInput input) {
        return service.copyFactory(key, input);
    }

    @GetMapping
    public List<DocumentTemplateService.TemplateView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('template:write')")
    public DocumentTemplateService.TemplateView create(
            @Valid @RequestBody DocumentTemplateService.TemplateInput input) {
        return service.create(input);
    }

    @GetMapping("/{id}")
    public DocumentTemplateService.TemplateView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<DocumentTemplateService.VersionView> listVersions(@PathVariable UUID id) {
        return service.listVersions(id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('template:write')")
    public DocumentTemplateService.VersionView createVersion(@PathVariable UUID id,
                                                             @Valid @RequestBody DocumentTemplateService.VersionInput input) {
        return service.createVersion(id, input);
    }

    /** Prévia com massa sintética; grava o instante e o texto para a aprovação. */
    @PostMapping("/{id}/versions/{versionId}/preview")
    public Map<String, String> preview(@PathVariable UUID id, @PathVariable UUID versionId) {
        return Map.of("rendered", service.preview(id, versionId));
    }

    @PostMapping("/{id}/versions/{versionId}/propose")
    @PreAuthorize("hasAuthority('template:write')")
    public DocumentTemplateService.VersionView propose(@PathVariable UUID id, @PathVariable UUID versionId) {
        return service.propose(id, versionId);
    }

    @PostMapping("/{id}/versions/{versionId}/approve")
    @PreAuthorize("hasAuthority('template:approve')")
    public DocumentTemplateService.VersionView approve(@PathVariable UUID id, @PathVariable UUID versionId) {
        return service.approve(id, versionId);
    }

    @PostMapping("/{id}/versions/{versionId}/reject")
    @PreAuthorize("hasAuthority('template:approve')")
    public DocumentTemplateService.VersionView reject(@PathVariable UUID id, @PathVariable UUID versionId,
                                                      @RequestBody(required = false) Map<String, String> body) {
        return service.reject(id, versionId, body == null ? null : body.get("reason"));
    }

    @PostMapping("/{id}/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplateService.GeneratedView generate(@PathVariable UUID id,
                                                           @Valid @RequestBody DocumentTemplateService.GenerateInput input) {
        return service.generate(id, input);
    }

    @GetMapping("/{id}/generated")
    public List<DocumentTemplateService.GeneratedView> listGenerated(@PathVariable UUID id) {
        return service.listGenerated(id);
    }
}
