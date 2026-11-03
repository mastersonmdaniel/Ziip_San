package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.RegistrySettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Parâmetros do cadastro do tenant (parameter_versions com aceite, V1_5; demais chaves em public.tenant_settings), para o assistente de imóvel. */
@RestController
@PreAuthorize(RegistryRoles.READ)
public class RegistrySettingsController {

    private final RegistrySettingsService service;

    public RegistrySettingsController(RegistrySettingsService service) {
        this.service = service;
    }

    @GetMapping(RegistryRoles.BASE + "/settings")
    public RegistrySettingsService.Settings settings() {
        return service.current();
    }
}
