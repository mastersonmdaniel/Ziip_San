package br.com.ziipsan.platform.tenantalert;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-07: um alerta do tenant já gerado. Nasce de uma {@link AlertRule} sobre um
 * evento do catálogo ({@link Kind#CATALOG_EVENT}) ou de uma falha de lote da
 * P2.1 ({@link Kind#BATCH_FAILURE}). Os dois nascimentos são TIPOS distintos
 * de propósito: a falha de lote NÃO finge um nome de evento do catálogo da
 * P1.38 — {@code eventName}/{@code eventVersion} ficam nulos nela e o que a
 * identifica é {@code sourceRef} = id da execução ({@code job_runs}).
 *
 * <p>{@code titleCode}/{@code bodyCode} são CÓDIGOS do catálogo de mensagens
 * (convenção da P3.36), nunca texto livre: a carga de evento da P1.38 não tem
 * dado pessoal e o alerta também não carrega — só identificadores e códigos.
 */
public record TenantAlert(UUID id, UUID tenantId, UUID ruleId, Kind kind,
                          String eventName, Integer eventVersion, String sourceRef,
                          UUID responsibleUserId, String titleCode, String bodyCode,
                          Instant createdAt) {

    public enum Kind { CATALOG_EVENT, BATCH_FAILURE }

    public TenantAlert {
        Objects.requireNonNull(id, "id do alerta é obrigatório");
        Objects.requireNonNull(tenantId, "tenant do alerta é obrigatório");
        Objects.requireNonNull(kind, "tipo do alerta é obrigatório");
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException("o alerta exige a referência da origem (evento ou execução)");
        }
        Objects.requireNonNull(responsibleUserId, "o alerta exige o usuário responsável");
        if (titleCode == null || titleCode.isBlank() || bodyCode == null || bodyCode.isBlank()) {
            throw new IllegalArgumentException("o alerta exige os códigos de título e corpo");
        }
        Objects.requireNonNull(createdAt, "o alerta exige o instante de criação");
        if (kind == Kind.CATALOG_EVENT) {
            Objects.requireNonNull(ruleId, "alerta de evento do catálogo exige a regra que o escolheu");
            if (eventName == null || eventName.isBlank() || eventVersion == null || eventVersion < 1) {
                throw new IllegalArgumentException("alerta de evento do catálogo exige nome e versão declarada do evento");
            }
        }
        if (kind == Kind.BATCH_FAILURE && (eventName != null || eventVersion != null)) {
            throw new IllegalArgumentException("alerta de falha de lote não carrega nome/versão de evento do catálogo");
        }
    }
}
