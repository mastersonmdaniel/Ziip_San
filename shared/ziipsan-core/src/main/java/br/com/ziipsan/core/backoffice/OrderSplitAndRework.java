package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Desdobramento e retrabalho da OS (ED-36), sobre os vinculos que a P1.31 ja
 * tem: desdobrar = OS-filhas ligadas por parent_order_id (V9_1), cada uma
 * com um subconjunto dos alvos da mae; retrabalho = OS NOVA ligada a
 * original ja encerrada como executada — a original NAO reabre (o motor da
 * P1.31 nao tem transicao de saida de CLOSED_*, e correcao e sempre OS nova,
 * como no reverses_order_id). Motivo do retrabalho e obrigatorio.
 */
public final class OrderSplitAndRework {

    public record SplitPlan(UUID parentOrderId, List<List<OrderTargets.Target>> childTargets) {}
    public record ReworkLink(UUID originalOrderId, UUID reworkOrderId, String reason) {}

    private OrderSplitAndRework() {}

    public static SplitPlan split(UUID parentOrderId, ServiceOrderStatus parentStatus,
                                  List<OrderTargets.Target> parentTargets, List<List<OrderTargets.Target>> childTargets) {
        Objects.requireNonNull(parentOrderId, "OS-mae e obrigatoria");
        Objects.requireNonNull(parentStatus, "estado da OS-mae e obrigatorio");
        if (parentStatus.isTerminal()) throw new IllegalStateException("OS encerrada (" + parentStatus + ") nao desdobra");
        if (childTargets == null || childTargets.size() < 2) throw new IllegalArgumentException("desdobramento exige ao menos 2 OS-filhas");
        Set<OrderTargets.Target> union = new HashSet<>();
        for (List<OrderTargets.Target> child : childTargets) {
            if (child == null || child.isEmpty()) throw new IllegalArgumentException("OS-filha sem alvo nao e desdobramento");
            for (OrderTargets.Target t : child)
                if (!union.add(t)) throw new IllegalArgumentException("alvo em mais de uma OS-filha: " + t.type() + ":" + t.entityId());
        }
        Set<OrderTargets.Target> parent = new HashSet<>(parentTargets);
        if (!union.equals(parent))
            throw new IllegalArgumentException("desdobramento deve repartir TODOS os alvos da mae, sem sobra nem falta: mae="
                    + parent.size() + ", filhas=" + union.size());
        List<List<OrderTargets.Target>> copy = new ArrayList<>();
        for (List<OrderTargets.Target> child : childTargets) copy.add(List.copyOf(child));
        return new SplitPlan(parentOrderId, List.copyOf(copy));
    }

    public static ReworkLink rework(UUID originalOrderId, ServiceOrderStatus originalStatus, UUID reworkOrderId, String reason) {
        Objects.requireNonNull(originalOrderId, "OS original e obrigatoria");
        Objects.requireNonNull(reworkOrderId, "OS de retrabalho e obrigatoria");
        if (originalOrderId.equals(reworkOrderId)) throw new IllegalArgumentException("retrabalho e uma OS nova, nao a original");
        if (originalStatus != ServiceOrderStatus.CLOSED_EXECUTED)
            throw new IllegalStateException("retrabalho so nasce de OS CLOSED_EXECUTED (atual: " + originalStatus + "); OS aberta se continua, nao se retrabalha");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("retrabalho exige motivo");
        return new ReworkLink(originalOrderId, reworkOrderId, reason.trim());
    }
}
