package br.com.ziipsan.platform.telephony;

import br.com.ziipsan.core.sac.Customer360View;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abertura da ficha pela chamada (aceite ED-44, item 1): chamada recebida
 * abre a ficha do cliente no atendimento PELO NUMERO CHAMADOR, com
 * registro de acesso. Toda abertura — encontrada, nao encontrada ou
 * ambigua — grava no log; o teste obrigatorio do aceite confere que a
 * abertura pela chamada APARECE no log.
 */
public final class ScreenPopService {
    private final TelephonyPorts.ContactDirectoryPort directory;
    private final TelephonyPorts.Customer360Port views;
    private final TelephonyPorts.AccessLogPort log;

    public ScreenPopService(TelephonyPorts.ContactDirectoryPort directory,
                            TelephonyPorts.Customer360Port views, TelephonyPorts.AccessLogPort log) {
        this.directory = java.util.Objects.requireNonNull(directory);
        this.views = java.util.Objects.requireNonNull(views);
        this.log = java.util.Objects.requireNonNull(log);
    }

    public record ScreenPopResult(Status status, Optional<Customer360View> view,
                                  List<UUID> candidateUnitIds, String callerNumberNormalized) {
        public enum Status { OPENED, NOT_FOUND, AMBIGUOUS }
    }

    public ScreenPopResult onIncomingCall(TelephonyPorts.IncomingCall call) {
        String normalized;
        try { normalized = PhoneNumber.normalize(call.callerNumber()); }
        catch (RuntimeException e) {
            // Correcao R1 (revisao ED-44): numero chamador invalido/oculto
            // (ex.: chamada com numero suprimido pelo PABX) NAO escapa como
            // excecao — vira NOT_FOUND explicito E grava no log, mantendo o
            // invariante do aceite de que TODA abertura e registrada.
            log.append(new TelephonyAccessLog(call.tenantId(), call.attendantId(), null,
                    TelephonyAccessLog.AccessType.CALL_SCREEN_POP, null, null,
                    TelephonyAccessLog.Result.NOT_FOUND, call.receivedAt()));
            return new ScreenPopResult(ScreenPopResult.Status.NOT_FOUND, Optional.empty(), List.of(), null);
        }
        List<UUID> matches = directory.contactsOf(call.tenantId()).stream()
                .filter(c -> PhoneNumber.matches(c.storedPhone(), call.callerNumber()))
                .map(TelephonyPorts.ContactEntry::consumerUnitId).distinct().toList();
        if (matches.isEmpty()) {
            log.append(new TelephonyAccessLog(call.tenantId(), call.attendantId(), null,
                    TelephonyAccessLog.AccessType.CALL_SCREEN_POP, null, normalized,
                    TelephonyAccessLog.Result.NOT_FOUND, call.receivedAt()));
            return new ScreenPopResult(ScreenPopResult.Status.NOT_FOUND, Optional.empty(), List.of(), normalized);
        }
        if (matches.size() > 1) {
            log.append(new TelephonyAccessLog(call.tenantId(), call.attendantId(), null,
                    TelephonyAccessLog.AccessType.CALL_SCREEN_POP, null, normalized,
                    TelephonyAccessLog.Result.AMBIGUOUS, call.receivedAt()));
            return new ScreenPopResult(ScreenPopResult.Status.AMBIGUOUS, Optional.empty(), matches, normalized);
        }
        UUID unit = matches.get(0);
        Optional<Customer360View> view = views.viewOf(call.tenantId(), unit);
        log.append(new TelephonyAccessLog(call.tenantId(), call.attendantId(), null,
                TelephonyAccessLog.AccessType.CALL_SCREEN_POP, unit, normalized,
                view.isPresent() ? TelephonyAccessLog.Result.FOUND : TelephonyAccessLog.Result.NOT_FOUND,
                call.receivedAt()));
        return new ScreenPopResult(view.isPresent() ? ScreenPopResult.Status.OPENED : ScreenPopResult.Status.NOT_FOUND,
                view, view.isPresent() ? List.of(unit) : List.of(), normalized);
    }
}
