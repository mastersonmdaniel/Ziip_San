package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Metadados da mudança que gerou uma versão (colunas da V4_9 em
 * {@code tariff_versions}): guardados <b>por tenant e por versão</b>, porque
 * ARESC e AGIR editam reajustes por prestador — o ato, o índice e a data de
 * aplicação de um prestador não são os de outro.
 *
 * <p>Invariantes por tipo de mudança, conferidas na construção:
 * <ul>
 *   <li>{@code READJUSTMENT}: data-base, código e percentual do índice,
 *       fórmula e percentual aplicado são obrigatórios (o percentual
 *       aplicado é o da fórmula, {@link ReadjustFormula});</li>
 *   <li>{@code REVIEW}: o ato da agência é obrigatório — revisão sem ato
 *       não existe; a metodologia da revisão <i>periódica</i> (receita
 *       requerida, base de remuneração) não entra aqui: ela só entra quando
 *       a norma da ANA em consulta pública for aprovada (aceite da P2.29);</li>
 *   <li>{@code COPY} e {@code READJUSTMENT}/{@code REVIEW} derivados: a
 *       versão de origem ({@code sourceVersionId}) é obrigatória, exceto em
 *       {@code INITIAL};</li>
 *   <li>homologação tácita ({@code approvalMode = TACIT}) exige a data em
 *       que ela se consumou; no fluxo de proposta essa data é imposta por
 *       {@link TariffApprovalRule} dentro de {@link TariffManagementService}
 *       (previsão na norma + prazo decorrido + data exata da consumação) —
 *       este record guarda a última trava, a presença da data.</li>
 * </ul>
 * A antecedência da publicação é conferida por {@link TariffPublicationRule}
 * no serviço de gestão, que conhece o parâmetro do tenant.
 *
 * @param calculationMethod método de cálculo da versão, em texto, mantido
 *                          com a versão para a transparência tarifária da
 *                          NR 13 (arts. 61 e 62); obrigatório em reajuste e
 *                          revisão (é ele que o histórico público mostra)
 */
public record VersionChangeMetadata(TariffVersionChangeType changeType, UUID sourceVersionId,
                                    LocalDate baseDate, String indexCode, BigDecimal indexPct,
                                    BigDecimal factorXPct, BigDecimal appliedPct, ReadjustFormula formula,
                                    String regulatoryAct, LocalDate publishedOn,
                                    TariffApprovalRule.ApprovalMode approvalMode, LocalDate approvedOn,
                                    String calculationMethod) {

    public VersionChangeMetadata {
        if (changeType == null) {
            throw new TariffConfigurationException("Versão da P2.29 sem tipo de mudança");
        }
        switch (changeType) {
            case INITIAL -> {
                if (sourceVersionId != null) {
                    throw new TariffConfigurationException("Versão INITIAL não tem versão de origem");
                }
            }
            case READJUSTMENT -> {
                requireSource(sourceVersionId, changeType);
                if (baseDate == null) {
                    throw new TariffConfigurationException("Reajuste sem data-base");
                }
                if (indexCode == null || indexCode.isBlank()) {
                    throw new TariffConfigurationException("Reajuste sem o código do índice");
                }
                if (formula == null || indexPct == null || appliedPct == null) {
                    throw new TariffConfigurationException(
                            "Reajuste exige fórmula, percentual do índice e percentual aplicado");
                }
                BigDecimal expected = formula.appliedPct(indexPct, factorXPct);
                if (appliedPct.compareTo(expected) != 0) {
                    throw new TariffConfigurationException(
                            "Percentual aplicado " + appliedPct + " diverge da fórmula " + formula
                                    + " (índice " + indexPct + ", fator X " + factorXPct + "): esperado " + expected);
                }
                requireMethod(calculationMethod, changeType);
            }
            case REVIEW -> {
                requireSource(sourceVersionId, changeType);
                if (regulatoryAct == null || regulatoryAct.isBlank()) {
                    throw new TariffConfigurationException("Revisão sem o ato da agência");
                }
                requireMethod(calculationMethod, changeType);
            }
            case COPY -> requireSource(sourceVersionId, changeType);
        }
        if (approvalMode == TariffApprovalRule.ApprovalMode.TACIT && approvedOn == null) {
            throw new TariffConfigurationException(
                    "Homologação tácita sem a data em que se consumou (ver TariffApprovalRule)");
        }
    }

    private static void requireSource(UUID sourceVersionId, TariffVersionChangeType type) {
        if (sourceVersionId == null) {
            throw new TariffConfigurationException("Mudança " + type + " sem a versão de origem");
        }
    }

    private static void requireMethod(String calculationMethod, TariffVersionChangeType type) {
        if (calculationMethod == null || calculationMethod.isBlank()) {
            throw new TariffConfigurationException(
                    "Mudança " + type + " sem o método de cálculo: ele é mantido com a versão "
                            + "para a transparência tarifária (NR 13, arts. 61 e 62)");
        }
    }
}
