package br.com.ziipsan.commercial.billing.tariff;

import java.time.LocalDate;

/**
 * Homologação da nova estrutura pela agência (P2.29): expressa ou tácita.
 * A homologação <b>tácita só existe quando a norma da agência a prevê</b>:
 * sem a previsão declarada no parâmetro ({@code bill.tariff_readjust},
 * campo {@code tacitApprovalAllowed}) e sem o prazo configurado
 * ({@code tariff.tacit_approval_days}), pedir homologação tácita é recusado
 * — o silêncio da agência não vira aprovação por presunção do sistema.
 */
public final class TariffApprovalRule {

    /** Modo de homologação gravado em {@code tariff_versions.approval_mode}. */
    public enum ApprovalMode {
        EXPRESS,
        TACIT
    }

    private TariffApprovalRule() {
    }

    /**
     * Data da homologação tácita: publicação + prazo da norma da agência.
     *
     * @param agencyAllowsTacit previsão da homologação tácita na norma da
     *                          agência (fato declarado no parâmetro, não
     *                          presumido)
     * @param publishedOn       data da publicação do reajuste/revisão
     * @param tacitApprovalDays prazo em dias; obrigatório e ≥ 1 quando a
     *                          norma prevê a homologação tácita
     * @param asOf              data em que se pergunta "já homologou?"
     * @return a data em que a homologação tácita se consuma
     * @throws TariffConfigurationException norma sem previsão de homologação
     *                        tácita, prazo ausente/inválido, ou prazo ainda
     *                        não decorrido em {@code asOf}
     */
    public static LocalDate tacitApprovalDate(boolean agencyAllowsTacit, LocalDate publishedOn,
                                              Integer tacitApprovalDays, LocalDate asOf) {
        if (!agencyAllowsTacit) {
            throw new TariffConfigurationException(
                    "Homologação tácita recusada: a norma da agência não a prevê para este prestador "
                            + "(bill.tariff_readjust.tacitApprovalAllowed não é verdadeiro)");
        }
        if (tacitApprovalDays == null || tacitApprovalDays < 1) {
            throw new TariffConfigurationException(
                    "Homologação tácita sem prazo configurado (tariff.tacit_approval_days >= 1): "
                            + tacitApprovalDays);
        }
        if (publishedOn == null) {
            throw new TariffConfigurationException(
                    "Homologação tácita sem data de publicação: o prazo conta da publicação");
        }
        LocalDate approvalDate = publishedOn.plusDays(tacitApprovalDays);
        if (asOf == null || asOf.isBefore(approvalDate)) {
            throw new TariffConfigurationException(
                    "Homologação tácita ainda não consumada: publica em " + publishedOn
                            + ", prazo de " + tacitApprovalDays + " dias, homologa em " + approvalDate
                            + " (consulta em " + asOf + ")");
        }
        return approvalDate;
    }

    /**
     * Homologação expressa: exige a data do ato de homologação, que não pode
     * ser anterior à publicação.
     */
    public static LocalDate expressApprovalDate(LocalDate publishedOn, LocalDate approvedOn) {
        if (approvedOn == null) {
            throw new TariffConfigurationException("Homologação expressa sem a data do ato de homologação");
        }
        if (publishedOn != null && approvedOn.isBefore(publishedOn)) {
            throw new TariffConfigurationException(
                    "Homologação expressa (" + approvedOn + ") anterior à publicação (" + publishedOn + ")");
        }
        return approvedOn;
    }
}
