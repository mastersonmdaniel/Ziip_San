package br.com.sigsan.app.config;

import br.com.sigsan.commercial.registry.repository.CustomerRepository;
import br.com.sigsan.commercial.registry.service.CustomerDocumentService;
import br.com.sigsan.core.audit.AuditLogRepository;
import br.com.sigsan.security.crypto.BlindIndex;
import br.com.sigsan.security.crypto.FieldCipher;
import br.com.sigsan.security.crypto.FieldProtector;
import br.com.sigsan.security.crypto.TenantKeyProvider;
import br.com.sigsan.security.crypto.dev.EnvDerivedTenantKeyProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;

/**
 * Cifra de campo (CPF/CNPJ e afins): chaves por tenant, cifra, índice cego e o serviço de documento.
 *
 * <p>Falha fechada. Sem {@link TenantKeyProvider} a aplicação NÃO sobe: subir sem chave significaria
 * recusar todo cadastro com documento em produção, ou, pior, alguém "contornar" gravando em claro. O
 * único provider existente é o de desenvolvimento ({@code sigsan.crypto.provider=dev-env}, chave mestra
 * única derivada por HKDF), que é recusado em qualquer perfil cujo nome contenha "prod". O provider de
 * produção (Vault, envelope com KEK) ainda não existe; ver {@link TenantKeyProvider}.
 *
 * <p>Chave do dev-env: {@code sigsan.crypto.dev-master-key} (só em teste, gerada pelo próprio teste) ou a
 * variável de ambiente {@value EnvDerivedTenantKeyProvider#ENV_VAR}. Nunca em arquivo versionado.
 */
@Configuration
public class CryptoConfig {

    public static final String PROVIDER_PROPERTY = "sigsan.crypto.provider";
    public static final String DEV_MASTER_KEY_PROPERTY = "sigsan.crypto.dev-master-key";
    public static final String DEV_ENV = "dev-env";

    @Bean
    @ConditionalOnProperty(name = PROVIDER_PROPERTY, havingValue = DEV_ENV)
    public TenantKeyProvider devEnvTenantKeyProvider(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        if (Arrays.stream(profiles).anyMatch(p -> p.toLowerCase(Locale.ROOT).contains("prod"))) {
            throw new IllegalStateException("sigsan.crypto.provider=dev-env é só para desenvolvimento e teste e foi "
                    + "recusado no perfil " + String.join(",", profiles) + ": uma chave mestra única em variável de "
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
