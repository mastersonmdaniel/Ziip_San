package br.com.ziipsan.platform.central.complete;

import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P3.15: instantâneos AGREGADOS que alimentam as abas próprias da
 * Central (padrão da P2.11: a Central não lê schema de tenant; quem
 * coleta monta estes registros a partir dos agregados — fiação do
 * coletor na integração). Contagem negativa ou mapa incompleto é erro
 * explícito, nunca zero presumido.
 */
public final class CentralSnapshots {

    private CentralSnapshots() {
    }

    private static long nonNegative(String field, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Contagem negativa em instantâneo da Central: " + field + "=" + value);
        }
        return value;
    }

    /** Aba Arrecadação (fonte: conciliação da P3.1). Valores em centavos. */
    public record CollectionSnapshot(UUID tenantId, YearMonth reference, long settledCount,
                                     long settledAmountCents, long pendingCount,
                                     long duplicateCount, long unidentifiedCount) {
        public CollectionSnapshot {
            if (tenantId == null || reference == null) {
                throw new IllegalArgumentException("tenant e referência são obrigatórios no instantâneo de arrecadação");
            }
            nonNegative("settledCount", settledCount);
            nonNegative("settledAmountCents", settledAmountCents);
            nonNegative("pendingCount", pendingCount);
            nonNegative("duplicateCount", duplicateCount);
            nonNegative("unidentifiedCount", unidentifiedCount);
        }
    }

    /** Aba Pix (fonte: Pix Cobrança da P3.2). */
    public record PixSnapshot(UUID tenantId, YearMonth reference, long callbacksReceived,
                              long callbacksConfirmed, long duplicatesIgnored, long forgedRejected,
                              long recoveredBySweep, long autoDebitActiveCount) {
        public PixSnapshot {
            if (tenantId == null || reference == null) {
                throw new IllegalArgumentException("tenant e referência são obrigatórios no instantâneo Pix");
            }
            nonNegative("callbacksReceived", callbacksReceived);
            nonNegative("callbacksConfirmed", callbacksConfirmed);
            nonNegative("duplicatesIgnored", duplicatesIgnored);
            nonNegative("forgedRejected", forgedRejected);
            nonNegative("recoveredBySweep", recoveredBySweep);
            nonNegative("autoDebitActiveCount", autoDebitActiveCount);
            if (callbacksConfirmed > callbacksReceived) {
                throw new IllegalArgumentException("Pix: confirmados (" + callbacksConfirmed
                        + ") maior que recebidos (" + callbacksReceived + ") — instantâneo incoerente");
            }
        }
    }

    /** Aba LGPD (fontes: resposta a incidente e RIPD da P3.10). */
    public record LgpdSnapshot(UUID tenantId, YearMonth reference, long openIncidents,
                               long incidentsNotifiedOnTime, long incidentsOverdue,
                               long openDataSubjectRequests, long overdueDataSubjectRequests,
                               long ripdModelsPublished) {
        public LgpdSnapshot {
            if (tenantId == null || reference == null) {
                throw new IllegalArgumentException("tenant e referência são obrigatórios no instantâneo LGPD");
            }
            nonNegative("openIncidents", openIncidents);
            nonNegative("incidentsNotifiedOnTime", incidentsNotifiedOnTime);
            nonNegative("incidentsOverdue", incidentsOverdue);
            nonNegative("openDataSubjectRequests", openDataSubjectRequests);
            nonNegative("overdueDataSubjectRequests", overdueDataSubjectRequests);
            nonNegative("ripdModelsPublished", ripdModelsPublished);
        }
    }

    /** Aba Suporte: chamados por severidade (S1–S5 da V10_2, P2.15). Os cinco devem vir declarados. */
    public record SupportSnapshot(UUID tenantId, YearMonth reference,
                                  Map<String, Long> openBySeverity, long breachedCount,
                                  long csatResponses, long csatSum) {
        public static final Set<String> SEVERITIES = Set.of("S1", "S2", "S3", "S4", "S5");

        public SupportSnapshot {
            if (tenantId == null || reference == null) {
                throw new IllegalArgumentException("tenant e referência são obrigatórios no instantâneo de suporte");
            }
            if (openBySeverity == null || !openBySeverity.keySet().equals(SEVERITIES)) {
                throw new IllegalArgumentException(
                        "Instantâneo de suporte deve declarar as cinco severidades S1–S5 (zero inclusive): "
                                + (openBySeverity == null ? null : openBySeverity.keySet()));
            }
            openBySeverity.forEach((k, v) -> {
                if (v == null) {
                    throw new IllegalArgumentException("Contagem nula na severidade " + k);
                }
                nonNegative("openBySeverity." + k, v);
            });
            openBySeverity = Map.copyOf(openBySeverity);
            nonNegative("breachedCount", breachedCount);
            nonNegative("csatResponses", csatResponses);
            nonNegative("csatSum", csatSum);
            if (csatResponses == 0 && csatSum != 0) {
                throw new IllegalArgumentException("CSAT: soma sem respostas é incoerente");
            }
            if (csatResponses > 0 && (csatSum < csatResponses || csatSum > 5 * csatResponses)) {
                throw new IllegalArgumentException("CSAT: soma fora de [respostas, 5×respostas] (nota 1–5 da V10_2)");
            }
        }

        public long totalOpen() {
            return openBySeverity.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
