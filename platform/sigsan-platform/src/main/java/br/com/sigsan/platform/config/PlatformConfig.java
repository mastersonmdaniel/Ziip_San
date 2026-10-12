package br.com.sigsan.platform.config;

import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.core.tenant.TenantFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableTransactionManagement
public class PlatformConfig {

    @Bean
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(tenantFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("br.com.sigsan");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        jpaProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // NAO usar "validate" aqui. Com schema por tenant, o Hibernate valida no boot, antes de
        // existir contexto de tenant, contra o schema public, onde as tabelas de negocio nao
        // estao: a aplicacao nao subiria. O mapeamento e validado contra um tenant provisionado
        // pelo FreshInstallTest (platform/sigsan-app).
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
        // No Hibernate 6 a estrategia SCHEMA e inferida da presenca do provider; "hibernate.multi_tenant"
        // foi removida.
        jpaProperties.setProperty("hibernate.multi_tenant_connection_provider", "br.com.sigsan.platform.tenant.MultiTenantConnectionProvider");
        jpaProperties.setProperty("hibernate.tenant_identifier_resolver", "br.com.sigsan.platform.tenant.TenantSchemaResolver");

        em.setJpaProperties(jpaProperties);
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}