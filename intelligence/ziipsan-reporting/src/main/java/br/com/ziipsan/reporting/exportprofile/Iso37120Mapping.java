package br.com.ziipsan.reporting.exportprofile;

/**
 * Mapeamento de um indicador do produto para a referencia do indicador
 * na ABNT NBR ISO 37120:2021. A referencia externa e DECLARADA por quem
 * integra, a partir do texto da norma: esta entrega NAO traz nenhum
 * mapeamento de fabrica, porque o texto da ISO 37120 nao esta nas fontes
 * desta VM e inventar codigos de indicador da norma violaria a regra de
 * nenhum dado inventado. Sem mapeamento declarado, o perfil ISO 37120
 * RECUSA a exportacao (ver ExportProfileService).
 */
public record Iso37120Mapping(String indicatorCode, String iso37120Reference, String declaredBy) {

    public Iso37120Mapping {
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new ExportValidationException("EXPORT_MAPPING_CODE_REQUIRED",
                    "mapeamento ISO 37120 sem codigo de indicador do produto");
        }
        if (iso37120Reference == null || iso37120Reference.isBlank()) {
            throw new ExportValidationException("EXPORT_MAPPING_REFERENCE_REQUIRED",
                    "mapeamento de " + indicatorCode + " sem a referencia declarada na "
                            + ExportReferences.ISO_37120_REFERENCE);
        }
        if (declaredBy == null || declaredBy.isBlank()) {
            throw new ExportValidationException("EXPORT_MAPPING_DECLARANT_REQUIRED",
                    "mapeamento de " + indicatorCode + " sem quem declarou a referencia "
                            + "(a referencia nao e presumida pelo sistema)");
        }
    }
}
