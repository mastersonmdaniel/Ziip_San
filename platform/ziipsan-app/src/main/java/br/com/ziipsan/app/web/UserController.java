package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.user.TenantUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Gestão de usuários do tenant pela API própria (P0.13).
 *
 * <p>O tenant nunca vem do corpo nem da URL: vem do token. O cadastro no Keycloak é manual;
 * aqui se vincula o registro ao {@code sub} do Keycloak ({@code keycloakSub}) e se atribuem
 * perfis de fábrica do catálogo, com vigência. Leitura exige {@code user:read}; escrita,
 * {@code user:write} (só o ADMIN a possui entre os perfis de fábrica).
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('user:read')")
public class UserController {

    public record CreateInput(
            @NotBlank @Size(max = 255) String keycloakSub,
            @NotBlank @Size(max = 200) String displayName,
            @Email @Size(max = 255) String email,
            List<String> profiles) {
    }

    public record UpdateInput(
            @NotBlank @Size(max = 200) String displayName,
            @Email @Size(max = 255) String email,
            @NotBlank String status,
            List<String> profiles) {
    }

    private final TenantUserService service;

    public UserController(TenantUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<TenantUserService.UserView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TenantUserService.UserView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:write')")
    public TenantUserService.UserView create(@Valid @RequestBody CreateInput in) {
        return service.create(new TenantUserService.CreateRequest(
                in.keycloakSub(), in.displayName(), in.email(), in.profiles()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:write')")
    public TenantUserService.UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateInput in) {
        return service.update(id, new TenantUserService.UpdateRequest(
                in.displayName(), in.email(), in.status(), in.profiles()));
    }
}
