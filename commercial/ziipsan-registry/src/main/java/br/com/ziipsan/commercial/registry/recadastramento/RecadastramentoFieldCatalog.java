package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.commercial.registry.application.CadastralChangeService;

import java.util.List;

/**
 * Campos cadastrais PRE-DEFINIDOS coletaveis no recadastramento (ED-16;
 * TR-234/SG-232 do levantamento 18d-E11: "alterar em campo dados
 * cadastrais pre-definidos").
 *
 * <p>A lista NAO e nova: e exatamente o mapa campo -&gt; tipo de OS da
 * P1.3/P1.31 ({@link CadastralChangeService#FIELD_OS_TYPES}). Campo fora
 * desse mapa nao e coletavel em campo — a coleta o recusa e registra o
 * erro (ver {@code RecadastramentoService}), em vez de inventar um campo
 * ou um tipo de OS. O tipo de OS de cada campo, usado quando a alteracao
 * aprovada e registrada no historico, tambem vem do mesmo mapa.
 *
 * <p>Documento ({@code CUSTOMER.document}): pela regra da P1.3, valores
 * de documento nunca circulam em claro no historico — entram
 * MASCARADOS. A coleta segue a mesma regra: o valor coletado desse
 * campo so e aceito se a submissao o declarar mascarado
 * ({@link #requiresMaskedValue}); o app de campo mascara na origem,
 * sobre o valor que o recadastrador digita/conferiu.
 */
public final class RecadastramentoFieldCatalog {

    public static final String TARGET_CUSTOMER = "CUSTOMER";
    public static final String TARGET_UNIT = "CONSUMER_UNIT";

    private RecadastramentoFieldCatalog() {
    }

    /** O campo esta no mapa pre-definido da P1.3/P1.31? */
    public static boolean isCollectable(String targetType, String field) {
        return targetType != null && field != null
                && CadastralChangeService.osTypeFor(targetType, field) != null;
    }

    /** Tipo de OS do campo no mapa da P1.31; fora do mapa = erro explicito. */
    public static String osTypeFor(String targetType, String field) {
        String type = targetType == null || field == null
                ? null : CadastralChangeService.osTypeFor(targetType, field);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Campo fora do mapa de campos pre-definidos do recadastramento: "
                            + targetType + "." + field);
        }
        return type;
    }

    /** Chave completa "ALVO.campo", como no mapa da P1.3. */
    public static String key(String targetType, String field) {
        return targetType + "." + field;
    }

    /**
     * O valor coletado deste campo so pode circular mascarado (documento
     * do cliente, regra da P1.3 para o historico cadastral).
     */
    public static boolean requiresMaskedValue(String targetType, String field) {
        return TARGET_CUSTOMER.equals(targetType) && "document".equals(field);
    }

    /** Todas as chaves coletaveis, ordenadas (para o pacote de campo da ED-18). */
    public static List<String> collectableFieldKeys() {
        return CadastralChangeService.FIELD_OS_TYPES.keySet().stream().sorted().toList();
    }
}
