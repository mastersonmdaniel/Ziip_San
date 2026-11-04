package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftInput;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftUpdate;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftView;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.ValidateStepInput;
import br.com.ziipsan.commercial.registry.application.RegistryDraftService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rascunhos do assistente de cadastro (P1.3). O contrato é o do cliente
 * {@code frontend/src/services/registry/drafts.ts}: o recurso da rota é
 * {@code customers} ou {@code consumer-units}; validate-step valida UMA etapa
 * sem gravar (200 ok; 422 com errors[] quando há erro bloqueante).
 */
@RestController
@RequestMapping(RegistryRoles.BASE + "/{resource}")
@PreAuthorize(RegistryRoles.READ)
public class RegistryDraftController {

    private final RegistryDraftService service;

    public RegistryDraftController(RegistryDraftService service) {
        this.service = service;
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public DraftView create(@PathVariable String resource, @RequestBody DraftInput in) {
        return service.create(resource, in);
    }

    @PutMapping("/drafts/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public DraftView update(@PathVariable String resource, @PathVariable UUID id, @RequestBody DraftUpdate in) {
        return service.update(resource, id, in);
    }

    @GetMapping("/drafts/{id}")
    public DraftView get(@PathVariable String resource, @PathVariable UUID id) {
        return service.get(resource, id);
    }

    @DeleteMapping("/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RegistryRoles.WRITE)
    public void discard(@PathVariable String resource, @PathVariable UUID id) {
        service.discard(resource, id);
    }

    @PostMapping("/validate-step")
    @PreAuthorize(RegistryRoles.WRITE)
    public void validateStep(@PathVariable String resource, @Valid @RequestBody ValidateStepInput in) {
        service.validateStep(resource, in);
    }
}
