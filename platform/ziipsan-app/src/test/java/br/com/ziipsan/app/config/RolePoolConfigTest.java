package br.com.ziipsan.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * P2.2: os beans dos pools por papel, sem contexto Spring e sem banco
 * (a fabrica usa {@code minimumIdle = 0}: criar o pool nao abre conexao).
 */
class RolePoolConfigTest {

    private static final String DEAD_URL = "jdbc:postgresql://127.0.0.1:59999/ziipsan";

    private static RolePoolConfig config() {
        return new RolePoolConfig(DEAD_URL,
                "ziipsan_batch", "senha-sintetica-lote", 10,
                "ziipsan_ingest", "senha-sintetica-ingestao", 5);
    }

    @Test
    @DisplayName("beans: lote e ingestao com usuario, teto e nome de pool proprios, nao primarios")
    void beansCarryOwnCredentialsAndLimits() {
        RolePoolConfig cfg = config();
        try (HikariDataSource batch = cfg.batchDataSource();
             HikariDataSource ingest = cfg.ingestDataSource()) {
            assertThat(batch.getPoolName()).isEqualTo("ziipsan-batch");
            assertThat(batch.getUsername()).isEqualTo("ziipsan_batch");
            assertThat(batch.getMaximumPoolSize()).isEqualTo(10);
            assertThat(ingest.getPoolName()).isEqualTo("ziipsan-ingest");
            assertThat(ingest.getUsername()).isEqualTo("ziipsan_ingest");
            assertThat(ingest.getMaximumPoolSize()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("config desligada por padrao: so existe com ziipsan.db.pools.enabled=true")
    void configIsDisabledByDefault() {
        ConditionalOnProperty cond = RolePoolConfig.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(cond).isNotNull();
        assertThat(cond.prefix()).isEqualTo("ziipsan.db.pools");
        assertThat(cond.name()).containsExactly("enabled");
        assertThat(cond.havingValue()).isEqualTo("true");
        assertThat(cond.matchIfMissing()).isFalse();
    }

    @Test
    @DisplayName("sem senha do papel: falha rapido na criacao do bean, nao no primeiro uso")
    void missingPasswordFailsFast() {
        RolePoolConfig cfg = new RolePoolConfig(DEAD_URL,
                "ziipsan_batch", "", 10,
                "ziipsan_ingest", "senha-sintetica-ingestao", 5);
        assertThatThrownBy(cfg::batchDataSource)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
