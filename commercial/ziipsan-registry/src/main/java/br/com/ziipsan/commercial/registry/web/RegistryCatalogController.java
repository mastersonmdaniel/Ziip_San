package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.PageResult;
import br.com.ziipsan.commercial.registry.application.PageSpec;
import br.com.ziipsan.commercial.registry.application.Ref;
import br.com.ziipsan.commercial.registry.application.RegistryCatalogService;
import br.com.ziipsan.commercial.registry.application.RegistryCatalogService.CatalogInput;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Catálogos R2 do cadastro (P1.3, V3_12): customer_types, property_profiles,
 * relation_roles, document_exemption_reasons, customer_delete_reasons,
 * unit_link_end_reasons, sewer_discharges, consent_purposes (escrita) e, só
 * leitura, block_faces, street_names, neighborhoods e ibge_municipalities.
 * Sem DELETE físico: desativar com {@code active=false} (padrão do CatalogController).
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/catalogs")
@PreAuthorize(RegistryRoles.READ)
public class RegistryCatalogController {

    private final RegistryCatalogService service;

    public RegistryCatalogController(RegistryCatalogService service) {
        this.service = service;
    }

    @GetMapping("/{table}")
    public PageResult<Ref> list(@PathVariable String table,
                                @RequestParam(required = false) Boolean active,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) String sort) {
        return service.list(table, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/{table}/{id}")
    public Ref get(@PathVariable String table, @PathVariable UUID id) {
        return service.get(table, id);
    }

    @PostMapping("/{table}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public Ref create(@PathVariable String table, @Valid @RequestBody CatalogInput in) {
        return service.create(table, in);
    }

    @PutMapping("/{table}/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public Ref update(@PathVariable String table, @PathVariable UUID id, @Valid @RequestBody CatalogInput in) {
        return service.update(table, id, in);
    }

    @DeleteMapping("/{table}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RegistryRoles.WRITE)
    public void deactivate(@PathVariable String table, @PathVariable UUID id) {
        service.deactivate(table, id);
    }
}
