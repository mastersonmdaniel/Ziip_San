package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.UUID;
/** Avaliacao de fornecedor sobre o cadastro de SupplierRecord (correcao D7: id e tenantId proprios,
 *  alinhados as colunas NOT NULL de cert_supplier_evaluations). */
public record SupplierEvaluation(UUID id, UUID tenantId, UUID supplierId, LocalDate evaluatedOn, int score, String criteriaDeclared, LocalDate nextEvaluationDue) {
    public SupplierEvaluation {
        if (id==null) throw new GovernanceValidationException("SUPPLIER_EVALUATION_ID_REQUIRED","avaliacao de fornecedor exige id");
        if (tenantId==null) throw new GovernanceValidationException("TENANT_REQUIRED","tenant obrigatorio");
        if (supplierId==null) throw new GovernanceValidationException("SUPPLIER_REQUIRED","avaliacao exige fornecedor do cadastro");
        if (evaluatedOn==null) throw new GovernanceValidationException("SUPPLIER_EVALUATED_ON_REQUIRED","avaliacao exige data da avaliacao");
        if (score<0||score>100) throw new GovernanceValidationException("SUPPLIER_SCORE_RANGE","nota fora de 0..100 (faixa declarada nesta tarefa, sem fonte externa)");
        if (criteriaDeclared==null||criteriaDeclared.isBlank()) throw new GovernanceValidationException("SUPPLIER_CRITERIA_REQUIRED","avaliacao exige criterio declarado");
        if (nextEvaluationDue==null) throw new GovernanceValidationException("SUPPLIER_NEXT_DUE_REQUIRED","avaliacao exige proximo vencimento");
    }
}
