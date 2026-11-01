package br.com.ziipsan.platform.security;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * {@link RateLimitStore} sobre a tabela {@code rate_limit_buckets} (core V5, schema
 * {@code public}, sem RLS de tenant — o limite é do servidor, anterior a qualquer
 * tenant). Uma linha por chave; a janela antiga é sobrescrita pela própria
 * instrução, sem limpeza periódica.
 *
 * <p>Uma única instrução atômica por requisição: dois processos concorrentes na
 * mesma chave não perdem contagem (o segundo espera o primeiro no conflito de
 * chave única e executa o ramo UPDATE). Sem Redis, por decisão do plano: o
 * compartilhamento entre réplicas é pelo banco que já existe.
 */
public class JdbcRateLimitStore implements RateLimitStore {

    private static final String INCREMENT_SQL = """
            INSERT INTO rate_limit_buckets (bucket_key, window_start, request_count)
            VALUES (?, ?, 1)
            ON CONFLICT (bucket_key) DO UPDATE SET
              window_start = CASE WHEN rate_limit_buckets.window_start < ?
                                  THEN ? ELSE rate_limit_buckets.window_start END,
              request_count = CASE WHEN rate_limit_buckets.window_start < ?
                                   THEN 1 ELSE rate_limit_buckets.request_count + 1 END
            RETURNING request_count""";

    private final JdbcTemplate jdbc;

    public JdbcRateLimitStore(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    JdbcRateLimitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int increment(String key, long windowStartEpochSecond) {
        Integer count = jdbc.queryForObject(INCREMENT_SQL, Integer.class,
                key, windowStartEpochSecond,
                windowStartEpochSecond, windowStartEpochSecond,
                windowStartEpochSecond);
        // queryForObject com RETURNING sempre devolve uma linha; o null é só para o compilador.
        return count == null ? 1 : count;
    }
}
