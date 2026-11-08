package br.com.ziipsan.core.backoffice;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Impressao da OS e do roteiro da equipe (ED-36) PELO MOTOR DE MODELOS DA
 * P2.30 — esta classe nao renderiza nada: monta o pedido de impressao
 * (modelo do tenant + variaveis + QR) e o entrega a porta {@link Printer},
 * cuja fiacao chama o motor da P2.30 (plataforma) — o nucleo nao importa a
 * plataforma, como nas demais portas do projeto. O codigo do modelo vem do
 * catalogo de modelos do tenant (P2.30), nunca fixado aqui.
 *
 * <p>QR: o conteudo e o caminho que ABRE a OS ("/os/" + id); quem desenha o
 * QR e o motor/renderizador na fiacao. Roteiro da equipe = um pedido com a
 * lista ordenada das OS do turno.
 */
public final class WorkOrderPrint {

    public interface Printer {
        /** Devolve a referencia do documento gerado (documento da P1.20/P2.30 na fiacao). */
        UUID print(PrintRequest request);
    }

    public record PrintRequest(String templateCode, List<UUID> orderIds, Map<String, String> variables, String qrPayload) {
        public PrintRequest {
            if (templateCode == null || templateCode.isBlank()) throw new IllegalArgumentException("codigo do modelo (catalogo P2.30 do tenant) e obrigatorio");
            Objects.requireNonNull(orderIds, "OS do pedido de impressao sao obrigatorias");
            if (orderIds.isEmpty()) throw new IllegalArgumentException("impressao sem OS nao existe");
            orderIds = List.copyOf(orderIds);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            Objects.requireNonNull(qrPayload, "payload do QR e obrigatorio");
        }
    }

    private WorkOrderPrint() {}

    /** Caminho que o QR abre: a propria OS. */
    public static String qrPayloadFor(UUID orderId) {
        Objects.requireNonNull(orderId, "OS e obrigatoria");
        return "/os/" + orderId;
    }

    public static PrintRequest forOrder(String templateCode, UUID orderId, Map<String, String> variables) {
        return new PrintRequest(templateCode, List.of(orderId), variables, qrPayloadFor(orderId));
    }

    /** Roteiro da equipe: varias OS num documento so; o QR abre a primeira OS do roteiro. */
    public static PrintRequest teamRoute(String templateCode, List<UUID> orderIdsInRouteOrder, Map<String, String> variables) {
        Objects.requireNonNull(orderIdsInRouteOrder, "roteiro exige as OS na ordem da rota");
        if (orderIdsInRouteOrder.isEmpty()) throw new IllegalArgumentException("roteiro sem OS nao se imprime");
        return new PrintRequest(templateCode, orderIdsInRouteOrder, variables, qrPayloadFor(orderIdsInRouteOrder.get(0)));
    }
}
