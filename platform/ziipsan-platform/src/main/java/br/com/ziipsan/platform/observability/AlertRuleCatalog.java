package br.com.ziipsan.platform.observability;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * P3.11: lê o catálogo versionado de regras de alerta
 * ({@code docs/observability/alert-rules.json}) somente pela classe
 * {@code Json} do core (regra do projeto: nenhum outro caminho monta ou lê
 * JSON) e constrói {@link AlertRule} — a construção recusa regra sem
 * {@code runbook_url} ou sem responsável.
 *
 * <p>Campo ausente no JSON é erro explícito — todos os campos, sem
 * exceção e sem fallback: o leitor não substitui {@code runbook_url},
 * {@code owner} nem {@code consecutive_readings} por padrão, porque um
 * alerta sem runbook é exatamente a falha que a P3.11 manda recusar e um
 * {@code consecutive_readings} assumido em silêncio mudaria a regra sem
 * registro. (Correção pós-revisão P-4: a primeira versão tinha fallback
 * silencioso para {@code consecutive_readings = 1}; removido — o
 * {@code DEFAULT 1} da coluna na V9_1 vale para INSERT direto no banco,
 * não para o catálogo versionado, que deve declarar o valor.)
 */
public final class AlertRuleCatalog {

    private AlertRuleCatalog() {
    }

    public static List<AlertRule> fromJson(String json) {
        JsonNode root = Json.readTree(json);
        if (!root.isArray()) {
            throw new IllegalArgumentException("Catálogo de alertas deve ser um array JSON");
        }
        List<AlertRule> rules = new ArrayList<>();
        for (JsonNode node : root) {
            // A recusa prioritária da P3.11 (runbook/responsável) é
            // verificada ANTES dos demais campos: sem ela, um JSON sem
            // runbook e sem outro campo qualquer falharia pelo outro
            // campo e esconderia justamente a recusa que a tarefa exige.
            // O construtor de AlertRule continua recusando os mesmos
            // casos para quem constrói a regra diretamente.
            String owner = node.hasNonNull("owner") ? node.get("owner").asString() : null;
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException(
                        "Campo obrigatório ausente no catálogo de alertas: owner");
            }
            String runbookUrl = node.hasNonNull("runbook_url") ? node.get("runbook_url").asString() : null;
            if (runbookUrl == null || runbookUrl.isBlank()) {
                throw new IllegalArgumentException(
                        "Campo obrigatório ausente no catálogo de alertas: runbook_url");
            }
            rules.add(new AlertRule(
                    requiredText(node, "rule_key"),
                    requiredText(node, "metric_key"),
                    requiredText(node, "comparator"),
                    requiredNumber(node, "threshold"),
                    requiredInt(node, "consecutive_readings"),
                    requiredText(node, "severity"),
                    requiredText(node, "scope"),
                    owner,
                    runbookUrl));
        }
        return List.copyOf(rules);
    }

    private static String requiredText(JsonNode node, String field) {
        if (!node.hasNonNull(field) || node.get(field).asString().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no catálogo de alertas: " + field);
        }
        return node.get(field).asString();
    }

    private static double requiredNumber(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isNumber()) {
            throw new IllegalArgumentException("Campo numérico obrigatório ausente no catálogo: " + field);
        }
        return node.get(field).asDouble();
    }

    private static int requiredInt(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isNumber()) {
            throw new IllegalArgumentException("Campo inteiro obrigatório ausente no catálogo: " + field);
        }
        return node.get(field).asInt();
    }
}
