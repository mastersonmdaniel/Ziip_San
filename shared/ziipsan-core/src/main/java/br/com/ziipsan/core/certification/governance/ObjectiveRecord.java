package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.UUID;
/** Objetivo ligado a indicador do registro P3.41 (JOR-19): o codigo so e aceito se existir la (porta). */
public record ObjectiveRecord(UUID id, UUID tenantId, String title, String indicatorCode, LocalDate dueDate, String ownerArea) {
    public ObjectiveRecord {
        if (title==null||title.isBlank()) throw new GovernanceValidationException("OBJECTIVE_TITLE_REQUIRED","titulo obrigatorio");
        if (indicatorCode==null||indicatorCode.isBlank()) throw new GovernanceValidationException("OBJECTIVE_INDICATOR_REQUIRED","objetivo exige indicador do registro P3.41");
        if (dueDate==null) throw new GovernanceValidationException("OBJECTIVE_DUE_REQUIRED","objetivo exige prazo declarado");
    }
}
