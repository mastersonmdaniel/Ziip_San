package br.com.ziipsan.platform.deployment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Leitura de {@code platform.deployment} (P0.42). Só leitura: a gravação pertence
 * ao job {@code migrate} ({@code DatabaseMigrator}), nunca à api. A role da
 * aplicação tem só SELECT na tabela (core V5_1); um INSERT daqui falharia no
 * banco, e é para falhar — o registro de implantação não é escrito em runtime.
 */
@Component
public class DeploymentRepository {

    private final JdbcTemplate jdbc;

    @Autowired
    public DeploymentRepository(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    DeploymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Implantação mais recente (a que {@code GET /api/v1/about} apresenta). */
    public Optional<Deployment> latest() {
        List<Deployment> rows = jdbc.query(
                "SELECT id, version, commit_hash, environment, started_at, finished_at, created_at"
                        + " FROM platform.deployment ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, i) -> map(rs));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Implantações mais recentes, para {@code GET /api/v1/platform/deployments}. */
    public List<Deployment> recent(int limit) {
        return jdbc.query(
                "SELECT id, version, commit_hash, environment, started_at, finished_at, created_at"
                        + " FROM platform.deployment ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, i) -> map(rs), Math.max(1, limit));
    }

    private static Deployment map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Deployment(
                rs.getObject("id", UUID.class),
                rs.getString("version"),
                rs.getString("commit_hash"),
                rs.getString("environment"),
                toOffset(rs.getTimestamp("started_at")),
                toOffset(rs.getTimestamp("finished_at")),
                toOffset(rs.getTimestamp("created_at")));
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
