package br.com.ziipsan.reporting.indicator;

import java.time.Instant;
import java.util.UUID;

/**
 * Trilha de auditoria do registro (P3.41): toda mutacao relevante —
 * definicao, nova versao de formula, meta, fechamento e, em especial, a
 * REABERTURA de periodo fechado, que so existe com motivo registrado aqui
 * e no proprio fechamento.
 */
public record IndicatorAuditTrailEntry(UUID id, UUID tenantId, String indicatorCode,
                                       String action, String detail, String reason,
                                       UUID actor, Instant at) {
}
