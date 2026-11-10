package br.com.ziipsan.commercial.billing.social.cycle;

import java.util.OptionalInt;

/**
 * II-3: parametros do ciclo completo da tarifa social (Lei 14.898/2024),
 * parametrizaveis por tenant - NENHUM valor legal e redefinido aqui.
 *
 * <p>Os tres prazos que a P1.5 ja fixou no catalogo de parametros do tenant
 * sao apenas TRANSPORTADOS por este record (quem chama le do catalogo e
 * entrega o valor; esta classe nao tem opiniao sobre eles):
 * <ul>
 *   <li>{@code cadunicoMaxAgeMonths} = {@code social.cadunico_max_age_months}
 *       (a P1.5 regista 24 no catalogo, "so amplia");</li>
 *   <li>{@code lossNoticeMonths} = {@code social.loss_notice_months}
 *       (a P1.5 regista o minimo de 3);</li>
 *   <li>{@code graceMonths} = {@code social.grace_months}
 *       (a P1.5 regista o minimo de 3, GRACE_3M).</li>
 * </ul>
 *
 * <p>{@code recertificationIntervalMonths} e o intervalo de recertificacao
 * periodica das concessoes do ciclo: NENHUMA fonte do projecto o fixa
 * (nem a P1.5, nem a ED-57), por isso nasce como parametro OPCIONAL
 * marcado [A DEFINIR]. Sem valor, o agendamento de recertificacao recusa
 * (ver {@code SocialGrantLifecycle.nextRecertificationDue}): nenhum
 * intervalo e inventado.
 *
 * <p>Os valores de PROVA ({@link #proofParameters()}) sao exactamente os
 * que a P1.5 fixou (24/3/3), usados pelos testes do pacote com a fonte
 * declarada; nao sao valores de producao nem de tenant nenhum.
 */
public record SocialTariffCycleParameters(int cadunicoMaxAgeMonths, int lossNoticeMonths,
                                          int graceMonths, OptionalInt recertificationIntervalMonths) {

    public SocialTariffCycleParameters {
        if (cadunicoMaxAgeMonths < 0) {
            throw new IllegalArgumentException("Idade maxima do CadUnico nao pode ser negativa");
        }
        if (lossNoticeMonths < 0) {
            throw new IllegalArgumentException("Prazo de aviso de perda nao pode ser negativo");
        }
        if (graceMonths < 0) {
            throw new IllegalArgumentException("Prazo de graca nao pode ser negativo");
        }
        if (recertificationIntervalMonths == null) {
            recertificationIntervalMonths = OptionalInt.empty();
        }
        if (recertificationIntervalMonths.isPresent() && recertificationIntervalMonths.getAsInt() < 1) {
            throw new IllegalArgumentException("Intervalo de recertificacao, quando definido, e de ao menos 1 mes");
        }
    }

    /** Sem intervalo de recertificacao definido: [A DEFINIR] ate fonte/parametro do tenant. */
    public static SocialTariffCycleParameters of(int cadunicoMaxAgeMonths, int lossNoticeMonths, int graceMonths) {
        return new SocialTariffCycleParameters(cadunicoMaxAgeMonths, lossNoticeMonths, graceMonths,
                OptionalInt.empty());
    }

    /**
     * Valores de prova declarados: os fixados pela P1.5 no catalogo
     * (CadUnico 24 meses; aviso de perda 3 meses; graca 3 meses) e
     * recertificacao [A DEFINIR] (ausente). So para testes/fixtures.
     */
    public static SocialTariffCycleParameters proofParameters() {
        return of(24, 3, 3);
    }
}
