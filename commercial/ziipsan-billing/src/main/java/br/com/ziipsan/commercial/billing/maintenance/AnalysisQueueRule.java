package br.com.ziipsan.commercial.billing.maintenance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de analise das leituras e contas retidas (JOR-09, aceite da P2.20):
 * cada item traz a leitura/conta retida, a comparacao com a media e os
 * ultimos meses, a foto e a ocorrencia da leitura; as acoes em lote sao
 * liberar, manter, abrir revisao ({@code BILL_REVIEW}) e pedir releitura.
 * Regra pura: o servico monta os itens a partir das linhas da
 * {@link AnalysisQueueSource} (fatos do metering e do billing) e aplica o
 * plano daqui; a foto e referencia opaca do registro de captura da leitura
 * (null quando a leitura nao tem foto -- a fila mostra a ausencia, nao
 * inventa imagem).
 */
public final class AnalysisQueueRule {

    public enum QueueAction {
        RELEASE,
        KEEP_HELD,
        OPEN_REVIEW,
        REQUEST_REREADING
    }

    /**
     * Item da fila: fatos da leitura/conta + serie para comparacao.
     * {@code rereadingOrderTypeCode} e o tipo de OS que a ocorrencia da
     * leitura declara no catalogo de anormalidades
     * ({@code reading_anomalies.service_order_type_code}, V5_7 da P2.5);
     * null = a ocorrencia nao gera OS, e pedir releitura do item e recusado.
     */
    public record QueueItem(UUID readingId, UUID billId, UUID unitId,
                            Integer currentConsumption, Integer averageConsumption,
                            List<Integer> lastMonthsConsumption, String photoRef,
                            String occurrenceCode, String holdReason,
                            String rereadingOrderTypeCode) {
        public QueueItem {
            lastMonthsConsumption = lastMonthsConsumption == null ? List.of() : List.copyOf(lastMonthsConsumption);
            if (currentConsumption != null && currentConsumption < 0) {
                throw new IllegalArgumentException("Consumo atual nao pode ser negativo");
            }
            if (averageConsumption != null && averageConsumption < 0) {
                throw new IllegalArgumentException("Media nao pode ser negativa");
            }
        }

        /**
         * Desvio percentual do consumo atual contra a media:
         * {@code (atual - media) / media * 100}, 2 casas HALF_UP.
         * Sem media (ou media zero) nao ha desvio calculavel: null.
         */
        public BigDecimal deviationPercent() {
            if (currentConsumption == null || averageConsumption == null || averageConsumption == 0) {
                return null;
            }
            return BigDecimal.valueOf(currentConsumption - averageConsumption)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(averageConsumption), 2, RoundingMode.HALF_UP);
        }
    }

    /** Decisao planejada para um item, com a OS quando a acao a exige. */
    public record PlannedAction(QueueItem item, QueueAction action, BillOrderType requiredOrderType) {
    }

    private AnalysisQueueRule() {
    }

    /**
     * Planeja uma acao em lote: valida o lote inteiro antes de qualquer
     * efeito (lote vazio, item duplicado ou item sem o fato que a acao
     * exige recusam o lote inteiro).
     * <ul>
     *   <li>RELEASE: libera a retencao (item precisa ter billId ou readingId);</li>
     *   <li>KEEP_HELD: mantem retido, so registra a decisao;</li>
     *   <li>OPEN_REVIEW: exige conta (billId) -- a revisao e da conta;</li>
     *   <li>REQUEST_REREADING: exige leitura (readingId) e o tipo de OS que
     *       a ocorrencia declara (rereadingOrderTypeCode) -- sem tipo no
     *       catalogo de anormalidades nao ha OS de releitura a pedir.</li>
     * </ul>
     */
    public static List<PlannedAction> plan(List<QueueItem> items, QueueAction action) {
        Objects.requireNonNull(action, "action");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Lote da fila nao pode ser vazio");
        }
        long distinct = items.stream().map(i -> i.readingId() + ":" + i.billId()).distinct().count();
        if (distinct != items.size()) {
            throw new IllegalArgumentException("Lote da fila tem item duplicado");
        }
        BillOrderType orderType = action == QueueAction.OPEN_REVIEW ? BillOrderType.BILL_REVIEW : null;
        List<PlannedAction> planned = new java.util.ArrayList<>();
        for (QueueItem item : items) {
            switch (action) {
                case RELEASE, KEEP_HELD -> {
                    if (item.readingId() == null && item.billId() == null) {
                        throw new IllegalArgumentException("Item da fila sem leitura nem conta nao pode ser decidido");
                    }
                }
                case OPEN_REVIEW -> {
                    if (item.billId() == null) {
                        throw new IllegalArgumentException("Abrir BILL_REVIEW exige a conta retida do item");
                    }
                }
                case REQUEST_REREADING -> {
                    if (item.readingId() == null) {
                        throw new IllegalArgumentException("Pedir releitura exige a leitura do item");
                    }
                    if (item.rereadingOrderTypeCode() == null || item.rereadingOrderTypeCode().isBlank()) {
                        throw new IllegalArgumentException("Pedir releitura exige o tipo de OS que a ocorrencia "
                                + item.occurrenceCode() + " declara no catalogo de anormalidades "
                                + "(reading_anomalies.service_order_type_code)");
                    }
                }
            }
            planned.add(new PlannedAction(item, action, orderType));
        }
        return List.copyOf(planned);
    }
}
