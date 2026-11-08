package br.com.ziipsan.core.inspection;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros da ED-24 (controle de fraudes e fiscalizacao), SEMPRE lidos do
 * tenant — nenhum prazo ou politica e constante de negocio nesta classe:
 * <pre>
 *   infraction.appeal_days            prazo de defesa/recurso do auto de
 *                                     infracao, em dias (inteiro; classe c —
 *                                     prazo minimo e teto da ERI; catalogo
 *                                     PARAMETROS-DO-TENANT, 20b 5.1, FAT-25)
 *   infraction.charge_timing          AFTER_DECISION (padrao de fabrica) |
 *                                     ON_ISSUE_SUSPENDED_BY_APPEAL — quando
 *                                     o debito do auto e lancado (classe b;
 *                                     catalogo, idem)
 *   infraction.max_retroactive_months teto de retroatividade da cobranca da
 *                                     infracao, em meses (inteiro; classe c;
 *                                     catalogo, idem)
 * </pre>
 * Ausencia de qualquer um e erro explicito ({@link MissingParameterException}),
 * nunca padrao assumido em silencio — principio 9 do PARAMETROS-DO-TENANT,
 * mesmo desenho de {@code SchedParameters} da ED-36 e de
 * {@code MaintenanceParameters} da P2.20.
 *
 * <p>[DECLARADO — D2 da revisao cruzada da ED-24]
 * {@code infraction.max_retroactive_months} e apenas VALIDADO por esta
 * classe (ausencia falha, valor invalido falha); nenhuma regra de calculo
 * o APLICA nesta entrega, porque as fontes nao definem como o teto entra
 * no calculo do valor do auto (o valor chega pronto ao
 * {@code InfractionLifecycle.issue}). Nenhuma regra foi inventada; a
 * aplicacao do teto fica para a integracao, com fonte.
 *
 * <p>[A DEFINIR] Nao ha nas fontes locais chave catalogada para os limiares
 * dos criterios de fiscalizacao (ex.: janela/volume do consumo em ligacao
 * cortada). Esta frente NAO inventa chave: os limiares chegam como valores
 * explicitos do chamador (ver {@code CutConsumptionRule}) e a chave
 * definitiva deve ser registrada no catalogo na integracao.
 */
public final class InspectionParameters {

    public static final String APPEAL_DAYS = "infraction.appeal_days";
    public static final String CHARGE_TIMING = "infraction.charge_timing";
    public static final String MAX_RETROACTIVE_MONTHS = "infraction.max_retroactive_months";

    private InspectionParameters() {}

    /** Quando o debito do auto de infracao e lancado (infraction.charge_timing). */
    public enum ChargeTiming {
        /** Debito so nasce depois da decisao (julgamento do recurso ou decurso do prazo sem recurso com decisao de manutencao). Padrao de fabrica do catalogo. */
        AFTER_DECISION,
        /** Debito lancado ja na emissao do auto; o recurso o SUSPENDE ate a decisao. */
        ON_ISSUE_SUSPENDED_BY_APPEAL
    }

    public static int appealDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(APPEAL_DAYS,
                "prazo de defesa do auto de infracao nao configurado para o tenant (inteiro da ERI, classe c)");
        if (raw <= 0) throw new IllegalArgumentException(APPEAL_DAYS + " deve ser maior que zero: " + raw);
        return raw;
    }

    public static ChargeTiming chargeTiming(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(CHARGE_TIMING,
                "momento do lancamento do debito nao configurado para o tenant (padrao de fabrica AFTER_DECISION na definition, classe b)");
        try {
            return ChargeTiming.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CHARGE_TIMING + " desconhecido: " + raw);
        }
    }

    public static int maxRetroactiveMonths(Integer raw) {
        if (raw == null) throw new MissingParameterException(MAX_RETROACTIVE_MONTHS,
                "teto de retroatividade da infracao nao configurado para o tenant (inteiro da ERI, classe c)");
        if (raw <= 0) throw new IllegalArgumentException(MAX_RETROACTIVE_MONTHS + " deve ser maior que zero: " + raw);
        return raw;
    }
}
