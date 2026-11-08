package br.com.ziipsan.platform.securityreview;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plano da varredura DAST autenticada (P3.8): a varredura so vale como
 * evidencia se for AUTENTICADA nos dois contextos (portal do consumidor
 * e backoffice), com usuarios SINTETICOS do ambiente local (ADR-0033:
 * nenhum dado pessoal real na base local), alvo LOCAL de homologacao e o
 * portao de achados altos ligado. Plano fora disto e recusado — varredura
 * anonima ou contra producao nao e evidencia desta tarefa.
 */
public record ZapScanPlan(boolean authenticated, Set<String> contexts, Set<String> syntheticUsers,
                          boolean failOnHigh, boolean productionTarget, String baseUrl) {

    public static final String PORTAL_CONTEXT = "portal";
    public static final String BACKOFFICE_CONTEXT = "backoffice";

    public ZapScanPlan {
        contexts = contexts == null ? Set.of() : Set.copyOf(contexts);
        syntheticUsers = syntheticUsers == null ? Set.of() : Set.copyOf(syntheticUsers);
    }

    /** Problemas do plano; lista vazia = plano valido como evidencia. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (!authenticated) {
            problems.add("varredura precisa ser autenticada (DAST autenticada do aceite)");
        }
        if (!contexts.contains(PORTAL_CONTEXT)) {
            problems.add("contexto autenticado do portal ausente");
        }
        if (!contexts.contains(BACKOFFICE_CONTEXT)) {
            problems.add("contexto autenticado do backoffice ausente");
        }
        if (syntheticUsers.isEmpty()) {
            problems.add("nenhum usuario sintetico declarado para a autenticacao da varredura");
        }
        for (String user : syntheticUsers) {
            if (!user.contains("sintetico") || !user.endsWith(".invalid")) {
                problems.add("usuario da varredura nao e sintetico marcado (*.invalid com marcador 'sintetico'): " + user);
            }
        }
        if (!failOnHigh) {
            problems.add("portao de achados altos desligado: rodada com alto aberto nao pode passar");
        }
        if (productionTarget) {
            problems.add("alvo de producao recusado: a P3.8 e no ambiente local (ADR-0033); producao e da LC.8");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            problems.add("baseUrl do ambiente local e obrigatoria");
        } else if (!isLocalUrl(baseUrl)) {
            // Correcao D3 da revisao cruzada: a localidade e derivada da
            // PROPRIA URL, nao da flag productionTarget — plano com URL de
            // producao e productionTarget=false era aceito como "valido".
            problems.add("baseUrl nao e do ambiente local de homologacao (so 127.0.0.1 ou localhost): " + baseUrl);
        }
        return problems;
    }

    public void requireValid() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("plano DAST invalido: " + String.join("; ", problems));
        }
    }

    /**
     * Alvo local de verdade: o HOST da URL precisa ser 127.0.0.1,
     * localhost ou ::1 — prefixo de string nao basta (um host como
     * {@code localhost.exemplo.com} NAO e local). Espelha a conferencia
     * do scripts/security/run-dast.sh, com o host parseado.
     */
    static boolean isLocalUrl(String url) {
        try {
            String host = java.net.URI.create(url.trim()).getHost();
            return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
