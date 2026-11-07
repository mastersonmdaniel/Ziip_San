package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressInput;
import br.com.ziipsan.commercial.registry.application.ConsumerUnitDtos.AddressView;
import br.com.ziipsan.commercial.registry.application.UnitAlternativeAddressService;
import br.com.ziipsan.core.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ED-12: endereço alternativo da unidade consumidora
 * ({@code addresses} tipo {@code ALTERNATIVE}). O endereço principal da
 * unidade continua nas rotas de {@code ConsumerUnitController}; aqui só o
 * alternativo: ler, gravar/substituir (PUT) e remover.
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/units/{unitId}/alternative-address")
@PreAuthorize(RegistryRoles.READ)
public class UnitAlternativeAddressController {

    private final UnitAlternativeAddressService service;

    public UnitAlternativeAddressController(UnitAlternativeAddressService service) {
        this.service = service;
    }

    @GetMapping
    public AddressView get(@PathVariable UUID unitId) {
        return service.get(unitId).orElseThrow(() ->
                new ApiException.NotFound("A unidade nao tem endereco alternativo cadastrado."));
    }

    @PutMapping
    @PreAuthorize(RegistryRoles.WRITE)
    public AddressView set(@PathVariable UUID unitId, @Valid @RequestBody AddressInput in) {
        return service.set(unitId, in);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RegistryRoles.WRITE)
    public void remove(@PathVariable UUID unitId) {
        service.remove(unitId);
    }
}
