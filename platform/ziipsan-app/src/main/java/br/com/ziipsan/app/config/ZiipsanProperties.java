package br.com.ziipsan.app.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuração da aplicação ({@code ziipsan.*}). Sem valor padrão para segredo ou endereço: falta de
 * configuração derruba a subida com a lista do que falta, em vez de conectar no lugar errado.
 * Os valores vêm de variáveis de ambiente (ver application.yml e .env.example).
 */
@Validated
@ConfigurationProperties("ziipsan")
public record ZiipsanProperties(@Valid @NotNull Db db, @Valid @NotNull Oidc oidc, @Valid Cors cors, Bff bff) {

    /**
     * Cookie de sessão do BFF (P0.14). Padrão: {@code __Host-ziipsan-session} com
     * Secure (o prefixo __Host- EXIGE Secure + Path=/ + sem Domain). Sem TLS, o
     * perfil {@code local} desliga o Secure E tira o prefixo (navegador rejeita
     * __Host- sem Secure) — exceção explícita de perfil, nunca em produção.
     */
    public record Bff(String cookieName, Boolean cookieSecure) {
        // Construtor compacto (mesmo padrão do Cors abaixo): um acessor de record
        // NÃO pode mudar o tipo do componente, então o default vive aqui e o
        // acessor gerado já devolve o valor normalizado.
        public Bff {
            if (cookieName == null || cookieName.isBlank()) {
                cookieName = "__Host-ziipsan-session";
            }
            if (cookieSecure == null) {
                cookieSecure = true;
            }
        }
    }

    public Bff bff() {
        return bff == null ? new Bff(null, null) : bff;
    }

    /**
     * Banco da aplicação (ziipsan_app, sem DDL, sujeita à RLS).
     *
     * <p>P0.6: a senha do migrador NÃO está mais aqui. Este record é {@code @Validated} e
     * todo processo (api, worker, migrate) o exige — se a senha do migrador ficasse aqui,
     * a api/worker exigiriam uma credencial que nunca usam. Ela vive em
     * {@link MigratorProperties} (sem validação, só lida quando o perfil {@code migrate}
     * cria o {@code migratorDataSource}).
     */
    public record Db(@NotBlank String url,
                     @NotBlank String appUser,
                     @NotBlank String appPassword,
                     boolean migrateOnStartup) {
    }

    /**
     * Validação do JWT. {@code jwkSetUri} em uso normal (chaves do Keycloak); {@code publicKey}
     * (RSA, X.509 em Base64) para testes com tokens assinados localmente. Um dos dois é obrigatório.
     */
    public record Oidc(@NotBlank String issuer,
                       String jwkSetUri,
                       String publicKey,
                       @NotBlank String audience) {
    }

    /** Origens do navegador aceitas (ex.: http://localhost:3000). Vazio: nenhuma. */
    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    public Cors cors() {
        return cors == null ? new Cors(List.of()) : cors;
    }
}
