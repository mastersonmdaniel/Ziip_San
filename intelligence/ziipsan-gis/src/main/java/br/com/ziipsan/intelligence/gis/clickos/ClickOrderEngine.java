package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.UUID;

/**
 * Costura estreita do adaptador de OS por clique sobre o motor de OS
 * da P1.31 ({@code ServiceOrderService}): só as quatro operações que
 * a tradução do payload usa. Existe para a tradução ficar provada
 * com um fake manuscrito, como os demais testes deste módulo — a
 * implementação real é {@link CoreClickOrderEngine}.
 */
interface ClickOrderEngine {

    /** Indicador do tipo declarado (INTERNAL/FIELD); tipo inexistente recusa no motor. */
    ServiceOrderEnums.TypeIndicator typeIndicator(String serviceTypeCode);

    /** Cria a ordem em DRAFT com origem INTERNAL (sem protocolo) e devolve o id. */
    UUID createOrder(String serviceTypeCode, ServiceOrderEnums.ExecutionMode executionMode,
                     UUID requestedBy);

    /** Regista um alvo do payload sobre a ordem. */
    void addTarget(UUID orderId, TargetType targetType, UUID targetId, UUID actorId);

    /** Submete a ordem (DRAFT → OPEN ou PENDING_APPROVAL, conforme o tipo). */
    void submit(UUID orderId, UUID actorId);
}
