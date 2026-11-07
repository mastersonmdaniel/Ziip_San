package br.com.ziipsan.operations.metering.hydrometry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Alerta de validade (ED-14): para cada hidrômetro vencido/a vencer no horizonte, abre
 * UMA OS com alvo {@code METER} (núcleo da P3.4/P1.31) — geração EM LOTE: o chamador
 * agenda a varredura; duplicidade é evitada por {@link OpenedOrderLookup} (OS aberta
 * de verificação para o mesmo hidrômetro suprime nova abertura).
 */
public class VerificationAlertService {

    /** Porta de abertura de OS (integração: ServiceOrderService do core, alvo METER). */
    public interface ServiceOrderOpener {
        UUID openVerificationOrder(UUID tenantId, UUID meterId);
    }
    public interface OpenedOrderLookup {
        boolean hasOpenVerificationOrder(UUID tenantId, UUID meterId);
    }

    private final ServiceOrderOpener opener;
    private final OpenedOrderLookup lookup;

    public VerificationAlertService(ServiceOrderOpener opener, OpenedOrderLookup lookup) {
        this.opener = opener; this.lookup = lookup;
    }

    /**
     * @return ids das OS abertas nesta varredura (ordem da entrada).
     * Deduplicação em DOIS níveis (correção A5): (i) intra-varredura — o mesmo
     * hidrômetro repetido na lista de candidatos abre UMA OS (conjunto de já
     * tratados nesta chamada); (ii) inter-varredura — quem já tem OS aberta de
     * verificação é suprimido pela porta {@link OpenedOrderLookup}.
     */
    public List<UUID> generateBatch(UUID tenantId, List<VerificationSchedule.DueMeter> candidates,
                                    LocalDate reference, int horizonDays) {
        java.util.Set<UUID> seenInThisSweep = new java.util.HashSet<>();
        return VerificationSchedule.selectDue(candidates, reference, horizonDays).stream()
                .filter(d -> seenInThisSweep.add(d.meterId()))
                .filter(d -> !lookup.hasOpenVerificationOrder(tenantId, d.meterId()))
                .map(d -> opener.openVerificationOrder(tenantId, d.meterId()))
                .toList();
    }
}
