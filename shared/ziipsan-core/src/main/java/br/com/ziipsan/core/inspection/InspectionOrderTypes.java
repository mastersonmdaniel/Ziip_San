package br.com.ziipsan.core.inspection;

/**
 * Codigos dos tipos de OS da ED-24 no catalogo {@code service_types} do
 * tenant (motor da P1.31).
 *
 * <p>{@code INFRACTION_NOTICE} e {@code INFRACTION_APPEAL} sao nomeados
 * literalmente pelo aceite da ED-24: a OS {@code INFRACTION_NOTICE} carrega
 * o auto de infracao e a OS {@code INFRACTION_APPEAL} carrega o recurso.
 *
 * <p>[A DEFINIR] O aceite diz apenas "OS de fiscalizacao gerada por
 * criterio", sem nomear o codigo do tipo. {@link #INSPECTION} e o marcador
 * proposto por esta frente; o codigo definitivo e decisao de catalogo do
 * tenant na integracao (declarar o tipo com este codigo ou renomear a
 * constante — nenhum outro codigo foi inventado a partir das fontes).
 */
public final class InspectionOrderTypes {

    /** [A DEFINIR — ver javadoc da classe] OS de fiscalizacao gerada por criterio. */
    public static final String INSPECTION = "INSPECTION";

    /** OS do auto de infracao (nomeada pelo aceite da ED-24). */
    public static final String INFRACTION_NOTICE = "INFRACTION_NOTICE";

    /** OS do recurso contra o auto de infracao (nomeada pelo aceite da ED-24). */
    public static final String INFRACTION_APPEAL = "INFRACTION_APPEAL";

    private InspectionOrderTypes() {}
}
