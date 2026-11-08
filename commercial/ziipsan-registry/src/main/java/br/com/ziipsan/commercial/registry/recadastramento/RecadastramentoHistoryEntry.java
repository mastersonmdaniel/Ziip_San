package br.com.ziipsan.commercial.registry.recadastramento;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma linha do historico do recadastramento (ED-16): a prova de que UMA
 * alteracao aprovada foi aplicada como efeito da OS de lote — campo,
 * valor anterior e novo, quem coletou, quem aprovou, a OS e a linha
 * correspondente no historico do cadastro
 * ({@code cadastral_change_requests} da P1.3). E a partir destas linhas
 * (e da tabela da P1.3, sua irma no cadastro) que o historico da unidade
 * e do cliente mostra o recadastramento.
 *
 * <p>Valores de documento entram aqui MASCARADOS, como em todo o
 * historico do cadastro (regra da P1.3).
 */
public record RecadastramentoHistoryEntry(
        UUID id,
        UUID batchId,
        UUID serviceOrderId,
        UUID collectedFieldId,
        String targetType,
        UUID targetId,
        String field,
        String oldValue,
        String newValue,
        UUID collectorId,
        UUID approverId,
        UUID changeRequestId,
        OffsetDateTime appliedAt) {

    public RecadastramentoHistoryEntry {
        if (id == null || batchId == null || serviceOrderId == null || collectedFieldId == null
                || changeRequestId == null || appliedAt == null) {
            throw new IllegalArgumentException(
                    "historico do recadastramento exige lote, OS, campo, registro do cadastro e instante");
        }
    }

    public static RecadastramentoHistoryEntry of(RecadastramentoBatch batch, CollectedField collected,
                                                 UUID changeRequestId, OffsetDateTime appliedAt) {
        return new RecadastramentoHistoryEntry(UUID.randomUUID(), batch.id(), batch.serviceOrderId(),
                collected.id(), collected.targetType(), collected.targetId(), collected.field(),
                collected.valueAtCollection(), collected.collectedValue(), collected.collectorId(),
                collected.decidedBy(), changeRequestId, appliedAt);
    }
}
