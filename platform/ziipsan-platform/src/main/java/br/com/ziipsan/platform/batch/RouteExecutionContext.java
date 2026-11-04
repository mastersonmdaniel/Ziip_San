package br.com.ziipsan.platform.batch;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * P2.1: contexto de uma rota em execução. O handler recebe o tenant
 * explícito (nunca ThreadLocal, como o worker da outbox da P1.9), o
 * ciclo, a rota, a data de referência do tenant e o seu fuso — tudo o
 * que a rota precisa sem consultar estado ambiente.
 */
public record RouteExecutionContext(UUID runId, UUID tenantId, String cycleRef, String route,
                                    int attempt, LocalDate referenceDate, ZoneId timezone,
                                    Clock clock) {
}
