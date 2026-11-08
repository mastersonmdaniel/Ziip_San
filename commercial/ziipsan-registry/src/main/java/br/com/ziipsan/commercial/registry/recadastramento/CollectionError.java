package br.com.ziipsan.commercial.registry.recadastramento;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Erro de coleta do recadastramento (ED-16): uma submissao de campo
 * recusada na sincronizacao do app de campo. O erro e REGISTRADO (nunca
 * some em silencio) e alimenta o relatorio de erros no recadastramento
 * (TR-685/SG-911); a submissao recusada nao cria campo coletado nem toca
 * o cadastro.
 *
 * @param detail evidencia legivel da recusa, sem dado pessoal em claro
 *               (valor de documento nunca aparece aqui, nem mascarado:
 *               so o campo e o codigo do erro)
 */
public record CollectionError(
        UUID id,
        UUID batchId,
        UUID taskId,
        UUID unitId,
        String targetType,
        String field,
        CollectionErrorCode code,
        String detail,
        UUID collectorId,
        OffsetDateTime occurredAt) {

    public CollectionError {
        if (id == null || batchId == null || code == null) {
            throw new IllegalArgumentException("erro de coleta exige id, lote e codigo");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("erro de coleta sem evidencia (detail) nao e registrado: " + code);
        }
        if (detail.length() > 500) {
            throw new IllegalArgumentException("detail do erro de coleta excede 500 caracteres (" + code + ")");
        }
    }

    public static CollectionError of(UUID batchId, UUID taskId, UUID unitId, String targetType,
                                     String field, CollectionErrorCode code, String detail,
                                     UUID collectorId, OffsetDateTime occurredAt) {
        return new CollectionError(UUID.randomUUID(), batchId, taskId, unitId, targetType, field,
                code, detail, collectorId, occurredAt);
    }
}
