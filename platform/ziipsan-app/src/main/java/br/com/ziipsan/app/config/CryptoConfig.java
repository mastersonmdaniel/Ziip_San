package br.com.ziipsan.app.config;

import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import br.com.ziipsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Cifra de campo (CPF/CNPJ e afins): chaves por tenant, cifra, índice cego e o serviço de documento.
 *
 * <p>Falha fechada. Sem {@link TenantKeyProvider} a aplicação NÃO sobe: subir sem chave significaria
 * recusar todo cadastro com documento em produção, ou, pior, alguém "contornar" gravando em claro. O
 * único provider existente é o de desenvolvimento ({@code ziipsan.crypto.provider=dev-env}, chave mestra
 * única derivada por HKDF), que é aceito SÓ numa lista fechada de perfis (P0.17): dev, local, test,
 * migrate e worker — os perfis que o código de fato usa com dev-env em desenvolvimento e teste. Sem
 * perfil ativo (contexto de teste unitário) também é aceito. Qualquer outro perfil (prod, staging,
 * production-eu, ...) é recusado: a regra antiga ("só recusa perfil com prod no nome") aceitava
 * dev-env em staging, homologação ou qualquer perfil novo por esquecimento. O provider de
 * produção (Vault, envelope com KEK) ainda não existe; ver {@link TenantKeyProvider}.
 *
 * <p>Chave do dev-env: {@code ziipsan.crypto.dev-master-key} (só em teste, gerada pelo próprio teste) ou a
 * variável de ambiente {@value EnvDerivedTenantKeyProvider#ENV_VAR}. Nunca em arquivo versionado.
 */
@Configuration
public class CryptoConfig {

    public static final String PROVIDER_PROPERTY = "ziipsan.crypto.provider";
    public static final String DEV_MASTER_KEY_PROPERTY = "ziipsan.crypto.dev-master-key";
    public static final String DEV_ENV = "dev-env";

    /** P0.17: lista fechada de perfis que podem usar o provider dev-env. */
    static final Set<String> DEV_ENV_ALLOWED_PROFILES = Set.of("dev", "local", "test", "migrate", "worker");

    @Bean
    @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = DEV_ENV)
    public TenantKeyProvider devEnvTenantKeyProvider(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        String refused = Arrays.stream(profiles)
                .filter(p -> !DEV_ENV_ALLOWED_PROFILES.contains(p.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        if (refused != null) {
            throw new IllegalStateException("ziipsan.crypto.provider=dev-env é só para desenvolvimento e teste e foi "
                    + "recusado no perfil " + refused + ": uma chave mestra única em variável de "
                    + "ambiente não protege dado real. Produção exige o provider de chaves do Vault.");
        }
        String key = environment.getProperty(DEV_MASTER_KEY_PROPERTY);
        return key != null && !key.isBlank()
                ? EnvDerivedTenantKeyProvider.fromBase64(key)
                : EnvDerivedTenantKeyProvider.fromEnvironment();
    }

    @Bean
    public FieldCipher fieldCipher(ObjectProvider<TenantKeyProvider> keys, Environment environment) {
        return new FieldCipher(requireProvider(keys, environment));
    }

    @Bean
    public BlindIndex blindIndex(ObjectProvider<TenantKeyProvider> keys, Environment environment) {
        return new BlindIndex(requireProvider(keys, environment));
    }

    @Bean
    public FieldProtector fieldProtector(FieldCipher cipher, BlindIndex index) {
        return new FieldProtector(cipher, index);
    }

    @Bean
    public CustomerDocumentService customerDocumentService(FieldProtector protector, CustomerRepository customers,
                                                           AuditLogRepository auditLog) {
        return new CustomerDocumentService(protector, customers, auditLog);
    }

    private static TenantKeyProvider requireProvider(ObjectProvider<TenantKeyProvider> keys, Environment environment) {
        TenantKeyProvider provider = keys.getIfAvailable();
        if (provider == null) {
            String configured = environment.getProperty(PROVIDER_PROPERTY, "");
            throw new IllegalStateException("Nenhum provedor de chaves de tenant configurado ("
                    + PROVIDER_PROPERTY + "=" + (configured.isBlank() ? "<vazio>" : configured) + "). Sem chave a "
                    + "aplicação não sobe, para não gravar CPF/CNPJ sem cifra. Em desenvolvimento use "
                    + PROVIDER_PROPERTY + "=" + DEV_ENV + " com " + EnvDerivedTenantKeyProvider.ENV_VAR
                    + "; o provider de produção (Vault) ainda não existe.");
        }
        return provider;
    }
}
