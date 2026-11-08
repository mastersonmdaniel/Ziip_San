package br.com.ziipsan.reporting.situation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93: a sonda de latencia de ponta a ponta NO NUCLEO — o teste do
 * aceite no nivel que esta entrega prova: fato na porta de fonte ->
 * projecao -> tabela de estado -> leitura do console, com o relogio
 * real, medido em {@link System#nanoTime()}. O orcamento contra o
 * qual se mede e o da {@link SituationLatencyPolicy} CONFIGURADA
 * (sem numero de fabrica: [SEM FONTE] — nos testes, o teto e massa
 * sintetica declarada).
 *
 * <p>Fronteira honesta: isto NAO e a latencia ate o navegador. Rede,
 * banco real, serializacao HTTP e renderizacao do mapa so existem na
 * integracao — a repeticao desta sonda sobre o caminho integrado e a
 * pendencia P-ED93-03, e nenhum numero daqui e apresentado como
 * latencia de producao.
 */
public final class SituationLatencyProbe {

    private SituationLatencyProbe() {
    }

    /** Resultado de uma medicao: duracao do caminho e o veredito contra o teto configurado. */
    public record Measurement(Duration elapsed, int projectedRows, boolean withinBudget,
                              SituationLatencyPolicy.Freshness oldestFreshness) {
    }

    /**
     * Mede o caminho completo uma vez: projeta o tenant para a tabela
     * e le o retrato do console, cronometrando os dois passos juntos.
     * {@code oldestFreshness} usa a fonte MAIS ANTIGA do retrato: e o
     * pior caso de idade que o operador veria.
     */
    public static Measurement measureEndToEnd(SituationProjector projector, SituationStateStore store,
                                              SituationConsoleService console, UUID tenantId,
                                              SituationLatencyPolicy policy, Instant readAt) {
        Objects.requireNonNull(projector, "projetor obrigatorio");
        Objects.requireNonNull(console, "console obrigatorio");
        long start = System.nanoTime();
        List<SituationStateRow> rows = projector.projectInto(tenantId, store);
        SituationSnapshot snapshot = console.snapshot(tenantId,
                SituationSourceGuard.FeedSource.NATIVE_SUMMARY_TABLE, policy, readAt);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        Instant oldest = snapshot.allRows().stream().map(SituationStateRow::sourceAt)
                .min(Instant::compareTo).orElse(null);
        return new Measurement(elapsed, rows.size(), policy.withinBudget(elapsed),
                policy.freshness(oldest, readAt));
    }
}
