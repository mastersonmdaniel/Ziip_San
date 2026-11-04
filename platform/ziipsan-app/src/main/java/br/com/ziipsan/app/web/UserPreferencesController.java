package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.tenant.TenantCatalog;
import br.com.ziipsan.platform.user.UserPreferencesService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * Preferências do próprio usuário no servidor (P1.18, ADR-0017,
 * MARCA-E-TEMAS §4.4): GET e PUT {@code /api/v1/me/preferences}.
 *
 * <p>Sem permissão fina: é dado do próprio usuário autenticado, não de
 * terceiros — qualquer token de tenant válido lê e escreve SÓ a sua
 * linha (a chave é o {@code sub} do token; a RLS por tenant impede o
 * resto). Não há rota de leitura por outro usuário, e a P3.36 (central
 * de notificações) não entra aqui.
 *
 * <p>PUT parcial: campo ausente mantém o guardado, campo explicitamente
 * nulo limpa a escolha (volta ao padrão do tenant). A resposta do GET e
 * do PUT é a mesma visão: escolha guardada (null = sem escolha),
 * efetivo resolvido (usuário → tenant → fábrica) e padrão do tenant.
 * Campo desconhecido no corpo é ignorado; valor fora do domínio é 422.
 */
@RestController
public class UserPreferencesController {

    public record PreferencesInput(JsonNode mode, JsonNode palette, JsonNode locale) {
    }

    public record StoredView(String mode, String palette, String locale) {
    }

    public record TenantDefaultsView(String defaultMode, String defaultPalette,
                                     List<String> allowedPalettes, String defaultLocale) {
    }

    public record PreferencesView(StoredView stored, String effectiveMode, String effectivePalette,
                                  String effectiveLocale, TenantDefaultsView tenantDefaults) {
    }

    private final DataSource dataSource;
    private final TenantCatalog catalog;

    public UserPreferencesController(DataSource dataSource, TenantCatalog catalog) {
        this.dataSource = dataSource;
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/me/preferences")
    public PreferencesView get(JwtAuthenticationToken auth) {
        return toView(service().get(subject(auth)));
    }

    @PutMapping("/api/v1/me/preferences")
    public PreferencesView put(@RequestBody PreferencesInput in, JwtAuthenticationToken auth) {
        if (in == null) {
            throw ApiException.Validation.of(
                    new ApiException.FieldError("body", "required", "O corpo da requisição é obrigatório."));
        }
        return toView(service().save(subject(auth),
                field(in.mode()), field(in.palette()), field(in.locale())));
    }

    private UserPreferencesService service() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        String schema = catalog.find(tenantId)
                .map(TenantCatalog.Entry::schema)
                .orElseThrow(() -> new ApiException.NotFound("O tenant não existe no catálogo."));
        return new UserPreferencesService(dataSource, schema, tenantId);
    }

    private static String subject(JwtAuthenticationToken auth) {
        String subject = auth.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
        return subject;
    }

    /** Ausente = manter; null JSON = limpar; texto = novo valor. */
    private static UserPreferencesService.FieldUpdate field(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return UserPreferencesService.FieldUpdate.absent();
        }
        if (node.isNull()) {
            return UserPreferencesService.FieldUpdate.of(null);
        }
        if (!node.isTextual()) {
            throw ApiException.Validation.of(
                    new ApiException.FieldError("preferences", "invalid", "mode, palette e locale precisam ser texto ou null."));
        }
        return UserPreferencesService.FieldUpdate.of(node.asText());
    }

    private static PreferencesView toView(UserPreferencesService.PreferencesView view) {
        return new PreferencesView(
                new StoredView(view.stored().mode(), view.stored().palette(), view.stored().locale()),
                view.effectiveMode(), view.effectivePalette(), view.effectiveLocale(),
                new TenantDefaultsView(view.tenantDefaults().defaultMode(),
                        view.tenantDefaults().defaultPalette(), view.tenantDefaults().allowedPalettes(),
                        view.tenantDefaults().defaultLocale()));
    }
}
