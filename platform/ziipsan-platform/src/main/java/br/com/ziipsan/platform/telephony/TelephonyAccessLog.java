package br.com.ziipsan.platform.telephony;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de acesso da telefonia (ED-44). O aceite EXIGE: a abertura da
 * ficha pela chamada recebida aparece no log de acesso. Semantica espelhada
 * na de PersonalDataAccessLog (P0.11/ADR-0008): append-only, um registro
 * por acesso, resultado explicito. A abertura por chamada grava
 * CALL_SCREEN_POP; a consulta da URA grava URA_QUERY (inclusive DENIED);
 * a geracao de lista grava DIALING_LIST_GENERATED.
 */
public record TelephonyAccessLog(UUID tenantId, UUID actorId, String uraClientId,
                                 AccessType accessType, UUID consumerUnitId,
                                 String callerNumberNormalized, Result result, Instant accessedAt) {
    public enum AccessType { CALL_SCREEN_POP, URA_QUERY, DIALING_LIST_GENERATED }
    public enum Result { FOUND, NOT_FOUND, AMBIGUOUS, DENIED }
    public TelephonyAccessLog {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(accessType, "tipo de acesso obrigatorio");
        Objects.requireNonNull(result, "resultado obrigatorio");
        Objects.requireNonNull(accessedAt, "instante obrigatorio");
    }
}
