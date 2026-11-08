package br.com.ziipsan.compliance.lgpd.privacy;

/**
 * Bases legais do registro de operações de tratamento (LGPD, art. 37) e do consentimento.
 *
 * <p>Os valores são EXATAMENTE os do CHECK de {@code lgpd_consent.legal_basis} na
 * {@code V2_5} (lida no repo): o registro de operações da {@code V23_1} usa o mesmo CHECK,
 * para o consentimento e a operação nunca divergirem de vocabulário.
 *
 * <p>PENDÊNCIA DECLARADA: o art. 7º da LGPD lista também o estudo por órgão de pesquisa,
 * o exercício regular de direitos e a tutela da saúde, que o CHECK da {@code V2_5} não
 * admite. Ampliar o CHECK é migration nova sobre tabela existente — decisão fora do
 * escopo da {@code V23_1} (que amplia, não altera, a {@code V2_5}/{@code V2_7}); fica
 * registrada no relatório da P3.40 para a integração/dono, sem valor inventado aqui:
 * base fora desta lista é recusada na escrita, nunca gravada por fora do CHECK.
 */
public enum LegalBasis {

    /** Art. 7º, I. */
    CONSENT,
    /** Art. 7º, V — execução de contrato. */
    CONTRACT,
    /** Art. 7º, II — obrigação legal ou regulatória. */
    LEGAL_OBLIGATION,
    /** Art. 7º, VII — proteção da vida ou da incolumidade física do titular ou de terceiro. */
    VITAL_INTEREST,
    /** Art. 7º, III — execução de políticas públicas pela administração pública. */
    PUBLIC_TASK,
    /** Art. 7º, IX — interesse legítimo. */
    LEGITIMATE_INTEREST,
    /** Art. 7º, X — proteção do crédito. */
    CREDIT_PROTECTION
}
