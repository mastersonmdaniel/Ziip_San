package br.com.ziipsan.app.search;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca global (P1.33). O portão é {@code search:read}; cada seção exige ainda a
 * permissão do próprio recurso (ver {@link GlobalSearchService}) — o portão não
 * amplia leitura de nenhum recurso. A spec do OpenAPI é regenerada na integração
 * (este arquivo não é tocado por ela, mas a rota nova entra na spec gerada).
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@PreAuthorize("hasAuthority('search:read')")
public class GlobalSearchController {

    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    /**
     * Busca global por teclado (Ctrl ou Cmd+K no frontend). O texto vai no corpo:
     * pode ser um CPF/CNPJ, e documento nunca vai na URL. Sem corpo, sem busca.
     */
    @PostMapping("/global")
    public GlobalSearchService.GlobalSearchResult search(@Valid @RequestBody GlobalSearchService.GlobalSearchRequest request) {
        return service.search(request);
    }
}
