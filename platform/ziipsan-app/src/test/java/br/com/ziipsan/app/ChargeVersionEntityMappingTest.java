package br.com.ziipsan.app;

import br.com.ziipsan.commercial.billing.domain.ChargeVersionEntity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R50-1-01 (Rodada 50, menor, não bloqueante): guarda de regressão mínima da
 * cura P-CHARGEVERSION-01.
 *
 * <p>A cura renomeou a coluna do número da versão tarifária de {@code version}
 * para {@code version_number} na entidade e no DDL: {@code version} já é a
 * coluna do lock otimista herdada de {@code BaseEntity}, e mapear as duas
 * para a mesma coluna impede o Hibernate de construir a SessionFactory
 * ({@code MappingException}: "Column 'version' is duplicated in mapping for
 * entity '...ChargeVersionEntity'"). Este teste prova exatamente esse ponto:
 * a SessionFactory constrói-se e o mapeamento carrega.
 *
 * <p>Desenho deliberadamente mínimo, na linha da prova dirigida da cura
 * (R39-A, item-05) e do precedente {@code CanonicalPaymentJpaTest}:
 * <ul>
 *   <li>NÃO arranca o contexto da aplicação ({@code ZiipsanApplication}),
 *       que está bloqueado pela P-ACCESSCONTROL-01 ({@code AccessControlService}
 *       sem bean). A P-APPDEVICE-01 já não bloqueia: o pacote
 *       {@code br.com.ziipsan.platform.field} entrou no
 *       {@code EnableJpaRepositories} da app pelo hunk R40-A do pouso
 *       {@code b3cc1216}, e a P-APPDEVICE-01 está formalmente fechada
 *       (Rodada 42). A SessionFactory constrói-se diretamente com o
 *       Hibernate, sem Spring.</li>
 *   <li>NÃO precisa de PostgreSQL: {@code hbm2ddl.auto=none} e
 *       {@code hibernate.boot.allow_jdbc_metadata_access=false} com o
 *       dialecto explícito, por isso a construção do metamodelo não abre
 *       nenhuma ligação JDBC. A correspondência mapeamento &lt;-&gt; DDL
 *       continua provada pela prova DDL da cura (R39-A, item-05,
 *       {@code prova-ddl-camada1.log}), não por este teste.</li>
 *   <li>NÃO toca código de produção nem nenhum POM: só a entidade
 *       {@code ChargeVersionEntity} é registada no {@code MetadataSources}.</li>
 * </ul>
 *
 * <p>Controle negativo: reverter {@code @Column(name = "version_number")}
 * para {@code "version"} na entidade faz este teste falhar na construção da
 * SessionFactory com {@code MappingException} (provado em
 * {@code prova-r50-1-01-controlo-negativo.log}, no pacote R50-A).
 */
class ChargeVersionEntityMappingTest {

    private static SessionFactory sessionFactory;

    @BeforeAll
    static void buildSessionFactory() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        sources.addAnnotatedClass(ChargeVersionEntity.class);
        Metadata metadata = sources.getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
    }

    @AfterAll
    static void closeSessionFactory() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    @DisplayName("a SessionFactory constrói-se: o mapeamento de ChargeVersionEntity carrega sem MappingException (P-CHARGEVERSION-01)")
    void sessionFactoryBuilds() {
        assertThat(sessionFactory).isNotNull();
        assertThat(sessionFactory.isOpen()).isTrue();
    }

    @Test
    @DisplayName("versionNumber mapeia para a coluna version_number, distinta da coluna version do lock otimista herdado de BaseEntity")
    void versionNumberMapsToItsOwnColumn() {
        EntityPersister persister = sessionFactory.unwrap(SessionFactoryImplementor.class)
                .getMappingMetamodel()
                .getEntityDescriptor(ChargeVersionEntity.class);
        assertThat(persister.getTableName()).contains("charge_versions");
        assertThat(persister.getPropertyColumnNames("versionNumber")).containsExactly("version_number");
        assertThat(persister.getPropertyColumnNames("version")).containsExactly("version");
    }
}
