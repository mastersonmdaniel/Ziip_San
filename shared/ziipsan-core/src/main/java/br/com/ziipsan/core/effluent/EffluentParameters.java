package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.List;

/**
 * Parametros da ED-105 nomeados no aceite (OPE-25), lidos do tenant —
 * nenhum destino, origem isenta ou limite nasce de fabrica:
 * <pre>
 *   waste.sludge_destination_catalog  LISTA de codigos de destino
 *       aceitos para o lodo e os residuos da estacao (aterro,
 *       compostagem etc. — os CODIGOS sao dado do tenant/agencia,
 *       carregados na implantacao; esta classe so valida a forma).
 *       Ausente ou vazia, a destinacao recusa: sem catalogo nao ha
 *       destino presumido.
 *   waste.mtr_exempt_origins          LISTA de codigos de origem
 *       isentos de MTR. Ausente = erro explicito (a isencao nao se
 *       presume); lista vazia e valida e significa "nenhuma origem
 *       isenta" — toda saida exige o item manual de MTR (RAS-17).
 * </pre>
 * O chamador (fiacao) le o valor do tenant e entrega a lista tipada;
 * esta classe valida, no padrao das demais entregas (ED-50/ED-87).
 */
public final class EffluentParameters {

    public static final String SLUDGE_DESTINATION_CATALOG = "waste.sludge_destination_catalog";
    public static final String MTR_EXEMPT_ORIGINS = "waste.mtr_exempt_origins";

    private EffluentParameters() {}

    /** Catalogo de destinos do lodo: obrigatorio e nao vazio para qualquer destinacao. */
    public static List<String> sludgeDestinationCatalog(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new MissingParameterException(SLUDGE_DESTINATION_CATALOG,
                "catalogo de destinos do lodo nao configurado para o tenant:"
                        + " sem ele nenhum destino e presumido (OPE-25)");
        return validatedCodes(raw, SLUDGE_DESTINATION_CATALOG);
    }

    /**
     * Origens isentas de MTR: a AUSENCIA do parametro e erro (isencao
     * nao se presume); a lista vazia e a declaracao explicita de que
     * nenhuma origem e isenta.
     */
    public static List<String> mtrExemptOrigins(List<String> raw) {
        if (raw == null) throw new MissingParameterException(MTR_EXEMPT_ORIGINS,
                "origens isentas de MTR nao declaradas para o tenant:"
                        + " declarar a lista (vazia = nenhuma origem isenta) (RAS-17)");
        return validatedCodes(raw, MTR_EXEMPT_ORIGINS);
    }

    private static List<String> validatedCodes(List<String> raw, String key) {
        List<String> codes = raw.stream().map(c -> c == null ? "" : c.trim()).toList();
        if (codes.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException(key + " com codigo em branco");
        if (codes.stream().distinct().count() != codes.size())
            throw new IllegalArgumentException(key + " com codigo duplicado");
        return List.copyOf(codes);
    }

    public static boolean destinationAllowed(List<String> validatedCatalog, String destinationCode) {
        return destinationCode != null && validatedCatalog.contains(destinationCode.trim());
    }

    public static boolean mtrExempt(List<String> validatedExemptOrigins, String originCode) {
        return originCode != null && validatedExemptOrigins.contains(originCode.trim());
    }
}
