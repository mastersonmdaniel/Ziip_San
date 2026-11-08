package br.com.ziipsan.core.certification.governance;

import java.util.Objects;
/** Criterio de risco DECLARADO pelo tenant (aceite P3.31): sem criterio declarado, nenhum risco e registrado. */
public record RiskCriterion(int maxProbability, int maxImpact, int appetiteThreshold, String description) {
    public RiskCriterion {
        if (maxProbability < 1 || maxImpact < 1) throw new GovernanceValidationException("RISK_CRITERION_SCALE","escalas de probabilidade/impacto devem ser >= 1");
        if (appetiteThreshold < 1 || appetiteThreshold > maxProbability*maxImpact) throw new GovernanceValidationException("RISK_CRITERION_APPETITE","apetite fora da faixa da escala declarada");
        if (description==null||description.isBlank()) throw new GovernanceValidationException("RISK_CRITERION_DESCRIPTION","criterio exige descricao declarada pelo tenant");
        Objects.requireNonNull(description);
    }
    public int score(int p,int i){ if(p<1||p>maxProbability||i<1||i>maxImpact) throw new GovernanceValidationException("RISK_SCORE_OUT_OF_CRITERION","probabilidade/impacto fora do criterio declarado"); return p*i; }
    public boolean withinAppetite(int score){ return score<=appetiteThreshold; }
}
