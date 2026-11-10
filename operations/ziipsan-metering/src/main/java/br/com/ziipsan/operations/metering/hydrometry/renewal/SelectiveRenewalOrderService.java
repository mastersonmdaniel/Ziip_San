package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Geração das OS selectivas do plano anual (II-5): transforma cada
 * linha do plano num {@link RenewalOrderPayload} (1:1, na ordem do
 * plano) e, no despacho, abre as OS pela porta do núcleo — sem duplicar
 * a máquina de estados da P1.31, no mesmo desenho da
 * {@code VerificationAlertService} da ED-14:
 *
 * <ul>
 *   <li>{@link #buildPayloads} é puro: um payload por linha, sempre —
 *       é esta a correspondência 1:1 com o plano;</li>
 *   <li>{@link #dispatch} abre a OS de cada payload e devolve os ids
 *       abertos na ordem do plano; hidrómetro que já tem OS aberta de
 *       troca selectiva (porta {@link OpenRenewalOrderLookup}) é
 *       suprimido — reexecutar a geração do mesmo plano não duplica OS
 *       (dedup inter-gerações; dentro de um plano, duplicados já são
 *       impossíveis por construção do {@link AnnualRenewalPlan}).</li>
 * </ul>
 *
 * A implementação de produção da porta (integração) declara o tipo
 * {@link RenewalOrderType} no tenant e cria a OS no
 * {@code ServiceOrderService} do core com alvo {@code METER} e a carga
 * {@code effectPayloadJson} — fiação nomeada nas pendências, não
 * fingida neste núcleo.
 */
public class SelectiveRenewalOrderService {

    /** Porta de abertura de OS no núcleo da P1.31 (integração: ServiceOrderService, alvo METER). */
    public interface RenewalOrderPort {
        UUID openReplacementOrder(UUID tenantId, RenewalOrderPayload payload);
    }

    /** Porta de consulta: já existe OS aberta de troca selectiva para o hidrómetro? */
    public interface OpenRenewalOrderLookup {
        boolean hasOpenReplacementOrder(UUID tenantId, UUID meterId);
    }

    private final RenewalOrderPort port;
    private final OpenRenewalOrderLookup lookup;

    public SelectiveRenewalOrderService(RenewalOrderPort port, OpenRenewalOrderLookup lookup) {
        if (port == null || lookup == null)
            throw new IllegalArgumentException("Porta de OS e consulta de OS aberta são obrigatórias");
        this.port = port;
        this.lookup = lookup;
    }

    /** Um payload por linha do plano, na ordem do plano (1:1). */
    public List<RenewalOrderPayload> buildPayloads(UUID tenantId, AnnualRenewalPlan.AnnualPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Plano anual é obrigatório");
        List<RenewalOrderPayload> out = new ArrayList<>(plan.lines().size());
        for (AnnualRenewalPlan.PlanLine line : plan.lines())
            out.add(RenewalOrderPayload.fromPlanLine(tenantId, plan, line));
        return List.copyOf(out);
    }

    /**
     * Abre as OS do plano pela porta.
     *
     * @return ids das OS abertas nesta chamada, na ordem do plano
     *         (hidrómetros com OS já aberta são suprimidos pelo lookup).
     */
    public List<UUID> dispatch(UUID tenantId, AnnualRenewalPlan.AnnualPlan plan) {
        List<UUID> opened = new ArrayList<>();
        for (RenewalOrderPayload payload : buildPayloads(tenantId, plan)) {
            if (lookup.hasOpenReplacementOrder(tenantId, payload.meterId())) continue;
            opened.add(port.openReplacementOrder(tenantId, payload));
        }
        return List.copyOf(opened);
    }
}
