package br.com.ziipsan.platform.central;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Visões das abas Faturamento e Fiscal da Central (P2.11) sobre os
 * instantâneos agregados: contagens por situação/estado, a contagem de
 * atraso, o tamanho da fila contra o limiar do RNF-08 e o sinal de
 * alerta avaliado. Serviço puro sobre os instantâneos recebidos — a
 * coleta (quem lê os agregados dos tenants e monta os instantâneos) é
 * a fiação do coletor da Central na integração, no modelo
 * {@code platform_stats.*} da V9_1; nenhum dado pessoal atravessa esta
 * fronteira.
 *
 * <p><b>Contrato do coletor — tenant sem cronograma (m-3):</b> para um
 * tenant ainda sem cronograma na referência, o coletor monta um
 * {@link BillingCycleSnapshot} com a lista de itens vazia (ausência
 * confirmada na fonte); a visão sai com contagens zero e sem alerta, e
 * a coleta dos demais tenants segue. O coletor NÃO deve pular o tenant
 * em silêncio nem montar instantâneo parcial: vazio explícito é o
 * sinal combinado de "sem cronograma".
 */
public final class CentralBillingFiscalService {

    /** Visão da aba Faturamento para um tenant e uma referência. */
    public record BillingTabView(CentralTab tab, BillingCycleSnapshot snapshot,
                                 Map<String, Long> countByStatus, long overdueMandatoryCount,
                                 Optional<CentralAlertSignal> alert) {
    }

    /** Visão da aba Fiscal para um tenant. */
    public record FiscalTabView(CentralTab tab, FiscalQueueSnapshot snapshot,
                                long queueSize, long queueLimitRnf08, boolean overLimit,
                                Optional<CentralAlertSignal> alert) {
    }

    public BillingTabView billingTab(BillingCycleSnapshot snapshot, LocalDate today) {
        Objects.requireNonNull(snapshot, "instantâneo do ciclo é obrigatório");
        Objects.requireNonNull(today, "a data de hoje é obrigatória");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : BillingCycleSnapshot.ITEM_STATUSES) {
            counts.put(status, 0L);
        }
        for (BillingCycleSnapshot.ScheduleItemView item : snapshot.items()) {
            counts.merge(item.status(), 1L, Long::sum);
        }
        return new BillingTabView(CentralTab.FATURAMENTO, snapshot, Map.copyOf(counts),
                CentralBillingFiscalAlerts.overdueMandatoryItems(snapshot, today).size(),
                CentralBillingFiscalAlerts.evaluateBillingCycle(snapshot, today));
    }

    public FiscalTabView fiscalTab(FiscalQueueSnapshot snapshot, CentralAlertThresholds thresholds) {
        Objects.requireNonNull(snapshot, "instantâneo fiscal é obrigatório");
        Objects.requireNonNull(thresholds, "limiares são obrigatórios (RNF-08)");
        Optional<CentralAlertSignal> alert = CentralBillingFiscalAlerts.evaluateFiscalQueue(snapshot, thresholds);
        return new FiscalTabView(CentralTab.FISCAL, snapshot, snapshot.queueSize(),
                thresholds.fiscalQueueLimitRnf08(), alert.isPresent(), alert);
    }
}
