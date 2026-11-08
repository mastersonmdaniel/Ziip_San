package br.com.ziipsan.reporting.exportprofile;

/**
 * Um item do checklist de completude do cadastro tecnico da ED-82.
 *
 * <p><b>Fronteira dura:</b> o texto das NBR 12586, 12587, 13133 e 14166
 * NAO esta disponivel nesta VM (a P1.28 compra os textos, guarda so a
 * referencia no produto e mantem as normas fora do repositorio, pela
 * licenca da ABNT). Por isso cada item e um ITEM DE VERIFICACAO NOMEADO:
 * o topico vem do que as fontes nomeiam (o acceptance da ED-82 e os
 * relatorios do cadastro da P3.27) e o requisito normativo fica
 * {@code [SEM FONTE]} - nenhum requisito de norma e fabricado aqui.
 * Na integracao, quem avalia confere o item contra o texto comprado.
 */
public record CadastreChecklistItem(String itemCode, String normReference, String verificationTopic,
                                    String requirementSource, boolean requiresReferenceNetworkLink) {

    /** Marcador do requisito enquanto o texto da norma nao e conferido item a item. */
    public static final String REQUIREMENT_WITHOUT_SOURCE =
            "[SEM FONTE] texto da norma nao disponivel nesta entrega: verificar o item contra o texto "
                    + "comprado na P1.28 antes de declarar conformidade";

    public CadastreChecklistItem {
        if (itemCode == null || itemCode.isBlank()) {
            throw new ExportValidationException("CHECKLIST_ITEM_CODE_REQUIRED", "item de checklist sem codigo");
        }
        if (!ExportReferences.CADASTRE_NORMS.contains(normReference)) {
            throw new ExportValidationException("CHECKLIST_NORM_NOT_IN_ACCEPTANCE",
                    "norma " + normReference + " fora da lista do acceptance da ED-82 "
                            + ExportReferences.CADASTRE_NORMS);
        }
        if (verificationTopic == null || verificationTopic.isBlank()) {
            throw new ExportValidationException("CHECKLIST_TOPIC_REQUIRED",
                    "item " + itemCode + " sem topico de verificacao nomeado");
        }
        if (requirementSource == null || requirementSource.isBlank()) {
            throw new ExportValidationException("CHECKLIST_REQUIREMENT_SOURCE_REQUIRED",
                    "item " + itemCode + " sem a fonte do requisito declarada "
                            + "(na falta do texto, o marcador [SEM FONTE] e obrigatorio)");
        }
    }
}
