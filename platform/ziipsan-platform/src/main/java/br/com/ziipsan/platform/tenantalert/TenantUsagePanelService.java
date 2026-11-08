package br.com.ziipsan.platform.tenantalert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-07: o painel de uso e desempenho DO TENANT (não é a Central Ziip da
 * P1.17, que é da plataforma): por período, os lotes do tenant (P2.1), os
 * alertas gerados e as entregas de webhook (P2.26).
 *
 * <p>Honestidade de fonte (precedente da P3.39): seção cuja fonte a fiação
 * ainda não ligou nasce {@code available=false} com contadores nulos — nunca
 * zero fingido. Taxa sem denominador (nenhuma execução/entrega no período)
 * é nula, não 100% nem 0%.
 *
 * <p>TODAS as seções respeitam o MESMO período [from, to) — inclusive a de
 * alertas (correção D2 pós-reprovação: a contagem recebia só o início e
 * somava alertas posteriores ao fim do período).
 */
public final class TenantUsagePanelService {

    public record Rate(BigDecimal value) {
        public static Rate of(long numerator, long denominator) {
            if (denominator <= 0) {
                return null;
            }
            return new Rate(BigDecimal.valueOf(numerator)
                    .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP));
        }
    }

    public record BatchSection(boolean available, Long runsTotal, Long runsDone, Long runsFailed,
                               Long runsInterrupted, Rate successRate, Long averageDurationMillis) {
        static BatchSection unavailable() {
            return new BatchSection(false, null, null, null, null, null, null);
        }
    }

    public record AlertSection(boolean available, Long alertsGenerated) {
        static AlertSection unavailable() {
            return new AlertSection(false, null);
        }
    }

    public record WebhookSection(boolean available, Long deliveriesTotal, Long deliveriesSucceeded,
                                 Long deliveriesFailed, Rate successRate, Integer endpointsDisabled) {
        static WebhookSection unavailable() {
            return new WebhookSection(false, null, null, null, null, null);
        }
    }

    public record TenantUsagePanel(UUID tenantId, Instant from, Instant to,
                                   BatchSection batch, AlertSection alerts, WebhookSection webhooks) {
    }

    private final TenantAlertPorts.BatchStatsSource batchSource;
    private final TenantAlertPorts.WebhookStatsSource webhookSource;
    private final TenantAlertPorts.AlertStore alertStore;

    public TenantUsagePanelService(TenantAlertPorts.BatchStatsSource batchSource,
                                   TenantAlertPorts.WebhookStatsSource webhookSource,
                                   TenantAlertPorts.AlertStore alertStore) {
        this.batchSource = Objects.requireNonNull(batchSource, "a fonte de lotes é obrigatória");
        this.webhookSource = Objects.requireNonNull(webhookSource, "a fonte de webhooks é obrigatória");
        this.alertStore = Objects.requireNonNull(alertStore, "a loja de alertas é obrigatória");
    }

    public TenantUsagePanel panelOf(UUID tenantId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "o painel exige o tenant");
        Objects.requireNonNull(from, "o painel exige o início do período");
        Objects.requireNonNull(to, "o painel exige o fim do período");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("o período do painel exige fim depois do início");
        }
        BatchSection batch = batchSource.statsOf(tenantId, from, to)
                .map(s -> new BatchSection(true, s.runsTotal(), s.runsDone(), s.runsFailed(), s.runsInterrupted(),
                        Rate.of(s.runsDone(), s.runsTotal()),
                        s.averageDuration() == null ? null : s.averageDuration().toMillis()))
                .orElseGet(BatchSection::unavailable);
        AlertSection alerts = new AlertSection(true, alertStore.countByTenantBetween(tenantId, from, to));
        WebhookSection webhooks = webhookSource.statsOf(tenantId, from, to)
                .map(s -> new WebhookSection(true, s.deliveriesTotal(), s.deliveriesSucceeded(),
                        s.deliveriesFailed(), Rate.of(s.deliveriesSucceeded(), s.deliveriesTotal()),
                        s.endpointsDisabled()))
                .orElseGet(WebhookSection::unavailable);
        return new TenantUsagePanel(tenantId, from, to, batch, alerts, webhooks);
    }

    /** Fonte ausente explícita, para a fiação declarar a seção até ligar a dona. */
    public static TenantAlertPorts.BatchStatsSource noBatchSource() {
        return (tenantId, from, to) -> Optional.empty();
    }

    public static TenantAlertPorts.WebhookStatsSource noWebhookSource() {
        return (tenantId, from, to) -> Optional.empty();
    }
}
