package br.com.ziipsan.reporting.bi;

import br.com.ziipsan.core.json.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-47: evento de auditoria de acesso ao BI (SG-1252).
 *
 * <p>LGPD por construção: o evento NÃO tem campo para o texto da
 * consulta, para parâmetros nem para linhas de resultado — consulta
 * livre pode conter documento de titular digitado no filtro, e auditoria
 * que guarda o SQL vira um segundo banco de dado pessoal. O que se
 * guarda é o suficiente para investigar: quem (usuário do SSO), de qual
 * tenant, em qual fonte, que ação, sobre qual objeto (nome do
 * dashboard/dataset, nunca o conteúdo) e, para consulta, o RESUMO
 * SHA-256 do SQL normalizado e o seu tamanho — correlação sem conteúdo.
 *
 * <p>A serialização sai só pela classe {@code Json} do core (regra do
 * projeto), a partir de um mapa de campos escalares declarados aqui.
 */
public record BiAccessEvent(
        UUID tenantId,
        UUID userId,
        Action action,
        String dataSourceName,
        String objectName,
        String queryHash,
        Integer queryLength,
        Instant occurredAt) {

    public enum Action {
        LOGIN, DATASET_ACCESS, DASHBOARD_VIEW, QUERY_EXECUTED, EXPORT,
        ACCESS_DENIED, DATASOURCE_REJECTED
    }

    public BiAccessEvent {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        Objects.requireNonNull(userId, "userId é obrigatório");
        Objects.requireNonNull(action, "action é obrigatória");
        Objects.requireNonNull(occurredAt, "occurredAt é obrigatório");
        if (action == Action.QUERY_EXECUTED && (queryHash == null || queryLength == null)) {
            throw new IllegalArgumentException("Consulta executada exige o resumo e o tamanho do SQL");
        }
        if (action != Action.QUERY_EXECUTED && queryHash != null) {
            throw new IllegalArgumentException("Resumo de SQL só existe em QUERY_EXECUTED");
        }
    }

    /** Consulta executada: recebe o SQL só para resumir; o texto não é guardado em campo algum. */
    public static BiAccessEvent queryExecuted(UUID tenantId, UUID userId, String dataSourceName,
                                              String sql, Instant now) {
        Objects.requireNonNull(sql, "sql é obrigatório para resumir");
        return new BiAccessEvent(tenantId, userId, Action.QUERY_EXECUTED, dataSourceName, null,
                sha256(sql), sql.length(), now);
    }

    public static BiAccessEvent of(UUID tenantId, UUID userId, Action action,
                                   String dataSourceName, String objectName, Instant now) {
        return new BiAccessEvent(tenantId, userId, action, dataSourceName, objectName, null, null, now);
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /** JSON do evento, só com os campos declarados, via {@code Json} do core. */
    public String toJson() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tenantId", tenantId.toString());
        fields.put("userId", userId.toString());
        fields.put("action", action.name());
        fields.put("dataSourceName", dataSourceName);
        fields.put("objectName", objectName);
        fields.put("queryHash", queryHash);
        fields.put("queryLength", queryLength);
        fields.put("occurredAt", occurredAt.toString());
        return Json.write(fields);
    }
}
