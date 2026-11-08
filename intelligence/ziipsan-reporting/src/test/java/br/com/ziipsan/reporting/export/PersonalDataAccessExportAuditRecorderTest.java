package br.com.ziipsan.reporting.export;

import br.com.ziipsan.core.audit.PersonalDataAccessLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correção RG-Lote3 (08/11/2026): o carimbo {@code accessed_at} do log de
 * auditoria de exportação vem do {@link Clock} injetado — com relógio fixo,
 * o instante gravado é exatamente o do relógio, sem depender do relógio/TZ
 * do processo. O {@code EntityManager} é um dublê por proxy que só captura
 * o {@code persist} (único método que a classe chama).
 */
class PersonalDataAccessExportAuditRecorderTest {

    private static final Instant FIXED = Instant.parse("2026-11-08T13:00:00Z");

    private EntityManager capturing(AtomicReference<PersonalDataAccessLog> persisted) {
        return (EntityManager) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("persist")) {
                        persisted.set((PersonalDataAccessLog) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void accessedAtComesFromInjectedClock() {
        var persisted = new AtomicReference<PersonalDataAccessLog>();
        var recorder = new PersonalDataAccessExportAuditRecorder(
                capturing(persisted), Clock.fixed(FIXED, ZoneOffset.UTC));
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        recorder.recordExport(tenantId, userId, "CONSUMO_MENSAL", "FOUND", "exportacao do relatorio");

        assertThat(persisted.get()).isNotNull();
        assertThat(persisted.get().getAccessedAt()).isEqualTo(FIXED);
        assertThat(persisted.get().getTenantId()).isEqualTo(tenantId);
        assertThat(persisted.get().getUserId()).isEqualTo(userId);
        assertThat(persisted.get().getAccessType()).isEqualTo("EXPORT");
        assertThat(persisted.get().getEntityType()).isEqualTo("REPORT:CONSUMO_MENSAL");
    }

    @Test
    void convenienceConstructorKeepsSystemClockBehaviour() {
        var persisted = new AtomicReference<PersonalDataAccessLog>();
        var recorder = new PersonalDataAccessExportAuditRecorder(capturing(persisted));

        Instant before = Instant.now();
        recorder.recordExport(UUID.randomUUID(), UUID.randomUUID(), "CONSUMO_MENSAL", "FOUND", null);
        Instant after = Instant.now();

        assertThat(persisted.get()).isNotNull();
        assertThat(persisted.get().getAccessedAt()).isBetween(before, after);
    }
}
