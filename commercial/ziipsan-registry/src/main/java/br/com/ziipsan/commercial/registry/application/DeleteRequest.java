package br.com.ziipsan.commercial.registry.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corpo do DELETE (exclusão lógica): o motivo vai para {@code deleted_reason}, que a auditoria redige.
 * P1.3: {@code linkEndReasonId} é o motivo de fim dos vínculos por catálogo (CHECK da V3_12);
 * exigido pela exclusão de unidade com vínculos vigentes.
 */
public record DeleteRequest(@NotBlank(message = "Informe o motivo da exclusão.") @Size(max = 200) String reason,
                            UUID linkEndReasonId) {
    /** Forma antiga (só o motivo), para não quebrar construção existente. */
    public DeleteRequest(String reason) {
        this(reason, null);
    }
}
