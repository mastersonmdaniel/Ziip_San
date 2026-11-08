package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.work.UserPermissions;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Unidade organizacional e escopo por área (P3.30, ADR-0040, V1_8).
 *
 * <p>O que este serviço garante, e onde:
 * <ul>
 *   <li><b>Hierarquia:</b> {@link #createUnit} recusa pai inexistente/inativo,
 *       código repetido no tenant e qualquer ciclo (anda a cadeia de pais
 *       com visitados, com teto de profundidade — árvore corrompida é erro
 *       explícito, nunca laço infinito);</li>
 *   <li><b>Áreas da unidade:</b> {@link #linkArea} só liga área que EXISTE
 *       no território do registry (porta), sem duplicar a ligação;</li>
 *   <li><b>Resolução de escopo:</b> {@link #effectiveScope} soma as
 *       concessões do usuário e EXPANDE para baixo (localidade → setores e
 *       rotas dela; setor → rotas dele, SEM subir à localidade-mãe — subir
 *       vazaria a localidade inteira para quem só tem um setor; unidade →
 *       subárvore e áreas de todas, expandidas do mesmo jeito);</li>
 *   <li><b>Filtro obrigatório:</b> {@link #requireReadable}/{@link
 *       #requireWritable} e os atalhos por recurso (protocolo, OS, unidade
 *       consumidora, conta) aplicam a {@link ScopeRule} em leitura E
 *       escrita. Fora do escopo, a resposta é 404 — como fora do tenant:
 *       o usuário da localidade A nem fica sabendo que o registro da
 *       localidade B existe. Os repositórios dos módulos donos chamam
 *       estes métodos (ou a ScopeRule sobre a mesma âncora) em TODA
 *       leitura/escrita — a fiação módulo a módulo é a pendência de
 *       integração declarada no relatório;</li>
 *   <li><b>Concessão:</b> {@link #grantOrgUnit}/{@link #grantArea}/
 *       {@link #grantUnrestricted}/{@link #revokeGrant} passam pela
 *       {@link GrantRule} com o escopo e as permissões ATUAIS do
 *       concedente — ADMIN só concede o que possui;</li>
 *   <li><b>Trâmite:</b> {@link #transferProtocol} aplica a
 *       {@link ProtocolTransferRule} (bloqueio com OS programada), move a
 *       atribuição e grava a trilha com as OS que acompanharam, numa ÚNICA
 *       operação atômica da loja ({@code transferAssignmentAndRecord} —
 *       na loja JDBC, uma transação só para as duas escritas; correção D1
 *       da revisão independente). A checagem de escopo do ator precede
 *       qualquer revelação de estado do protocolo (correção D3);</li>
 *   <li><b>Unidade inativa:</b> {@link #deactivateUnit} desativa a unidade
 *       (correção D4); unidade inativa NÃO recebe concessão, atribuição
 *       inicial nem trâmite de destino — mas tramitar PARA FORA dela e
 *       revogar concessão dela continuam possíveis (são as saídas).</li>
 * </ul>
 */
public class OrgScopeService {

    /** Teto de profundidade da hierarquia: acima disso a árvore é tratada como corrompida. */
    static final int MAX_DEPTH = 50;

    private final UUID tenantId;
    private final OrgScopePorts.OrgScopeStore store;
    private final OrgScopePorts.TerritoryPort territory;
    private final OrgScopePorts.AnchorPort anchors;
    private final OrgScopePorts.OrderPort orders;
    private final UserPermissions userPermissions;
    private final Clock clock;

    public OrgScopeService(UUID tenantId, OrgScopePorts.OrgScopeStore store,
                           OrgScopePorts.TerritoryPort territory, OrgScopePorts.AnchorPort anchors,
                           OrgScopePorts.OrderPort orders, UserPermissions userPermissions, Clock clock) {
        if (tenantId == null || store == null || territory == null || anchors == null
                || orders == null || userPermissions == null || clock == null) {
            throw new IllegalArgumentException("tenant, loja, território, âncoras, OS, permissões e relógio são obrigatórios");
        }
        this.tenantId = tenantId;
        this.store = store;
        this.territory = territory;
        this.anchors = anchors;
        this.orders = orders;
        this.userPermissions = userPermissions;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Hierarquia e áreas
    // ------------------------------------------------------------------

    public List<OrgUnit> listUnits() {
        return store.listUnits();
    }

    public OrgUnit createUnit(UUID actorId, UUID parentId, String code, String name) {
        if (actorId == null) {
            throw new IllegalArgumentException("ator é obrigatório");
        }
        List<OrgUnit> units = store.listUnits();
        Map<UUID, OrgUnit> byId = index(units);
        if (parentId != null) {
            OrgUnit parent = byId.get(parentId);
            if (parent == null) {
                throw new ApiException.NotFound("Unidade organizacional pai não encontrada.");
            }
            if (!parent.active()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "parentId", "inactive-parent", "A unidade pai está inativa."));
            }
            assertDepthWithinLimit(byId, parentId);
        }
        for (OrgUnit unit : units) {
            if (unit.code().equals(code)) {
                throw new ApiException.Conflict("org-unit-code-taken",
                        "Já existe unidade organizacional com este código neste tenant.");
            }
        }
        OrgUnit unit = new OrgUnit(UUID.randomUUID(), tenantId, parentId, code, name, true, clock.instant());
        store.insertUnit(unit);
        return unit;
    }

    public void linkArea(UUID actorId, UUID unitId, OrgAreaType areaType, UUID areaId) {
        if (actorId == null) {
            throw new IllegalArgumentException("ator é obrigatório");
        }
        requireUnit(unitId);
        if (!territory.areaExists(areaType, areaId)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "areaId", "area-not-found", "A área não existe no território do tenant."));
        }
        for (OrgAreaLink link : store.areasOfUnit(unitId)) {
            if (link.areaType() == areaType && link.areaId().equals(areaId)) {
                throw new ApiException.Conflict("org-unit-area-duplicated",
                        "Esta área já está ligada a esta unidade organizacional.");
            }
        }
        store.insertAreaLink(new OrgAreaLink(unitId, areaType, areaId));
    }

    // ------------------------------------------------------------------
    // Resolução de escopo (expansão descendente, sem subir)
    // ------------------------------------------------------------------

    public EffectiveScope effectiveScope(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("usuário é obrigatório");
        }
        EffectiveScope result = EffectiveScope.EMPTY;
        for (UserScopeGrant grant : store.grantsOfUser(userId)) {
            result = result.union(expandGrant(grant));
        }
        return result;
    }

    private EffectiveScope expandGrant(UserScopeGrant grant) {
        if (grant.unrestricted()) {
            return EffectiveScope.UNRESTRICTED;
        }
        if (grant.orgUnitId() != null) {
            return expandOrgUnit(grant.orgUnitId());
        }
        return expandArea(grant.areaType(), grant.areaId());
    }

    /**
     * Expansão de UMA área, SEMPRE para baixo: localidade traz ela mesma,
     * seus setores e suas rotas; setor traz ele mesmo e suas rotas (NÃO a
     * localidade-mãe: incluí-la cobriria os demais setores da localidade);
     * rota traz só ela.
     */
    EffectiveScope expandArea(OrgAreaType type, UUID areaId) {
        return switch (type) {
            case LOCALITY -> {
                Set<UUID> sectors = territory.sectorIdsOfLocality(areaId);
                Set<UUID> routes = new HashSet<>(territory.routeIdsOfLocality(areaId));
                for (UUID sectorId : sectors) {
                    routes.addAll(territory.routeIdsOfSector(sectorId));
                }
                yield new EffectiveScope(false, Set.of(), Set.of(areaId), sectors, routes);
            }
            case SECTOR -> new EffectiveScope(false, Set.of(), Set.of(), Set.of(areaId),
                    territory.routeIdsOfSector(areaId));
            case ROUTE -> new EffectiveScope(false, Set.of(), Set.of(), Set.of(), Set.of(areaId));
        };
    }

    /** Expansão de uma unidade: ela, a subárvore inteira e as áreas de todas, expandidas. */
    EffectiveScope expandOrgUnit(UUID unitId) {
        List<OrgUnit> units = store.listUnits();
        Map<UUID, OrgUnit> byId = index(units);
        if (!byId.containsKey(unitId)) {
            throw new ApiException.NotFound("Unidade organizacional não encontrada.");
        }
        Set<UUID> subtree = subtreeOf(byId, unitId);
        EffectiveScope result = new EffectiveScope(false, subtree, Set.of(), Set.of(), Set.of());
        for (OrgAreaLink link : store.areasOfUnits(subtree)) {
            result = result.union(expandArea(link.areaType(), link.areaId()));
        }
        return result;
    }

    private static Set<UUID> subtreeOf(Map<UUID, OrgUnit> byId, UUID rootId) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (OrgUnit unit : byId.values()) {
            if (unit.parentId() != null) {
                children.computeIfAbsent(unit.parentId(), k -> new ArrayList<>()).add(unit.id());
            }
        }
        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.remove();
            if (!result.add(current)) {
                throw new IllegalStateException("hierarquia de unidades com ciclo a partir de " + rootId);
            }
            if (result.size() > MAX_DEPTH * MAX_DEPTH) {
                throw new IllegalStateException("hierarquia de unidades excede o tamanho sadio a partir de " + rootId);
            }
            queue.addAll(children.getOrDefault(current, List.of()));
        }
        return result;
    }

    private static void assertDepthWithinLimit(Map<UUID, OrgUnit> byId, UUID unitId) {
        Set<UUID> seen = new HashSet<>();
        UUID current = unitId;
        int depth = 0;
        while (current != null) {
            if (!seen.add(current)) {
                throw new IllegalStateException("hierarquia de unidades com ciclo em " + unitId);
            }
            if (++depth > MAX_DEPTH) {
                throw new IllegalStateException("hierarquia de unidades excede a profundidade sadia (" + MAX_DEPTH + ")");
            }
            OrgUnit unit = byId.get(current);
            current = unit == null ? null : unit.parentId();
        }
    }

    private static Map<UUID, OrgUnit> index(List<OrgUnit> units) {
        Map<UUID, OrgUnit> byId = new HashMap<>();
        for (OrgUnit unit : units) {
            byId.put(unit.id(), unit);
        }
        return byId;
    }

    private OrgUnit requireUnit(UUID unitId) {
        return store.findUnit(unitId)
                .orElseThrow(() -> new ApiException.NotFound("Unidade organizacional não encontrada."));
    }

    /** A unidade existe E está ativa (correção D4: inativa não recebe concessão nem protocolo). */
    private OrgUnit requireActiveUnit(UUID unitId) {
        OrgUnit unit = requireUnit(unitId);
        if (!unit.active()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "orgUnitId", "inactive-unit", "A unidade organizacional está inativa."));
        }
        return unit;
    }

    /**
     * Desativação de unidade (correção D4): a hierarquia deixa de ser só de
     * criação. Desativar NÃO apaga a unidade nem revoga concessões já
     * gravadas — ela apenas fecha a porta de entrada (concessão, atribuição
     * e trâmite de destino recusam unidade inativa); a revogação das
     * concessões existentes continua sendo ato explícito de quem concede.
     * Idempotente: desativar unidade já inativa devolve a unidade como está.
     */
    public OrgUnit deactivateUnit(UUID actorId, UUID unitId) {
        if (actorId == null) {
            throw new IllegalArgumentException("ator é obrigatório");
        }
        OrgUnit unit = requireUnit(unitId);
        if (!unit.active()) {
            return unit;
        }
        OrgUnit deactivated = new OrgUnit(unit.id(), unit.tenantId(), unit.parentId(),
                unit.code(), unit.name(), false, unit.createdAt());
        store.updateUnit(deactivated);
        return deactivated;
    }

    // ------------------------------------------------------------------
    // Concessão e revogação (ADMIN só concede o que possui)
    // ------------------------------------------------------------------

    public UserScopeGrant grantOrgUnit(UUID grantorId, UUID targetUserId, UUID orgUnitId) {
        requireActiveUnit(orgUnitId);
        EffectiveScope expansion = expandOrgUnit(orgUnitId);
        GrantRule.assertCanGrantOrgUnit(effectiveScope(grantorId), orgUnitId, expansion);
        UserScopeGrant grant = UserScopeGrant.orgUnitGrant(
                UUID.randomUUID(), tenantId, targetUserId, orgUnitId, grantorId, clock.instant());
        store.insertGrant(grant);
        return grant;
    }

    public UserScopeGrant grantArea(UUID grantorId, UUID targetUserId, OrgAreaType areaType, UUID areaId) {
        if (!territory.areaExists(areaType, areaId)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "areaId", "area-not-found", "A área não existe no território do tenant."));
        }
        GrantRule.assertCanGrantArea(effectiveScope(grantorId), expandArea(areaType, areaId));
        UserScopeGrant grant = UserScopeGrant.areaGrant(
                UUID.randomUUID(), tenantId, targetUserId, areaType, areaId, grantorId, clock.instant());
        store.insertGrant(grant);
        return grant;
    }

    public UserScopeGrant grantUnrestricted(UUID grantorId, UUID targetUserId) {
        GrantRule.assertCanGrantUnrestricted(effectiveScope(grantorId));
        UserScopeGrant grant = UserScopeGrant.unrestrictedGrant(
                UUID.randomUUID(), tenantId, targetUserId, grantorId, clock.instant());
        store.insertGrant(grant);
        return grant;
    }

    /** Revogar exige cobrir a concessão (a mesma régua de conceder). */
    public void revokeGrant(UUID grantorId, UUID grantId) {
        UserScopeGrant grant = store.findGrant(grantId)
                .orElseThrow(() -> new ApiException.NotFound("Concessão de escopo não encontrada."));
        EffectiveScope grantorScope = effectiveScope(grantorId);
        if (grant.unrestricted()) {
            GrantRule.assertCanGrantUnrestricted(grantorScope);
        } else if (grant.orgUnitId() != null) {
            GrantRule.assertCanGrantOrgUnit(grantorScope, grant.orgUnitId(), expandOrgUnit(grant.orgUnitId()));
        } else {
            GrantRule.assertCanGrantArea(grantorScope, expandArea(grant.areaType(), grant.areaId()));
        }
        store.deleteGrant(grantId);
    }

    /** A régua de PERMISSÃO da concessão, exposta para a gestão de usuários da P0.13. */
    public void assertGrantorOwnsPermissions(UUID grantorId, Set<String> requestedPermissions) {
        GrantRule.assertCanGrantPermissions(
                userPermissions.permissionsOf(tenantId, grantorId), requestedPermissions);
    }

    // ------------------------------------------------------------------
    // Filtro obrigatório (leitura e escrita dos recursos recortados)
    // ------------------------------------------------------------------

    public void requireReadable(UUID userId, AreaAnchor anchor) {
        if (!ScopeRule.covers(effectiveScope(userId), anchor)) {
            throw new ApiException.NotFound("Registro não encontrado.");
        }
    }

    public void requireWritable(UUID userId, AreaAnchor anchor) {
        if (!ScopeRule.covers(effectiveScope(userId), anchor)) {
            throw new ApiException.NotFound("Registro não encontrado.");
        }
    }

    public void requireProtocolReadable(UUID userId, UUID protocolId) {
        requireReadable(userId, anchors.anchorForProtocol(protocolId)
                .orElseThrow(() -> new ApiException.NotFound("Protocolo não encontrado.")));
    }

    public void requireProtocolWritable(UUID userId, UUID protocolId) {
        requireWritable(userId, anchors.anchorForProtocol(protocolId)
                .orElseThrow(() -> new ApiException.NotFound("Protocolo não encontrado.")));
    }

    public void requireServiceOrderReadable(UUID userId, UUID serviceOrderId) {
        requireReadable(userId, anchors.anchorForServiceOrder(serviceOrderId)
                .orElseThrow(() -> new ApiException.NotFound("Ordem de serviço não encontrada.")));
    }

    public void requireServiceOrderWritable(UUID userId, UUID serviceOrderId) {
        requireWritable(userId, anchors.anchorForServiceOrder(serviceOrderId)
                .orElseThrow(() -> new ApiException.NotFound("Ordem de serviço não encontrada.")));
    }

    public void requireConsumerUnitReadable(UUID userId, UUID consumerUnitId) {
        requireReadable(userId, anchors.anchorForConsumerUnit(consumerUnitId)
                .orElseThrow(() -> new ApiException.NotFound("Unidade consumidora não encontrada.")));
    }

    public void requireConsumerUnitWritable(UUID userId, UUID consumerUnitId) {
        requireWritable(userId, anchors.anchorForConsumerUnit(consumerUnitId)
                .orElseThrow(() -> new ApiException.NotFound("Unidade consumidora não encontrada.")));
    }

    public void requireBillReadable(UUID userId, UUID billId) {
        requireReadable(userId, anchors.anchorForBill(billId)
                .orElseThrow(() -> new ApiException.NotFound("Conta não encontrada.")));
    }

    public void requireBillWritable(UUID userId, UUID billId) {
        requireWritable(userId, anchors.anchorForBill(billId)
                .orElseThrow(() -> new ApiException.NotFound("Conta não encontrada.")));
    }

    // ------------------------------------------------------------------
    // Atribuição inicial e trâmite de protocolo
    // ------------------------------------------------------------------

    /**
     * Primeira atribuição de unidade a um protocolo (não é trâmite: não há
     * origem). Exige escopo do ator cobrindo as áreas da unidade de destino,
     * ou a permissão de tramitar para qualquer unidade, ou escopo irrestrito.
     */
    public void assignProtocolUnit(UUID actorId, UUID protocolId, UUID orgUnitId) {
        if (!orders.protocolExists(protocolId)) {
            throw new ApiException.NotFound("Protocolo não encontrado.");
        }
        requireActiveUnit(orgUnitId);
        // Escopo ANTES do conflito "já atribuído" (mesma disciplina da
        // correção D3 no trâmite): ator sem escopo no destino não aprende,
        // pela resposta, se o protocolo já tem unidade.
        EffectiveScope actorScope = effectiveScope(actorId);
        Set<String> actorPermissions = userPermissions.permissionsOf(tenantId, actorId);
        boolean anyUnit = actorPermissions.contains(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT);
        if (!anyUnit && !ScopeRule.coversAllAreas(actorScope, expandOrgUnit(orgUnitId))) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "orgUnitId", "destination-outside-actor-scope",
                    "Atribuir o protocolo a esta unidade exige escopo nela ou a permissão protocol:transfer-any-unit."));
        }
        if (store.protocolOrgUnit(protocolId).isPresent()) {
            throw new ApiException.Conflict("protocol-already-assigned",
                    "O protocolo já tem unidade organizacional; use o trâmite para mudar.");
        }
        store.upsertProtocolAssignment(protocolId, orgUnitId, actorId);
    }

    /**
     * Trâmite: move a atribuição do protocolo para outra unidade, com as OS
     * acompanhando (a unidade derivada delas vem desta atribuição) e
     * bloqueio quando há OS programada. Move a atribuição e grava a trilha
     * numa ÚNICA operação atômica da loja (correção D1).
     *
     * <p>Ordem das checagens (correção D3): o escopo do ator sobre as duas
     * pontas é conferido ANTES de revelar o estado do protocolo — quem não
     * tem escopo recebe 404/validação de escopo, nunca o conflito
     * "sem unidade" nem o 409 com a contagem de OS programadas.
     *
     * @return os ids das OS que acompanharam o protocolo
     */
    public List<UUID> transferProtocol(UUID actorId, UUID protocolId, UUID toOrgUnitId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "reason", "required", "O trâmite exige o motivo."));
        }
        if (!orders.protocolExists(protocolId)) {
            throw new ApiException.NotFound("Protocolo não encontrado.");
        }
        OrgUnit destination = requireUnit(toOrgUnitId);
        EffectiveScope actorScope = effectiveScope(actorId);
        Set<String> actorPermissions = userPermissions.permissionsOf(tenantId, actorId);
        boolean anyUnit = actorPermissions.contains(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT);
        EffectiveScope destinationAreas = expandOrgUnit(toOrgUnitId);
        Optional<UUID> currentUnit = store.protocolOrgUnit(protocolId);
        if (currentUnit.isEmpty()) {
            // Sem unidade atual não há origem para conferir: a régua é o
            // destino (ou o desvio). Quem falha nela recebe o MESMO 404 de
            // protocolo inexistente — não aprende que ele existe sem unidade.
            if (!anyUnit && !actorScope.unrestricted()
                    && !ScopeRule.coversAllAreas(actorScope, destinationAreas)) {
                throw new ApiException.NotFound("Protocolo não encontrado.");
            }
            throw new ApiException.Conflict("protocol-without-unit",
                    "O protocolo ainda não tem unidade organizacional; atribua a primeira unidade antes de tramitar.");
        }
        UUID fromOrgUnitId = currentUnit.get();
        // 1) ESCOPO das duas pontas, antes de qualquer estado/bloqueio (D3).
        ProtocolTransferRule.assertTransferScope(fromOrgUnitId, toOrgUnitId,
                actorPermissions, actorScope, expandOrgUnit(fromOrgUnitId), destinationAreas);
        // 2) Destino diferente e ativo (a ORIGEM pode estar inativa:
        //    tramitar para fora dela é justamente a saída).
        ProtocolTransferRule.assertDifferentUnit(fromOrgUnitId, toOrgUnitId);
        if (!destination.active()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "toOrgUnitId", "inactive-unit", "A unidade de destino está inativa."));
        }
        // 3) Só agora a contagem de OS programadas pode aparecer na resposta.
        List<OrgScopePorts.OrderRef> protocolOrders = orders.ordersOfProtocol(protocolId);
        int scheduled = 0;
        List<UUID> movedOrderIds = new ArrayList<>();
        for (OrgScopePorts.OrderRef order : protocolOrders) {
            movedOrderIds.add(order.id());
            if (ProtocolTransferRule.BLOCKING_ORDER_STATUS.equals(order.status())) {
                scheduled++;
            }
        }
        ProtocolTransferRule.assertNoScheduledOrders(scheduled);
        // 4) Mover + trilhar numa operação atômica só da loja (D1).
        store.transferAssignmentAndRecord(new ProtocolTransfer(UUID.randomUUID(), tenantId, protocolId,
                fromOrgUnitId, toOrgUnitId, actorId, reason, movedOrderIds, clock.instant()));
        return List.copyOf(movedOrderIds);
    }
}
