package br.com.ziipsan.app;

import br.com.ziipsan.commercial.collection.domain.CanonicalPayment;
import br.com.ziipsan.commercial.collection.repository.PaymentRepository;
import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova de escrita/leitura do modelo canónico (decisão 75, PD-1) com JPA REAL
 * (Hibernate, não fake): a {@code SessionFactory} constrói-se sobre o schema
 * migrado até à V112 com {@code hbm2ddl=validate} — só a entidade canónica é
 * gerida aqui — e o repositório retargetado ({@code PaymentRepository} sobre
 * {@code CanonicalPayment}) persiste e lê nos dois canais (Pix e boleto/retorno).
 *
 * <p>É o "teste de contexto focado no modelo canónico" do lote: NÃO arranca o
 * contexto da aplicação ({@code ZiipsanApplication}), que está bloqueado pela
 * P-APPDEVICE-01 (a pendência R40-A: {@code AppDeviceRepository} sem bean — o
 * {@code EnableJpaRepositories} da app não inclui {@code platform.field} nesta
 * base; ver o RELATÓRIO). Nada aqui depende desse contexto.
 */
class CanonicalPaymentJpaTest {

    private static final String SCHEMA = "conv_jpa_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AGREEMENT = UUID.fromString("a2000000-0000-4000-8000-000000000001");

    private static SessionFactory sessionFactory;
    private static DataSource migratorDs;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável " + name + " não definida");
        }
        return value;
    }

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ZIIPSAN_IT_JDBC_URL",
                "jdbc:postgresql://127.0.0.1:55439/ziipsan");
        String appUser = System.getenv().getOrDefault("ZIIPSAN_IT_DB_USER", "ziipsan_app");
        String appPassword = requireEnv("ZIIPSAN_IT_DB_PASSWORD");
        String migratorUser = System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator");
        String migratorPassword = requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD");
        migratorDs = new DriverManagerDataSource(url, migratorUser, migratorPassword);

        new CoreMigrationRunner(migratorDs).run(null);
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + SCHEMA + " AUTHORIZATION ziipsan_migrator");
            st.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + SCHEMA
                    + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + SCHEMA
                    + " GRANT USAGE, SELECT ON SEQUENCES TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + SCHEMA
                    + " GRANT EXECUTE ON FUNCTIONS TO ziipsan_app");
        }
        var result = new TenantMigrator(migratorDs).migrate(SCHEMA, TENANT);
        assertThat(result.success).as("migração até à V112 conclui").isTrue();
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + SCHEMA
                    + " TO ziipsan_app");
            st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA " + SCHEMA + " TO ziipsan_app");
            st.execute("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA " + SCHEMA + " TO ziipsan_app");
        }

        // Convénio para os pagamentos de boleto (a FK exige a linha real).
        try (Connection c = migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".collection_agreements"
                            + " (id, tenant_id, bank_code, name, agreement_code, document_kind,"
                            + " barcode_layout, valid_from) VALUES (?, ?, '001', 'Convenio Teste',"
                            + " 'AG1', 'BANK_SLIP', '{}'::jsonb, DATE '2026-01-01')")) {
                ps.setObject(1, AGREEMENT);
                ps.setObject(2, TENANT);
                ps.executeUpdate();
            }
            c.commit();
        }

        // SessionFactory só com a entidade canónica, validate contra o schema migrado.
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(url + "?currentSchema=" + SCHEMA);
        hikari.setUsername(appUser);
        hikari.setPassword(appPassword);
        hikari.setMaximumPoolSize(2);
        hikari.setConnectionInitSql("SELECT set_config('app.current_tenant', '" + TENANT + "', false)");
        HikariDataSource ds = new HikariDataSource(hikari);
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", ds)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        sources.addAnnotatedClass(CanonicalPayment.class);
        Metadata metadata = sources.getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (migratorDs != null) {
            try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    private static CanonicalPayment pixPayment() {
        CanonicalPayment payment = new CanonicalPayment();
        payment.setTenantId(TENANT);
        payment.setChannel("PIX");
        payment.setGrossAmount(new BigDecimal("100.00"));
        payment.setSegregatedTaxAmount(BigDecimal.ZERO);
        payment.setWithheldAmount(BigDecimal.ZERO);
        payment.setNetAmount(new BigDecimal("100.00"));
        payment.setPaidOn(LocalDate.of(2026, 10, 5));
        payment.setCollectionMonth(LocalDate.of(2026, 10, 1));
        payment.setStatus("RECEBIDO");
        payment.setTxid("txid-teste-jpa-1");
        return payment;
    }

    private static CanonicalPayment boletoPayment() {
        CanonicalPayment payment = new CanonicalPayment();
        payment.setTenantId(TENANT);
        payment.setAgreementId(AGREEMENT);
        payment.setAmount(new BigDecimal("250.50"));
        payment.setPaidOn(LocalDate.of(2026, 10, 6));
        payment.setStatus("CLASSIFICADO");
        payment.setIdentified(true);
        payment.setDocumentType("BILL");
        payment.setDocumentId(UUID.fromString("b0000000-0000-4000-8000-000000000002"));
        payment.setIdempotencyKey("jpa-boleto-1");
        payment.setChannel("BOLETO");
        payment.setGrossAmount(new BigDecimal("250.50"));
        payment.setSegregatedTaxAmount(BigDecimal.ZERO);
        payment.setWithheldAmount(new BigDecimal("5.00"));
        payment.setNetAmount(new BigDecimal("245.50"));
        payment.setCollectionMonth(LocalDate.of(2026, 10, 1));
        return payment;
    }

    @Test
    @DisplayName("a SessionFactory constrói-se com validate: o mapeamento canónico casa com o schema V112")
    void sessionFactoryBuilds() {
        assertThat(sessionFactory).isNotNull();
        assertThat(sessionFactory.isOpen()).isTrue();
    }

    @Test
    @DisplayName("JPA real: INSERT via repositório canónico num canal Pix e leitura de volta")
    void pixWriteAndReadViaRepository() {
        EntityManager em = sessionFactory.createEntityManager();
        PaymentRepository repo = new JpaRepositoryFactory(em).getRepository(PaymentRepository.class);
        em.getTransaction().begin();
        CanonicalPayment saved = repo.save(pixPayment());
        em.getTransaction().commit();
        assertThat(saved.getId()).isNotNull();

        em.clear();
        CanonicalPayment read = repo.findById(saved.getId()).orElseThrow();
        assertThat(read.getChannel()).isEqualTo("PIX");
        assertThat(read.getStatus()).isEqualTo("RECEBIDO");
        assertThat(read.getGrossAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(read.getNetAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(read.getTxid()).isEqualTo("txid-teste-jpa-1");
        // PD-4: um Pix escreve sem convénio, sem amount, sem identified e sem chave.
        assertThat(read.getAgreementId()).isNull();
        assertThat(read.getAmount()).isNull();
        assertThat(read.getIdentified()).isNull();
        assertThat(read.getIdempotencyKey()).isNull();
        assertThat(read.isPayerPublicNature()).isFalse();
        em.close();
    }

    @Test
    @DisplayName("JPA real: INSERT via repositório canónico num canal boleto/retorno e leitura de volta")
    void boletoWriteAndReadViaRepository() {
        EntityManager em = sessionFactory.createEntityManager();
        PaymentRepository repo = new JpaRepositoryFactory(em).getRepository(PaymentRepository.class);
        em.getTransaction().begin();
        CanonicalPayment saved = repo.save(boletoPayment());
        em.getTransaction().commit();

        em.clear();
        CanonicalPayment read = repo.findById(saved.getId()).orElseThrow();
        assertThat(read.getChannel()).isEqualTo("BOLETO");
        assertThat(read.getStatus()).isEqualTo("CLASSIFICADO");
        assertThat(read.getAgreementId()).isEqualTo(AGREEMENT);
        assertThat(read.getAmount()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(read.getIdentified()).isTrue();
        assertThat(read.getIdempotencyKey()).isEqualTo("jpa-boleto-1");
        assertThat(read.getDocumentType()).isEqualTo("BILL");
        assertThat(read.getWithheldAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(read.getNetAmount()).isEqualByComparingTo(new BigDecimal("245.50"));
        em.close();
    }

    @Test
    @DisplayName("JPA real: boleto sem convénio é recusado pelo CHECK (PD-4); Pix sem obrigações escreve")
    void boletoWithoutAgreementRejectedByCheck() {
        EntityManager em = sessionFactory.createEntityManager();
        PaymentRepository repo = new JpaRepositoryFactory(em).getRepository(PaymentRepository.class);
        CanonicalPayment invalid = boletoPayment();
        invalid.setAgreementId(null);
        assertThatThrownBy(() -> {
            em.getTransaction().begin();
            repo.save(invalid);
            em.getTransaction().commit();
        }).isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_payments_boleto_agreement");
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }

    @Test
    @DisplayName("JPA real: status fora do vocabulário unificado é recusado pelo CHECK (PD-2)")
    void statusOutsideVocabularyRejected() {
        EntityManager em = sessionFactory.createEntityManager();
        PaymentRepository repo = new JpaRepositoryFactory(em).getRepository(PaymentRepository.class);
        CanonicalPayment invalid = pixPayment();
        invalid.setStatus("INVENTADO");
        assertThatThrownBy(() -> {
            em.getTransaction().begin();
            repo.save(invalid);
            em.getTransaction().commit();
        }).isInstanceOf(PersistenceException.class)
                .hasMessageContaining("chk_payments_status_unified");
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }
}
