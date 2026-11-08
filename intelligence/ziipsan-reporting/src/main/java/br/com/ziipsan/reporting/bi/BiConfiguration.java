package br.com.ziipsan.reporting.bi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ED-47: fiação do BI embarcado dentro do módulo de relatórios.
 *
 * <p>O que esta configuração monta sozinha: a guarda de réplica (com o
 * endpoint do primário vindo de {@code ziipsan.bi.primary-endpoint},
 * no formato {@code host:porta/banco} da configuração da aplicação) e a
 * política de acesso. O que continua dependendo da integração
 * (declarado, como na P3.22):
 * <ul>
 *   <li>uma implementação de {@link BiAccessAuditPort} ligada à trilha
 *       do core — sem ela o contexto NÃO sobe com a política (o bean da
 *       política exige a porta); não há padrão silencioso;</li>
 *   <li>a implementação JDBC de {@link BiRoleProvisioner} sobre o
 *       {@code DataSource} da réplica e a troca do
 *       {@link InMemoryBiRoleStore} pela persistência definitiva quando
 *       a reserva de migration sair;</li>
 *   <li>o contêiner do Superset (infra/superset), o cliente OIDC no
 *       Keycloak e os ativos de marca da P0.34.</li>
 * </ul>
 */
@Configuration
public class BiConfiguration {

    @Bean
    ReplicaOnlyGuard replicaOnlyGuard(
            @Value("${ziipsan.bi.primary-endpoint}") String primaryEndpoint) {
        String[] parts = primaryEndpoint.split(":", 2);
        if (parts.length != 2 || !parts[1].contains("/")) {
            throw new IllegalArgumentException(
                    "ziipsan.bi.primary-endpoint deve ser host:porta/banco");
        }
        int slash = parts[1].indexOf('/');
        int port = Integer.parseInt(parts[1].substring(0, slash));
        String database = parts[1].substring(slash + 1);
        return new ReplicaOnlyGuard(new SupersetDataSource.Endpoint(parts[0], port, database));
    }

    @Bean
    BiAccessPolicy biAccessPolicy(BiAccessAuditPort auditPort) {
        return new BiAccessPolicy(auditPort);
    }

    @Bean
    @ConditionalOnMissingBean(BiRoleStore.class)
    InMemoryBiRoleStore inMemoryBiRoleStore() {
        return new InMemoryBiRoleStore();
    }
}
