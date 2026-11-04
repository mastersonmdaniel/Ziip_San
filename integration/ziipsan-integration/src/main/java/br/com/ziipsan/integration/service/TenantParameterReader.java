package br.com.ziipsan.integration.service;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.console.TenantParameterService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Valor vigente de um parâmetro do tenant (P2.26), na semântica da P1.6:
 * versão aprovada + aceite vigente, ou o padrão de fábrica do catálogo
 * global quando não há versão aceita. A leitura usa o próprio
 * {@link TenantParameterService} (sem duplicar a regra de vigência) e só
 * cai para o catálogo quando ele devolve nulo.
 *
 * <p>Interpretação das classes do plano, aplicada em runtime:
 * <ul>
 *   <li>{@code api.enabled} (d, desligado sem previsão no contrato):
 *       sem aceite, falso — a API nasce desligada;</li>
 *   <li>{@code api.rate_limit_per_minute} (c, só reduz, teto da
 *       plataforma): o valor vigente nunca passa do teto (1000/min) —
 *       valor aceito acima do teto é limitado ao teto, não recusado
 *       aqui (o teto também é limite legal MAX_VALUE do catálogo);</li>
 *   <li>demais chaves (d): valor aceito, ou o padrão de fábrica.</li>
 * </ul>
 */
@Service
public class TenantParameterReader {

    private final DataSource dataSource;

    public TenantParameterReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Valor vigente bruto (JSON) da chave, ou nulo se nem catálogo a tem. */
    public String rawValue(UUID tenantId, String schema, String key) {
        String accepted = null;
        try {
            accepted = new TenantParameterService(dataSource, schema, tenantId).currentAcceptedValue(key);
        } catch (RuntimeException ignored) {
            // Definição ausente (tenant sem a V7_1): cai para o padrão de fábrica abaixo.
        }
        if (accepted != null) {
            return accepted;
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT factory_default::text FROM public.parameter_definitions WHERE definition_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler o parâmetro " + key, e);
        }
    }

    public boolean booleanValue(UUID tenantId, String schema, String key, boolean fallback) {
        String raw = rawValue(tenantId, schema, key);
        if (raw == null || raw.isBlank() || raw.equals("null")) {
            return fallback;
        }
        JsonNode node = Json.readTree(raw);
        return node.isBoolean() ? node.asBoolean() : Boolean.parseBoolean(node.asText());
    }

    public Integer intValue(UUID tenantId, String schema, String key) {
        String raw = rawValue(tenantId, schema, key);
        if (raw == null || raw.isBlank() || raw.equals("null")) {
            return null;
        }
        JsonNode node = Json.readTree(raw);
        return node.isNumber() ? node.asInt() : Integer.parseInt(node.asText());
    }

    /** Lista JSON de strings (allowlist de IPs, tipos de evento) ou lista vazia. */
    public List<String> stringListValue(UUID tenantId, String schema, String key) {
        String raw = rawValue(tenantId, schema, key);
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank() || raw.equals("null")) {
            return out;
        }
        JsonNode node = Json.readTree(raw);
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    out.add(item.asText());
                }
            }
        } else if (node.isTextual()) {
            out.add(node.asText());
        }
        return out;
    }
}
