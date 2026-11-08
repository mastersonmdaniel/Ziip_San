package br.com.ziipsan.core.inspection;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Criterios que geram OS de fiscalizacao (aceite da ED-24: "OS de
 * fiscalizacao gerada por criterio"). Cada ocorrencia de criterio e um
 * {@link CriterionHit} sobre uma unidade consumidora; quem detecta (rotina
 * de leitura, cadastro, campo) entrega o hit e o
 * {@link InspectionOrderPlanner} decide a abertura da OS — a deteccao em si
 * (varredura de leituras, analitica) NAO e desta entrega: a analitica de
 * fraude e perdas aparentes e da ED-95, fora desta entrega por ordem do
 * aceite.
 */
public enum InspectionCriterion {

    /** Consumo medido em ligacao cortada (v2.15; teste nomeado no aceite, "20d OS-10"). */
    CONSUMPTION_WHILE_CUT,
    /** Fonte alternativa de agua declarada no campo da P1.3 (unit_alternative_sources, CAD-69) entra nos criterios. */
    ALTERNATIVE_SOURCE_DECLARED,
    /** Fonte alternativa declarada sem a autorizacao exigida (connection.alt_source_requires_authorization da P1.3). */
    ALTERNATIVE_SOURCE_UNAUTHORIZED,
    /** Denuncia ou boletim externo que pede verificacao em campo. */
    EXTERNAL_REPORT,
    /** Unidade com fraude ja confirmada volta ao conjunto de fiscalizacao. */
    FRAUD_RECURRENCE;

    /** Uma ocorrencia de criterio sobre uma unidade, com a evidencia resumida que a sustenta. */
    public record CriterionHit(InspectionCriterion criterion, UUID consumerUnitId,
                               String evidenceSummary, LocalDateTime detectedAt) {
        public CriterionHit {
            Objects.requireNonNull(criterion, "criterio e obrigatorio");
            Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria");
            if (evidenceSummary == null || evidenceSummary.isBlank())
                throw new IllegalArgumentException("resumo da evidencia do criterio e obrigatorio — fiscalizacao sem evidencia nao abre OS");
            Objects.requireNonNull(detectedAt, "data/hora da deteccao e obrigatoria");
        }
    }
}
