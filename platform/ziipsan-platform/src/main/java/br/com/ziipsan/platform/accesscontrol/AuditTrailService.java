package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta da trilha por usuário (ED-06; TR-128, SG-062), sessões ativas e
 * exportação — a superfície do ADMIN do tenant sobre a trilha da P1.7:
 * <ul>
 *   <li>a consulta é POR USUÁRIO e por período, sobre o {@code audit_log}
 *       particionado da P1.7 (a ED-06 não cria trilha paralela);</li>
 *   <li>a exportação sai em JSON por linha via {@link Json} do core, SEM
 *       dado pessoal em claro além do que a própria trilha já registra
 *       (RNF-52 da P1.7);</li>
 *   <li>a integridade é VERIFICÁVEL pela prova da P1.7 (raiz diária por
 *       tenant/partição, fora do banco): a consulta devolve o resultado da
 *       conferência, não uma promessa;</li>
 *   <li>sessões ativas do tenant são visíveis ao ADMIN, que pode revogar
 *       (a revogação vai ao Keycloak pela porta de sessões E remove o
 *       espelho em {@code active_sessions} — correção D8: sessão revogada
 *       não continua listada como ativa).</li>
 * </ul>
 */
public class AuditTrailService {

    public record AuditEventView(UUID eventId, UUID userId, String action, Instant occurredAt,
                                 String detailJson) {
    }

    public record TrailExport(UUID userId, Instant from, Instant to, boolean integrityVerified,
                              String jsonLines) {
    }

    private final UUID tenantId;
    private final AccessControlPorts.AccessControlStore store;
    private final AccessControlPorts.AuditTrailPort trail;
    private final AccessControlPorts.SessionAdminPort sessions;

    public AuditTrailService(UUID tenantId, AccessControlPorts.AccessControlStore store,
                             AccessControlPorts.AuditTrailPort trail,
                             AccessControlPorts.SessionAdminPort sessions) {
        if (tenantId == null || store == null || trail == null || sessions == null) {
            throw new IllegalArgumentException("tenant, loja, trilha e sessões são obrigatórios");
        }
        this.tenantId = tenantId;
        this.store = store;
        this.trail = trail;
        this.sessions = sessions;
    }

    public List<AuditEventView> trailOfUser(UUID userId, Instant from, Instant to) {
        if (userId == null || from == null || to == null || !to.isAfter(from)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "period", "invalid-period", "A consulta da trilha exige usuário e período válido."));
        }
        return trail.eventsOfUser(userId, from, to);
    }

    /** Exportação JSONL da trilha do usuário, com o selo de integridade da P1.7. */
    public TrailExport exportTrailOfUser(UUID userId, Instant from, Instant to) {
        List<AuditEventView> events = trailOfUser(userId, from, to);
        boolean verified = trail.integrityVerifies(userId, from, to);
        StringBuilder lines = new StringBuilder();
        for (AuditEventView event : events) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", event.eventId().toString());
            row.put("userId", event.userId().toString());
            row.put("action", event.action());
            row.put("occurredAt", event.occurredAt().toString());
            row.put("detail", event.detailJson() == null ? null : Json.readMap(event.detailJson()));
            lines.append(Json.write(row)).append('\n');
        }
        return new TrailExport(userId, from, to, verified, lines.toString());
    }

    /** Sessões ativas do tenant, visíveis ao ADMIN. */
    public List<LoginGuard.ActiveSession> activeSessions() {
        return store.activeSessions();
    }

    public void revokeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessão é obrigatória");
        }
        boolean known = store.activeSessions().stream().anyMatch(s -> s.sessionId().equals(sessionId));
        if (!known) {
            throw new ApiException.NotFound("Sessão não encontrada.");
        }
        sessions.revokeSession(sessionId);
        store.removeSession(sessionId);
    }
}
