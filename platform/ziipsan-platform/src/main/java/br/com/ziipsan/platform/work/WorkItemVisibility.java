package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quem enxerga o item na SUA caixa ("uma caixa por usuário"): o responsável direto,
 * um aprovador elegível nomeado, o portador da permissão de um item endereçado por
 * permissão — e o DELEGADO vigente de qualquer um deles (a delegação move o trabalho,
 * não só a decisão). O solicitante NÃO vê o próprio pedido na caixa de tarefas: pedido
 * dele é acompanhamento no módulo de origem, não trabalho dele — salvo se ele também
 * for um dos endereçados acima por outro fato.
 *
 * <p><b>Visibilidade por delegação e permissão ATUAL do delegante</b> (correção
 * pós-revisão A2): para item endereçado POR PERMISSÃO, o delegado só enxerga pelo
 * canal da delegação se a permissão exigida estiver no escopo EFETIVO da delegação
 * ({@code concedidas ∩ permissões ATUAIS do delegante}, via
 * {@link DelegationRule#effectiveGrantedPermissions}) — se o delegante perde a
 * permissão depois de delegar, o delegado perde a visibilidade junto, e permissão
 * que o delegante ganhou depois não aparece para o delegado (a foto limita).
 * Endereçamento por NOME (responsável/elegível nomeado) não é permissão: a
 * visibilidade do delegado do nomeado não depende de conjunto de permissões —
 * mas a DECISÃO delegada continua mais estrita que a visibilidade (ver
 * {@link ApprovalRule}: exige escopo efetivo não vazio, correção M1). Ou seja:
 * enxergar o item do delegante não implica poder aprová-lo por ele.
 *
 * <p>FRONTEIRA DECLARADA (aceite): o escopo por ÁREA é da P3.30, que depende desta
 * tarefa — aqui NÃO há filtro por unidade organizacional/área; quando a P3.30 nascer,
 * ela filtra DEPOIS desta visibilidade, sem mudar este contrato.
 */
public final class WorkItemVisibility {

    private WorkItemVisibility() {
    }

    /**
     * @param currentPermissionsByDelegator permissões ATUAIS de cada delegante das
     *        delegações recebidas pelo usuário; delegante ausente do mapa vale ZERO
     *        para o caminho por permissão (ausência de fato não concede visibilidade)
     */
    public static boolean isVisibleTo(WorkItem item, UUID userId, Set<String> userPermissions,
                                      List<WorkDelegation> delegationsReceivedByUser,
                                      Map<UUID, Set<String>> currentPermissionsByDelegator, Instant now) {
        if (item == null || userId == null || now == null) {
            throw new IllegalArgumentException("item, usuário e instante são obrigatórios");
        }
        if (!item.status().isOpen()) {
            return false;
        }
        if (ApprovalRule.isEligible(item, userId, userPermissions)) {
            return true;
        }
        for (WorkDelegation delegation : delegationsReceivedByUser == null ? List.<WorkDelegation>of() : delegationsReceivedByUser) {
            if (!delegation.delegateId().equals(userId) || !delegation.isActiveAt(now)) {
                continue;
            }
            if (item.isDirectlyAddressedTo(delegation.delegatorId())) {
                return true;
            }
            if (item.isPermissionAddressed()) {
                Set<String> effective = DelegationRule.effectiveGrantedPermissions(delegation,
                        currentPermissionsByDelegator == null
                                ? null : currentPermissionsByDelegator.get(delegation.delegatorId()));
                if (effective.contains(item.requiredPermission())) {
                    return true;
                }
            }
        }
        return false;
    }
}
