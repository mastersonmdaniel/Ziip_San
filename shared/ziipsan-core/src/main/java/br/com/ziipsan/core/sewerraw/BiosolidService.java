package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.effluent.SludgeRecord;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Biossolido produzido a partir do lodo registrado na ED-105, com
 * os criterios da CONAMA 498/2020 (aceite ED-80).
 *
 * <p>Disciplina identica a da ED-105: os CRITERIOS sao DECLARADOS
 * pelo responsavel tecnico do tenant a partir da norma (chave,
 * descricao e referencia) — esta classe nao enumera criterio,
 * limite nem classificacao da CONAMA 498/2020, porque nenhum valor
 * dela consta das fontes conferidas nesta VM [SEM FONTE para
 * valores]. A avaliacao de cada criterio chega como fato declarado
 * com evidencia; criterio sem avaliacao torna o lote
 * INDETERMINATE — biossolido nunca e declarado apto por omissao.
 */
public final class BiosolidService {

    /** Criterio declarado pelo responsavel tecnico a partir da CONAMA 498/2020. */
    public record BiosolidCriterion(String criterionKey, String description, String normReference) {
        public BiosolidCriterion {
            if (criterionKey == null || criterionKey.isBlank())
                throw new IllegalArgumentException("chave do criterio de biossolido e obrigatoria");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("descricao do criterio de biossolido e obrigatoria (declarada, CONAMA 498/2020)");
            if (normReference == null || normReference.isBlank())
                throw new IllegalArgumentException("referencia do criterio (CONAMA 498/2020) e obrigatoria");
        }
    }

    /** Avaliacao declarada de um criterio, com a evidencia (ensaio/relatorio). */
    public record CriterionEvaluation(String criterionKey, boolean conforming, String evidenceRef) {
        public CriterionEvaluation {
            if (criterionKey == null || criterionKey.isBlank())
                throw new IllegalArgumentException("criterio avaliado e obrigatorio");
            if (evidenceRef == null || evidenceRef.isBlank())
                throw new IllegalArgumentException("evidencia da avaliacao do criterio " + criterionKey + " e obrigatoria");
        }
    }

    public enum BiosolidStatus { ELIGIBLE, NOT_ELIGIBLE, INDETERMINATE }

    public record BiosolidLot(
            UUID id, UUID tenantId, UUID sludgeRecordId, UUID sourceUnitId,
            BigDecimal quantity, String quantityUnit, BiosolidStatus status,
            int criteriaDeclared, int criteriaEvaluated, String reason) {
        public BiosolidLot {
            Objects.requireNonNull(id, "id do lote de biossolido e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant do lote e obrigatorio");
            Objects.requireNonNull(sludgeRecordId, "registro de lodo da ED-105 e obrigatorio");
            Objects.requireNonNull(sourceUnitId, "unidade de origem do lote e obrigatoria");
            Objects.requireNonNull(quantity, "quantidade do lote e obrigatoria");
            Objects.requireNonNull(status, "situacao do lote e obrigatoria");
        }
    }

    private BiosolidService() {}

    public static BiosolidLot produce(SludgeRecord sludge, List<BiosolidCriterion> criteria,
                                      List<CriterionEvaluation> evaluations) {
        if (sludge == null) throw new IllegalArgumentException("registro de lodo da ED-105 e obrigatorio");
        if (sludge.residueKind() != SludgeRecord.ResidueKind.SLUDGE)
            throw new IllegalArgumentException("biossolido nasce de LODO (ED-105), nao de " + sludge.residueKind());
        if (sludge.status() != SludgeRecord.Status.DISPATCHED && sludge.status() != SludgeRecord.Status.CLOSED)
            throw new IllegalArgumentException("lodo ainda nao saiu para destinacao (estado " + sludge.status()
                    + "): biossolido so se produz de lodo com destinacao registrada na ED-105");
        if (criteria == null || criteria.isEmpty())
            throw new IllegalArgumentException("criterios da CONAMA 498/2020 declarados pelo responsavel tecnico sao obrigatorios:"
                    + " nenhum criterio e enumerado por esta classe [SEM FONTE para valores da norma]");
        List<CriterionEvaluation> evals = evaluations == null ? List.of() : evaluations;
        for (CriterionEvaluation e : evals) {
            boolean known = criteria.stream().anyMatch(c -> c.criterionKey().equals(e.criterionKey()));
            if (!known) throw new IllegalArgumentException("avaliacao de criterio nao declarado: " + e.criterionKey());
        }
        long evaluated = criteria.stream()
                .filter(c -> evals.stream().anyMatch(e -> e.criterionKey().equals(c.criterionKey()))).count();
        BiosolidStatus status;
        String reason;
        if (evaluated < criteria.size()) {
            status = BiosolidStatus.INDETERMINATE;
            reason = "criterios declarados sem avaliacao: " + (criteria.size() - evaluated)
                    + " — aptidao nao se presume (CONAMA 498/2020)";
        } else if (evals.stream().allMatch(CriterionEvaluation::conforming)) {
            status = BiosolidStatus.ELIGIBLE;
            reason = "todos os criterios declarados avaliados como conformes, com evidencia";
        } else {
            status = BiosolidStatus.NOT_ELIGIBLE;
            reason = "ao menos um criterio declarado avaliado como nao conforme";
        }
        return new BiosolidLot(UUID.randomUUID(), sludge.tenantId(), sludge.id(), sludge.unitId(),
                sludge.quantity(), sludge.quantityUnit(), status,
                criteria.size(), (int) evaluated, reason);
    }
}
