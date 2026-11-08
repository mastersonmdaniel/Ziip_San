package br.com.ziipsan.platform.telephony;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Listas de discagem geradas pelo produto (aceite ED-44, item 3): a lista
 * MATERIALIZA a selecao de uma regua existente (cobranca P4.1/ED-29 ou
 * fila de atendimento ED-35) — a ED-44 nao cria regua de campanha.
 * Regras desta materializacao: ordem da selecao preservada; telefone
 * invalido/ausente sai com motivo registrado; mesmo numero para unidades
 * distintas e deduplicado mantendo a primeira ocorrencia (as demais
 * saem com motivo DUPLICATE_NUMBER). A geracao vai ao log de acesso.
 */
public final class DialingListGenerator {
    private final TelephonyPorts.DialingCandidateSourcePort source;
    private final TelephonyPorts.AccessLogPort log;
    public DialingListGenerator(TelephonyPorts.DialingCandidateSourcePort source, TelephonyPorts.AccessLogPort log) {
        this.source = java.util.Objects.requireNonNull(source); this.log = java.util.Objects.requireNonNull(log);
    }
    public record Item(UUID consumerUnitId, String phoneNormalized, String sourceRuleCode, String sourceKind) {}
    public record Excluded(UUID consumerUnitId, String reason) {}
    public record DialingList(UUID tenantId, List<Item> items, List<Excluded> excluded, Instant generatedAt) {}

    public DialingList generate(UUID tenantId, UUID requestedBy, Instant now) {
        var seen = new LinkedHashMap<String, Item>(); var excluded = new ArrayList<Excluded>();
        for (var c : source.selectedOf(tenantId)) {
            String n; try { n = PhoneNumber.normalize(c.storedPhone()); }
            catch (RuntimeException e) { excluded.add(new Excluded(c.consumerUnitId(), "INVALID_OR_MISSING_PHONE")); continue; }
            if (seen.putIfAbsent(n, new Item(c.consumerUnitId(), n, c.sourceRuleCode(), c.sourceKind())) != null)
                excluded.add(new Excluded(c.consumerUnitId(), "DUPLICATE_NUMBER"));
        }
        log.append(new TelephonyAccessLog(tenantId, requestedBy, null,
                TelephonyAccessLog.AccessType.DIALING_LIST_GENERATED, null, null,
                TelephonyAccessLog.Result.FOUND, now));
        return new DialingList(tenantId, List.copyOf(seen.values()), List.copyOf(excluded), now);
    }
}
