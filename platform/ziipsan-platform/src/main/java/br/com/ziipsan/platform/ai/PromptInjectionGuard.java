package br.com.ziipsan.platform.ai;

import java.util.List;
import java.util.Locale;

/** Protecoes OWASP Top 10 para LLM 2025 (aceite): injecao de instrucao (LLM01), vazamento de dado
 * sensivel (LLM02), agencia excessiva (LLM06 — ver ClosedFunctionRegistry) e consumo ilimitado
 * (LLM10 — ver AiQuotaRule). Injecao detectada NAO executa: transborda para humano. */
public final class PromptInjectionGuard {
    private static final List<String> PATTERNS = List.of(
        "ignore as instrucoes", "ignore suas instrucoes", "esqueca as regras", "system prompt",
        "prompt do sistema", "revele suas instrucoes", "voce agora e", "aja como administrador",
        "modo desenvolvedor", "jailbreak", "desconsidere o que foi dito", "nova instrucao:");
    private PromptInjectionGuard() {}
    public static boolean looksLikeInjection(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return PATTERNS.stream().anyMatch(t::contains);
    }
    /** Vazamento na SAIDA: resposta do modelo com PII estruturada e descartada, nunca entregue. */
    public static boolean outputLeaksPii(String modelOutput) { return PiiMasker.containsStructuredPii(modelOutput); }
}
