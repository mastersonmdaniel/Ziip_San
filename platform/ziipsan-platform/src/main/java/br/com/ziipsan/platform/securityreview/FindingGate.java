package br.com.ziipsan.platform.securityreview;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portao de achados da P3.8: "nenhum achado alto aberto". Alto ou critico
 * em aberto BLOQUEIA a rodada — nao existe caminho de aceite de risco
 * neste portao (aceitar risco de achado alto e decisao fora desta
 * classe, registrada no diario, nunca um status que passe em silencio).
 *
 * <p>Le relatorios ZAP em JSON pela classe {@link Json} do core.
 * Relatorio malformado lanca excecao (falha fechada): relatorio que nao
 * se le nao vira rodada verde. Relatorio ESTRUTURALMENTE VAZIO tambem
 * falha fechado (correcao D4 da revisao cruzada): sem a estrutura
 * {@code site[]}, com {@code site} vazio/nao-lista, com site sem
 * {@code alerts} ou com ZERO alertas no total, o relatorio e tratado
 * como incompleto/truncado — nunca como "nenhum achado alto". O espelho
 * em Python (scripts/security/avaliar-achados.py) aplica a mesma regra.
 */
public final class FindingGate {

    public enum Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }

    public enum Status { OPEN, RESOLVED, FALSE_POSITIVE }

    public record Finding(String id, String title, Severity severity, Status status, String source) { }

    public record Result(boolean passed, List<Finding> blocking, String summaryJson) { }

    private FindingGate() { }

    public static Result evaluate(List<Finding> findings) {
        if (findings == null) {
            throw new IllegalArgumentException("lista de achados e obrigatoria");
        }
        List<Finding> blocking = new ArrayList<>();
        for (Finding f : findings) {
            if (f.status() == Status.OPEN
                    && (f.severity() == Severity.HIGH || f.severity() == Severity.CRITICAL)) {
                blocking.add(f);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", findings.size());
        summary.put("blockingOpenHighOrCritical", blocking.size());
        summary.put("passed", blocking.isEmpty());
        List<Object> items = new ArrayList<>();
        for (Finding f : blocking) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id());
            m.put("title", f.title());
            m.put("severity", f.severity().name());
            m.put("source", f.source());
            items.add(m);
        }
        summary.put("blocking", items);
        return new Result(blocking.isEmpty(), List.copyOf(blocking), Json.write(summary));
    }

    /**
     * Achados a partir do relatorio JSON do OWASP ZAP (estrutura
     * {@code site[].alerts[]}, com {@code riskdesc} como "High (Medium)").
     * Cada alerta vira um achado OPEN: o relatorio de varredura nao traz
     * resolucao — quem resolve registra o achado como RESOLVED ou
     * FALSE_POSITIVE na lista consolidada da rodada, com justificativa
     * fora deste portao.
     */
    public static List<Finding> fromZapReport(String zapJson) {
        Map<String, Object> root = Json.readMap(zapJson);
        List<Finding> findings = new ArrayList<>();
        Object site = root.get("site");
        if (site == null) {
            throw new IllegalStateException(
                    "relatorio ZAP sem a estrutura 'site': relatorio vazio/incompleto nao vira rodada verde (falha fechada)");
        }
        if (!(site instanceof List)) {
            throw new IllegalStateException(
                    "relatorio ZAP com 'site' fora do formato de lista esperado (falha fechada)");
        }
        List<Object> sites = asList(site);
        if (sites.isEmpty()) {
            throw new IllegalStateException(
                    "relatorio ZAP com 'site' vazio: nenhum site varrido nao e evidencia de rodada (falha fechada)");
        }
        int totalAlerts = 0;
        for (Object siteItem : sites) {
            if (!(siteItem instanceof Map)) {
                throw new IllegalStateException(
                        "relatorio ZAP com item de 'site' fora do formato de objeto esperado (falha fechada)");
            }
            Map<String, Object> siteMap = asMap(siteItem);
            Object alerts = siteMap.get("alerts");
            if (alerts == null) {
                throw new IllegalStateException(
                        "relatorio ZAP com site sem 'alerts': relatorio incompleto (falha fechada)");
            }
            if (!(alerts instanceof List)) {
                throw new IllegalStateException(
                        "relatorio ZAP com 'alerts' fora do formato de lista esperado (falha fechada)");
            }
            for (Object alertItem : asList(alerts)) {
                if (!(alertItem instanceof Map)) {
                    throw new IllegalStateException(
                            "relatorio ZAP com alerta fora do formato de objeto esperado (falha fechada)");
                }
                totalAlerts++;
                Map<String, Object> alert = asMap(alertItem);
                String risk = String.valueOf(alert.getOrDefault("riskdesc", ""));
                Severity severity = severityOf(risk);
                if (severity == null) {
                    continue;
                }
                String pluginId = String.valueOf(alert.getOrDefault("pluginid", "zap"));
                String name = String.valueOf(alert.getOrDefault("name", "alerta ZAP"));
                findings.add(new Finding("ZAP-" + pluginId, name, severity, Status.OPEN, "OWASP ZAP"));
            }
        }
        if (totalAlerts == 0) {
            throw new IllegalStateException(
                    "relatorio ZAP sem nenhum alerta: relatorio vazio nao e evidencia de varredura (falha fechada)");
        }
        return findings;
    }

    static Severity severityOf(String riskdesc) {
        if (riskdesc == null) {
            return null;
        }
        String r = riskdesc.trim().toLowerCase(java.util.Locale.ROOT);
        if (r.startsWith("critical")) {
            return Severity.CRITICAL;
        }
        if (r.startsWith("high")) {
            return Severity.HIGH;
        }
        if (r.startsWith("medium")) {
            return Severity.MEDIUM;
        }
        if (r.startsWith("low")) {
            return Severity.LOW;
        }
        if (r.startsWith("informational")) {
            return Severity.INFO;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }
}
