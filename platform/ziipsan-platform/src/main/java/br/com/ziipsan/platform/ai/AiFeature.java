package br.com.ziipsan.platform.ai;

/** Funcoes de IA da ED-43. TODAS nascem DESLIGADAS (ai.enabled_features, classe b, padrao desligado).
 * Funcoes ligadas a corte/negativacao/tarifa social NUNCA sao funcoes de IA: sao DECISOES PROIBIDAS (ADR-0042/GES-08). */
public enum AiFeature {
    CHATBOT, BILL_EXPLAIN, ATTENDANT_COPILOT, OS_OPINION_DRAFT, SHIFT_SUMMARY, SEMANTIC_SEARCH, SENTIMENT
}
