package br.com.ziipsan.core.certification;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

/**
 * Transicao automatica de vigencia do catalogo global de normas (P1.34;
 * plano v3.0 §3; header da V9_8).
 *
 * <p>Regra, unica e sem excecao: uma norma com {@code status =
 * VIGENCIA_FUTURA} e {@code effective_from} nao nulo passa a {@code
 * VIGENTE} quando a data de efeito chega ({@code effective_from <=
 * hoje}). A mesma regra roda na carga (o UPDATE final da
 * R__norm_catalog.sql, para o provisionamento ja nascer correto) e aqui,
 * para as datas que chegarem depois — nunca em outro lugar, e nunca
 * sobre outro status: REVOGADA, SUBSTITUIDA e VIGENTE nao sao tocadas.
 *
 * <p>Relogio injetavel: os testes usam {@link Clock#fixed} (data
 * simulada, como o plano exige); em producao usa-se o relogio do
 * sistema. A data de efeito vem do catalogo, curada da nota de
 * situacao de cada norma na carga — este servico nunca inventa data.
 * Norma em vigencia futura SEM data (hoje: nt-2026-001-rtc-nfag, cujas
 * datas "serao fixadas por CGIBS e RFB") nao transiciona ate uma
 * versao futura do catalogo trazer a data.
 *
 * <p>Concorrencia: a transicao roda dentro de uma transacao com
 * {@code pg_try_advisory_xact_lock} de chave fixa (precedente do
 * CentralCollectorService), para duas replicas nao transicionarem a
 * mesma norma ao mesmo tempo. Quem nao obtem a trava devolve o
 * relatorio com {@code lockAcquired = false} e nao escreve nada.
 *
 * <p>Privilegio: a escrita aqui e so sobre (status, updated_at) de
 * platform.cert_standard — o GRANT de coluna correspondente vive na
 * propria R__norm_catalog.sql, porque a V9_8 concede so SELECT a
 * ziipsan_app. Sem a carga, este servico falha por falta de
 * privilegio, e isso e intencional: a transicao so existe sobre o
 * catalogo carregado.
 *
 * <p>Alerta antecipado: {@link #upcomingEffective} lista as normas em
 * vigencia futura cuja data de efeito cai na janela pedida. O
 * scheduler registra essa lista em log (WARN); a entrega do alerta ao
 * responsavel do tenant (notificacao no produto) NAO esta ligada aqui
 * — nao existe canal de notificacao por tenant no modulo de
 * certificacao, e inventar um nesta frente seria escopo novo. O dado
 * necessario (a lista) ja sai deste servico, pronto para o notificador
 * quando ele existir.
 */
public class NormEffectivenessService {

    /** Chave fixa da trava de transicao (distinta da chave da Central). */
    public static final long LOCK_KEY = 2026110401L;

    /** Uma norma transicionada nesta execucao. */
    public record TransitionedView(String catalogKey, String code, LocalDate effectiveFrom) {
    }

    /** Uma norma em vigencia futura com data de efeito proxima. */
    public record UpcomingView(String catalogKey, String code, String title, LocalDate effectiveFrom) {
    }

    /** Resultado de uma execucao da transicao. */
    public record TransitionReport(boolean lockAcquired, LocalDate asOf,
                                   List<TransitionedView> transitioned) {
    }

    private final DataSource dataSource;
    private final String platformSchema;
    private final Clock clock;

    public NormEffectivenessService(DataSource dataSource, String platformSchema) {
        this(dataSource, platformSchema, Clock.systemDefaultZone());
    }

    public NormEffectivenessService(DataSource dataSource, String platformSchema, Clock clock) {
        if (dataSource == null || platformSchema == null || platformSchema.isBlank() || clock == null) {
            throw new IllegalArgumentException("dataSource, platformSchema and clock are required");
        }
        this.dataSource = dataSource;
        this.platformSchema = platformSchema;
        this.clock = clock;
    }

    /** Transiciona as normas vencidas "hoje" (relogio do servico). */
    public TransitionReport transitionDue() {
        return transitionDue(LocalDate.now(clock));
    }

    /**
     * Transiciona as normas em vigencia futura com {@code effective_from
     * <= asOf}. Idempotente: uma norma ja VIGENTE nao casa o WHERE e
     * nao e reescrita; rodar duas vezes no mesmo dia transiciona zero
     * na segunda vez.
     */
    public TransitionReport transitionDue(LocalDate asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean locked;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT pg_try_advisory_xact_lock(?)")) {
                    ps.setLong(1, LOCK_KEY);
                    try (ResultSet rs = ps.executeQuery()) {
                        locked = rs.next() && rs.getBoolean(1);
                    }
                }
                if (!locked) {
                    connection.rollback();
                    return new TransitionReport(false, asOf, List.of());
                }
                List<TransitionedView> transitioned = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + platformSchema + ".cert_standard"
                                + " SET status = 'VIGENTE', updated_at = NOW()"
                                + " WHERE status = 'VIGENCIA_FUTURA'"
                                + " AND effective_from IS NOT NULL"
                                + " AND effective_from <= ?"
                                + " RETURNING catalog_key, code, effective_from")) {
                    ps.setDate(1, Date.valueOf(asOf));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            transitioned.add(new TransitionedView(
                                    rs.getString("catalog_key"),
                                    rs.getString("code"),
                                    rs.getDate("effective_from").toLocalDate()));
                        }
                    }
                }
                connection.commit();
                return new TransitionReport(true, asOf, transitioned);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Norm effectiveness transition failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Norm effectiveness transition failed: " + e.getMessage(), e);
        }
    }

    /**
     * Normas em vigencia futura com data de efeito em (today,
     * today + daysAhead] — a janela do alerta antecipado. Norma sem
     * data nunca aparece aqui: sem data nao ha o que antecipar.
     */
    public List<UpcomingView> upcomingEffective(LocalDate today, int daysAhead) {
        if (today == null) {
            throw new IllegalArgumentException("today is required");
        }
        if (daysAhead < 0) {
            throw new IllegalArgumentException("daysAhead must not be negative");
        }
        LocalDate until = today.plusDays(daysAhead);
        List<UpcomingView> out = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT catalog_key, code, title, effective_from"
                             + " FROM " + platformSchema + ".cert_standard"
                             + " WHERE status = 'VIGENCIA_FUTURA'"
                             + " AND effective_from IS NOT NULL"
                             + " AND effective_from > ? AND effective_from <= ?"
                             + " ORDER BY effective_from, catalog_key")) {
            ps.setDate(1, Date.valueOf(today));
            ps.setDate(2, Date.valueOf(until));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UpcomingView(
                            rs.getString("catalog_key"),
                            rs.getString("code"),
                            rs.getString("title"),
                            rs.getDate("effective_from").toLocalDate()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Norm effectiveness lookup failed: " + e.getMessage(), e);
        }
        return out;
    }
}
