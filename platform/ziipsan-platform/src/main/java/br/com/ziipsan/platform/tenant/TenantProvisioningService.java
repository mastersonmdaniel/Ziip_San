package br.com.ziipsan.platform.tenant;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.core.audit.AuditLog;
import br.com.ziipsan.core.audit.AuditLogRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P0.6: este serviço só existe no perfil {@code migrate} — ele provisiona schemas com
 * a role de migração (DDL). Fora do job de migração não há bean para injetar, e a api
 * nunca provisiona tenant em runtime. (Construção manual em testes, fora do Spring,
 * não é afetada pelo perfil.)
 */
@Service
@Profile("migrate")
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private static final String PUBLIC_SCHEMA = "public";
    private static final String APP_ROLE = "ziipsan_app";
    private static final String MIGRATOR_ROLE = "ziipsan_migrator";
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

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogRepository auditLogRepository;


    /**
     * DDL de tenant (CREATE SCHEMA, migrations, GRANT) roda com a role de migracao.
     *
     * A role da aplicacao e NOSUPERUSER e NOCREATEDB de proposito, para que a RLS valha. Se o
     * DDL usasse a mesma dataSource, "CREATE SCHEMA ... AUTHORIZATION ziipsan_migrator" falharia
     * com "must be member of role ziipsan_migrator", porque ziipsan_app nao e membro dela nem
     * pode criar schema.
     */
    @Qualifier("migratorDataSource")
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
        // P0.25: sem seed de usuario/admin e sem realm Keycloak no provisionamento. O tenant
        // nasce com schema, migrations e catalogo; usuario e papeis voltam na P0.13 (catalogo
        // de usuarios) e o acesso e pelo Keycloak do ambiente, nao por realm por tenant.
        UUID tenantId = request.getTenantId() != null ? request.getTenantId() : UUID.randomUUID();

        // O rollback so pode remover o que ESTA invocacao criou. Reprovisionar um tenant
        // existente e operacao legitima. Sem esta distincao, o catch executava DROP SCHEMA
        // CASCADE sobre um tenant em producao com consumidores, e devolvia "failure" como
        // se fosse um erro recuperavel.
        boolean schemaCreatedNow = false;

        // P4.6 (correcção R-P46-05): tenant DEDICATED no cluster 'default' é
        // recusado AQUI, no caminho de produção — antes, a recusa vivia só no
        // construtor do record DedicatedTenantProfile, que nenhum caminho de
        // provisionamento invocava, e um DEDICATED nascia no cluster
        // compartilhado sem ninguém o barrar. É erro de validação do chamador
        // (como o nome de schema inválido): a excepção escapa, não vira
        // "failure" de infra.
        requireDedicatedOwnCluster(clusterIdOf(request),
                IsolationMode.parse(isolationModeOf(request)));

        try {
            // P4.6 (correcção R-P46-06): a guarda do teto é count-then-insert;
            // sem serialização, dois provisionamentos concorrentes no limiar
            // podiam ambos passar na contagem e furar o teto. O lock de
            // sessão por cluster é tomado ANTES da contagem e só é largado
            // no finally, depois do registo em catálogo — a janela entre
            // contar e registar fica serializada por cluster em todo o
            // banco (o lock é do PostgreSQL, não da JVM).
            acquireClusterProvisioningLock(clusterIdOf(request));
            enforceClusterCeiling(clusterIdOf(request), schemaName,
                    IsolationMode.parse(isolationModeOf(request)));
            schemaCreatedNow = ensureSchema(schemaName, tenantId,
                    clusterIdOf(request), IsolationMode.parse(isolationModeOf(request)));

            runTenantMigrations(schemaName, tenantId);
            grantTenantTablePrivileges(schemaName);

            registerTenantInCatalog(tenantId, schemaName, request);

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
        } finally {
            releaseClusterProvisioningLock();
        }
    }

    /**
     * P4.6 (correcção R-P46-05): a regra "DEDICATED exige cluster próprio"
     * deixa de viver só no record {@link DedicatedTenantProfile}: o
     * provisionamento aplica-a a todo o pedido DEDICATED, delegando a
     * validação do cluster ao próprio perfil (fonte única da regra).
     * Pedidos dos demais modos não são afectados.
     */
    static void requireDedicatedOwnCluster(String clusterId, IsolationMode isolationMode) {
        if (isolationMode == IsolationMode.DEDICATED) {
            DedicatedTenantProfile.requireOwnCluster(clusterId);
        }
    }

    /**
     * P4.6 (correcção R-P46-05): a isenção do teto só é legítima para
     * DEDICATED COM cluster próprio — um DEDICATED no 'default' disputaria
     * o teto do cluster compartilhado como qualquer outro tenant (e, desde
     * esta correcção, é recusado antes de cá chegar; a condição mantém-se
     * explícita para a guarda ser correcta mesmo isolada).
     */
    static boolean ceilingExempt(String clusterId, IsolationMode isolationMode) {
        return isolationMode == IsolationMode.DEDICATED && !"default".equals(clusterId);
    }

    // --- Lock de sessão por cluster (correcção R-P46-06) -------------------
    // A conexão que segura o lock fica guardada até ao fim do
    // provisionamento: pg_advisory_lock é por SESSÃO, logo largar a
    // conexão (ou devolvê-la ao pool com o lock tomado) quebraria a
    // serialização ou a vazaria para outro chamador do pool.

    private final ThreadLocal<Connection> ceilingLockConnection = new ThreadLocal<>();
    private final ThreadLocal<String> ceilingLockCluster = new ThreadLocal<>();

    private void acquireClusterProvisioningLock(String clusterId) {
        try {
            Connection conn = migratorDataSource.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
                ps.setString(1, "ziipsan:provisioning:" + clusterId);
                ps.execute();
            }
            ceilingLockConnection.set(conn);
            ceilingLockCluster.set(clusterId);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Falha ao serializar o provisionamento do cluster " + clusterId, e);
        }
    }

    private void releaseClusterProvisioningLock() {
        Connection conn = ceilingLockConnection.get();
        String clusterId = ceilingLockCluster.get();
        ceilingLockConnection.remove();
        ceilingLockCluster.remove();
        if (conn == null) {
            return;
        }
        try (Connection c = conn;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
            ps.setString(1, "ziipsan:provisioning:" + clusterId);
            ps.execute();
        } catch (SQLException e) {
            // A conexão fecha a seguir de qualquer forma (try-with-resources
            // sobre ela), e o fecho da sessão larga o lock no servidor: a
            // falha do unlock explícito não deixa lock órfão.
            log.warn("Unlock de provisionamento do cluster {} falhou; "
                    + "o fecho da sessão larga o lock", clusterId, e);
        }
    }

    /**
     * Cria o schema do tenant e devolve true se ele foi criado agora.
     *
     * P0.17: sem CREATE SCHEMA IF NOT EXISTS. Um CREATE silenciosamente pulado escondia a
     * colisao de dois tenants no mesmo schema. Se o schema ja existe, so o proprio tenant
     * (linha do catalogo com o mesmo id) pode seguir, e sem CREATE: o rollback nunca apaga
     * dado alheio. Schema existente sem linha no catalogo (orfao) tambem e recusado, porque
     * nao ha dono provado para autorizar o reuso. Reprovisionar o proprio tenant continua
     * legitimo e idempotente (passos por tenant_id): o CREATE e pulado, as migrations rodam
     * pelo Flyway e o catalogo e atualizado pela chave id.
     *
     * A verificacao ANTES do CREATE e o que permite ao rollback saber se o schema e seguro de
     * remover.
     */
    /**
     * P4.6, item 1 do aceite: aplicacao do TETO de schemas por cluster da
     * P0.21 ({@link TenantCatalog#MAX_SCHEMAS_PER_CLUSTER}, 300 — o numero
     * provado pelo ShardCatalogScaleTest). Ate a P4.6 o teto era so o numero
     * registado; ninguem o aplicava.
     *
     * <p>Regras: conta SCHEMAS distintos do cluster (nao tenants — tenants
     * SHARED dividem schema e nao consomem teto novo); schema que ja existe
     * nesse cluster nao e cobranca nova (reprovisionamento e segundo tenant
     * SHARED passam); tenant DEDICATED COM CLUSTER PROPRIO nao e barrado
     * pelo teto (correcção R-P46-05: a isenção era incondicional para
     * qualquer DEDICATED, incluindo um DEDICATED indevidamente no
     * 'default'; ver {@link #ceilingExempt}). A contagem corre sob o lock
     * de sessão por cluster tomado em {@code provisionTenant} (correcção
     * R-P46-06), que serializa contar-e-registar entre provisionamentos
     * concorrentes do mesmo cluster.
     */
    private void enforceClusterCeiling(String clusterId, String schemaName, IsolationMode isolationMode) {
        if (ceilingExempt(clusterId, isolationMode)) {
            return;
        }
        Integer present = migratorJdbc().queryForObject(
                "SELECT count(*) FROM public.tenant_catalog WHERE cluster_id = ? AND schema_name = ?",
                Integer.class, clusterId, schemaName);
        if (present != null && present > 0) {
            return;
        }
        Integer schemas = migratorJdbc().queryForObject(
                "SELECT count(DISTINCT schema_name) FROM public.tenant_catalog WHERE cluster_id = ?",
                Integer.class, clusterId);
        if (schemas != null && schemas >= TenantCatalog.MAX_SCHEMAS_PER_CLUSTER) {
            throw new IllegalStateException(
                    "Cluster " + clusterId + " atingiu o teto de " + TenantCatalog.MAX_SCHEMAS_PER_CLUSTER
                            + " schemas (P0.21/P4.6): o tenant deve ir para outro cluster"
                            + " ou para banco dedicado.");
        }
    }

    private boolean ensureSchema(String schemaName, UUID tenantId,
                                 String clusterId, IsolationMode isolationMode) {
        Integer existed = migratorJdbc().queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        boolean alreadyExisted = existed != null && existed > 0;

        if (alreadyExisted) {
            // P4.6: a consulta de dono passa a ser por LISTA. No modo SHARED o
            // mesmo (cluster, schema) tem varias linhas no catalogo — a forma
            // antiga (queryForObject de uma linha por schema_name) rebentaria
            // com IncorrectResultSizeDataAccessException no segundo tenant
            // compartilhado, alem de nao distinguir clusters.
            java.util.List<String> occupants = migratorJdbc().queryForList(
                    "SELECT id::text || '|' || cluster_id || '|' || isolation_mode"
                            + " FROM public.tenant_catalog WHERE schema_name = ?",
                    String.class, schemaName);
            boolean own = occupants.stream()
                    .anyMatch(row -> row.startsWith(tenantId.toString() + "|"));
            if (own) {
                // Reprovisionar o proprio tenant: legitimo e idempotente, como antes.
            } else if (isolationMode == IsolationMode.SHARED
                    && canReuseSchemaAsShared(occupants, clusterId)) {
                // ADR-0005 ("o que muda no codigo"): o provisionamento recusa
                // schema existente SALVO no modo compartilhado explicito. O
                // schema ja e a casa de tenants SHARED deste cluster: este
                // tenant entra nele, isolado por tenant_id e RLS. O rollback
                // continua seguro: schemaCreatedNow fica false, logo o catch
                // nunca executa DROP SCHEMA sobre o schema compartilhado.
                log.info("Schema compartilhado {} (cluster {}) reutilizado para o tenant {} (modo SHARED)",
                        schemaName, clusterId, tenantId);
            } else {
                throw new IllegalStateException(
                        "Schema " + schemaName + " ja existe e nao pertence a este tenant; "
                                + "recusado para nao misturar dados de tenants distintos.");
            }
        } else {
            migratorJdbc().execute(String.format("CREATE SCHEMA %s AUTHORIZATION %s",
                    schemaName, MIGRATOR_ROLE));
        }

        String[] grants = {
            // USAGE basta para a aplicacao resolver nomes. CREATE no schema permitiria a
            // aplicacao criar objetos, o que nao e necessario em runtime.
            String.format("GRANT USAGE ON SCHEMA %s TO %s", schemaName, APP_ROLE),
            // Default privileges valem para objetos criados pela MESMA role que executa o
            // ALTER. Como as migrations rodam como ziipsan_migrator, e essa role que precisa
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
     * P4.6 (correcção R-P46-07): o reuso SHARED de um schema existente só é
     * legítimo quando TODOS os ocupantes registados desse schema são
     * tenants SHARED DESTE cluster. A forma anterior bastava UM ocupante
     * SHARED do cluster para reutilizar — um schema que também albergasse
     * um tenant SCHEMA (dono de schema próprio por desenho) seria
     * reutilizado por um SHARED, misturando modos no mesmo schema físico.
     * Schema sem nenhum ocupante registado (órfão) também não é
     * reutilizável: não há dono provado. Linhas no formato
     * {@code id|cluster_id|isolation_mode} (a projecção de ensureSchema);
     * linha malformada conta como ocupante não-SHARED (falha fechado).
     */
    static boolean canReuseSchemaAsShared(java.util.List<String> occupantRows, String clusterId) {
        if (occupantRows == null || occupantRows.isEmpty()) {
            return false;
        }
        return occupantRows.stream().allMatch(row -> {
            String[] parts = row.split("\\|");
            return parts.length == 3
                    && clusterId.equals(parts[1])
                    && IsolationMode.SHARED.columnValue().equals(parts[2]);
        });
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
        new TenantMigrator(migratorDataSource).migrate(schemaName, tenantId);

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

    /** P0.21/P4.6: cluster do pedido; em branco = 'default'. Fonte unica do default. */
    private static String clusterIdOf(TenantProvisioningRequest request) {
        return request.getClusterId() == null || request.getClusterId().isBlank()
                ? "default" : request.getClusterId();
    }

    /** P0.21/P4.6: modo do pedido, validado pelo enum (lista fechada da core V7); em branco = 'SCHEMA'. */
    private static String isolationModeOf(TenantProvisioningRequest request) {
        return IsolationMode.parse(request.getIsolationMode()).columnValue();
    }

    private void registerTenantInCatalog(UUID tenantId, String schemaName, TenantProvisioningRequest request) {
        // P0.17 + P0.21 (fusão na integração): conflito resolvido pela chave id, não por
        // schema_name. Reprovisionar o mesmo tenant atualiza a linha dele (inclusive se o
        // schema mudou); um segundo tenant NÃO-SHARED com o mesmo schema_name NO MESMO
        // cluster esbarra no índice único parcial (cluster_id, schema_name) e falha, em
        // vez de sobrescrever a linha alheia; em cluster diferente o mesmo schema_name é
        // aceito. Desde a core V15 (P4.6, correcção R-P46-01) o índice exclui as linhas
        // SHARED: o segundo tenant SHARED no mesmo (cluster, schema) regista-se sem
        // colisão — é o desenho do ADR-0005. O ON CONFLICT (id) já resolve a quebra que
        // a forma antiga ON CONFLICT (schema_name) teria (o PK id continua existindo).
        // cluster_id e isolation_mode entram no catálogo (P0.21).
        String sql = """
            INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, phone, address, status, plan, settings, cluster_id, isolation_mode, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, ?::jsonb, ?, ?, NOW(), NOW())
            ON CONFLICT (id) DO UPDATE SET
                schema_name = EXCLUDED.schema_name,
                name = EXCLUDED.name,
                cnpj = EXCLUDED.cnpj,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                address = EXCLUDED.address,
                plan = EXCLUDED.plan,
                settings = EXCLUDED.settings,
                isolation_mode = EXCLUDED.isolation_mode,
                updated_at = NOW()
            """;

        // P0.17 + P0.21 (fusão na integração): a escrita do catálogo usa a conexão de
        // migração. Desde a core V4 (F17) a ziipsan_app só tem SELECT no catálogo, então
        // o DataSource da aplicação não pode mais registrar tenant; a core V7 (F21)
        // reafirma o SELECT-only. O catálogo não tem RLS (cadastro global da plataforma,
        // ver V1); a GUC antiga era inócua aqui e some com a troca de conexão.
        // migratorDataSource é o mesmo DataSource que migratorJdbc() embrulha.
        String clusterId = clusterIdOf(request);
        String isolationMode = isolationModeOf(request);
        try (Connection conn = migratorDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
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
                    ps.setString(10, clusterId);
                    ps.setString(11, isolationMode);
                    ps.executeUpdate();
                }
                // GIS-01 (P1.26): todo tenant nasce com o pacote sanitation ativo, como
                // o backfill da core V6_1 (P0.47) fez para os tenants existentes. Sem
                // isso, a gis_domain_visible da V13_1 devolveria vazio para tenants
                // novos e o gate de pacote da V13_2 recusaria as classes do tenant.
                // Idempotente (reprovisionar nao duplica). O savepoint existe porque
                // qualquer erro no PostgreSQL aborta a transacao inteira: sem ele, uma
                // platform.tenant_vertical ausente (core anterior a V6_1) derrubaria
                // o registro do tenant junto; com ele, so a ativacao e desfeita e a
                // ativacao fica para o backfill da V6_1.
                java.sql.Savepoint activationPoint = conn.setSavepoint("gis_sanitation_activation");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                                + " VALUES (?, 'sanitation', 'ACTIVE', NOW())"
                                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                    conn.releaseSavepoint(activationPoint);
                } catch (SQLException activationEx) {
                    conn.rollback(activationPoint);
                    log.warn("Pacote sanitation nao ativado no provisionamento de {} "
                            + "(platform.tenant_vertical ausente ou inacessivel); "
                            + "a ativacao fica para o backfill da core V6_1", schemaName, activationEx);
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
                // P0.25: set_config por parametro, nao por concatenacao.
                try (PreparedStatement st = conn.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                    st.setString(1, tenantId.toString());
                    st.execute();
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
                throw e;
            }
        } catch (SQLException e) {
            log.error("Falha ao gravar auditoria do provisionamento", e);
            throw new IllegalStateException("Falha ao gravar auditoria do provisionamento", e);
        }
    }

    /**
     * Valida o nome do schema como identificador Postgres seguro.
     *
     * P0.17: o nome vem de entrada externa e é interpolado em DDL, e a normalizacao
     * silenciosa antiga escondia colisoes: "tenant-a" e "tenant_a" viravam o mesmo schema,
     * e um nome de 64 caracteres era truncado para 63 sem aviso. Agora, qualquer nome que
     * MUDE na normalizacao (troca de caracteres, caixa ou truncamento em 63) e recusado
     * com IllegalArgumentException, antes de qualquer DDL. O chamador deve enviar o nome
     * ja normalizado. O TenantFilter valida o header com regex; esta e a segunda barreira,
     * para o caminho de provisionamento.
     */
    private String sanitizeSchemaName(String schemaName) {
        if (schemaName == null) {
            throw new IllegalArgumentException("Nome de schema invalido: null");
        }
        String sanitized = schemaName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Nome de schema invalido: " + schemaName);
        }
        if (sanitized.length() > 63) {
            throw new IllegalArgumentException(
                    "Nome de schema excede 63 caracteres e seria truncado: " + schemaName);
        }
        if (!sanitized.equals(schemaName)) {
            throw new IllegalArgumentException(
                    "Nome de schema muda na normalizacao (troca de caracteres ou caixa): " + schemaName);
        }
        if (!sanitized.matches("^[a-z_][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Nome de schema invalido: " + schemaName);
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

    private void dropSchema(String schemaName) {
        try {
            migratorJdbc().execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", schemaName));
        } catch (Exception e) {
            log.warn("Failed to drop schema {}", schemaName, e);
        }
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
        /** P0.21: cluster do tenant; em branco = 'default'. */
        private String clusterId;
        /** P0.21: SCHEMA, SHARED ou DEDICATED; em branco = 'SCHEMA' (o CHECK do banco barra outro valor). */
        private String isolationMode;
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
