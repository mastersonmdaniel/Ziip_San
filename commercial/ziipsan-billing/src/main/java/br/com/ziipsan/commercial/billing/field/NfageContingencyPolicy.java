package br.com.ziipsan.commercial.billing.field;

/**
 * Contingencia da NFAg-e na emissao de campo (ED-19).
 *
 * <p><b>Dependencia textual declarada:</b> o aceite da ED-19 define a
 * contingencia "pelo leiaute de contingencia registrado no §2.4". O texto
 * do §2.4 (documento de leiaute da NFAg-e) NAO esta entre as fontes desta
 * frente — portanto esta classe NAO inventa o leiaute: ela fixa apenas o
 * comportamento que independe dele e que o aceite exige aqui:
 *
 * <ul>
 *   <li>contingencia so existe COM motivo declarado (sem motivo, recusa);</li>
 *   <li>conta emitida em contingencia nasce marcada e a transmissao da
 *       NFAg-e fica PENDENTE — nenhum estado de autorizada e fingido
 *       (o mesmo principio do {@code NfageEmissionService} da P2.6, que sem
 *       gateway para em RESERVED);</li>
 *   <li>a forma fiscal definitiva (ADR-0031, item 4) e a confirmacao contra
 *       o manual + homologacao na SEFAZ sao ensaio externo (relatorio).</li>
 * </ul>
 *
 * A geracao do documento em contingencia (campos, chave com tipo de
 * emissao de contingencia, prazo de transmissao) segue o §2.4 quando ele
 * for lido pela integracao — pendencia nomeada, nao lacuna silenciosa.
 */
public final class NfageContingencyPolicy {

    /** Situacao fiscal da conta de campo quanto a NFAg-e. */
    public enum FiscalSituation {
        /** Emissao normal: NFAg-e segue o fluxo da P2.6. */
        NORMAL,
        /** Contingencia declarada: transmissao pendente, conta marcada. */
        CONTINGENCY_PENDING_TRANSMISSION
    }

    private NfageContingencyPolicy() {
    }

    /**
     * @param contingencyDeclared o aparelho declarou contingencia
     * @param motive motivo da contingencia (obrigatorio quando declarada)
     */
    public static FiscalSituation situation(boolean contingencyDeclared, String motive) {
        if (!contingencyDeclared) {
            if (motive != null && !motive.isBlank()) {
                throw new IllegalArgumentException(
                        "Motivo de contingencia sem contingencia declarada e contradicao");
            }
            return FiscalSituation.NORMAL;
        }
        if (motive == null || motive.isBlank()) {
            throw new IllegalArgumentException(
                    "Contingencia da NFAg-e exige o motivo declarado (leiaute de contingencia, §2.4)");
        }
        return FiscalSituation.CONTINGENCY_PENDING_TRANSMISSION;
    }
}
