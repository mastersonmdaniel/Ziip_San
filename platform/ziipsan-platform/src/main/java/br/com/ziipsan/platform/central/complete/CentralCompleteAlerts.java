package br.com.ziipsan.platform.central.complete;

import br.com.ziipsan.platform.observability.AlertRule;
import br.com.ziipsan.platform.observability.BurnRateCalculator;
import br.com.ziipsan.platform.observability.SloDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P3.15 + P3.11: "falha provocada alerta" na Central completa.
 *
 * <h2>Regra ÚNICA existente (correção pós-revisão, achado M2)</h2>
 * A condição "coleta velha" JÁ tem regra semeada e catalogada: a core
 * {@code V9_1} semeia {@code central.collector.stale}
 * ({@code platform.collector.last_run_age_s > 900}, CRITICAL) e a P3.11
 * a cataloga como RB-02 em {@code alert-rules.json}. A versão anterior
 * desta classe criava uma SEGUNDA regra idêntica
 * ({@code central.complete.probe_stale}) — o que geraria
 * {@code alert_event} duplicado na integração — e avaliava com o limiar
 * hardcoded, sem ler a {@link AlertRule}. Correção: <b>nenhuma regra
 * nova</b>; {@link #collectorStaleRule()} apenas referencia a regra
 * existente (mesma chave, métrica, comparador, limiar e runbook RB-02
 * do catálogo da P3.11) e {@link #evaluateCollectorStale(AlertRule,
 * ProbeResult)} avalia a medição USANDO o comparador e o limiar DA
 * REGRA recebida. Sinal e regra descrevem a mesma condição.
 *
 * <p>"Falha provocada" é o exercício da P3.11: uma <b>medição
 * simulada</b> (coleta parada → idade velha e/ou sucesso falso)
 * alimentando ESSA avaliação única. Sucesso falso com coleta fresca
 * NÃO dispara: sem idade além do limiar da regra não há a condição da
 * regra — a versão anterior disparava nesse caminho, divergindo da
 * regra que dizia representar.
 *
 * <p>A aba Infraestrutura reaproveita o {@link BurnRateCalculator} da
 * P3.11: o tier deriva dos burn rates CALCULADOS das janelas curta e
 * longa (correção M3 — antes o tier vinha de doubles soltos do
 * chamador, sem relação com o cálculo).
 */
public final class CentralCompleteAlerts {

    /** Chave da regra EXISTENTE da V9_1/P3.11 — nenhuma regra nova é criada aqui. */
    public static final String COLLECTOR_STALE_RULE_KEY = "central.collector.stale";
    public static final String COLLECTOR_METRIC_KEY = "platform.collector.last_run_age_s";
    /** Runbook da regra existente no catálogo da P3.11 (RB-02); o RB-09 é o procedimento do exercício provocado. */
    public static final String COLLECTOR_STALE_RUNBOOK = "docs/operacao/runbooks/RB-02-fila-parada.md";

    private CentralCompleteAlerts() {
    }

    /**
     * A regra existente {@code central.collector.stale} (V9_1, catalogada
     * pela P3.11 com runbook RB-02), referenciada — NÃO uma regra nova.
     * Os valores espelham {@code alert-rules.json} da P3.11 e a semente
     * da V9_1: {@code > 900}, 1 leitura, CRITICAL, GLOBAL, owner platform.
     */
    public static AlertRule collectorStaleRule() {
        return new AlertRule(COLLECTOR_STALE_RULE_KEY, COLLECTOR_METRIC_KEY, ">", 900,
                1, "CRITICAL", "GLOBAL", "platform", COLLECTOR_STALE_RUNBOOK);
    }

    /** Medição (real ou simulada no exercício) da coleta: nome, sucesso e idade da última coleta em segundos. */
    public record ProbeResult(String probeName, boolean success, long lastCollectionAgeSeconds) {
        public ProbeResult {
            if (probeName == null || probeName.isBlank()) {
                throw new IllegalArgumentException("Nome da sonda é obrigatório");
            }
            if (lastCollectionAgeSeconds < 0) {
                throw new IllegalArgumentException("Idade da coleta não pode ser negativa");
            }
        }
    }

    /** Sinal de alerta avaliado (entrada da abertura de {@code alert_event} na fiação). */
    public record AlertSignal(String ruleKey, String probeName, double measuredValue,
                              double threshold, String detail) {
    }

    /**
     * Avalia a medição contra a regra EXISTENTE recebida: dispara se e
     * somente se o comparador da regra, aplicado à idade medida contra o
     * limiar da regra, é verdadeiro (na regra da V9_1: {@code idade > 900}
     * — exatamente 900 NÃO dispara). Regra de outra métrica é recusada:
     * avaliar a coleta contra regra alheia fingiria uma condição que a
     * regra não descreve.
     */
    public static Optional<AlertSignal> evaluateCollectorStale(AlertRule rule, ProbeResult probe) {
        Objects.requireNonNull(rule, "regra existente (V9_1/P3.11) é obrigatória");
        Objects.requireNonNull(probe, "medição da coleta é obrigatória");
        if (!COLLECTOR_METRIC_KEY.equals(rule.metricKey())) {
            throw new IllegalArgumentException("Regra " + rule.ruleKey() + " não é da métrica da coleta ("
                    + COLLECTOR_METRIC_KEY + "): " + rule.metricKey());
        }
        double measured = probe.lastCollectionAgeSeconds();
        boolean fires = switch (rule.comparator()) {
            case ">" -> measured > rule.threshold();
            case ">=" -> measured >= rule.threshold();
            case "<" -> measured < rule.threshold();
            case "<=" -> measured <= rule.threshold();
            case "=" -> measured == rule.threshold();
            default -> throw new IllegalArgumentException("Comparador fora do catálogo da V9_1: " + rule.comparator());
        };
        if (!fires) {
            return Optional.empty();
        }
        String detail = "Coleta da Central velha: " + probe.lastCollectionAgeSeconds() + "s "
                + rule.comparator() + " " + (long) rule.threshold() + "s (regra " + rule.ruleKey()
                + " da V9_1/P3.11" + (probe.success() ? "" : "; medição simulada sem sucesso — falha provocada") + ")";
        return Optional.of(new AlertSignal(rule.ruleKey(), probe.probeName(), measured, rule.threshold(), detail));
    }

    /**
     * Aba Infraestrutura: visão de um SLO com burn rates CALCULADOS pela
     * {@link BurnRateCalculator} da P3.11 nas janelas curta e longa; o
     * tier deriva DESSES burn rates calculados (M3), nunca de doubles do
     * chamador. Janela sem eventos é o erro explícito da própria
     * calculadora — nunca burn zero presumido.
     */
    public record InfraSloView(SloDefinition slo, BurnRateCalculator.Result burnShortWindow,
                               BurnRateCalculator.Result burnLongWindow,
                               BurnRateCalculator.AlertTier tierShortLong) {
    }

    public static InfraSloView infraSlo(SloDefinition slo, long totalShort, long badShort,
                                        long totalLong, long badLong) {
        BurnRateCalculator.Result burnShort = BurnRateCalculator.calculate(slo, totalShort, badShort);
        BurnRateCalculator.Result burnLong = BurnRateCalculator.calculate(slo, totalLong, badLong);
        return new InfraSloView(slo, burnShort, burnLong,
                BurnRateCalculator.tierFor(burnShort.burnRate(), burnLong.burnRate()));
    }

    /** Item do Radar da Central: vencimento agregado próximo ou vencido (sem dado pessoal). */
    public record RadarItem(String kind, UUID tenantId, java.time.LocalDate dueDate, String detail) {
        public RadarItem {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("Tipo do item de radar é obrigatório");
            }
            // b3: tenant e detalhe são obrigatórios — item de radar sem
            // tenant não é atribuível e sem detalhe não é acionável.
            Objects.requireNonNull(tenantId, "tenant do item de radar é obrigatório");
            Objects.requireNonNull(detail, "detalhe do item de radar é obrigatório");
            if (detail.isBlank()) {
                throw new IllegalArgumentException("Detalhe do item de radar não pode ser vazio");
            }
            Objects.requireNonNull(dueDate, "vencimento do item de radar é obrigatório");
        }

        public long daysRemaining(java.time.LocalDate reference) {
            Objects.requireNonNull(reference, "referência é obrigatória");
            return dueDate.toEpochDay() - reference.toEpochDay();
        }

        public boolean overdueAt(java.time.LocalDate reference) {
            return daysRemaining(reference) < 0;
        }
    }

    /** Radar ordenado por vencimento; janela em dias é do chamador (configuração), nunca presumida. */
    public static List<RadarItem> radarWithin(List<RadarItem> items,
                                              java.time.LocalDate reference, long windowDays) {
        Objects.requireNonNull(items, "itens do radar são obrigatórios");
        Objects.requireNonNull(reference, "referência é obrigatória");
        if (windowDays < 0) {
            throw new IllegalArgumentException("Janela do radar não pode ser negativa");
        }
        return items.stream()
                .filter(i -> i.daysRemaining(reference) <= windowDays)
                .sorted(Comparator.comparing(RadarItem::dueDate))
                .toList();
    }
}
