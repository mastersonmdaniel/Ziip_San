package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.2: a fabrica de pools por papel, sem banco.
 *
 * <p>A URL aponta para uma porta onde nada escuta: como a fabrica usa
 * {@code minimumIdle = 0}, criar o pool nao abre conexao — se um dia a
 * fabrica passar a abrir conexao na criacao, este teste falha aqui (erro
 * de conexao) em vez de falhar la na frente, num contexto Spring.
 */
class RoleDataSourceFactoryTest {

    private static final String DEAD_URL = "jdbc:postgresql://127.0.0.1:59999/ziipsan";

    private static RoleDataSourceFactory.RoleCredentials creds(String user) {
        return new RoleDataSourceFactory.RoleCredentials(user, "senha-sintetica-de-teste");
    }

    @Test
    @DisplayName("papeis tem usuario, teto e nome de pool proprios (ARQUITETURA-ALVO §4)")
    void roleDefaults() {
        assertThat(PoolRole.API.defaultUsername()).isEqualTo("ziipsan_app");
        assertThat(PoolRole.API.defaultMaxPoolSize()).isEqualTo(20);
        assertThat(PoolRole.BATCH.defaultUsername()).isEqualTo("ziipsan_batch");
        assertThat(PoolRole.BATCH.defaultMaxPoolSize()).isEqualTo(10);
        assertThat(PoolRole.INGEST.defaultUsername()).isEqualTo("ziipsan_ingest");
        assertThat(PoolRole.INGEST.defaultMaxPoolSize()).isEqualTo(5);
        assertThat(PoolRole.API.poolName()).isEqualTo("ziipsan-api");
        assertThat(PoolRole.BATCH.poolName()).isEqualTo("ziipsan-batch");
        assertThat(PoolRole.INGEST.poolName()).isEqualTo("ziipsan-ingest");
    }

    @Test
    @DisplayName("create: nome, usuario, teto e minimumIdle 0, sem prepareThreshold (regra P0.21)")
    void createHonorsRoleAndNeverSetsPrepareThreshold() {
        try (HikariDataSource ds = RoleDataSourceFactory.create(
                PoolRole.BATCH, DEAD_URL, creds("ziipsan_batch"))) {
            assertThat(ds.getPoolName()).isEqualTo("ziipsan-batch");
            assertThat(ds.getUsername()).isEqualTo("ziipsan_batch");
            assertThat(ds.getMaximumPoolSize()).isEqualTo(10);
            assertThat(ds.getMinimumIdle()).isEqualTo(0);
            // Regra da P0.21 (PLT-35): prepareThreshold NUNCA e 0 — aqui, nem
            // definido: o padrao do pgJDBC (5) vale. Qualquer propriedade com
            // esse nome, com qualquer valor, reprova o teste.
            assertThat(ds.getDataSourceProperties().stringPropertyNames())
                    .doesNotContain("prepareThreshold");
        }
    }

    @Test
    @DisplayName("create com teto explicito: o teto vence o padrao do papel")
    void createWithExplicitMaxPoolSize() {
        try (HikariDataSource ds = RoleDataSourceFactory.create(
                PoolRole.BATCH, DEAD_URL, creds("ziipsan_batch"), 1)) {
            assertThat(ds.getMaximumPoolSize()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("createAll: tres pools distintos, um por papel")
    void createAllBuildsThreeDistinctPools() {
        Map<PoolRole, HikariDataSource> pools = RoleDataSourceFactory.createAll(DEAD_URL, Map.of(
                PoolRole.API, creds("ziipsan_app"),
                PoolRole.BATCH, creds("ziipsan_batch"),
                PoolRole.INGEST, creds("ziipsan_ingest")));
        try {
            assertThat(pools).containsOnlyKeys(PoolRole.API, PoolRole.BATCH, PoolRole.INGEST);
            assertThat(pools.values()).hasSize(3).doesNotHaveDuplicates();
            assertThat(pools.get(PoolRole.API).getPoolName()).isEqualTo("ziipsan-api");
            assertThat(pools.get(PoolRole.INGEST).getMaximumPoolSize()).isEqualTo(5);
        } finally {
            pools.values().forEach(HikariDataSource::close);
        }
    }

    @Test
    @DisplayName("validacao: URL, credencial e teto em branco ou ausente sao recusados")
    void validationRejectsBlankInputs() {
        assertThatThrownBy(() -> RoleDataSourceFactory.create(PoolRole.API, "  ", creds("ziipsan_app")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleDataSourceFactory.RoleCredentials("ziipsan_app", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoleDataSourceFactory.RoleCredentials("", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleDataSourceFactory.create(PoolRole.API, DEAD_URL, creds("ziipsan_app"), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleDataSourceFactory.createAll(DEAD_URL,
                Map.of(PoolRole.API, creds("ziipsan_app"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BATCH");
    }
}
