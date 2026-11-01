package br.com.ziipsan.app;

import br.com.ziipsan.app.config.CryptoConfig;
import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.security.crypto.BlindIndex;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.FieldProtector;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuração da cifra: sobe com o provider de desenvolvimento e uma chave, recusa esse provider em
 * perfil de produção e não sobe sem provider (falha fechada).
 */
class CryptoConfigTest {

    private static final String KEY = randomKey();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CryptoConfig.class)
            .withBean(CustomerRepository.class, () -> Mockito.mock(CustomerRepository.class))
            .withBean(AuditLogRepository.class, () -> Mockito.mock(AuditLogRepository.class));

    @Test
    @DisplayName("dev-env com chave: cifra, índice cego, protetor e serviço de documento disponíveis e funcionando")
    void devEnvWithKeyStarts() {
        runner.withPropertyValues("ziipsan.crypto.provider=dev-env", "ziipsan.crypto.dev-master-key=" + KEY)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed()
                            .hasSingleBean(TenantKeyProvider.class)
                            .hasSingleBean(FieldCipher.class)
                            .hasSingleBean(BlindIndex.class)
                            .hasSingleBean(FieldProtector.class)
                            .hasSingleBean(CustomerDocumentService.class);
                    FieldProtector protector = ctx.getBean(FieldProtector.class);
                    UUID tenant = UUID.randomUUID();
                    var value = protector.protect(tenant, CustomerDocumentService.FIELD, "52998224725", "***.982.247-**");
                    assertThat(value.ciphertext()).doesNotContain("52998224725");
                    assertThat(value.hash()).hasSize(64).doesNotContain("52998224725");
                    assertThat(protector.reveal(tenant, CustomerDocumentService.FIELD, value.ciphertext()))
                            .isEqualTo("52998224725");
                });
    }

    @Test
    @DisplayName("dev-env em perfil de produção: a aplicação não sobe")
    void devEnvIsRefusedInProduction() {
        for (String profile : new String[]{"prod", "production-eu"}) {
            runner.withPropertyValues("ziipsan.crypto.provider=dev-env", "ziipsan.crypto.dev-master-key=" + KEY)
                    .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        assertThat(ctx.getStartupFailure()).rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("recusado no perfil " + profile);
                    });
        }
    }

    @Test
    @DisplayName("sem provider (ou com um desconhecido): a aplicação não sobe e diz por quê")
    void noProviderFailsClosed() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nenhum provedor de chaves de tenant configurado")
                    .hasMessageContaining("<vazio>");
        });
        runner.withPropertyValues("ziipsan.crypto.provider=vault").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .hasMessageContaining("ziipsan.crypto.provider=vault");
        });
    }

    @Test
    @DisplayName("dev-env sem chave nenhuma (nem propriedade nem variável de ambiente): não sobe")
    void devEnvWithoutKeyFails() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("ZIIPSAN_DEV_MASTER_KEY") == null,
                "a variável de ambiente está definida nesta máquina");
        runner.withPropertyValues("ziipsan.crypto.provider=dev-env").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("ZIIPSAN_DEV_MASTER_KEY");
        });
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
