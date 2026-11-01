package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.HistoryEvent;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitCustomerLink;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitSearch;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitSummary;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.UnitView;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitService;
import br.com.ziipsan.commercial.registry.application.DeleteRequest;
import br.com.ziipsan.commercial.registry.application.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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

import java.util.List;
import java.util.UUID;

/**
 * Imóveis (unidades consumidoras). Como em clientes, a busca livre (inscrição, matrícula, nome ou
 * documento do titular) é {@code POST /consumer-units/search}; o GET de lista só aceita os filtros por id.
 *
 * <p>P0.16: {@code @Validated} na classe para o {@code @Size} do parâmetro {@code q} do GET ser
 * avaliado (mesma precedência que em clientes: q com mais de 200 caracteres dá 422 antes da recusa
 * 400 de busca-na-URL).
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/consumer-units")
@Validated
@PreAuthorize(RegistryRoles.READ)
public class ConsumerUnitController {

    private final ConsumerUnitService service;

    public ConsumerUnitController(ConsumerUnitService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<UnitSummary> list(@RequestParam(required = false) @Size(max = 200, message = "A busca aceita no máximo 200 caracteres.") String q,
                                        @RequestParam(required = false) UUID localityId,
                                        @RequestParam(required = false) UUID sectorId,
                                        @RequestParam(required = false) UUID blockId,
                                        @RequestParam(required = false) UUID waterStatusId,
                                        @RequestParam(required = false) UUID sewerStatusId,
                                        @RequestParam(required = false) UUID categoryId,
                                        @RequestParam(required = false) UUID routeId,
                                        @RequestParam(required = false) Boolean includeDeleted,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size,
                                        @RequestParam(required = false) String sort) {
        return service.list(q, new UnitSearch(null, localityId, sectorId, blockId, waterStatusId, sewerStatusId,
                categoryId, routeId, null, null, null, null, includeDeleted, page, size, sort));
    }

    @PostMapping("/search")
    public PageResult<UnitSummary> search(@Valid @RequestBody UnitSearch search) {
        return service.search(search);
    }

    @GetMapping("/{id}")
    public UnitView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/customers")
    public List<UnitCustomerLink> customers(@PathVariable UUID id) {
        return service.customers(id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEvent> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public UnitView create(@Valid @RequestBody UnitInput in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public UnitView update(@PathVariable UUID id, @Valid @RequestBody UnitInput in) {
        return service.update(id, in);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RegistryRoles.WRITE)
    public void delete(@PathVariable UUID id, @Valid @RequestBody DeleteRequest body) {
        service.delete(id, body.reason());
    }
}
