package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Historico do consumidor (P3.20): contas e pagamentos de 120 MESES numa
 * so lista, cada documento com a sua ORIGEM (emitido pelo Ziip SAN,
 * importado do sistema anterior ou do emissor do intervalo). O consumidor
 * so ve as matriculas VINCULADAS a conta dele; unidade nao vinculada e
 * recusada antes de qualquer leitura.
 */
public final class ConsumerHistoryService {

    public static final int WINDOW_MONTHS = 120;

    public enum DocumentOrigin { ZIIPSAN, PREVIOUS_SYSTEM, INTERVAL_ISSUER }

    public enum EntryKind { BILL, PAYMENT }

    public record HistoryEntry(EntryKind kind, UUID documentId, UUID unitId, YearMonth reference,
                               LocalDate dueOrPaidDate, BigDecimal amount, DocumentOrigin origin,
                               boolean settled) {
        public HistoryEntry {
            if (kind == null || documentId == null || unitId == null || reference == null
                    || dueOrPaidDate == null || amount == null || origin == null) {
                throw new IllegalArgumentException("entrada do historico incompleta (origem e obrigatoria)");
            }
        }
    }

    public interface HistoryPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        List<HistoryEntry> bills(UUID tenantId, UUID unitId, YearMonth fromInclusive);
        List<HistoryEntry> payments(UUID tenantId, UUID unitId, YearMonth fromInclusive);
    }

    private final HistoryPort port;

    public ConsumerHistoryService(HistoryPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    /** Lista unica (contas + pagamentos) dos ultimos 120 meses, mais recente primeiro. */
    public List<HistoryEntry> history(UUID tenantId, String consumerSubject, UUID unitId, YearMonth current) {
        ConsumerAccessGuard.requireAuthenticatedChannel(ConsumerAccessGuard.Channel.OWN_ACCOUNT);
        return history(tenantId, consumerSubject, unitId, current, ConsumerAccessGuard.Channel.OWN_ACCOUNT);
    }

    public List<HistoryEntry> history(UUID tenantId, String consumerSubject, UUID unitId, YearMonth current,
                                      ConsumerAccessGuard.Channel channel) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank()
                || unitId == null || current == null) {
            throw new IllegalArgumentException("tenant, consumidor, unidade e mes de referencia sao obrigatorios");
        }
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new ApiException.Forbidden("Unidade nao vinculada a esta conta do portal.");
        }
        YearMonth from = current.minusMonths(WINDOW_MONTHS - 1L);
        List<HistoryEntry> all = new ArrayList<>();
        all.addAll(port.bills(tenantId, unitId, from));
        all.addAll(port.payments(tenantId, unitId, from));
        // Defesa em profundidade: mesmo que a porta devolva fora da janela, nada alem de 120 meses sai.
        all.removeIf(e -> e.reference().isBefore(from) || e.reference().isAfter(current));
        all.sort(Comparator.comparing(HistoryEntry::dueOrPaidDate).reversed()
                .thenComparing(e -> e.kind().name()));
        return List.copyOf(all);
    }
}
