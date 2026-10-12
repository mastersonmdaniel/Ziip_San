package br.com.sigsan.commercial.registry.web;

import br.com.sigsan.commercial.registry.application.PageResult;
import br.com.sigsan.commercial.registry.application.PageSpec;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.BlockInput;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.BlockView;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.LocalityInput;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.LocalityView;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.RouteInput;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.RouteView;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.SectorInput;
import br.com.sigsan.commercial.registry.application.TerritoryDtos.SectorView;
import br.com.sigsan.commercial.registry.application.TerritoryService;
import br.com.sigsan.core.api.ApiException;
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
 * Hierarquia territorial: {@code localities}, {@code commercial-sectors}, {@code blocks} e
 * {@code reading-routes}. Sem exclusão (ver {@link TerritoryService}): o DELETE responde 405 com o que
 * usar no lugar, em vez do 405 genérico de rota.
 */
@RestController
@RequestMapping(RegistryRoles.BASE)
@PreAuthorize(RegistryRoles.READ)
public class TerritoryController {

    private final TerritoryService service;

    public TerritoryController(TerritoryService service) {
        this.service = service;
    }

    // ---------- localidades

    @GetMapping("/localities")
    public PageResult<LocalityView> listLocalities(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Boolean blocked,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        return service.listLocalities(q, blocked, PageSpec.of(page, size, sort));
    }

    @GetMapping("/localities/{id}")
    public LocalityView getLocality(@PathVariable UUID id) {
        return service.getLocality(id);
    }

    @PostMapping("/localities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public LocalityView createLocality(@Valid @RequestBody LocalityInput in) {
        return service.createLocality(in);
    }

    @PutMapping("/localities/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public LocalityView updateLocality(@PathVariable UUID id, @Valid @RequestBody LocalityInput in) {
        return service.updateLocality(id, in);
    }

    @DeleteMapping("/localities/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public void deleteLocality(@PathVariable UUID id) {
        throw noDelete("A localidade não tem exclusão lógica no cadastro: bloqueie-a com blocked=true.");
    }

    // ---------- setores comerciais

    @GetMapping("/commercial-sectors")
    public PageResult<SectorView> listSectors(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) UUID localityId,
                                              @RequestParam(required = false) Boolean blocked,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
        return service.listSectors(q, localityId, blocked, PageSpec.of(page, size, sort));
    }

    @GetMapping("/commercial-sectors/{id}")
    public SectorView getSector(@PathVariable UUID id) {
        return service.getSector(id);
    }

    @PostMapping("/commercial-sectors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public SectorView createSector(@Valid @RequestBody SectorInput in) {
        return service.createSector(in);
    }

    @PutMapping("/commercial-sectors/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public SectorView updateSector(@PathVariable UUID id, @Valid @RequestBody SectorInput in) {
        return service.updateSector(id, in);
    }

    @DeleteMapping("/commercial-sectors/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public void deleteSector(@PathVariable UUID id) {
        throw noDelete("O setor comercial não tem exclusão lógica no cadastro: bloqueie-o com blocked=true.");
    }

    // ---------- quadras

    @GetMapping("/blocks")
    public PageResult<BlockView> listBlocks(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) UUID sectorId,
                                            @RequestParam(required = false) UUID localityId,
                                            @RequestParam(required = false) UUID readingRouteId,
                                            @RequestParam(required = false) Boolean blocked,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return service.listBlocks(q, sectorId, localityId, readingRouteId, blocked, PageSpec.of(page, size, sort));
    }

    @GetMapping("/blocks/{id}")
    public BlockView getBlock(@PathVariable UUID id) {
        return service.getBlock(id);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public BlockView createBlock(@Valid @RequestBody BlockInput in) {
        return service.createBlock(in);
    }

    @PutMapping("/blocks/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public BlockView updateBlock(@PathVariable UUID id, @Valid @RequestBody BlockInput in) {
        return service.updateBlock(id, in);
    }

    @DeleteMapping("/blocks/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public void deleteBlock(@PathVariable UUID id) {
        throw noDelete("A quadra não tem exclusão lógica no cadastro: bloqueie-a com blocked=true.");
    }

    // ---------- rotas de leitura

    @GetMapping("/reading-routes")
    public PageResult<RouteView> listRoutes(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return service.listRoutes(q, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/reading-routes/{id}")
    public RouteView getRoute(@PathVariable UUID id) {
        return service.getRoute(id);
    }

    @PostMapping("/reading-routes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public RouteView createRoute(@Valid @RequestBody RouteInput in) {
        return service.createRoute(in);
    }

    @PutMapping("/reading-routes/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public RouteView updateRoute(@PathVariable UUID id, @Valid @RequestBody RouteInput in) {
        return service.updateRoute(id, in);
    }

    @DeleteMapping("/reading-routes/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public void deleteRoute(@PathVariable UUID id) {
        throw noDelete("A rota de leitura não tem exclusão lógica no cadastro: desative-a com active=false.");
    }

    private static ApiException noDelete(String message) {
        return new ApiException.MethodNotAllowed("exclusao-indisponivel", message);
    }
}
