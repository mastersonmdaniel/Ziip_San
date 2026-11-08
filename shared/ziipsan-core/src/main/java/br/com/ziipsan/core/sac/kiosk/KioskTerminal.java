package br.com.ziipsan.core.sac.kiosk;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Totem em modo quiosque (ED-40): o portal da ED-38 rodando no hardware
 * do cliente, TRAVADO nas acoes do quiosque. Modo quiosque desligado no
 * parametro do tenant ({@code portal.kiosk_enabled}, validado por
 * {@link KioskParameters}) = terminal nao emite senha. Acoes autenticadas
 * do portal (faturas, protocolos, dados do cliente) NUNCA sao acoes do
 * quiosque: o terminal publico nao tem sessao de cliente.
 *
 * <p>Correcao D4 da revisao: esta classe NAO e mais um record — o
 * construtor canonico publico de um record permitia montar um terminal
 * habilitado ({@code new KioskTerminal(device, unit, true)}) sem passar
 * pelo parametro do tenant. Agora o construtor e privado e a UNICA
 * fabrica publica e {@link #fromParameters}, que impoe
 * {@link KioskParameters#kioskEnabled(Boolean)} (ausencia do parametro =
 * {@code MissingParameterException}, nunca habilitado em silencio).
 * Acessores e metodos publicos sao os mesmos do record original.
 */
public final class KioskTerminal {

    public enum KioskAction { ISSUE_TICKET, ACCESSIBILITY_HELP, VIEW_CALL_PANEL }

    private static final Set<KioskAction> ALLOWED = Set.of(
            KioskAction.ISSUE_TICKET, KioskAction.ACCESSIBILITY_HELP, KioskAction.VIEW_CALL_PANEL);

    private final UUID deviceId;
    private final UUID serviceUnitId;
    private final boolean kioskModeEnabled;

    private KioskTerminal(UUID deviceId, UUID serviceUnitId, boolean kioskModeEnabled) {
        this.deviceId = Objects.requireNonNull(deviceId, "dispositivo do totem obrigatorio");
        this.serviceUnitId = Objects.requireNonNull(serviceUnitId, "unidade de atendimento do totem obrigatoria");
        this.kioskModeEnabled = kioskModeEnabled;
    }

    public static KioskTerminal fromParameters(UUID deviceId, UUID serviceUnitId, Boolean kioskEnabledParam) {
        return new KioskTerminal(deviceId, serviceUnitId, KioskParameters.kioskEnabled(kioskEnabledParam));
    }

    public UUID deviceId() { return deviceId; }

    public UUID serviceUnitId() { return serviceUnitId; }

    public boolean kioskModeEnabled() { return kioskModeEnabled; }

    public boolean actionAllowed(KioskAction action) {
        return kioskModeEnabled && action != null && ALLOWED.contains(action);
    }

    public QueueTicket issueTicket(UUID ticketId, int sequenceNumber, String serviceType,
                                   PriorityAttendanceProfile profile, LocalDateTime now) {
        if (!kioskModeEnabled) {
            throw new IllegalStateException("modo quiosque desligado para este tenant/terminal");
        }
        return new QueueTicket(ticketId, serviceUnitId, sequenceNumber, serviceType, profile, now);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KioskTerminal other)) return false;
        return kioskModeEnabled == other.kioskModeEnabled
                && deviceId.equals(other.deviceId)
                && serviceUnitId.equals(other.serviceUnitId);
    }

    @Override public int hashCode() {
        return Objects.hash(deviceId, serviceUnitId, kioskModeEnabled);
    }

    @Override public String toString() {
        return "KioskTerminal[deviceId=" + deviceId + ", serviceUnitId=" + serviceUnitId
                + ", kioskModeEnabled=" + kioskModeEnabled + "]";
    }
}
