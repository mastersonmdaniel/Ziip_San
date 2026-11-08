package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;

import java.util.Set;
import java.util.UUID;

/**
 * Regras puras do TRÂMITE de protocolo entre unidades organizacionais
 * (P3.30, aceite): "trâmite do protocolo entre unidades com as OS
 * acompanhando e bloqueio quando há OS programada".
 * <ul>
 *   <li><b>Bloqueio por OS programada:</b> havendo QUALQUER OS do protocolo
 *       em {@code SCHEDULED} (estado do motor da P1.31), o trâmite é
 *       recusado (409) nomeando quantas são — a programação foi feita
 *       contra a unidade atual (equipe, rota, agenda); mover o protocolo
 *       por baixo dela deixaria a OS programada órfã de contexto. OS em
 *       outro estado NÃO bloqueia: o aceite nomeia só a programada, e
 *       ampliar o bloqueio por conta própria inventaria regra;</li>
 *   <li><b>Quem pode tramitar:</b> com a permissão
 *       {@code protocol:transfer-any-unit} (concessão direta do catálogo),
 *       para qualquer unidade; sem ela, o escopo do ator precisa cobrir as
 *       áreas da unidade de ORIGEM e as da unidade de DESTINO (ele enxerga
 *       o protocolo onde ele está e para onde ele vai);</li>
 *   <li><b>Destino diferente e existente:</b> tramitar para a unidade atual
 *       é erro de validação, não um no-op silencioso; a existência/atividade
 *       das unidades é conferida pelo serviço antes desta regra.</li>
 * </ul>
 * "As OS acompanhando" é efeito do serviço: a unidade derivada das OS do
 * protocolo vem da atribuição do protocolo (protocol_org_assignments), que
 * o trâmite atualiza e cuja trilha é gravada na MESMA transação da loja
 * (método único {@code transferAssignmentAndRecord} da porta — correção D1
 * da revisão independente) — nenhuma linha de OS muda, e a trilha grava os
 * ids que acompanharam.
 *
 * <p><b>Ordem das checagens (correção D3 da revisão independente):</b>
 * escopo do ator PRIMEIRO ({@link #assertTransferScope}), destino
 * diferente depois ({@link #assertDifferentUnit}) e o bloqueio com a
 * CONTAGEM de OS programadas POR ÚLTIMO ({@link #assertNoScheduledOrders}):
 * um ator sem escopo nas pontas não pode aprender, pela resposta, quantas
 * OS programadas o protocolo tem.
 */
public final class ProtocolTransferRule {

    /** Estado do motor de OS (P1.31) que bloqueia o trâmite: OS programada. */
    public static final String BLOCKING_ORDER_STATUS = "SCHEDULED";

    private ProtocolTransferRule() {
    }

    /**
     * A régua completa do trâmite, na ordem da correção D3: escopo do ator,
     * destino diferente, bloqueio por OS programada (com a contagem) por
     * último. O serviço chama as três partes nesta mesma ordem, intercalando
     * a contagem das OS — que só ele conhece — depois do escopo.
     */
    public static void assertCanTransfer(UUID fromOrgUnitId, UUID toOrgUnitId,
                                         Set<String> actorPermissions, EffectiveScope actorScope,
                                         EffectiveScope originAreas, EffectiveScope destinationAreas,
                                         int scheduledOrderCount) {
        assertTransferScope(fromOrgUnitId, toOrgUnitId, actorPermissions, actorScope,
                originAreas, destinationAreas);
        assertDifferentUnit(fromOrgUnitId, toOrgUnitId);
        assertNoScheduledOrders(scheduledOrderCount);
    }

    /** Só o escopo do ator sobre as DUAS pontas (ou o desvio transfer-any-unit). */
    public static void assertTransferScope(UUID fromOrgUnitId, UUID toOrgUnitId,
                                           Set<String> actorPermissions, EffectiveScope actorScope,
                                           EffectiveScope originAreas, EffectiveScope destinationAreas) {
        if (toOrgUnitId == null || actorScope == null || originAreas == null || destinationAreas == null) {
            throw new IllegalArgumentException("destino, escopo do ator e áreas das unidades são obrigatórios");
        }
        boolean anyUnit = actorPermissions != null
                && actorPermissions.contains(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT);
        if (anyUnit || actorScope.unrestricted()) {
            return;
        }
        if (!ScopeRule.coversAllAreas(actorScope, originAreas)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "fromOrgUnitId", "origin-outside-actor-scope",
                    "Sem a permissão protocol:transfer-any-unit, o trâmite exige escopo na unidade de origem."));
        }
        if (!ScopeRule.coversAllAreas(actorScope, destinationAreas)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "toOrgUnitId", "destination-outside-actor-scope",
                    "Sem a permissão protocol:transfer-any-unit, o trâmite exige escopo na unidade de destino."));
        }
    }

    /** Tramitar para a unidade atual é erro de validação, não no-op silencioso. */
    public static void assertDifferentUnit(UUID fromOrgUnitId, UUID toOrgUnitId) {
        if (toOrgUnitId == null) {
            throw new IllegalArgumentException("destino é obrigatório");
        }
        if (toOrgUnitId.equals(fromOrgUnitId)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "toOrgUnitId", "same-unit", "O protocolo já está nesta unidade organizacional."));
        }
    }

    /** O bloqueio por OS programada, nomeando a contagem — chamado SÓ depois do escopo (D3). */
    public static void assertNoScheduledOrders(int scheduledOrderCount) {
        if (scheduledOrderCount < 0) {
            throw new IllegalArgumentException("contagem de OS programadas não pode ser negativa");
        }
        if (scheduledOrderCount > 0) {
            throw new ApiException.Conflict("protocol-transfer-blocked-scheduled-orders",
                    "Trâmite bloqueado: há " + scheduledOrderCount
                            + " ordem(ns) de serviço programada(s) para este protocolo; "
                            + "desprograme ou encerre antes de tramitar.");
        }
    }
}
