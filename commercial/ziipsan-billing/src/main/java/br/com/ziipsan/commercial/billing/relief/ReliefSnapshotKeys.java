package br.com.ziipsan.commercial.billing.relief;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Chaves aditivas do snapshot completo da conta (DESENHO-PA02 secao 4.4):
 * documento JSON novo convive com as colunas vigentes, sem reescrever
 * {@code tariff_snapshot}/{@code parameter_version_ids} (precedente ADR-0048
 * Pilar 3).
 *
 * <p>A explicacao da conta (ED-38) diz a proveniencia a partir destas chaves
 * gravadas — apresentacao apenas, sem recalcular.
 */
public record ReliefSnapshotKeys(UUID reliefProgramId, BigDecimal reliefFactor,
                                 UUID reliefSourceVersionId, boolean reliefProvisionalFallback,
                                 boolean provisionalIncludesCbsIbs) {

    /** Chaves de uma conta desonerada normal. */
    public static ReliefSnapshotKeys relieved(ReliefLink link) {
        return new ReliefSnapshotKeys(link.programId(), link.factor(), link.sourceVersionId(),
                false, false);
    }

    /**
     * Chaves de uma conta provisoria (NT 5.3): o total provisorio ja inclui
     * CBS e IBS; a regularizacao e compensacao das diferencas.
     */
    public static ReliefSnapshotKeys provisional(UUID programId) {
        return new ReliefSnapshotKeys(programId, null, null, true, true);
    }

    /** Fragmento JSON deterministico para o documento de snapshot. */
    public String toJsonFragment() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"reliefProgramId\":").append(reliefProgramId == null ? "null" : "\"" + reliefProgramId + "\"");
        sb.append(",\"reliefFactor\":").append(reliefFactor == null ? "null" : reliefFactor.toPlainString());
        sb.append(",\"reliefSourceVersionId\":")
                .append(reliefSourceVersionId == null ? "null" : "\"" + reliefSourceVersionId + "\"");
        sb.append(",\"reliefProvisionalFallback\":").append(reliefProvisionalFallback);
        sb.append(",\"provisionalIncludesCbsIbs\":").append(provisionalIncludesCbsIbs);
        if (reliefProvisionalFallback) {
            sb.append(",\"reliefRegularizationForm\":\"")
                    .append(ReliefEmissionGuard.REGULARIZATION_FORM).append("\"");
        }
        return sb.append("}").toString();
    }

    /** Texto de proveniencia para a explicacao da conta (ED-38), a partir do snapshot. */
    public String explanationText() {
        if (reliefProvisionalFallback) {
            return "Conta provisoria (programa " + reliefProgramId + "): valor total vigente, "
                    + "ja incluidos CBS e IBS, por falta de homologacao da tabela desonerada; "
                    + "regularizacao por compensacao das diferencas.";
        }
        if (reliefProgramId != null) {
            return "Tabela desonerada pelo programa " + reliefProgramId + ", factor " + reliefFactor
                    + " sobre a versao " + reliefSourceVersionId + ".";
        }
        return "Sem desoneracao aplicada.";
    }
}
