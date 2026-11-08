package br.com.ziipsan.platform.tenantalert;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-07: a escolha do tenant — UM evento do catálogo da P1.38 (nome + versão
 * declarada), UM usuário responsável e a opção de também entregar por webhook
 * de saída (P2.26). Sem esta escolha gravada, evento nenhum do tenant vira
 * alerta: a ED-07 não assina evento por conta própria.
 *
 * <p>A existência do evento no catálogo NÃO é validada aqui (record puro):
 * quem grava a regra é o {@link TenantAlertService}, que pergunta ao catálogo
 * da P1.38 (porta {@code BusinessEventCatalog} da P3.36) e recusa o que não
 * estiver declarado — é lá que mora o teste de recusa do aceite.
 */
public record AlertRule(UUID id, UUID tenantId, String eventName, int eventVersion,
                        UUID responsibleUserId, boolean webhookEnabled, boolean enabled,
                        UUID createdBy, Instant createdAt) {
    public AlertRule {
        Objects.requireNonNull(id, "id da regra é obrigatório");
        Objects.requireNonNull(tenantId, "tenant da regra é obrigatório");
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("a regra exige o nome do evento do catálogo");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("a regra exige a versão declarada do evento (>= 1): " + eventVersion);
        }
        Objects.requireNonNull(responsibleUserId, "a regra exige o usuário responsável do alerta");
        Objects.requireNonNull(createdBy, "a regra exige quem a configurou");
        Objects.requireNonNull(createdAt, "a regra exige o instante de criação");
    }

    public AlertRule withEnabled(boolean newEnabled) {
        return new AlertRule(id, tenantId, eventName, eventVersion, responsibleUserId,
                webhookEnabled, newEnabled, createdBy, createdAt);
    }
}
