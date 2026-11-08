package br.com.ziipsan.compliance.lgpd.privacyiso;

import java.util.Objects;

/**
 * Papel do tenant em cada tratamento (ED-74): controlador ou operador —
 * na terminologia da norma, controlador/operador de dados pessoais
 * (PII controller / PII processor); na da LGPD, controlador/operador
 * (art. 5º, VI e VII). É conteúdo que SÓ existe com a norma ligada: a
 * LGPD exige os campos do registro de operações (art. 37, entregue na
 * P3.40 em {@code lgpd_processing_records}), mas não exige a declaração
 * de papel por tratamento no sistema de gestão — essa declaração é o
 * acréscimo da 27701 e é o que decide quais conjuntos de controles do
 * mapa se aplicam ao tenant naquele tratamento (ver
 * {@link PrivacyClauseAssessment.Audience}).
 *
 * <p>O vínculo é pelo CÓDIGO do registro de operações da P3.40
 * ({@code lgpd_processing_records.code}, VARCHAR(60) na V23_1) — nunca
 * uma cópia do registro: finalidade, base legal e compartilhamento têm
 * uma só fonte, a P3.40. Sem FK declarada na migration pendente pelo
 * mesmo motivo da V21_1/V23_1: o código tem versões (uma ativa por vez)
 * e a amarração é validada na integração contra a versão ativa.
 */
public record ProcessingRoleAssignment(String processingRecordCode, PrivacyRole role,
                                       String counterpartyNote) {

    /** Papel do tenant no tratamento. */
    public enum PrivacyRole {
        /** Decide finalidade e meios (LGPD, art. 5º, VI): aplicam-se os controles de controlador. */
        CONTROLLER,
        /** Trata em nome do controlador (LGPD, art. 5º, VII): aplicam-se os controles de operador. */
        PROCESSOR
    }

    /** Teto do código do registro de operações ({@code V23_1}: VARCHAR(60)). */
    public static final int RECORD_CODE_MAX = 60;

    public ProcessingRoleAssignment {
        if (processingRecordCode == null || processingRecordCode.isBlank()
                || processingRecordCode.length() > RECORD_CODE_MAX) {
            throw new IllegalArgumentException("processingRecordCode é obrigatório (código do "
                    + "registro de operações da P3.40) e tem no máximo " + RECORD_CODE_MAX
                    + " caracteres");
        }
        Objects.requireNonNull(role, "role (controlador ou operador) é obrigatório por tratamento");
        counterpartyNote = counterpartyNote == null ? "" : counterpartyNote;
    }

    /**
     * Nascimento do registro: só com a norma ligada (porta da
     * {@link Iso27701Activation}). A nota de contraparte é livre e pode
     * ficar vazia — quem é a outra parte consta do compartilhamento do
     * registro de operações da P3.40, que é a fonte; a nota é só contexto
     * da declaração de papel.
     */
    public static ProcessingRoleAssignment register(Iso27701Activation activation,
                                                    String processingRecordCode,
                                                    PrivacyRole role, String counterpartyNote) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        return new ProcessingRoleAssignment(processingRecordCode, role, counterpartyNote);
    }

    /** Conjunto de controles do mapa que este papel atrai (audiência da autoavaliação). */
    public PrivacyClauseAssessment.Audience audience() {
        return role == PrivacyRole.CONTROLLER
                ? PrivacyClauseAssessment.Audience.CONTROLLER
                : PrivacyClauseAssessment.Audience.PROCESSOR;
    }
}
