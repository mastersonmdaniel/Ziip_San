package br.com.ziipsan.core.fieldos;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aviso "tecnico a caminho" (ED-20): disparado pelo canal multicanal da
 * ED-41 (porta {@link FieldOsPorts.EnRouteNotifier}), com a janela
 * ESTIMADA de atendimento derivada da janela combinada do tenant
 * ({@code so.appointment_window_hours}): [inicio agendado, inicio +
 * janela]. Sem o parametro, nao ha janela a estimar e o aviso nao sai
 * com janela inventada — recusa nomeada.
 */
public final class EnRouteNotice {

    public record Window(Instant start, Instant end) {}

    private EnRouteNotice() {}

    public static Window estimatedWindow(Instant scheduledStart, int windowHours) {
        Objects.requireNonNull(scheduledStart, "inicio agendado e obrigatorio");
        if (windowHours <= 0) throw new IllegalArgumentException("janela de atendimento deve ser positiva");
        return new Window(scheduledStart, scheduledStart.plus(java.time.Duration.ofHours(windowHours)));
    }

    /** Dispara pelo canal da ED-41 e devolve a janela estimada usada no aviso. */
    public static Window dispatch(FieldOsPorts.EnRouteNotifier notifier, FieldOsParameters parameters,
                                  UUID tenantId, UUID orderId, UUID customerId, Instant scheduledStart) {
        Objects.requireNonNull(notifier, "canal da ED-41 e obrigatorio");
        Objects.requireNonNull(parameters, "parametros do tenant sao obrigatorios");
        int hours = FieldOsParameters.requireAppointmentWindowHours(parameters.appointmentWindowHours(tenantId));
        Window window = estimatedWindow(scheduledStart, hours);
        if (!notifier.notifyEnRoute(tenantId, orderId, customerId, window.start(), window.end())) {
            throw new IllegalStateException("canal da ED-41 nao disparou o aviso 'a caminho' da OS " + orderId
                    + " (motivo no servico da ED-41: canal/opt-in/teto do tenant)");
        }
        return window;
    }
}
