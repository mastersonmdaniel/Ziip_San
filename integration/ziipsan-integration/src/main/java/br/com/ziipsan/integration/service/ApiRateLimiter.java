package br.com.ziipsan.integration.service;

import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Limite de taxa por cliente da API pública (P2.26), em baldes de 60 s
 * na tabela {@code api_rate_buckets} do tenant (compartilhado entre
 * réplicas, como o {@code JdbcRateLimitStore} da P0.16).
 *
 * <p>O limite efetivo é o menor entre o limite do cliente, o parâmetro
 * {@code api.rate_limit_per_minute} do tenant e o teto da plataforma
 * (1000/min). Acima do limite, a resposta é 429 com {@code Retry-After}
 * em segundos até a virada da janela — nunca um 500, nunca um bloqueio
 * silencioso. A janela é o minuto do relógio (epoch / 60), não uma janela
 * deslizante: o cálculo é determinístico e testável.
 */
@Service
public class ApiRateLimiter {

    public record Check(boolean allowed, int retryAfterSeconds, int limit) {
    }

    private final DataSource dataSource;

    public ApiRateLimiter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Check check(UUID clientUuid, int limit) {
        long window = Instant.now().getEpochSecond() / 60;
        int count = TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO api_rate_buckets (tenant_id, client_id, window_start, request_count)"
                            + " VALUES (?, ?, ?, 1)"
                            + " ON CONFLICT (tenant_id, client_id, window_start)"
                            + " DO UPDATE SET request_count = api_rate_buckets.request_count + 1"
                            + " RETURNING request_count")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, clientUuid);
                ps.setLong(3, window);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
        if (count <= limit) {
            return new Check(true, 0, limit);
        }
        long now = Instant.now().getEpochSecond();
        int retryAfter = (int) Math.max(1, (window + 1) * 60 - now);
        return new Check(false, retryAfter, limit);
    }
}
