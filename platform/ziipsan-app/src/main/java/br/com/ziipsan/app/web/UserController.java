package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.user.TenantUserService;
import br.com.ziipsan.platform.web.ConditionalWrite;
import br.com.ziipsan.platform.web.IdempotencyStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * perfis de fábrica do catálogo (P1.37: os 26 perfis, exceto os desligados — auditor
 * externo e regulador, ED-75), com vigência. Leitura exige {@code user:read}; escrita,
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
    private final IdempotencyStore idempotency;

    public UserController(TenantUserService service, IdempotencyStore idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<TenantUserService.UserView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantUserService.UserView> get(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .eTag(ConditionalWrite.etagFor(service.versionOf(id)))
                .body(service.get(id));
    }

    /**
     * POST idempotente (P1.11): com {@code Idempotency-Key}, o segundo POST com a
     * mesma chave, no mesmo tenant, devolve a resposta gravada (201 e o mesmo corpo)
     * sem criar outro usuário. Sem a chave, o comportamento é o de antes; erro de
     * validação ou conflito nunca é gravado, então o cliente pode corrigir e repetir
     * a chave.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:write')")
    public ResponseEntity<TenantUserService.UserView> create(
            @Valid @RequestBody CreateInput in,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(new TenantUserService.CreateRequest(
                    in.keycloakSub(), in.displayName(), in.email(), in.profiles())));
        }
        var key = new IdempotencyStore.Key(TenantContextHolder.getTenantId(),
                "POST", "/api/v1/users", idempotencyKey.trim());
        synchronized (idempotency.lockFor(key)) {
            var replay = idempotency.find(key);
            if (replay.isPresent()) {
                return ResponseEntity.status(replay.get().status())
                        .body((TenantUserService.UserView) replay.get().body());
            }
            TenantUserService.UserView created = service.create(new TenantUserService.CreateRequest(
                    in.keycloakSub(), in.displayName(), in.email(), in.profiles()));
            idempotency.store(key, new IdempotencyStore.StoredResponse(HttpStatus.CREATED.value(), created));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        }
    }

    /**
     * PUT condicional (P1.11): sem {@code If-Match} é 428; com versão diferente da
     * atual é 412. A ordem importa: a pré-condição é conferida antes da escrita, e
     * o 404 de outro tenant continua 404 quando a pré-condição passa ({@code *}).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:write')")
    public TenantUserService.UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateInput in,
                                             @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException.PreconditionRequired(
                    "Esta escrita exige o cabeçalho If-Match com o ETag atual do recurso.");
        }
        long current = service.versionOf(id);
        if (!ConditionalWrite.matches(ifMatch, current)) {
            throw new ApiException.PreconditionFailed(
                    "O recurso foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
        return service.update(id, new TenantUserService.UpdateRequest(
                in.displayName(), in.email(), in.status(), in.profiles()));
    }
}
