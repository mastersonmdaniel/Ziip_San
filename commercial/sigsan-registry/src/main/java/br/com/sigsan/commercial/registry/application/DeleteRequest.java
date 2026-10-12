package br.com.sigsan.commercial.registry.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo do DELETE (exclusão lógica): o motivo vai para {@code deleted_reason}, que a auditoria redige. */
public record DeleteRequest(@NotBlank(message = "Informe o motivo da exclusão.") @Size(max = 200) String reason) {
}
