package br.com.ziipsan.commercial.fiscal.queue;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Linha da trilha de ações do operador (V4_11): quem fez, com qual permissão, por
 * qual motivo, sobre qual documento (ou faixa, na inutilização), em qual ambiente e
 * com qual desfecho. O {@code detailJson} é serializado com a classe {@code Json} do
 * core por quem grava — nunca {@code Map.toString()}.
 *
 * @param idempotencyKey chave da tentativa: para o reenvio, deriva do documento e da
 *                       sua chave de acesso — reenvio repetido da mesma tentativa
 *                       encontra esta linha e não gera segundo documento nem segunda
 *                       chamada à porta (aceite expresso)
 */
public record FiscalOperatorAction(UUID id, UUID tenantId, FiscalOperatorActionType type,
                                   FiscalOperatorActionStatus status, UUID fiscalDocumentId,
                                   UUID issuerId, Integer serie, Long numberFrom, Long numberTo,
                                   UUID actorId, String permission, String reason,
                                   Integer tpAmb, String protocol, String idempotencyKey,
                                   String detailJson, OffsetDateTime createdAt) {

    public FiscalOperatorAction {
        Objects.requireNonNull(id, "Ação é obrigatória");
        Objects.requireNonNull(tenantId, "Tenant é obrigatório");
        Objects.requireNonNull(type, "Tipo da ação é obrigatório");
        Objects.requireNonNull(status, "Estado da ação é obrigatório");
        Objects.requireNonNull(actorId, "Autor da ação é obrigatório (trilha)");
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("Ação sem a permissão que a autorizou não entra na trilha");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Ação do operador exige motivo (aceite da P2.33)");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Ação do operador exige chave de idempotência");
        }
        Objects.requireNonNull(createdAt, "Instante da ação é obrigatório");
    }
}
