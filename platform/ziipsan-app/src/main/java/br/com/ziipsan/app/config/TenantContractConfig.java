package br.com.ziipsan.app.config;

import br.com.ziipsan.platform.contract.ObjectStorageSaaSContractDocumentResolver;
import br.com.ziipsan.platform.contract.SaaSContractDocumentResolver;
import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Fiacao da area do tenant do contrato SaaS (P2.14).
 *
 * <p>O armazenamento de documentos SaaS e dedicado (bucket proprio
 * sobre a mesma raiz local da P1.23), para nao misturar documento de
 * contrato com anexo de chamado: o resolvedor recebe o bean
 * qualificado, nao o {@code ticketObjectStorage}. Sem
 * {@code local-root}, cai em {@code <tmp>/ziipsan-objects}, como os
 * anexos; o S3 e outra frente (mesma interface da P0.45).
 */
@Configuration
public class TenantContractConfig {

    @Bean("saasContractObjectStorage")
    public ObjectStorage saasContractObjectStorage(ZiipsanProperties properties) {
        String root = properties.storage().localRoot();
        Path path = root == null || root.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "ziipsan-objects")
                : Path.of(root);
        return new LocalFileObjectStorage(path);
    }

    @Bean
    public SaaSContractDocumentResolver saasContractDocumentResolver(
            @Qualifier("saasContractObjectStorage") ObjectStorage storage,
            ZiipsanProperties properties, javax.sql.DataSource dataSource) {
        return new ObjectStorageSaaSContractDocumentResolver(storage, "ziipsan-saas-docs",
                new org.springframework.jdbc.core.JdbcTemplate(dataSource));
    }
}
