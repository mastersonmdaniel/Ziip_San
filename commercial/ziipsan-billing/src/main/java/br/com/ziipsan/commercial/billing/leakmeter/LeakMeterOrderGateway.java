package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Porta para o nucleo de ordens de servico da P1.31
 * ({@code ServiceOrderService} do ziipsan-core), no mesmo contrato da porta
 * da P2.20 ({@code ServiceOrderGateway}): a implementacao entregue nesta
 * frente e o {@link CoreLeakMeterOrderGateway}, que traduz cada metodo para
 * a API real do motor -- nao ha outra implementacao de producao.
 *
 * <p>Diferencas para a porta da P2.20, ditadas por esta tarefa:
 * <ul>
 *   <li>as OS sao de CAMPO: criacao com {@code ExecutionMode.FIELD} e os
 *       passos de agenda do motor ({@link #schedule} e
 *       {@link #startExecution}) expostos -- as telas de agenda e o app de
 *       campo sao da ED-23, o motor ja guarda as transicoes;</li>
 *   <li>a OS de vazamento nasce FILHA da {@code BILL_REVIEW} da P2.20:
 *       {@code parentOrderId} vai no {@code OrderInput} do motor;</li>
 *   <li>o alvo e sempre a unidade consumidora com a carga de efeito do tipo
 *       (o hidrometro da afericao vai na carga -- ver
 *       {@link LeakMeterOrderType}).</li>
 * </ul>
 */
public interface LeakMeterOrderGateway {

    /** O tipo no catalogo do tenant, como o motor o ve (fabrica declarada). */
    record TypeInfo(boolean active, boolean requiresApproval) {
    }

    /**
     * Efeito que o handler do motor invoca durante o {@code execute} da OS:
     * aplica a operacao (decisao do vazamento, laudo da afericao) na
     * transacao de quem chamou o servico. Lancar excecao aqui faz o motor
     * recusar a execucao -- efeito nunca fica "aplicado" no motor sem ter
     * sido aplicado no dominio.
     */
    @FunctionalInterface
    interface LeakMeterEffect {
        void apply();
    }

    /** O tipo no catalogo do tenant; {@code null} = tipo nao declarado. */
    TypeInfo findType(LeakMeterOrderType type);

    /**
     * Garante o tipo declarado no tenant com a exigencia de aprovacao dada
     * (declaracao idempotente por codigo, {@link LeakMeterOrderType#declaration}):
     * tipo ja existente e devolvido como esta -- o motor nao redeclara.
     */
    TypeInfo ensureType(LeakMeterOrderType type, boolean requiresApproval);

    /**
     * Cria a OS de campo e a submete: {@code createOrder} com
     * {@code OrderInput} (origem EXTERNAL exige protocolo; INTERNAL o
     * proibe -- o motor recusa a combinacao errada), alvo CONSUMER_UNIT com
     * a carga de efeito dada, e {@code submit}.
     *
     * @param parentOrderId OS mae (a BILL_REVIEW da P2.20, na revisao por
     *                      vazamento; {@code null} na afericao)
     * @return id da OS criada e submetida (OPEN ou PENDING_APPROVAL,
     *         conforme a fabrica do tipo)
     */
    UUID createOrder(LeakMeterOrderType type, ServiceOrderEnums.Origin origin, UUID protocolId,
                     UUID createdBy, UUID unitId, UUID parentOrderId, String effectPayloadJson);

    /** Aprova a OS em PENDING_APPROVAL (quem aprova e diferente de quem criou -- o motor garante). */
    void approve(UUID orderId, UUID approverId);

    /** Agenda a OS de campo (OPEN/WAITING_DEPENDENCY -> SCHEDULED), com os portoes de seguranca do motor. */
    void schedule(UUID orderId, OffsetDateTime scheduledFor, UUID actorId);

    /** Inicia a execucao em campo (SCHEDULED/OPEN -> IN_EXECUTION). */
    void startExecution(UUID orderId, UUID actorId);

    /**
     * Executa a OS: registra o handler do efeito do tipo e delega ao
     * {@code execute} do motor, que invoca {@code effect} ao aplicar o alvo.
     * Estado nao executavel e recusado pelo motor, sem traducao.
     */
    void execute(UUID orderId, UUID actorId, LeakMeterEffect effect);
}
