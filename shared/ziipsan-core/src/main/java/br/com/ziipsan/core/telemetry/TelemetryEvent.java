package br.com.ziipsan.core.telemetry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * P4.8 / PLT-42: evento de telemetria de produto JÁ VALIDADO. Este tipo é
 * a garantia estrutural da privacidade: ele simplesmente NÃO TEM campo
 * para identificador de pessoa, sessão ou conteúdo digitado — o que não
 * existe no tipo não pode ser gravado, agregado nem vazado depois.
 *
 * <p>O que se mede (aceite): adoção de funcionalidades
 * ({@code FEATURE_USED}, por {@code featureKey}), tempo para concluir o
 * primeiro ciclo ({@code FIRST_CYCLE_COMPLETED}, com
 * {@code durationMillis} medido pelo chamador), abandono de assistentes
 * ({@code WIZARD_STARTED}/{@code WIZARD_COMPLETED}/
 * {@code WIZARD_ABANDONED}, por {@code wizardKey}) e tarefas mais
 * repetidas ({@code TASK_EXECUTED}, por {@code taskKey}). A agregação é
 * por {@code tenantId} e por {@code role} (papel) — nunca por pessoa.
 *
 * <p>A entrada bruta (mapa de atributos) NÃO entra neste record: ela
 * passa antes por {@link TelemetryPrivacyGuard}, que recusa qualquer
 * atributo proibido. Construir este record diretamente com chaves fora
 * dos campos acima é impossível — não há mapa livre aqui.
 */
public record TelemetryEvent(
        UUID tenantId,
        String role,
        Kind kind,
        String featureKey,
        String wizardKey,
        String taskKey,
        Long durationMillis,
        Instant occurredAt) {

    public enum Kind {
        FEATURE_USED, FIRST_CYCLE_COMPLETED,
        WIZARD_STARTED, WIZARD_COMPLETED, WIZARD_ABANDONED,
        TASK_EXECUTED
    }

    public TelemetryEvent {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório (a agregação é por tenant)");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role (papel) é obrigatório: a agregação é por tenant e por papel");
        }
        Objects.requireNonNull(kind, "kind é obrigatório");
        Objects.requireNonNull(occurredAt, "occurredAt é obrigatório");
        switch (kind) {
            case FEATURE_USED -> requireKey(featureKey, "featureKey", kind);
            case FIRST_CYCLE_COMPLETED -> {
                if (durationMillis == null || durationMillis < 0) {
                    throw new IllegalArgumentException(
                            "FIRST_CYCLE_COMPLETED exige durationMillis >= 0 (o tempo até o primeiro ciclo)");
                }
            }
            case WIZARD_STARTED, WIZARD_COMPLETED, WIZARD_ABANDONED ->
                    requireKey(wizardKey, "wizardKey", kind);
            case TASK_EXECUTED -> requireKey(taskKey, "taskKey", kind);
        }
        if (durationMillis != null && durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis não pode ser negativo");
        }
    }

    private static void requireKey(String value, String field, Kind kind) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório para " + kind);
        }
        if (!value.matches("^[a-z0-9][a-z0-9._-]{0,99}$")) {
            throw new IllegalArgumentException(
                    field + " inválido (minúsculas, números, '.', '_' ou '-', até 100): " + value);
        }
    }

    public static TelemetryEvent featureUsed(UUID tenantId, String role, String featureKey, Instant at) {
        return new TelemetryEvent(tenantId, role, Kind.FEATURE_USED, featureKey, null, null, null, at);
    }

    public static TelemetryEvent firstCycleCompleted(UUID tenantId, String role, long durationMillis, Instant at) {
        return new TelemetryEvent(tenantId, role, Kind.FIRST_CYCLE_COMPLETED, null, null, null, durationMillis, at);
    }

    public static TelemetryEvent wizard(UUID tenantId, String role, Kind kind, String wizardKey, Instant at) {
        return new TelemetryEvent(tenantId, role, kind, null, wizardKey, null, null, at);
    }

    public static TelemetryEvent taskExecuted(UUID tenantId, String role, String taskKey, Instant at) {
        return new TelemetryEvent(tenantId, role, Kind.TASK_EXECUTED, null, null, taskKey, null, at);
    }
}
