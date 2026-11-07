package br.com.ziipsan.core.restore;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Trilha da restauracao seletiva e da operacao em massa (P3.33).
 *
 * <p>O plano v3.0 §3 declara a P3.33 tarefa SEM migration: "restauracao
 * e desfazer operam sobre as tabelas existentes, com registro na
 * auditoria". O registro e, portanto, nas tabelas que ja existem:
 * {@code public.audit_log} (do tenant, com RLS) para os eventos do
 * tenant e {@code platform.audit_event} para os eventos de plataforma —
 * os mesmos dois destinos da P3.17. Esta classe define a ENTRADA e a
 * PORTA; a escrita JDBC sobre essas tabelas e fiacao da integracao
 * (mesmo desenho do {@code AccessAuditPort} da P3.17, cujos testes usam
 * dubles em memoria e conferem a trilha entrada a entrada).
 *
 * <p>Toda tentativa e registrada — permitida ou negada — com o codigo
 * estavel da decisao ({@link TenantRestorePolicy.Decision#code()} ou
 * {@code BulkOperation.BulkRefusedException#code()}): a trilha e a prova
 * do portao, nao so do sucesso.
 *
 * <p>JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public final class RestoreAudit {

    private RestoreAudit() {
    }

    /** Acoes registradas na trilha por esta tarefa. */
    public enum RestoreAction {
        /** Copia logica diaria gerada e gravada no armazenamento imutavel. */
        DAILY_COPY_CREATED,
        /** PITR restaurado na instancia lateral e schema extraido. */
        SIDE_RESTORE_EXTRACTED,
        /** Comparacao extraido x atual apresentada (diff). */
        RESTORE_COMPARED,
        /** Aplicacao da restauracao (substituicao ou mesclagem) em janela. */
        RESTORE_APPLIED,
        /** Tentativa negada pelo portao (papel, step-up, consentimento ou janela). */
        RESTORE_DENIED,
        /** Etapa de simulacao da operacao em massa. */
        BULK_SIMULATED,
        /** Etapa de aplicacao da operacao em massa. */
        BULK_APPLIED,
        /** Remocao por execucao (reversao da operacao em massa). */
        BULK_REVERTED,
        /** OS inversa aberta para corrigir efeito de negocio (ADR-0039). */
        INVERSE_ORDER_REQUESTED
    }

    /** Uma entrada da trilha. */
    public record RestoreAuditEntry(
            UUID tenantId,
            RestoreAction action,
            UUID actorId,
            String actorRole,
            boolean allowed,
            String decisionCode,
            UUID consentId,
            UUID runId,
            UUID restoreId,
            Instant targetInstant,
            Instant occurredAt) {

        public RestoreAuditEntry {
            Objects.requireNonNull(tenantId, "tenantId da entrada de auditoria e obrigatorio");
            Objects.requireNonNull(action, "action da entrada de auditoria e obrigatoria");
            Objects.requireNonNull(actorId, "actorId da entrada de auditoria e obrigatorio");
            if (decisionCode == null || decisionCode.isBlank()) {
                throw new IllegalArgumentException(
                        "decisionCode da entrada de auditoria e obrigatorio: a trilha registra o codigo estavel da decisao");
            }
            Objects.requireNonNull(occurredAt, "occurredAt da entrada de auditoria e obrigatorio");
        }

        /** Detalhes JSON da entrada (metadata do audit_log / details do audit_event), via {@code Json}. */
        public String detailsJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("action", action.name());
            map.put("actorRole", actorRole);
            map.put("allowed", allowed);
            map.put("decisionCode", decisionCode);
            map.put("consentId", consentId == null ? null : consentId.toString());
            map.put("runId", runId == null ? null : runId.toString());
            map.put("restoreId", restoreId == null ? null : restoreId.toString());
            map.put("targetInstant", targetInstant == null ? null : targetInstant.toString());
            map.put("occurredAt", occurredAt.toString());
            return Json.write(map);
        }
    }

    /** Porta de escrita da trilha (implementacao JDBC = fiacao da integracao). */
    public interface RestoreAuditPort {

        /** Grava a entrada no {@code audit_log} do tenant. */
        void appendTenantAudit(RestoreAuditEntry entry);

        /** Grava a entrada em {@code platform.audit_event}. */
        void appendPlatformAudit(RestoreAuditEntry entry);
    }
}
