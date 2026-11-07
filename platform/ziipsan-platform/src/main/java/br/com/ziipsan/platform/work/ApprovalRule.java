package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Segregação e elegibilidade da aprovação comum — a regra que o COMPONENTE aplica,
 * para que nenhum módulo precise (nem possa) reimplementá-la:
 *
 * <ol>
 *   <li><b>Quem solicita não aprova.</b> {@code wf.self_approval} é classe a, sempre
 *       falso. A regra recebe o valor RESOLVIDO do parâmetro e o trata como trava de
 *       produto: se qualquer caminho o trouxer {@code true}, a decisão é recusada com
 *       erro de estado (o catálogo foi adulterado) — NUNCA como permissão. Não existe
 *       ramo neste código em que solicitante == aprovador passe.</li>
 *   <li><b>Elegibilidade.</b> Decide quem o item endereça: o responsável direto, um
 *       aprovador elegível nomeado, ou — só quando o item não nomeia ninguém — qualquer
 *       portador da permissão exigida. Fora disso, só por delegação vigente de alguém
 *       elegível, e a decisão volta marcada como {@link Channel#DELEGATED} para a
 *       auditoria registrar o canal.</li>
 *   <li><b>Delegação não lava segregação — nem pelo ator, nem pelo DELEGANTE</b>
 *       (correção pós-revisão A1). O ator continua proibido de decidir a tarefa que
 *       ELE solicitou. E, no canal delegado, uma delegação cujo DELEGANTE é o
 *       solicitante da tarefa/do item NÃO habilita decisão nenhuma: o solicitante
 *       não pode escolher, por delegação, quem decide o próprio pedido — antes da
 *       correção, bastava o solicitante constar como endereçado do item (ex.: na
 *       lista de elegíveis) e delegar a um terceiro para o terceiro decidir por
 *       ele, passando pelo record e pelo CHECK {@code decided_by <> requester_id}
 *       do banco (o terceiro, formalmente, não é o solicitante). Delegação do
 *       solicitante é ignorada como canal e, se era o único caminho do ator, a
 *       recusa sai com o código {@code self-approval}.</li>
 *   <li><b>Escopo concedido é o teto de TODA decisão delegada</b> (correção
 *       pós-revisão M1 + A2). Decisão pelo canal {@link Channel#DELEGATED} exige
 *       escopo EFETIVO não vazio, onde efetivo =
 *       {@code concedidas ∩ permissões ATUAIS do delegante} (ver
 *       {@link DelegationRule#effectiveGrantedPermissions}): delegação com escopo
 *       vazio não decide aprovação nenhuma (nem de item endereçado por nome);
 *       delegante que perdeu a permissão depois de delegar retira o poder do
 *       delegado junto; permissão que o delegante ganhou depois NÃO entra (a foto
 *       da concessão limita). E, quando o item declara {@code requiredPermission},
 *       ela precisa estar nesse conjunto efetivo.</li>
 * </ol>
 */
public final class ApprovalRule {

    /** Por qual canal o ator pode decidir: direto (é elegível) ou por delegação vigente. */
    public enum Channel { DIRECT, DELEGATED }

    private ApprovalRule() {
    }

    /**
     * Trava da classe a: {@code wf.self_approval} verdadeiro é inconsistência do
     * catálogo/produto, não uma licença. Erro de estado explícito.
     */
    public static void assertSelfApprovalForbidden(boolean selfApprovalParameterValue) {
        if (selfApprovalParameterValue) {
            throw new IllegalStateException("wf.self_approval é classe a, sempre falso: "
                    + "um valor verdadeiro indica catálogo adulterado e nenhuma aprovação pode prosseguir");
        }
    }

    /**
     * Confere se {@code actorId} pode decidir a tarefa e devolve o canal.
     * Recusas: tarefa já decidida (409), solicitante == ator (422, campo decidedBy,
     * código self-approval — o TESTE do aceite), decisão delegada a partir de
     * delegação DO solicitante (422, código self-approval — correção A1), ator fora
     * da elegibilidade e sem delegação vigente que o cubra COM ESCOPO EFETIVO
     * (422, código not-eligible — correções A2/M1).
     *
     * @param currentPermissionsByDelegator permissões ATUAIS de cada delegante das
     *        delegações recebidas pelo ator (resolvidas pelo chamador pela porta de
     *        permissões); delegante ausente do mapa vale ZERO — a foto da concessão
     *        sozinha nunca habilita o canal delegado
     */
    public static Channel assertCanDecide(WorkItem item, ApprovalTask task, boolean selfApprovalParameterValue,
                                          UUID actorId, Set<String> actorPermissions,
                                          List<WorkDelegation> delegationsReceivedByActor,
                                          Map<UUID, Set<String>> currentPermissionsByDelegator, Instant now) {
        if (item == null || task == null || actorId == null || now == null) {
            throw new IllegalArgumentException("item, tarefa, ator e instante são obrigatórios para decidir");
        }
        assertSelfApprovalForbidden(selfApprovalParameterValue);
        if (!task.id().equals(item.approvalTaskId()) || !task.workItemId().equals(item.id())) {
            throw new IllegalStateException("a tarefa de aprovação não é a tarefa do item informado");
        }
        if (!task.status().isPending()) {
            throw new ApiException.Conflict("approval-already-decided",
                    "Esta aprovação já foi decidida e não aceita nova decisão.");
        }
        if (!item.status().isOpen()) {
            throw new ApiException.Conflict("work-item-closed",
                    "O item de trabalho já foi encerrado e não aceita decisão.");
        }
        // Segregação PRIMEIRO, antes de qualquer canal: nem elegibilidade direta,
        // nem permissão, nem delegação autorizam o solicitante a decidir o próprio pedido.
        // Vale contra o solicitante da tarefa E contra o solicitante do item (são o mesmo
        // fato na criação; conferir os dois fecha a porta a divergência de gravação).
        if (actorId.equals(task.requesterId()) || actorId.equals(item.requesterId())) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "decidedBy", "self-approval",
                    "Quem solicitou não pode aprovar o próprio pedido (wf.self_approval, classe a, sempre falso)."));
        }
        if (isEligible(item, actorId, actorPermissions)) {
            return Channel.DIRECT;
        }
        // Canal delegado: delegação vigente recebida pelo ator, de alguém elegível ao
        // item, com escopo efetivo (concedidas ∩ delegante ATUAL) não vazio — e, se o
        // item declara permissão exigida, com ela dentro do escopo efetivo.
        List<WorkDelegation> delegations = delegationsReceivedByActor == null ? List.of() : delegationsReceivedByActor;
        boolean delegationFromRequester = false;
        for (WorkDelegation delegation : delegations) {
            if (!delegation.delegateId().equals(actorId) || !delegation.isActiveAt(now)) {
                continue;
            }
            // A1: delegação DO SOLICITANTE não é canal — é o solicitante escolhendo
            // quem decide o pedido dele. Ignora esta delegação (outra, de elegível
            // que não seja o solicitante, ainda pode habilitar) e lembra a tentativa
            // para recusar com o código de segregação se ela era o único caminho.
            if (delegation.delegatorId().equals(task.requesterId())
                    || delegation.delegatorId().equals(item.requesterId())) {
                delegationFromRequester = true;
                continue;
            }
            Set<String> effective = DelegationRule.effectiveGrantedPermissions(delegation,
                    currentPermissionsByDelegator == null
                            ? null : currentPermissionsByDelegator.get(delegation.delegatorId()));
            if (effective.isEmpty()) {
                continue;
            }
            if (item.requiredPermission() != null && !item.requiredPermission().isBlank()
                    && !effective.contains(item.requiredPermission())) {
                continue;
            }
            boolean delegatorEligible = item.isDirectlyAddressedTo(delegation.delegatorId())
                    || (item.isPermissionAddressed() && effective.contains(item.requiredPermission()));
            if (delegatorEligible) {
                return Channel.DELEGATED;
            }
        }
        if (delegationFromRequester) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "decidedBy", "self-approval",
                    "Quem solicitou não pode aprovar o próprio pedido por delegação: "
                            + "a delegação do solicitante não habilita ninguém a decidir o pedido dele "
                            + "(wf.self_approval, classe a, sempre falso)."));
        }
        throw ApiException.Validation.of(new ApiException.FieldError(
                "decidedBy", "not-eligible",
                "O usuário não é aprovador elegível desta tarefa, nem tem delegação vigente de um elegível "
                        + "com escopo efetivo (concedido e ainda vigente no delegante) para ela."));
    }

    /** Elegibilidade direta (sem delegação): nomeado, ou portador da permissão de item não nomeado. */
    public static boolean isEligible(WorkItem item, UUID userId, Set<String> userPermissions) {
        if (item.isDirectlyAddressedTo(userId)) {
            return true;
        }
        return item.isPermissionAddressed() && userPermissions != null
                && userPermissions.contains(item.requiredPermission());
    }
}
