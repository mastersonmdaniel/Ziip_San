package br.com.ziipsan.platform.ai;

import java.math.BigDecimal;

/** Cota e teto de custo por tenant (PLT-18) + consumo ilimitado (OWASP LLM10): sem cota configurada,
 * sem teto configurado ou estouro de qualquer um => chamada recusada ANTES do modelo.
 * CORRECAO ED-43 (m2 da revisao cruzada) — DECISAO REGISTRADA (fail-closed): spent == null (sem
 * leitura do gasto acumulado) ou estimatedCost == null (sem estimativa) NAO pulam o teto: a chamada
 * e recusada como CEILING_EXCEEDED. Sem leitura de gasto nao ha chamada — e a regua mais segura,
 * preferida a "teto tratado como zero disponivel" porque nao presume saldo que ninguem mediu. */
public final class AiQuotaRule {
    private AiQuotaRule() {}
    public enum Verdict { ALLOWED, NO_QUOTA_CONFIGURED, QUOTA_EXCEEDED, NO_CEILING_CONFIGURED, CEILING_EXCEEDED }
    public static Verdict check(Long quotaTokens, long usedTokens, long requestedEstimate,
                                BigDecimal ceiling, BigDecimal spent, BigDecimal estimatedCost) {
        if (quotaTokens == null) return Verdict.NO_QUOTA_CONFIGURED;
        if (ceiling == null) return Verdict.NO_CEILING_CONFIGURED;
        if (usedTokens + Math.max(0, requestedEstimate) > quotaTokens) return Verdict.QUOTA_EXCEEDED;
        if (spent == null || estimatedCost == null) return Verdict.CEILING_EXCEEDED;
        if (spent.add(estimatedCost).compareTo(ceiling) > 0) return Verdict.CEILING_EXCEEDED;
        return Verdict.ALLOWED;
    }
    /** Reconciliacao POS-chamada (m2): confere o consumo REAL devolvido pelo modelo contra cota e
     * teto. spent/realCost nulos reprovam (fail-closed, mesma decisao da pre-checagem). */
    public static boolean reconciles(Long quotaTokens, long usedTokensBefore, long realTokens,
                                     BigDecimal ceiling, BigDecimal spentBefore, BigDecimal realCost) {
        if (quotaTokens == null || ceiling == null || spentBefore == null || realCost == null) return false;
        if (usedTokensBefore + Math.max(0, realTokens) > quotaTokens) return false;
        return spentBefore.add(realCost).compareTo(ceiling) <= 0;
    }
}
