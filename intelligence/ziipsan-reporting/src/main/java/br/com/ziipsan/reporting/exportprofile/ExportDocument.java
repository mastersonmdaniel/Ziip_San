package br.com.ziipsan.reporting.exportprofile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Documento de exportacao de um perfil da ED-82: cabecalho com a
 * referencia normativa nomeada no acceptance, os avisos do perfil e as
 * linhas (indicador + serie). Documento sem linha e recusado.
 * {@code notices} carrega, no PNQS, a natureza do premio e o aviso de
 * estrutura neutra; no ISO 37120, a referencia da edicao brasileira.
 */
public record ExportDocument(UUID tenantId, ExportProfileKind kind, String normativeReference,
                             List<String> notices, Instant generatedAt, List<ExportLine> lines) {

    public ExportDocument {
        if (tenantId == null) {
            throw new ExportValidationException("EXPORT_TENANT_REQUIRED", "exportacao sem tenant");
        }
        if (kind == null) {
            throw new ExportValidationException("EXPORT_KIND_REQUIRED", "exportacao sem perfil");
        }
        if (normativeReference == null || normativeReference.isBlank()) {
            throw new ExportValidationException("EXPORT_REFERENCE_REQUIRED",
                    "exportacao " + kind + " sem referencia normativa declarada");
        }
        if (generatedAt == null) {
            throw new ExportValidationException("EXPORT_GENERATED_AT_REQUIRED", "exportacao sem instante de geracao");
        }
        if (lines == null || lines.isEmpty()) {
            throw new ExportValidationException("EXPORT_DOCUMENT_EMPTY",
                    "documento " + kind + " sem nenhuma linha: nada a exportar");
        }
        notices = notices == null ? List.of() : List.copyOf(notices);
        lines = List.copyOf(lines);
    }
}
