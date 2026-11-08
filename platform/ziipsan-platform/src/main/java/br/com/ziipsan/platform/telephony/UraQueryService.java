package br.com.ziipsan.platform.telephony;

import br.com.ziipsan.core.sac.Customer360View;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * API de consulta para URA de terceiros (aceite ED-44, item 2; ADR-0028,
 * item k). Autenticacao FAIL-CLOSED pelo padrao do projeto: o cliente URA
 * guarda so a REFERENCIA ao cofre (nunca o valor); o segredo apresentado
 * e comparado em tempo constante com o segredo resolvido do cofre.
 * Cliente desconhecido/desligado, segredo ausente ou divergente = DENIED
 * (e o DENIED tambem vai ao log). A resposta e MINIMA para uma URA:
 * identificou ou nao, a unidade e o resumo de faturas abertas da ficha 360.
 */
public final class UraQueryService {
    private final TelephonyPorts.UraClientRegistryPort registry;
    private final TelephonyPorts.VaultPort vault;
    private final TelephonyPorts.ContactDirectoryPort directory;
    private final TelephonyPorts.Customer360Port views;
    private final TelephonyPorts.AccessLogPort log;

    public UraQueryService(TelephonyPorts.UraClientRegistryPort registry, TelephonyPorts.VaultPort vault,
                           TelephonyPorts.ContactDirectoryPort directory, TelephonyPorts.Customer360Port views,
                           TelephonyPorts.AccessLogPort log) {
        this.registry = java.util.Objects.requireNonNull(registry); this.vault = java.util.Objects.requireNonNull(vault);
        this.directory = java.util.Objects.requireNonNull(directory); this.views = java.util.Objects.requireNonNull(views);
        this.log = java.util.Objects.requireNonNull(log);
    }

    public record UraAnswer(boolean identified, UUID consumerUnitId, long openBills, boolean denied) {}

    public UraAnswer queryByCallerNumber(UUID tenantId, String clientId, String presentedSecret,
                                         String callerNumber, Instant now) {
        String normalized; try { normalized = PhoneNumber.normalize(callerNumber); }
        catch (RuntimeException e) { normalized = null; }
        Optional<TelephonyPorts.UraClient> client = registry.clientOf(tenantId, clientId);
        boolean authed = client.isPresent() && client.get().enabled()
                && client.get().credentialReference() != null && !client.get().credentialReference().isBlank()
                && presentedSecret != null && !presentedSecret.isBlank()
                && vault.secretOf(client.get().credentialReference())
                        .map(s -> MessageDigest.isEqual(s.getBytes(StandardCharsets.UTF_8),
                                presentedSecret.getBytes(StandardCharsets.UTF_8))).orElse(false);
        if (!authed) {
            log.append(new TelephonyAccessLog(tenantId, null, clientId, TelephonyAccessLog.AccessType.URA_QUERY,
                    null, normalized, TelephonyAccessLog.Result.DENIED, now));
            return new UraAnswer(false, null, 0, true);
        }
        var matches = directory.contactsOf(tenantId).stream()
                .filter(c -> PhoneNumber.matches(c.storedPhone(), callerNumber))
                .map(TelephonyPorts.ContactEntry::consumerUnitId).distinct().toList();
        if (matches.size() != 1) {
            log.append(new TelephonyAccessLog(tenantId, null, clientId, TelephonyAccessLog.AccessType.URA_QUERY,
                    null, normalized, matches.isEmpty() ? TelephonyAccessLog.Result.NOT_FOUND
                            : TelephonyAccessLog.Result.AMBIGUOUS, now));
            return new UraAnswer(false, null, 0, false);
        }
        UUID unit = matches.get(0);
        Optional<Customer360View> view = views.viewOf(tenantId, unit);
        if (view.isEmpty()) {
            // Correcao R3 (revisao ED-44): sem a ficha 360 nao ha como afirmar
            // o numero de faturas abertas — alinhado ao ScreenPopService,
            // view ausente = NAO identificado, log NOT_FOUND. Nunca responder
            // openBills=0 como fato.
            log.append(new TelephonyAccessLog(tenantId, null, clientId, TelephonyAccessLog.AccessType.URA_QUERY,
                    null, normalized, TelephonyAccessLog.Result.NOT_FOUND, now));
            return new UraAnswer(false, null, 0, false);
        }
        log.append(new TelephonyAccessLog(tenantId, null, clientId, TelephonyAccessLog.AccessType.URA_QUERY,
                unit, normalized, TelephonyAccessLog.Result.FOUND, now));
        return new UraAnswer(true, unit, view.get().openBills(), false);
    }
}
