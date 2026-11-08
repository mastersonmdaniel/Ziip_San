package br.com.ziipsan.commercial.billing.cobill;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Aviso ao usuario do inicio do cofaturamento (ED-25; NR 13, arts. 46 a
 * 56): pelo menos {@code cobill.notice_min_days} dias (piso travado de
 * 60) entre o aviso e o inicio da cobranca, veiculado pelo motor de
 * mensagens legais da P2.32.
 *
 * <p>Integracao com a P2.32 (provada contra o pacote local): o catalogo
 * {@code LegalMessageCatalog} ja reserva o slot
 * {@value #LEGAL_SLOT_KEY} ("ED-25 (cofaturamento)", gatilho
 * {@code SLOT_ACTIVATION}) - ele NAO emite sozinho; esta regra e a dona
 * da ativacao: o slot so pode ser ativado para uma competencia quando a
 * antecedencia esta cumprida. O texto da mensagem e do modelo aprovado
 * do tenant (motor da P2.30), nunca texto juridico inventado aqui.
 */
public final class CobillNoticeRule {

    /** Chave EXATA do slot reservado no catalogo da P2.32 para a ED-25. */
    public static final String LEGAL_SLOT_KEY = "slot.cobilling_60d";

    private CobillNoticeRule() {
    }

    /**
     * A antecedencia esta cumprida? Calculo a mao: aviso em 01/01 e
     * inicio em 02/03 do mesmo ano (nao bissexto) = 60 dias exatos
     * (cumprida); inicio em 01/03 = 59 dias (nao cumprida).
     */
    public static boolean leadTimeMet(LocalDate noticeDate, LocalDate startDate, int noticeMinDays) {
        Objects.requireNonNull(noticeDate, "data do aviso e obrigatoria");
        Objects.requireNonNull(startDate, "data de inicio e obrigatoria");
        if (noticeMinDays < CobillParameters.NOTICE_MIN_DAYS_FLOOR) {
            throw new IllegalArgumentException("antecedencia abaixo do piso de 60 dias: " + noticeMinDays);
        }
        return !noticeDate.plusDays(noticeMinDays).isAfter(startDate);
    }

    /** A antecedencia do contrato, pelos parametros do tenant. */
    public static boolean leadTimeMet(CobillContract contract, CobillParameters parameters) {
        Objects.requireNonNull(contract, "contrato e obrigatorio");
        Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        return leadTimeMet(contract.noticeDate(), contract.startDate(), parameters.noticeMinDays());
    }

    /**
     * O slot da P2.32 pode ser ativado para este contrato? So com o
     * cofaturamento ligado E a antecedencia cumprida; fora disso o slot
     * permanece inativo e a cobranca nao pode comecar (ver o portao em
     * {@link SolidWasteChargeCalculator}).
     */
    public static boolean legalSlotActivates(CobillContract contract, CobillParameters parameters) {
        return parameters.enabled() && leadTimeMet(contract, parameters);
    }

    /**
     * Portao do inicio: lancar a primeira cobranca sem a antecedencia
     * cumprida e recusado com erro explicito.
     */
    public static void requireLeadTime(CobillContract contract, CobillParameters parameters) {
        if (!leadTimeMet(contract, parameters)) {
            throw new IllegalStateException("Cofaturamento sem aviso com a antecedencia minima de "
                    + parameters.noticeMinDays() + " dias (aviso " + contract.noticeDate()
                    + ", inicio " + contract.startDate() + "): cobranca recusada (ED-25, NR 13)");
        }
    }
}
