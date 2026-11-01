package br.com.ziipsan.platform.config;

import br.com.ziipsan.platform.tenant.MultiTenantConnectionProvider;
import br.com.ziipsan.platform.tenant.TenantSchemaResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

// Sem @EnableJpaAuditing: apontava para um bean "auditorAware" inexistente (a aplicacao nao subiria) e
// nenhuma entidade usa @CreatedBy/AuditingEntityListener. Quem registra autoria e o trigger de
// auditoria, a partir da GUC app.current_user.
@Configuration
@EnableTransactionManagement
public class PlatformConfig {

    // O tenant da requisicao vem do token (JwtTenantFilter, na cadeia do Spring Security), nao mais de
    // um cabecalho enviado pelo cliente (achado C3 do PDCA de 12/10/2026).
    //
    // P0.6: SEM @DependsOn("databaseMigrator"). O bean databaseMigrator só existe no
    // perfil migrate; com o DependsOn, a api (sem o perfil) falharia por bean ausente.
    // A ordem agora é externa: o job migrate roda ANTES da api (scripts/run-app.sh,
    // entrypoint da imagem). A api assume o schema já migrado — ela nunca migra sozinha.
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            MultiTenantConnectionProvider tenantConnectionProvider,
            TenantSchemaResolver tenantSchemaResolver) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("br.com.ziipsan");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        // NAO usar "validate" aqui. Com schema por tenant, o Hibernate valida no boot, antes de
        // existir contexto de tenant, contra o schema public, onde as tabelas de negocio nao
        // estao: a aplicacao nao subiria. O mapeamento e validado contra um tenant provisionado
        // pelo FreshInstallTest (platform/ziipsan-app).
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "none");
        jpaProperties.setProperty("hibernate.show_sql", "false");
        jpaProperties.setProperty("hibernate.format_sql", "true");
        jpaProperties.setProperty("hibernate.jdbc.batch_size", "50");
        jpaProperties.setProperty("hibernate.order_inserts", "true");
        jpaProperties.setProperty("hibernate.order_updates", "true");
        jpaProperties.setProperty("hibernate.jdbc.fetch_size", "50");
        jpaProperties.setProperty("hibernate.generate_statistics", "false");
        // Cache de segundo nivel desligado: a configuracao anterior apontava para
        // org.hibernate.cache.redis.RedisRegionFactory, que nao esta nas dependencias do projeto, e a
        // aplicacao falharia na subida. Alem disso, cache compartilhado entre tenants exige a chave
        // do tenant na regiao; ligar de novo so depois de testar o isolamento.
        jpaProperties.setProperty("hibernate.cache.use_second_level_cache", "false");
        jpaProperties.setProperty("hibernate.cache.use_query_cache", "false");
        em.setJpaProperties(jpaProperties);

        // Provider e resolver vao como INSTANCIAS (beans do Spring), nao como nome de classe: pelo
        // nome o Hibernate tenta instanciar com construtor vazio, que o provider nao tem (ele
        // precisa do DataSource), e a aplicacao nao subiria. A estrategia de multi-tenancy e
        // inferida da presenca do provider.
        em.getJpaPropertyMap().put("hibernate.multi_tenant_connection_provider", tenantConnectionProvider);
        em.getJpaPropertyMap().put("hibernate.tenant_identifier_resolver", tenantSchemaResolver);
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}