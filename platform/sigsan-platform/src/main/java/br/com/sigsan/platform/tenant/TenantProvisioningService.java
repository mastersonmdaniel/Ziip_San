package br.com.sigsan.platform.tenant;

import br.com.sigsan.core.json.Json;
import br.com.sigsan.core.domain.TenantAwareEntity;
import br.com.sigsan.core.tenant.TenantContextHolder;
import br.com.sigsan.core.audit.AuditLog;
import br.com.sigsan.core.audit.AuditLogRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private static final String PUBLIC_SCHEMA = "public";
    private static final String APP_ROLE = "sigsan_app";
    private static final String MIGRATOR_ROLE = "sigsan_migrator";
    private static final String TENANT_MIGRATIONS_LOCATION = "db/migration/tenant";

    /**
     * Schemas que o PostgreSQL reserva. Um tenant jamais pode ocupar um deles.
     *
     * O mais perigoso e "public": as tabelas de tenant seriam criadas ao lado de tenant_catalog e
     * audit_log, e um rollback de provisioning executaria DROP SCHEMA public CASCADE, apagando o
     * banco inteiro.
     */
    private static final Set<String> RESERVED_SCHEMAS = Set.of(
            "public",
            "information_schema",
            "pg_catalog",
            "pg_toast",
            "pg_temp",
            "pg_toast_temp");

    /**
     * Hash bcrypt completo ({@code $2a$}, {@code $2b$} ou {@code $2y$}, custo com dois digitos, 22
     * caracteres de sal e 31 de hash). A versao anterior prefixava "$2a$12$" ao valor recebido: um hash
     * ja completo virava "$2a$12$$2a$12$...", que nenhum verificador aceita, e o admin do tenant
     * nascia sem conseguir entrar.
     */
    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRepository auditLogRepository;
    private final KeycloakAdminService keycloakAdminService;

    private final Map<String, Boolean> schemaInitializedCache = new ConcurrentHashMap<>();

    /**
     * DDL de tenant (CREATE SCHEMA, migrations, GRANT) roda com a role de migracao.
     *
     * A role da aplicacao e NOSUPERUSER e NOCREATEDB de proposito, para que a RLS valha. Se o
     * DDL usasse a mesma dataSource, "CREATE SCHEMA ... AUTHORIZATION sigsan_migrator" falharia
     * com "must be member of role sigsan_migrator", porque sigsan_app nao e membro dela nem
     * pode criar schema.
     */
    private final DataSource migratorDataSource;

    private JdbcTemplate migratorJdbc() {
        return new JdbcTemplate(migratorDataSource);
    }

    public TenantProvisioningResult provisionTenant(TenantProvisioningRequest request) {
        String schemaName;
        try {
            schemaName = sanitizeSchemaName(request.getSchemaName());
        } catch (IllegalArgumentException e) {
            // Entrada rejeitada nao e falha de provisionamento: e um erro de validacao do
            // chamador. Deixar a excecao escapar e melhor que devolver "failure", que seria
            // lido como erro de infra e tentaria rollback de um schema que nunca foi criado.
            throw e;
        }
        // Validado antes de qualquer DDL, pelo mesmo motivo: hash invalido descoberto so no seed do
        // admin deixaria schema, migrations e catalogo feitos para um tenant sem acesso.
        requireBcryptHash(request.getAdminPasswordHash());
        UUID tenantId = request.getTenantId() != null ? request.getTenantId() : UUID.randomUUID();

        // O rollback so pode remover o que ESTA invocacao criou. Reprovisionar um tenant
        // existente e operacao legitima, e Keycloak fora do ar no meio do caminho e rotina.
        // Sem esta distincao, o catch executava DROP SCHEMA CASCADE sobre um tenant em
        // producao com consumidores e ordens de servico, e devolvia "failure" como se fosse
        // um erro recuperavel.
        boolean schemaCreatedNow = false;

        try {
            schemaCreatedNow = createSchema(schemaName);

            runTenantMigrations(schemaName, tenantId);
            grantTenantTablePrivileges(schemaName);

            registerTenantInCatalog(tenantId, schemaName, request);

            keycloakAdminService.createTenantRealm(tenantId, schemaName, request);

            initializeTenantData(schemaName, tenantId, request);

            schemaInitializedCache.put(schemaName, true);

            writeProvisioningAudit(tenantId, schemaName, request.getDisplayName());

            return TenantProvisioningResult.success(tenantId, schemaName);
        } catch (Exception e) {
            log.error("Failed to provision tenant: {}", schemaName, e);
            if (schemaCreatedNow) {
                try {
                    dropSchema(schemaName);
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback schema creation for {}", schemaName, rollbackEx);
                    throw new IllegalStateException(
                            "Provisionamento falhou e o rollback tambem. Schema " + schemaName
                                    + " pode estar orfao e exige intervencao manual.", rollbackEx);
                }
            } else {
                log.warn("Schema {} ja existia; nenhum rollback executado para preservar "
                        + "dados do tenant existente", schemaName);
            }
            return TenantProvisioningResult.failure(e.getMessage());
        }
    }

    /**
     * Cria o schema do tenant e devolve true se ele foi criado agora.
     *
     * A verificacao ANTES do CREATE e o que permite ao rollback saber se o schema e seguro de
     * remover. Consultar depois nao serviria: um CREATE ... IF NOT EXISTS ja skipped.
     */
    private boolean createSchema(String schemaName) {
        Integer existed = migratorJdbc().queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        boolean alreadyExisted = existed != null && existed > 0;

        // IF NOT EXISTS porque reprovisionar um tenant existente e operacao legitima: o
        // catalogo tem ON CONFLICT e o Flyway tem baseline. Sem isso, o segundo provisionamento
        // do mesmo tenant falharia com "schema already exists".
        String sql = String.format("CREATE SCHEMA IF NOT EXISTS %s AUTHORIZATION %s",
                schemaName, MIGRATOR_ROLE);
        migratorJdbc().execute(sql);

        String[] grants = {
            // USAGE basta para a aplicacao resolver nomes. CREATE no schema permitiria a
            // aplicacao criar objetos, o que nao e necessario em runtime.
            String.format("GRANT USAGE ON SCHEMA %s TO %s", schemaName, APP_ROLE),
            // Default privileges valem para objetos criados pela MESMA role que executa o
            // ALTER. Como as migrations rodam como sigsan_migrator, e essa role que precisa
            // conceder. Sem FORCE, quem cria a tabela e o proprio dono, entao o default e
            // apenas uma camada extra de seguranca.
            String.format("ALTER DEFAULT PRIVILEGES FOR ROLE %s IN SCHEMA %s "
                    + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %s",
                    MIGRATOR_ROLE, schemaName, APP_ROLE),
            String.format("ALTER DEFAULT PRIVILEGES FOR ROLE %s IN SCHEMA %s "
                    + "GRANT USAGE, SELECT ON SEQUENCES TO %s", MIGRATOR_ROLE, schemaName, APP_ROLE),
            String.format("ALTER DEFAULT PRIVILEGES FOR ROLE %s IN SCHEMA %s "
                    + "GRANT EXECUTE ON FUNCTIONS TO %s", MIGRATOR_ROLE, schemaName, APP_ROLE)
        };

        for (String grant : grants) {
            migratorJdbc().execute(grant);
        }

        return !alreadyExisted;
    }

    /**
     * Concede privilegios nas tabelas do tenant depois que as migrations rodaram.
     *
     * ALTER DEFAULT PRIVILEGES so vale para objetos criados depois do ALTER, e depende de a
     * migration ter rodado com a mesma role que concedeu. Fazer o GRANT explicito sobre as
     * tabelas existentes remove essa dependencia: mesmo que uma tabela seja criada por outra
     * role, a aplicacao recebe o privilegio.
     */
    private void grantTenantTablePrivileges(String schemaName) {
        String[] grants = {
            String.format("GRANT USAGE ON SCHEMA %s TO %s", schemaName, APP_ROLE),
            String.format("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %s TO %s",
                    schemaName, APP_ROLE),
            String.format("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %s TO %s",
                    schemaName, APP_ROLE),
            String.format("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA %s TO %s", schemaName, APP_ROLE)
        };

        for (String grant : grants) {
            migratorJdbc().execute(grant);
        }
    }

    private void runTenantMigrations(String schemaName, UUID tenantId) {
        // defaultSchema define onde o Flyway cria a tabela de histórico. As extensões
        // (uuid-ossp, pgcrypto) vivem em public, e as migrations chamam uuid_generate_v4(),
        // então o search_path precisa incluir public junto do schema do tenant. Sem isso,
        // toda migration de tenant falha com "function uuid_generate_v4() does not exist".
        //
        // O placeholder tenant_id permite que as migrations de seed gravem com o tenant
        // correto: dentro de uma migration não há contexto de tenant (a GUC app.current_tenant
        // está vazia), então current_tenant_id() devolve NULL. O checksum do Flyway é
        // calculado sobre o texto original, antes da substituição, então o valor não o altera.
        Flyway flyway = Flyway.configure()
                .dataSource(migratorDataSource)
                .schemas(schemaName, PUBLIC_SCHEMA)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .placeholders(Map.of("tenant_id", tenantId.toString()))
                .locations(TENANT_MIGRATIONS_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .table("flyway_schema_history")
                .load();

        flyway.migrate();

        // O Flyway nao falha quando o location nao tem migration: ele simplesmente nao
        // aplica nada e devolve sucesso. O resultado e um tenant com schema criado e zero
        // tabelas, sem nenhum erro visivel. Por isso a verificacao abaixo.
        Integer tableCount = migratorJdbc().queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ?",
                Integer.class, schemaName);
        if (tableCount == null || tableCount == 0) {
            throw new IllegalStateException(
                    "Nenhuma migration aplicada no schema " + schemaName
                            + ". Verifique o location '" + TENANT_MIGRATIONS_LOCATION + "'.");
        }
        log.info("Tenant schema {} provisionado com {} tabelas", schemaName, tableCount);
    }

    private void registerTenantInCatalog(UUID tenantId, String schemaName, TenantProvisioningRequest request) {
        String sql = """
            INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, phone, address, status, plan, settings, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, ?::jsonb, NOW(), NOW())
            ON CONFLICT (schema_name) DO UPDATE SET
                name = EXCLUDED.name,
                cnpj = EXCLUDED.cnpj,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                address = EXCLUDED.address,
                plan = EXCLUDED.plan,
                settings = EXCLUDED.settings,
                updated_at = NOW()
            """;

        // public.tenant_catalog tem RLS por id = current_tenant_id(). O INSERT do proprio
        // tenant satisfaz a policy desde que a GUC app.current_tenant esteja definida nesta
        // conexao. Sem ela, o INSERT e barrado com "new row violates row-level security".
        // A definicao e local a transacao, entao nao vaza para a proxima conexao do pool.
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, schemaName);
                    ps.setString(3, request.getDisplayName());
                    ps.setString(4, request.getCnpj());
                    ps.setString(5, request.getEmail());
                    ps.setString(6, request.getPhone());
                    ps.setString(7, Json.orDefault(request.getAddress(), "{}"));
                    ps.setString(8, request.getPlan());
                    ps.setString(9, Json.orDefault(request.getSettings(), "{}"));
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Falha ao registrar tenant {} no catalogo", schemaName, e);
            throw new IllegalStateException("Falha ao registrar tenant no catalogo", e);
        }
    }

    /**
     * Semeia os dados iniciais do tenant (papeis, admin, metadata).
     *
     * Tudo numa transação unica e numa unica conexao, porque as policies RLS leem a GUC
     * app.current_tenant. SET LOCAL vale so dentro da transacao, entao o contexto some no
     * commit e nunca vaza para a proxima conexao do pool — que seria um tenant vazando para
     * outro.
     *
     * O INSERT inicial e excecao controlada: uma linha cuja tenant_id difere do contexto viola
     * a policy. Precisa do contexto correto, e nao de BYPASSRLS, que foi removido de proposito.
     */
    /**
     * Grava o registro de auditoria do provisionamento.
     *
     * public.audit_log tem RLS por tenant_id = current_tenant_id(), entao o INSERT precisa da
     * GUC definida. O save pelo JPARepository abriria outra conexao, sem a GUC, e seria
     * barrado. Por isso a escrita e explicita e transacional.
     */
    private void writeProvisioningAudit(UUID tenantId, String schemaName, String displayName) {
        String payload = Json.write(Map.of("schemaName", schemaName, "displayName", displayName));

        String sql = """
            INSERT INTO public.audit_log
                (tenant_id, entity_type, entity_id, action, new_values, success, created_at)
            VALUES (?, 'Tenant', ?, 'CREATE', ?::jsonb, true, NOW())
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                }
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, tenantId);
                    ps.setString(3, payload);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                log.error("DEBUG audit falhou", e);
                throw e;
            }
        } catch (SQLException e) {
            log.error("Falha ao gravar auditoria do provisionamento", e);
            throw new IllegalStateException("Falha ao gravar auditoria do provisionamento", e);
        }
    }

    /**
     * Semeia os dados iniciais do tenant (papeis, admin, metadata).
     *
     * Tudo numa transacao unica e numa unica conexao, porque as policies RLS leem a GUC
     * app.current_tenant. SET LOCAL vale so dentro da transacao, entao o contexto some no
     * commit e nunca vaza para a proxima conexao do pool — que seria um tenant vazando para
     * outro.
     */
    private void initializeTenantData(String schemaName, UUID tenantId,
                                      TenantProvisioningRequest request) {
        TenantContextHolder.setTenant(schemaName);
        TenantContextHolder.setTenantId(tenantId);

        try (Connection conn = migratorDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                }

                seedRoles(conn, schemaName, tenantId);
                seedAdminUser(conn, schemaName, tenantId, request);
                seedTenantMetadata(conn, schemaName, tenantId, request);

                conn.commit();
                log.info("Dados iniciais do tenant {} semeados", schemaName);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao semear dados do tenant " + schemaName, e);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void seedRoles(Connection conn, String schemaName, UUID tenantId) throws SQLException {
        String sql = String.format("""
            INSERT INTO %s.roles (id, tenant_id, code, name, description, permissions, is_system, created_at, updated_at)
            VALUES (uuid_generate_v4(), ?, 'ADMIN', 'Administrador', 'Acesso total ao sistema',
                    '["*"]'::jsonb, true, NOW(), NOW()),
                   (uuid_generate_v4(), ?, 'OPERATOR', 'Operador', 'Acesso operacional',
                    '["consumer:read","consumer:write","service_order:read","service_order:write"]'::jsonb, false, NOW(), NOW()),
                   (uuid_generate_v4(), ?, 'MANAGER', 'Gerente', 'Acesso gerencial',
                    '["consumer:read","consumer:write","service_order:read","service_order:write","reports:read","billing:read"]'::jsonb, false, NOW(), NOW()),
                   (uuid_generate_v4(), ?, 'VIEWER', 'Visualizador', 'Apenas leitura',
                    '["consumer:read","service_order:read","reports:read"]'::jsonb, false, NOW(), NOW())
            ON CONFLICT (tenant_id, code) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                permissions = EXCLUDED.permissions,
                updated_at = NOW()
            """, schemaName);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, tenantId);
            ps.setObject(3, tenantId);
            ps.setObject(4, tenantId);
            ps.executeUpdate();
        }
    }

    private void seedAdminUser(Connection conn, String schemaName, UUID tenantId,
                               TenantProvisioningRequest request) throws SQLException {
        String sql = String.format("""
            INSERT INTO %s.users (id, tenant_id, username, email, full_name, password_hash, status, mfa_enabled, created_at, updated_at)
            VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, 'ACTIVE', false, NOW(), NOW())
            ON CONFLICT (tenant_id, username) DO NOTHING
            """, schemaName);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, request.getAdminUsername());
            ps.setString(3, request.getAdminEmail());
            ps.setString(4, request.getAdminName());
            ps.setString(5, request.getAdminPasswordHash());
            ps.executeUpdate();
        }

        String roleSql = String.format("""
            INSERT INTO %s.user_roles (id, tenant_id, user_id, role_id, assigned_at, assigned_by)
            SELECT uuid_generate_v4(), ?, u.id, r.id, NOW(), ?
            FROM %s.users u, %s.roles r
            WHERE u.tenant_id = ? AND u.username = ? AND r.tenant_id = ? AND r.code = 'ADMIN'
            ON CONFLICT DO NOTHING
            """, schemaName, schemaName, schemaName);

        try (PreparedStatement ps = conn.prepareStatement(roleSql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, tenantId);
            ps.setObject(3, tenantId);
            ps.setString(4, request.getAdminUsername());
            ps.setObject(5, tenantId);
            ps.executeUpdate();
        }
    }

    private void seedTenantMetadata(Connection conn, String schemaName, UUID tenantId,
                                    TenantProvisioningRequest request) throws SQLException {
        String sql = String.format("""
            INSERT INTO %s.tenant_metadata (id, tenant_id, schema_name, display_name, cnpj, address, settings, status, created_at, updated_at)
            VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?::jsonb, ?::jsonb, 'ACTIVE', NOW(), NOW())
            ON CONFLICT (tenant_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                cnpj = EXCLUDED.cnpj,
                address = EXCLUDED.address,
                settings = EXCLUDED.settings,
                updated_at = NOW()
            """, schemaName);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, schemaName);
            ps.setString(3, request.getDisplayName());
            ps.setString(4, request.getCnpj());
            ps.setString(5, Json.orDefault(request.getAddress(), "{}"));
            ps.setString(6, Json.orDefault(request.getSettings(), "{}"));
            ps.executeUpdate();
        }
    }

    /**
     * Normaliza o nome do schema para um identificador Postgres seguro.
     *
     * O nome vem de entrada externa e é interpolado em DDL, então precisa ser filtrado. O
     * TenantFilter valida o header com regex; esta é a segunda barreira, para o caminho de
     * provisionamento.
     */
    private String sanitizeSchemaName(String schemaName) {
        String sanitized = schemaName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Nome de schema invalido: " + schemaName);
        }
        if (sanitized.length() > 63) {
            sanitized = sanitized.substring(0, 63);
        }
        if (!sanitized.matches("^[a-z_][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Nome de schema invalido apos normalizacao: " + schemaName);
        }

        // Denylist obrigatoria. Sem ela, provisionTenant("public") criaria as tabelas de tenant
        // dentro de public e, se algum passo depois falhasse, o rollback executaria
        // DROP SCHEMA public CASCADE — apagando tenant_catalog e audit_log do banco inteiro.
        // A regex acima aceita "public" e "information_schema" porque ambos sao identificadores
        // validos; so a lista explicita barra.
        String normalized = sanitized.toLowerCase(Locale.ROOT);
        if (RESERVED_SCHEMAS.contains(normalized) || normalized.startsWith("pg_")) {
            throw new IllegalArgumentException(
                    "Nome de schema reservado pelo PostgreSQL: " + schemaName);
        }

        return normalized;
    }

    private static void requireBcryptHash(String hash) {
        // A mensagem nao repete o valor: mesmo invalido, pode ser uma senha colada no campo errado.
        if (hash == null || !BCRYPT_HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Hash de senha do administrador invalido: esperado bcrypt "
                    + "completo ($2a$, $2b$ ou $2y$, custo e 53 caracteres de sal e hash)");
        }
    }

    private void dropSchema(String schemaName) {
        try {
            migratorJdbc().execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName));
        } catch (Exception e) {
            log.warn("Failed to drop schema {}", schemaName, e);
        }
    }

    public boolean isSchemaInitialized(String schemaName) {
        return schemaInitializedCache.getOrDefault(schemaName, false);
    }

    public List<String> listInitializedSchemas() {
        String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('public', 'information_schema', 'pg_catalog', 'pg_toast')";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    // DTOs
    @Getter
    @Builder
    @AllArgsConstructor
    public static class TenantProvisioningRequest {
        private UUID tenantId;
        private String schemaName;
        private String displayName;
        private String cnpj;
        private String email;
        private String phone;
        private Map<String, Object> address;
        private String plan;
        private Map<String, Object> settings;
        private String adminUsername;
        private String adminEmail;
        private String adminName;
        private String adminPasswordHash;
    }

    public record TenantProvisioningResult(
            boolean success,
            UUID tenantId,
            String schemaName,
            String error
    ) {
        public static TenantProvisioningResult success(UUID tenantId, String schemaName) {
            return new TenantProvisioningResult(true, tenantId, schemaName, null);
        }

        public static TenantProvisioningResult failure(String error) {
            return new TenantProvisioningResult(false, null, null, error);
        }
    }
}
