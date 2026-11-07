package br.com.ziipsan.reporting.export;

import java.util.UUID;

/**
 * Porta de registro da exportacao no {@code personal_data_access_log}
 * (regra unica da P1.12: TODA exportacao e registrada, inclusive a negada).
 */
public interface ExportAuditRecorder {

    void recordExport(UUID tenantId, UUID userId, String reportCode, String result, String purpose);
}
