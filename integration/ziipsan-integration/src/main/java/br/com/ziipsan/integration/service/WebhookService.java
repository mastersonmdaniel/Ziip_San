package br.com.ziipsan.integration.service;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.integration.PublicApiScopes;
import br.com.ziipsan.integration.webhook.SsrfUrlValidator;
import br.com.ziipsan.integration.webhook.WebhookBackoff;
import br.com.ziipsan.integration.webhook.WebhookEventCatalog;
import br.com.ziipsan.integration.webhook.WebhookHttpSender;
import br.com.ziipsan.integration.webhook.WebhookSigner;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Webhooks de saída (P2.26) — endpoints, entregas e reenvio.
 *
 * <p>Entrega pelo menos uma vez, com {@code event_id} único por tenant
 * (a unicidade do banco torna o reenvio idempotente: publicar o mesmo
 * evento duas vezes não duplica a entrega), ordem por chave de agregado
 * (o lote de envio ordena por {@code aggregate_key, created_at} e
 * processa um agregado por vez na ordem de criação), assinatura
 * HMAC-SHA-256 com carimbo de tempo, rotação de segredo com sobreposição
 * de 24 h, tentativas com recuo exponencial e jitter, desativação
 * automática após 5 falhas consecutivas (com alerta em
 * {@code webhook_alerts}, visível na tela), e reenvio manual por 30 dias.
 *
 * <p>A carga leva só identificadores e códigos (catálogo da P1.38): o
 * publicador valida nome, versão e campos antes de gravar qualquer
 * entrega, e evento fora do catálogo é recusado.
 *
 * <p>Portões do tenant: {@code api.webhooks_enabled} precisa estar
 * ligado; o número de endpoints ativos respeita
 * {@code api.webhook_max_endpoints}; os tipos de evento respeitam
 * {@code api.webhook_event_types} quando o parâmetro está definido.
 */
@Service
public class WebhookService {

    public record EndpointView(UUID id, String url, List<String> eventTypes, String status,
                               int consecutiveFailures, Instant disabledAt, Instant createdAt) {
    }

    public record DeliveryView(UUID id, UUID endpointId, UUID eventId, String eventName, int eventVersion,
                               String aggregateKey, String payload, String status, int attempts,
                               Instant nextAttemptAt, String lastError, Instant deliveredAt, Instant createdAt) {
    }

    public record CreatedEndpoint(EndpointView endpoint, String secret) {
    }

    private final DataSource dataSource;
    private final TenantParameterReader parameters;
    private final WebhookHttpSender sender;
    private final FieldCipher cipher;

    public WebhookService(DataSource dataSource, TenantParameterReader parameters, WebhookHttpSender sender,
                          FieldCipher cipher) {
        this.dataSource = dataSource;
        this.parameters = parameters;
        this.sender = sender;
        this.cipher = cipher;
    }

    // -------------------------------------------------------------- endpoints
    public List<EndpointView> listEndpoints() {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            List<EndpointView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, url, event_types, status, consecutive_failures, disabled_at, created_at"
                            + " FROM webhook_endpoints WHERE tenant_id = ? ORDER BY created_at")) {
                ps.setObject(1, IntegrationContext.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(endpointView(rs));
                    }
                }
            }
            return out;
        });
    }

    public CreatedEndpoint createEndpoint(String url, List<String> eventTypes) {
        if (url == null || eventTypes == null || eventTypes.isEmpty()) {
            throw new ApiException.Validation("A URL e pelo menos um tipo de evento são obrigatórios.",
                    List.of(new ApiException.FieldError("url", "required",
                            "Informe a URL e os tipos de evento.")), List.of());
        }
        try {
            SsrfUrlValidator.validate(url, SsrfUrlValidator.SYSTEM);
        } catch (IllegalArgumentException e) {
            throw new ApiException.Validation("A URL do endpoint é inválida: " + e.getMessage(),
                    List.of(new ApiException.FieldError("url", "invalid", e.getMessage())), List.of());
        }
        UUID tenantId = IntegrationContext.tenantId();
        String schema = IntegrationContext.schema();
        if (!parameters.booleanValue(tenantId, schema, "api.webhooks_enabled", false)) {
            throw new ApiException.Forbidden("Os webhooks de saída estão desligados para este tenant.");
        }
        List<String> allowedTypes = parameters.stringListValue(tenantId, schema, "api.webhook_event_types");
        List<ApiException.FieldError> errors = new ArrayList<>();
        for (String type : eventTypes) {
            if (WebhookEventCatalog.find(type) == null) {
                errors.add(new ApiException.FieldError("eventTypes", "unknown-event",
                        "Tipo de evento fora do catálogo: " + type + "."));
            } else if (!allowedTypes.isEmpty() && !allowedTypes.contains(type)) {
                errors.add(new ApiException.FieldError("eventTypes", "not-allowed",
                        "Tipo de evento não permitido pelo parâmetro do tenant: " + type + "."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException.Validation("Há campos inválidos.", errors, List.of());
        }
        Integer max = parameters.intValue(tenantId, schema, "api.webhook_max_endpoints");
        int maxEndpoints = max == null ? 5 : max;
        String secret = SecretHash.generate("ziip_whsec_");
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement count = c.prepareStatement(
                    "SELECT count(*) FROM webhook_endpoints WHERE tenant_id = ? AND status = 'ACTIVE'")) {
                count.setObject(1, tenantId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) >= maxEndpoints) {
                        throw new ApiException.Validation("Limite de endpoints ativos do tenant atingido.",
                                List.of(new ApiException.FieldError("url", "limit-reached",
                                        "O tenant já tem " + maxEndpoints + " endpoints ativos.")), List.of());
                    }
                }
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO webhook_endpoints (id, tenant_id, url, event_types, secret_cipher)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, url);
                ps.setArray(4, c.createArrayOf("text", eventTypes.toArray()));
                ps.setString(5, cipher.encrypt(tenantId, "webhook_endpoints.secret", secret));
                ps.executeUpdate();
            }
            return new CreatedEndpoint(endpointInTx(c, id), secret);
        });
    }

    public CreatedEndpoint rotateEndpointSecret(UUID id) {
        UUID tenantId = IntegrationContext.tenantId();
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            EndpointSecrets secrets = secretsInTx(c, id);
            String secret = SecretHash.generate("ziip_whsec_");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_endpoints SET secret_cipher = ?, previous_secret_cipher = ?,"
                            + " previous_valid_until = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, cipher.encrypt(tenantId, "webhook_endpoints.secret", secret));
                ps.setString(2, secrets.secretCipher());
                ps.setTimestamp(3, Timestamp.from(Instant.now().plusSeconds(
                        PublicApiScopes.SECRET_OVERLAP_HOURS * 3600L)));
                ps.setObject(4, tenantId);
                ps.setObject(5, id);
                ps.executeUpdate();
            }
            return new CreatedEndpoint(endpointInTx(c, id), secret);
        });
    }

    public void disableEndpoint(UUID id) {
        setEndpointStatus(id, "DISABLED", true);
    }

    /**
     * Atualiza o status do endpoint, carimbando {@code disabled_at} quando
     * {@code markDisabled} é verdadeiro. A existência do endpoint é validada
     * por {@link #endpointInTx(java.sql.Connection, UUID)} antes do UPDATE.
     */
    private void setEndpointStatus(UUID id, String status, boolean markDisabled) {
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            endpointInTx(c, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_endpoints SET status = ?,"
                            + " disabled_at = CASE WHEN ? THEN NOW() ELSE disabled_at END,"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, status);
                ps.setBoolean(2, markDisabled);
                ps.setObject(3, IntegrationContext.tenantId());
                ps.setObject(4, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void enableEndpoint(UUID id) {
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            endpointInTx(c, id);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_endpoints SET status = 'ACTIVE', consecutive_failures = 0,"
                            + " disabled_at = NULL, updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Envio de teste: publica o evento de teste para o endpoint. */
    public UUID sendTest(UUID endpointId) {
        UUID tenantId = IntegrationContext.tenantId();
        // O endpoint precisa existir e estar ativo: publicar para um id
        // desconhecido devolveria um event_id sem nenhuma entrega.
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            EndpointView endpoint = endpointInTx(c, endpointId);
            if (!"ACTIVE".equals(endpoint.status())) {
                throw new ApiException.Validation("O endpoint está desativado.",
                        List.of(new ApiException.FieldError("status", "endpoint-disabled",
                                "Reative o endpoint antes de enviar o teste.")), List.of());
            }
            return null;
        });
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("endpointId", endpointId.toString());
        payload.put("sentAt", Instant.now().toString());
        return publish(tenantId, WebhookEventCatalog.WEBHOOK_TEST.name(), 1,
                "endpoint:" + endpointId, payload, List.of(endpointId));
    }

    // -------------------------------------------------------------- publicação
    /**
     * Publica um evento para os endpoints ativos inscritos no tipo.
     * Devolve o {@code event_id} (único por tenant). Publicar o mesmo
     * evento duas vezes (mesmo event_id) não duplica entregas: a segunda
     * publicação é absorvida pela unicidade do banco.
     */
    public UUID publish(UUID tenantId, String eventName, int version, String aggregateKey,
                        Map<String, Object> payload) {
        return publish(tenantId, eventName, version, aggregateKey, payload, null, UUID.randomUUID());
    }

    public UUID publish(UUID tenantId, String eventName, int version, String aggregateKey,
                        Map<String, Object> payload, List<UUID> onlyEndpoints) {
        return publish(tenantId, eventName, version, aggregateKey, payload, onlyEndpoints,
                UUID.randomUUID());
    }

    /**
     * Publica um evento com {@code event_id} dado (o caminho da outbox da
     * P1.9: o id da mensagem vira o event_id, e republicar a mesma
     * mensagem não duplica entregas — a unicidade
     * (tenant, endpoint, event) absorve a segunda publicação).
     */
    public UUID publish(UUID tenantId, String eventName, int version, String aggregateKey,
                        Map<String, Object> payload, List<UUID> onlyEndpoints, UUID eventId) {
        Map<String, Object> full = new LinkedHashMap<>(payload);
        full.put("tenantId", tenantId.toString());
        WebhookEventCatalog.requirePublishable(eventName, version, full);
        String payloadJson = Json.write(full);
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            String sql = "SELECT id FROM webhook_endpoints WHERE tenant_id = ? AND status = 'ACTIVE'"
                    + " AND ? = ANY(event_types)";
            if (onlyEndpoints != null) {
                sql = "SELECT id FROM webhook_endpoints WHERE tenant_id = ? AND status = 'ACTIVE'"
                        + " AND ? = ANY(event_types) AND id = ANY(?)";
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setString(2, eventName);
                if (onlyEndpoints != null) {
                    ps.setArray(3, c.createArrayOf("uuid", onlyEndpoints.toArray()));
                }
                List<UUID> endpoints = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        endpoints.add((UUID) rs.getObject(1));
                    }
                }
                for (UUID endpointId : endpoints) {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO webhook_deliveries (id, tenant_id, endpoint_id, event_id, event_name,"
                                    + " event_version, aggregate_key, payload, next_attempt_at)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())"
                                    + " ON CONFLICT (tenant_id, endpoint_id, event_id) DO NOTHING")) {
                        // O mesmo event_id vai para cada endpoint inscrito
                        // (é o identificador do evento, não da entrega); a
                        // unicidade é (tenant, endpoint, event).
                        ins.setObject(1, UUID.randomUUID());
                        ins.setObject(2, tenantId);
                        ins.setObject(3, endpointId);
                        ins.setObject(4, eventId);
                        ins.setString(5, eventName);
                        ins.setInt(6, version);
                        ins.setString(7, aggregateKey);
                        ins.setString(8, payloadJson);
                        ins.executeUpdate();
                    }
                }
            }
            return null;
        });
        return eventId;
    }

    // ---------------------------------------------------------------- entregas
    public List<DeliveryView> listDeliveries(UUID endpointId, String status) {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, endpoint_id, event_id, event_name, event_version, aggregate_key, payload,"
                            + " status, attempts, next_attempt_at, last_error, delivered_at, created_at"
                            + " FROM webhook_deliveries WHERE tenant_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(IntegrationContext.tenantId());
            if (endpointId != null) {
                sql.append(" AND endpoint_id = ?");
                params.add(endpointId);
            }
            if (status != null) {
                sql.append(" AND status = ?");
                params.add(status);
            }
            sql.append(" ORDER BY created_at DESC LIMIT 200");
            List<DeliveryView> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    if (params.get(i) instanceof UUID uuid) {
                        ps.setObject(i + 1, uuid);
                    } else {
                        ps.setString(i + 1, String.valueOf(params.get(i)));
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(deliveryView(rs));
                    }
                }
            }
            return out;
        });
    }

    /** Reenvio manual, dentro da janela de 30 dias da criação. */
    public void retryDelivery(UUID deliveryId) {
        TenantJdbc.inTenantTransaction(dataSource, c -> {
            DeliveryRow row;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.id, d.status, d.created_at, e.status FROM webhook_deliveries d"
                            + " JOIN webhook_endpoints e ON e.tenant_id = d.tenant_id AND e.id = d.endpoint_id"
                            + " WHERE d.tenant_id = ? AND d.id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, deliveryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("A entrega não existe neste tenant.");
                    }
                    row = new DeliveryRow(rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getString(4));
                }
            }
            if ("SENT".equals(row.status())) {
                throw new ApiException.Validation("A entrega já foi concluída.",
                        List.of(new ApiException.FieldError("status", "already-sent",
                                "Entrega já enviada; não há o que reenviar.")), List.of());
            }
            if (row.createdAt().isBefore(Instant.now().minusSeconds(
                    PublicApiScopes.DELIVERY_RETRY_DAYS * 86400L))) {
                throw new ApiException.Validation("A janela de reenvio de 30 dias expirou.",
                        List.of(new ApiException.FieldError("status", "retry-window-expired",
                                "Entregas só podem ser reenviadas por 30 dias.")), List.of());
            }
            if (!"ACTIVE".equals(row.endpointStatus())) {
                throw new ApiException.Validation("O endpoint está desativado.",
                        List.of(new ApiException.FieldError("status", "endpoint-disabled",
                                "Reative o endpoint antes de reenviar.")), List.of());
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_deliveries SET status = 'PENDING', next_attempt_at = NOW(),"
                            + " last_error = NULL, updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, deliveryId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Envia as entregas vencidas (lote), na ordem por chave de agregado e
     * criação. Devolve quantas foram enviadas com sucesso. Chamado pelo
     * agendador do tenant (ou manualmente, na tela, via reenvio); o
     * agendamento recorrente em produção é da LC (o worker da P1.9 é o
     * caminho de execução, ver relatório).
     *
     * <p>Entra no lote toda entrega PENDING e toda FAILED com a próxima
     * tentativa vencida: a falha marca FAILED com {@code next_attempt_at}
     * (recuo exponencial), e é essa data que a traz de volta ao lote —
     * sem isto, a primeira falha encerraria a cadeia de reenvio
     * automático e só restaria o reenvio manual.
     */
    public int deliverDue(int limit) {
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            List<DueDelivery> due = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT d.id, d.endpoint_id, d.event_id, d.event_name, d.payload, d.attempts,"
                            + " e.url, e.secret_cipher, e.previous_secret_cipher, e.previous_valid_until,"
                            + " e.consecutive_failures"
                            + " FROM webhook_deliveries d JOIN webhook_endpoints e"
                            + " ON e.tenant_id = d.tenant_id AND e.id = d.endpoint_id"
                            + " WHERE d.tenant_id = ? AND d.status IN ('PENDING', 'FAILED')"
                            + " AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= NOW())"
                            + " AND e.status = 'ACTIVE'"
                            + " ORDER BY d.aggregate_key, d.created_at LIMIT ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        due.add(new DueDelivery((UUID) rs.getObject(1), (UUID) rs.getObject(2),
                                (UUID) rs.getObject(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                                rs.getString(7), rs.getString(8), rs.getString(9),
                                rs.getTimestamp(10), rs.getInt(11)));
                    }
                }
            }
            int sent = 0;
            for (DueDelivery delivery : due) {
                sent += deliverOne(c, delivery) ? 1 : 0;
            }
            return sent;
        });
    }

    private boolean deliverOne(Connection c, DueDelivery delivery) throws java.sql.SQLException {
        // O segredo é guardado cifrado com a chave do tenant (FieldCipher):
        // decifra-se no envio, nunca se grava em claro. Se a decifra
        // falhar (chave rodada sem re-cifra), a entrega falha sem vazar o
        // motivo interno — e conta como falha consecutiva do endpoint.
        UUID tenantId = IntegrationContext.tenantId();
        String secret;
        try {
            secret = cipher.decrypt(tenantId, "webhook_endpoints.secret", delivery.secretCipher());
        } catch (RuntimeException e) {
            failDelivery(c, delivery, "Falha ao decifrar o segredo do endpoint.");
            return false;
        }
        long timestamp = Instant.now().getEpochSecond();
        String signature = WebhookSigner.sign(secret, timestamp, delivery.eventId().toString(), delivery.payload());
        boolean ok = false;
        String error = null;
        try {
            var result = sender.send(URI.create(delivery.url()), delivery.payload(),
                    delivery.eventId().toString(), timestamp, signature);
            if (result.status() >= 200 && result.status() < 300) {
                ok = true;
            } else if (result.status() >= 300 && result.status() < 400) {
                error = "Redirecionamento não seguido (HTTP " + result.status() + ").";
            } else {
                error = "HTTP " + result.status() + ".";
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName()
                    : e.getMessage().substring(0, Math.min(500, e.getMessage().length()));
        }
        if (ok) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_deliveries SET status = 'SENT', attempts = attempts + 1,"
                            + " delivered_at = NOW(), next_attempt_at = NULL, last_error = NULL,"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, delivery.id());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_endpoints SET consecutive_failures = 0, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, delivery.endpointId());
                ps.executeUpdate();
            }
            return true;
        }
        failDelivery(c, delivery, error);
        return false;
    }

    private void failDelivery(Connection c, DueDelivery delivery, String error) throws java.sql.SQLException {
        int failures = delivery.consecutiveFailures() + 1;
        Instant next = Instant.now().plus(WebhookBackoff.nextDelay(delivery.attempts() + 1));
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE webhook_deliveries SET status = 'FAILED', attempts = attempts + 1,"
                        + " next_attempt_at = ?, last_error = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setTimestamp(1, Timestamp.from(next));
            ps.setString(2, error);
            ps.setObject(3, IntegrationContext.tenantId());
            ps.setObject(4, delivery.id());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE webhook_endpoints SET consecutive_failures = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setInt(1, failures);
            ps.setObject(2, IntegrationContext.tenantId());
            ps.setObject(3, delivery.endpointId());
            ps.executeUpdate();
        }
        if (failures >= PublicApiScopes.MAX_CONSECUTIVE_FAILURES) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE webhook_endpoints SET status = 'DISABLED', disabled_at = NOW(),"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, delivery.endpointId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO webhook_alerts (id, tenant_id, endpoint_id, reason) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, IntegrationContext.tenantId());
                ps.setObject(3, delivery.endpointId());
                ps.setString(4, "Endpoint desativado após " + failures + " falhas consecutivas.");
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------ utils
    private record DeliveryRow(String status, Instant createdAt, String endpointStatus) {
    }

    private record EndpointSecrets(String secretCipher) {
    }

    private record DueDelivery(UUID id, UUID endpointId, UUID eventId, String eventName, String payload,
                               int attempts, String url, String secretCipher, String previousSecretCipher,
                               Timestamp previousValidUntil, int consecutiveFailures) {
    }

    private EndpointView endpointInTx(Connection c, UUID id) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, url, event_types, status, consecutive_failures, disabled_at, created_at"
                        + " FROM webhook_endpoints WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, IntegrationContext.tenantId());
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O endpoint não existe neste tenant.");
                }
                return endpointView(rs);
            }
        }
    }

    private EndpointSecrets secretsInTx(Connection c, UUID id) throws java.sql.SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT secret_cipher FROM webhook_endpoints WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, IntegrationContext.tenantId());
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O endpoint não existe neste tenant.");
                }
                return new EndpointSecrets(rs.getString(1));
            }
        }
    }

    private static EndpointView endpointView(ResultSet rs) throws java.sql.SQLException {
        Array array = rs.getArray(3);
        List<String> types = array == null ? List.of() : List.of((String[]) array.getArray());
        Timestamp disabled = rs.getTimestamp(6);
        Timestamp created = rs.getTimestamp(7);
        return new EndpointView((UUID) rs.getObject(1), rs.getString(2), types, rs.getString(4),
                rs.getInt(5), disabled == null ? null : disabled.toInstant(),
                created == null ? null : created.toInstant());
    }

    private static DeliveryView deliveryView(ResultSet rs) throws java.sql.SQLException {
        Timestamp next = rs.getTimestamp(10);
        Timestamp delivered = rs.getTimestamp(12);
        Timestamp created = rs.getTimestamp(13);
        return new DeliveryView((UUID) rs.getObject(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3),
                rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getInt(9), next == null ? null : next.toInstant(), rs.getString(11),
                delivered == null ? null : delivered.toInstant(),
                created == null ? null : created.toInstant());
    }
}
