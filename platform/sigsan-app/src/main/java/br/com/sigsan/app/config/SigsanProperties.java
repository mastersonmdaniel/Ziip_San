package br.com.sigsan.app.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuração da aplicação ({@code sigsan.*}). Sem valor padrão para segredo ou endereço: falta de
 * configuração derruba a subida com a lista do que falta, em vez de conectar no lugar errado.
 * Os valores vêm de variáveis de ambiente (ver application.yml e .env.example).
 */
@Validated
@ConfigurationProperties("sigsan")
public record SigsanProperties(@Valid @NotNull Db db, @Valid @NotNull Oidc oidc, @Valid Cors cors) {

    /**
     * Dois usuários de banco: a aplicação (sigsan_app, sem DDL, sujeita à RLS) e a migração
     * (sigsan_migrator, só para Flyway e provisionamento).
     */
    public record Db(@NotBlank String url,
                     @NotBlank String appUser,
                     @NotBlank String appPassword,
                     @NotBlank String migratorUser,
                     @NotBlank String migratorPassword,
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
