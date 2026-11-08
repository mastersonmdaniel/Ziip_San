package br.com.ziipsan.compliance.lgpd.privacy;

/**
 * Direitos do titular — LGPD, art. 18 — exatamente os enumerados no aceite da P3.40:
 * confirmação, acesso, correção, anonimização, bloqueio ou eliminação, portabilidade,
 * informação sobre compartilhamento e revogação do consentimento.
 *
 * <p>Os nomes são os valores já gravados em {@code lgpd_data_subject_request.request_type}
 * (CHECK da {@code V2_5}, lida no repo): esta enumeração NÃO cria vocabulário novo.
 * A {@code V2_5} admite ainda {@code OPPOSITION}, que o aceite da P3.40 não lista entre os
 * direitos do art. 18 desta tarefa; ele não entra aqui e o pedido de oposição segue pelo
 * fluxo já existente do módulo — divergência registrada no relatório da P3.40.
 *
 * <p>O prazo de resposta NÃO é do art. 18: a declaração completa tem prazo de 15 dias pelo
 * art. 19, II — ver {@link DsrDeadline} (é a correção de referência que o aceite pede).
 */
public enum DataSubjectRight {

    /** Art. 18, I — confirmação da existência de tratamento. */
    CONFIRMATION("confirmação da existência de tratamento"),
    /** Art. 18, II — acesso aos dados. */
    ACCESS("acesso aos dados"),
    /** Art. 18, III — correção de dados incompletos, inexatos ou desatualizados. */
    CORRECTION("correção de dados incompletos, inexatos ou desatualizados"),
    /** Art. 18, IV — anonimização de dados desnecessários, excessivos ou em desconformidade. */
    ANONYMIZATION("anonimização de dados desnecessários ou excessivos"),
    /** Art. 18, IV — bloqueio de dados desnecessários, excessivos ou em desconformidade. */
    BLOCKING("bloqueio de dados desnecessários ou excessivos"),
    /** Art. 18, IV/VI — eliminação (sempre confrontada com a retenção: art. 16, ver {@link RetentionDecision}). */
    DELETION("eliminação dos dados pessoais"),
    /** Art. 18, V — portabilidade a outro fornecedor. */
    PORTABILITY("portabilidade dos dados a outro fornecedor"),
    /** Art. 18, VII — informação sobre compartilhamento com entidades públicas e privadas. */
    INFORMATION("informação sobre compartilhamento dos dados"),
    /** Art. 18, IX — revogação do consentimento (art. 8º, §5º). */
    REVOCATION("revogação do consentimento");

    private final String label;

    DataSubjectRight(String label) {
        this.label = label;
    }

    /** Rótulo em linguagem do titular, para as telas do portal e do atendimento. */
    public String label() {
        return label;
    }
}
