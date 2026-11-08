package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.BcbDebitRegimeRules;
import br.com.ziipsan.commercial.collection.pix.HybridChargeRule;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Calendario regulatorio do debito em conta e da cobranca hibrida (ED-27)
 * - VISAO/RELATORIO sobre a FONTE UNICA de datas da P3.24.
 *
 * <p>Correcao pos-revisao (D3): esta classe NAO e mais a fonte das datas
 * de desligamento do legado. A fonte unica e
 * {@link BcbDebitRegimeRules} (P3.24, dona da regra no aceite dela):
 * <ul>
 *   <li>01/01/2027 - convenio de servico publico:
 *       {@code BcbDebitRegimeRules.PUBLIC_SERVICE_PIX_DEADLINE}
 *       (Res. CMN 5.278/2025; Res. BCB 544/2025);</li>
 *   <li>01/07/2027 - regime geral:
 *       {@code BcbDebitRegimeRules.GENERAL_REGIME_START}
 *       (Res. BCB 565/2026).</li>
 * </ul>
 * NENHUM literal dessas duas datas existe nesta classe: os metodos e as
 * constantes de compatibilidade DELEGAM a {@code BcbDebitRegimeRules}.
 * O papel que resta a esta classe e o de visao da ED-27: mapear o seu
 * {@link AgreementKind} para o {@code Framing} da P3.24 e montar o
 * calendario ({@link #events()}) juntando os marcos do BCB (via P3.24)
 * com a vigencia da cobranca hibrida, lida de
 * {@link HybridChargeRule#EFFECTIVE_FROM} (P3.2, Res. BCB 587/2026),
 * tambem sem duplicar o literal.
 *
 * <p>A data de desligamento continua DEDUZIDA da regra vigente, nunca
 * digitada por quem chama: quem chama informa apenas o enquadramento do
 * convenio ({@link AgreementKind}), levantado por optante na P0.30 /
 * cadastro do tenant.
 */
public final class DebitRegulatoryCalendar {

    /** Enquadramento do convenio de debito, levantado por optante (P0.30). */
    public enum AgreementKind { PUBLIC_SERVICE, GENERAL }

    /** Marco regulatorio do calendario. */
    public record RegulatoryEvent(String code, LocalDate date, String source, String description) {
    }

    /** Delegado a P3.24: {@code BcbDebitRegimeRules.PUBLIC_SERVICE_PIX_DEADLINE}. */
    public static final LocalDate PUBLIC_SERVICE_ADEQUACY_DEADLINE =
            BcbDebitRegimeRules.PUBLIC_SERVICE_PIX_DEADLINE;

    /** Delegado a P3.24: {@code BcbDebitRegimeRules.GENERAL_REGIME_START}. */
    public static final LocalDate GENERAL_REGIME_FROM =
            BcbDebitRegimeRules.GENERAL_REGIME_START;

    private DebitRegulatoryCalendar() {
    }

    /** Mapeamento do enquadramento da ED-27 para o enquadramento (Framing) da fonte da P3.24. */
    public static BcbDebitRegimeRules.Framing framingOf(AgreementKind kind) {
        Objects.requireNonNull(kind, "enquadramento do convenio e obrigatorio");
        return switch (kind) {
            case PUBLIC_SERVICE -> BcbDebitRegimeRules.Framing.CONVENIO_SERVICO_PUBLICO;
            case GENERAL -> BcbDebitRegimeRules.Framing.REGIME_GERAL;
        };
    }

    /**
     * Data de desligamento do legado FEBRABAN 150 para o enquadramento
     * dado, DELEGADA a fonte unica da P3.24
     * ({@link BcbDebitRegimeRules#deducedShutdownDate}) - nao e entrada
     * de usuario e nenhum literal vive aqui.
     */
    public static LocalDate legacyShutdownDate(AgreementKind kind) {
        return BcbDebitRegimeRules.deducedShutdownDate(framingOf(kind));
    }

    /** Referencia normativa da regra que produziu a data, da fonte da P3.24. */
    public static String shutdownRuleReference(AgreementKind kind) {
        return BcbDebitRegimeRules.ruleReference(framingOf(kind));
    }

    /** O legado ainda pode operar nesta data para este enquadramento? (estritamente antes do desligamento, pela fonte da P3.24). */
    public static boolean legacyOperatesOn(AgreementKind kind, LocalDate date) {
        Objects.requireNonNull(date, "data e obrigatoria");
        return !BcbDebitRegimeRules.isShutdownOn(framingOf(kind), date);
    }

    /** Vigencia da cobranca hibrida, da fonte da P3.2 (Res. BCB 587/2026). */
    public static LocalDate hybridEffectiveFrom() {
        return HybridChargeRule.EFFECTIVE_FROM;
    }

    /** Calendario completo, em ordem cronologica, alimentado pelas fontes (P3.24 para o BCB, P3.2 para a hibrida). */
    public static List<RegulatoryEvent> events() {
        return List.of(
                new RegulatoryEvent("ADEQUACAO_PIX_CONVENIO_SERVICO_PUBLICO",
                        legacyShutdownDate(AgreementKind.PUBLIC_SERVICE),
                        BcbDebitRegimeRules.PUBLIC_SERVICE_RULE,
                        "fim da adequacao ao Pix Automatico para convenios de prestacao de servicos publicos; "
                                + "legado FEBRABAN 150 desses convenios desliga nesta data"),
                new RegulatoryEvent("COBRANCA_HIBRIDA_VIGENCIA", hybridEffectiveFrom(),
                        "Res. BCB 587/2026",
                        "cobranca hibrida (Pix Cobranca com vencimento + boleto) passa a vigorar"),
                new RegulatoryEvent("NOVO_REGIME_GERAL_DEBITO_AUTOMATICO",
                        legacyShutdownDate(AgreementKind.GENERAL),
                        BcbDebitRegimeRules.GENERAL_REGIME_RULE,
                        "vigencia do novo regime geral do debito automatico; legado dos demais convenios desliga"));
    }
}
