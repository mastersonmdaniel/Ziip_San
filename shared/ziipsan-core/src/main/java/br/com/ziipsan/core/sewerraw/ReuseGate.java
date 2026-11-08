package br.com.ziipsan.core.sewerraw;

import java.util.List;

/**
 * Portao do reuso (aceite ED-80, literal): "reuso codificado so
 * depois da publicacao da resolucao conjunta CNRH/CONAMA aprovada
 * em 01/09/2026, ainda a confirmar no catalogo, ficando as
 * CNRH 54/2005 e 121/2010, em revisao, como referencia antes dela".
 *
 * <p>Portanto esta classe NAO codifica uso, parametro nem limite de
 * reuso: ela avalia a PRONTIDAO normativa. Sem a confirmacao de
 * publicacao conferida no catalogo, o resultado e NOT_CODIFIED e
 * qualquer autorizacao de reuso fica recusada por desenho. As
 * referencias anteriores sao as nomeadas no aceite, como
 * REFERENCIA — nao como base codificada.
 */
public final class ReuseGate {

    public static final String PRIOR_REFERENCE_1 = "CNRH 54/2005 (em revisao)";
    public static final String PRIOR_REFERENCE_2 = "CNRH 121/2010 (em revisao)";
    public static final String SUCCESSOR_DESCRIPTION =
            "Resolucao conjunta CNRH/CONAMA aprovada em 01/09/2026 (publicacao a confirmar no catalogo)";

    public enum ReuseStatus { NOT_CODIFIED, CODIFIED_BASIS_CONFIRMED }

    /**
     * Estado normativo declarado apos conferencia no catalogo:
     * {@code successorPublishedConfirmed} so e verdadeiro quando a
     * publicacao foi conferida e a referencia dela declarada.
     */
    public record ReuseNormStatus(boolean successorPublishedConfirmed, String successorReference) {
        public ReuseNormStatus {
            if (successorPublishedConfirmed && (successorReference == null || successorReference.isBlank()))
                throw new IllegalArgumentException("publicacao confirmada exige a referencia da resolucao publicada declarada");
        }
    }

    public record Readiness(ReuseStatus status, String reason, List<String> references) {}

    private ReuseGate() {}

    public static Readiness evaluate(ReuseNormStatus normStatus) {
        if (normStatus == null) throw new IllegalArgumentException("estado normativo do reuso e obrigatorio");
        if (!normStatus.successorPublishedConfirmed()) {
            return new Readiness(ReuseStatus.NOT_CODIFIED,
                    "reuso NAO codificado: a " + SUCCESSOR_DESCRIPTION + " nao esta confirmada como publicada"
                            + " no catalogo [SEM FONTE ate a confirmacao]; valem como referencia, sem codificar,"
                            + " as normas anteriores em revisao",
                    List.of(PRIOR_REFERENCE_1, PRIOR_REFERENCE_2));
        }
        return new Readiness(ReuseStatus.CODIFIED_BASIS_CONFIRMED,
                "base do reuso confirmada como publicada no catalogo: " + normStatus.successorReference()
                        + " — os parametros de uso continuam sendo os declarados pelo tenant a partir dela"
                        + " (nenhum parametro nasce desta classe)",
                List.of(normStatus.successorReference(), PRIOR_REFERENCE_1, PRIOR_REFERENCE_2));
    }

    /** Autorizar reuso sem base codificada e recusado por desenho. */
    public static void requireCodified(ReuseNormStatus normStatus, String requestedUse) {
        if (requestedUse == null || requestedUse.isBlank())
            throw new IllegalArgumentException("uso pretendido do reuso e obrigatorio (declarado)");
        Readiness r = evaluate(normStatus);
        if (r.status() != ReuseStatus.CODIFIED_BASIS_CONFIRMED)
            throw new IllegalStateException("reuso recusado para '" + requestedUse + "': " + r.reason());
    }
}
