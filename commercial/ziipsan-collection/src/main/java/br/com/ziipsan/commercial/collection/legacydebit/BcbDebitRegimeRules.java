package br.com.ziipsan.commercial.collection.legacydebit;

import java.time.LocalDate;

/**
 * Regras do Banco Central que DEDUZEM a data de desligamento do debito
 * automatico legado (FEBRABAN 150) - P3.24, aceite literal.
 *
 * <p>A data de desligamento de {@code pay.legacy_debit_enabled} NUNCA e
 * digitada: ela e deduzida da regra vigente do BCB aplicavel ao tenant,
 * nesta classe, que e a fonte unica parametrizada da data - a ED-27 e a
 * P4.5 seguem a MESMA data daqui na integracao (nenhuma das duas deve
 * redeclarar a data).
 *
 * <p>Uma regra por data, um teste por regra:
 * <ul>
 *   <li>Convenio de prestacao de servico publico: 01/01/2027 e o fim do
 *       prazo de adequacao ao Pix Automatico (Res. CMN 5.278/2025, contas
 *       de depositos e conta-salario; Res. BCB 544/2025, contas de
 *       pagamento pre-pagas).</li>
 *   <li>Regime geral: 01/07/2027 e a vigencia do novo regime geral do
 *       debito automatico (Res. BCB 565/2026).</li>
 * </ul>
 *
 * <p>Decisao do dono de 08/11/2026 (DECISOES-DO-DONO.md, item 9): os
 * optantes EXISTEM e o caminho e DUPLO - campanha de conversao ao Pix
 * Automatico (P4.5) E FEBRABAN 150 mantido como legado para a base atual.
 * O parecer formal RAS-06/PD-10 NAO existe como documento: o enquadramento
 * usado aqui e a decisao do dono, nao um parecer juridico (ver relatorio).
 */
public final class BcbDebitRegimeRules {

    /** Enquadramento do tenant para fins da regra do BCB. */
    public enum Framing { CONVENIO_SERVICO_PUBLICO, REGIME_GERAL }

    /** Fim da adequacao ao Pix Automatico p/ convenios de servico publico. */
    public static final LocalDate PUBLIC_SERVICE_PIX_DEADLINE = LocalDate.of(2027, 1, 1);
    public static final String PUBLIC_SERVICE_RULE =
            "Res. CMN 5.278/2025; Res. BCB 544/2025";

    /** Vigencia do novo regime geral do debito automatico. */
    public static final LocalDate GENERAL_REGIME_START = LocalDate.of(2027, 7, 1);
    public static final String GENERAL_REGIME_RULE = "Res. BCB 565/2026";

    private BcbDebitRegimeRules() {
    }

    /**
     * Data de desligamento DEDUZIDA da regra vigente - nunca digitada,
     * nunca lida de parametro. A partir desta data (inclusive) o legado
     * nao gera remessa nova.
     */
    public static LocalDate deducedShutdownDate(Framing framing) {
        if (framing == null) {
            throw new IllegalArgumentException(
                    "enquadramento do tenant e obrigatorio para deduzir a data de desligamento");
        }
        return switch (framing) {
            case CONVENIO_SERVICO_PUBLICO -> PUBLIC_SERVICE_PIX_DEADLINE;
            case REGIME_GERAL -> GENERAL_REGIME_START;
        };
    }

    /** Referencia normativa da regra que produziu a data (para o diario/auditoria). */
    public static String ruleReference(Framing framing) {
        if (framing == null) {
            throw new IllegalArgumentException("enquadramento do tenant e obrigatorio");
        }
        return switch (framing) {
            case CONVENIO_SERVICO_PUBLICO -> PUBLIC_SERVICE_RULE;
            case REGIME_GERAL -> GENERAL_REGIME_RULE;
        };
    }

    /** O legado esta desligado nesta data? (fronteira: no dia, ja desligado). */
    public static boolean isShutdownOn(Framing framing, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("data e obrigatoria");
        }
        return !date.isBefore(deducedShutdownDate(framing));
    }
}
