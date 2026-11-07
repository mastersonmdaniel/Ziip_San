package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ChangeStatusInput;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.ConnectionView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.CreateInput;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionDtos.HistoryView;
import br.com.ziipsan.commercial.registry.application.ServiceConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ligações de água e esgoto (GIS-05, P2.23). Leitura com
 * {@code registry:read}; escrita com {@code registry:write}. A ficha
 * ({@code GET /service-connections/{id}/card}) decide a forma completa
 * ou restrita no serviço, pela autoridade {@code gis:view-personal} do
 * token — nunca por parâmetro.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/service-connections")
@Validated
@PreAuthorize(RegistryRoles.READ)
public class ServiceConnectionController {

    private final ServiceConnectionService service;

    public ServiceConnectionController(ServiceConnectionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public ConnectionView create(@Valid @RequestBody CreateInput in) {
        return service.create(in);
    }

    @GetMapping("/{id}")
    public ConnectionView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping
    public List<ConnectionView> listByProperty(@RequestParam UUID propertyUnitId) {
        return service.listByProperty(propertyUnitId);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(RegistryRoles.WRITE)
    public ConnectionView changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusInput in) {
        return service.changeStatus(id, in);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize(RegistryRoles.WRITE)
    public ConnectionView end(@PathVariable UUID id,
                              @RequestParam UUID serviceOrderId,
                              @RequestParam(required = false) String reason) {
        return service.end(id, serviceOrderId, reason);
    }

    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable UUID id) {
        return service.history(id);
    }

    /** Ficha: completa com gis:view-personal; sem ela, só identificador e situação. */
    @GetMapping("/{id}/card")
    public Object card(@PathVariable UUID id) {
        return service.card(id);
    }
}
