package br.com.ziipsan.core.access;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas de auditoria e de vínculo da P3.17 (ADR-0020).
 *
 * <p>O aceite exige trilha em dois lugares, conforme o modo:
 * <ul>
 *   <li><b>assistido</b>: cada requisição — permitida ou negada — no
 *       {@code audit_log} DO TENANT, com o id do consentimento;</li>
 *   <li><b>emergência</b>: cada requisição — permitida ou negada — no
 *       {@code audit_log} do tenant E em {@code platform.audit_event}, com
 *       o id do incidente; e a sessão registrada no registro do incidente.</li>
 * </ul>
 *
 * <p>As portas separam a regra (pura, testável sem banco) da escrita: a
 * implementação JDBC é o {@link AccessSessionService}; os testes usam
 * dublês em memória e conferem a trilha entrada a entrada. O JSON dos
 * detalhes sai só pela classe {@code Json} do core.
 */
public interface AccessAuditPort {

    /** Grava a entrada no {@code audit_log} do tenant (public.audit_log). */
    void appendTenantAudit(AccessAuditEntry entry);

    /** Grava a entrada em {@code platform.audit_event} (só emergência). */
    void appendPlatformAudit(AccessAuditEntry entry);

    /**
     * Uma requisição (ou tentativa) auditada. {@code sessionId} é nulo nas
     * tentativas negadas antes de existir sessão; nesse caso o
     * {@code requestId} é a identidade da tentativa na trilha.
     * {@code consentId}/{@code incidentId} carregam o vínculo do modo —
     * nulo quando a própria ausência dele é o motivo da negação.
     */
    record AccessAuditEntry(
            UUID tenantId,
            AccessMode mode,
            UUID sessionId,
            UUID requestId,
            UUID actorId,
            String actorRole,
            AccessOperation operation,
            boolean allowed,
            String decisionCode,
            UUID consentId,
            UUID incidentId,
            Instant occurredAt) {

        public AccessAuditEntry {
            Objects.requireNonNull(tenantId, "tenantId da entrada de auditoria é obrigatório");
            Objects.requireNonNull(mode, "mode da entrada de auditoria é obrigatório");
            Objects.requireNonNull(requestId, "requestId da entrada de auditoria é obrigatório");
            Objects.requireNonNull(actorId, "actorId da entrada de auditoria é obrigatório");
            Objects.requireNonNull(operation, "operation da entrada de auditoria é obrigatória");
            Objects.requireNonNull(decisionCode, "decisionCode da entrada de auditoria é obrigatório");
            Objects.requireNonNull(occurredAt, "occurredAt da entrada de auditoria é obrigatório");
        }

        /** Detalhes JSON da entrada (metadata do audit_log / details do audit_event), via {@code Json}. */
        public String detailsJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mode", mode.name());
            map.put("sessionId", sessionId == null ? null : sessionId.toString());
            map.put("requestId", requestId.toString());
            map.put("operation", operation.name());
            map.put("allowed", allowed);
            map.put("decisionCode", decisionCode);
            map.put("consentId", consentId == null ? null : consentId.toString());
            map.put("incidentId", incidentId == null ? null : incidentId.toString());
            return Json.write(map);
        }
    }
}

/** Leitura do registro do incidente (P3.10/P2.25) por id: ausência = vazio, nunca um incidente presumido. */
interface SecurityIncidentLookup {
    Optional<SecurityIncidentRef> find(UUID tenantId, UUID incidentId);
}

/** Registro da sessão de emergência NO registro do incidente ao qual ela se vincula. */
interface IncidentSessionRegistry {
    void registerSessionInIncident(AccessSession session);
}

/**
 * Aviso ao tenant sobre a sessão de emergência, enviado pela outbox
 * (P1.9): a implementação JDBC grava a linha em {@code public.outbox} na
 * mesma transação da abertura, com {@code dedup_key} derivada da sessão.
 */
interface EmergencyNoticePort {
    void scheduleTenantNotice(AccessSession session);
}
