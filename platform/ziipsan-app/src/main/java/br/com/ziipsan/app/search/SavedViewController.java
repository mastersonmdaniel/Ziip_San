package br.com.ziipsan.app.search;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Visões salvas da P1.33. O portão é {@code search:read}; o dono é sempre o usuário
 * autenticado (nunca vem do corpo): ler, alterar ou excluir a visão de outro dono
 * dá 404. A spec do OpenAPI é regenerada na integração.
 */
@RestController
@RequestMapping("/api/v1/search/saved-views")
@Validated
@PreAuthorize("hasAuthority('search:read')")
public class SavedViewController {

    private final SavedViewService service;

    public SavedViewController(SavedViewService service) {
        this.service = service;
    }

    /** Só as visões do próprio usuário — inclusive as marcadas ORG_UNIT, até a P3.30. */
    @GetMapping
    public List<SavedViewService.SavedViewView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedViewService.SavedViewView create(@Valid @RequestBody SavedViewService.SavedViewInput input) {
        return service.create(input);
    }

    @GetMapping("/{id}")
    public SavedViewService.SavedViewView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public SavedViewService.SavedViewView update(@PathVariable UUID id,
                                                 @Valid @RequestBody SavedViewService.SavedViewInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
