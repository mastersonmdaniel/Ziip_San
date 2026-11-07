package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;

import java.util.UUID;

/**
 * Porta para o nucleo de ordens de servico da P1.31
 * ({@code ServiceOrderService} do ziipsan-core). A implementacao entregue
 * nesta frente e o {@link CoreServiceOrderGateway}, que traduz cada metodo
 * para a API real do motor -- nao ha outra implementacao de producao.
 *
 * <p>Contrato alinhado ao motor real (correcao G3 da revisao cruzada):
 * <ul>
 *   <li>{@code findTypeByCode} do motor <b>lanca</b> {@code ApiException.NotFound}
 *       quando o tipo nao existe -- {@link #findType} traduz isso para
 *       {@code null} ("nao declarado"); tipo inativo volta com
 *       {@code active() == false} e quem chama decide;</li>
 *   <li>a aprovacao e decidida pelo motor a partir do
 *       {@code requires_approval} <b>declarado no tipo</b>: o {@code submit}
 *       desvia para {@code PENDING_APPROVAL} ou {@code OPEN} por ele, o
 *       {@code approve} so aceita {@code PENDING_APPROVAL} e o
 *       {@code execute} de OS interna so sai de {@code OPEN}/{@code SCHEDULED}.
 *       A porta nao promete nenhum atalho em volta disso;</li>
 *   <li>{@link #createOrder} ja deixa a OS submetida (cria, adiciona o alvo
 *       CONSUMER_UNIT em {@code DRAFT} e submete), como o fluxo do motor
 *       exige -- alvo so entra em {@code DRAFT}/{@code OPEN};</li>
 *   <li>{@link #execute} registra, antes de delegar, o
 *       {@code EffectHandler} do codigo de efeito do tipo: sem handler o
 *       motor recusa a execucao. O handler invoca o {@link MaintenanceEffect}
 *       da operacao <b>durante</b> o {@code execute}, na mesma chamada --
 *       e ele quem aplica a manutencao (versao da conta, acerto, revisao);
 *       o motor so marca o efeito como aplicado depois que o handler volta
 *       sem erro.</li>
 * </ul>
 */
public interface ServiceOrderGateway {

    /** O tipo no catalogo do tenant, como o motor o ve (fabrica declarada). */
    record TypeInfo(boolean active, boolean requiresApproval) {
    }

    /**
     * Efeito de manutencao que o handler do motor invoca durante o
     * {@code execute} da OS: aplica a operacao (nova versao da conta, acerto
     * do cancelamento, abertura da revisao) na transacao de quem chamou o
     * servico. Lancar excecao aqui faz o motor recusar a execucao e o
     * servico desfazer a operacao inteira -- efeito nunca fica "aplicado"
     * no motor sem ter sido aplicado na conta.
     */
    @FunctionalInterface
    interface MaintenanceEffect {
        void apply();
    }

    /** O tipo no catalogo do tenant; {@code null} = tipo nao declarado. */
    TypeInfo findType(BillOrderType type);

    /**
     * Garante o tipo declarado no tenant com a exigencia de aprovacao dada
     * (declaracao idempotente por codigo, {@link BillOrderType#declaration}):
     * tipo ja existente e devolvido como esta -- o motor nao redeclara.
     *
     * @return o TypeInfo vigente apos a garantia
     */
    TypeInfo ensureType(BillOrderType type, boolean requiresApproval);

    /**
     * Cria a OS interna e a submete: {@code createOrder} com
     * {@code OrderInput} (origem EXTERNAL exige protocolo; INTERNAL o
     * proibe -- o motor recusa a combinacao errada), alvo CONSUMER_UNIT
     * para a unidade com a carga de efeito dada, e {@code submit}.
     *
     * @param origin            EXTERNAL quando ha protocolo da P1.36; INTERNAL
     *                          na alteracao interna do backoffice (sem protocolo)
     * @param protocolId        obrigatorio com EXTERNAL; proibido com INTERNAL
     * @param effectPayloadJson carga do efeito (contrato de
     *                          {@link BillOrderType#effectSchemaJson()})
     * @return id da OS criada e submetida (OPEN ou PENDING_APPROVAL,
     *         conforme a fabrica do tipo)
     */
    UUID createOrder(BillOrderType type, ServiceOrderEnums.Origin origin, UUID protocolId,
                     UUID createdBy, UUID unitId, String effectPayloadJson);

    /** Aprova a OS em PENDING_APPROVAL (quem aprova e diferente de quem criou -- o motor garante). */
    void approve(UUID orderId, UUID approverId);

    /**
     * Executa a OS interna: registra o handler do efeito do tipo e delega
     * ao {@code execute} do motor, que invoca {@code effect} ao aplicar o
     * alvo. So funciona a partir de OPEN/SCHEDULED -- qualquer outro estado
     * e recusado pelo motor, sem traducao.
     */
    void execute(UUID orderId, UUID actorId, MaintenanceEffect effect);

    /**
     * Cria e submete uma OS de um tipo qualquer do catalogo do tenant, SEM
     * executar: e o pedido que fica para o fluxo dono do tipo (ex.: a OS de
     * releitura que o catalogo de anormalidades da leitura declara --
     * {@code reading_anomalies.service_order_type_code}, V5_7 da P2.5).
     * Tipo inexistente ou inativo e erro explicito do motor, sem traducao.
     *
     * @param typeCode codigo do tipo em {@code service_types} (vem do
     *                 catalogo do tenant, nunca inventado por esta frente)
     * @return id da OS criada e submetida
     */
    UUID createSubmittedOrder(String typeCode, UUID unitId, UUID createdBy);
}
