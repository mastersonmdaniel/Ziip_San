package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.work.UserPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serviço de escopo com lojas em memória: a expansão descendente, o filtro
 * de leitura/escrita do aceite, a concessão limitada ao que o concedente
 * possui e o trâmite com OS acompanhando / bloqueio por OS programada.
 */
class OrgScopeServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();
    private static final UUID SECTOR_A = UUID.randomUUID();
    private static final UUID SECTOR_B = UUID.randomUUID();
    private static final UUID ROUTE_A = UUID.randomUUID();
    private static final UUID ROUTE_B = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_C = UUID.randomUUID();
    private static final UUID NOBODY = UUID.randomUUID();
    private static final UUID PROTOCOL_1 = UUID.randomUUID();
    private static final UUID PROTOCOL_2 = UUID.randomUUID();
    private static final UUID ORDER_1 = UUID.randomUUID();
    private static final UUID ORDER_2 = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private InMemoryStore store;
    private OrgScopeService service;
    private UUID unitA;
    private UUID unitB;

    static final class InMemoryStore implements OrgScopePorts.OrgScopeStore {
        final Map<UUID, OrgUnit> units = new HashMap<>();
        final List<OrgAreaLink> links = new ArrayList<>();
        final Map<UUID, UserScopeGrant> grants = new HashMap<>();
        final Map<UUID, UUID> assignments = new HashMap<>();
        final List<ProtocolTransfer> transfers = new ArrayList<>();
        /** Quantas vezes o trâmite passou pela operação ATÔMICA da loja (correção D1). */
        int atomicTransfers;

        public Optional<OrgUnit> findUnit(UUID id) {
            return Optional.ofNullable(units.get(id));
        }

        public List<OrgUnit> listUnits() {
            return List.copyOf(units.values());
        }

        public void insertUnit(OrgUnit unit) {
            units.put(unit.id(), unit);
        }

        public void updateUnit(OrgUnit unit) {
            units.put(unit.id(), unit);
        }

        public List<OrgAreaLink> areasOfUnit(UUID unitId) {
            return links.stream().filter(l -> l.orgUnitId().equals(unitId)).toList();
        }

        public List<OrgAreaLink> areasOfUnits(Set<UUID> ids) {
            return links.stream().filter(l -> ids.contains(l.orgUnitId())).toList();
        }

        public void insertAreaLink(OrgAreaLink link) {
            links.add(link);
        }

        public List<UserScopeGrant> grantsOfUser(UUID userId) {
            return grants.values().stream().filter(g -> g.userId().equals(userId)).toList();
        }

        public Optional<UserScopeGrant> findGrant(UUID id) {
            return Optional.ofNullable(grants.get(id));
        }

        public void insertGrant(UserScopeGrant grant) {
            grants.put(grant.id(), grant);
        }

        public void deleteGrant(UUID id) {
            grants.remove(id);
        }

        public Optional<UUID> protocolOrgUnit(UUID protocolId) {
            return Optional.ofNullable(assignments.get(protocolId));
        }

        public void upsertProtocolAssignment(UUID protocolId, UUID orgUnitId, UUID actorId) {
            assignments.put(protocolId, orgUnitId);
        }

        public void insertTransfer(ProtocolTransfer transfer) {
            transfers.add(transfer);
        }

        @Override
        public void transferAssignmentAndRecord(ProtocolTransfer transfer) {
            atomicTransfers++;
            assignments.put(transfer.protocolId(), transfer.toOrgUnitId());
            transfers.add(transfer);
        }
    }

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        OrgScopePorts.TerritoryPort territory = new OrgScopePorts.TerritoryPort() {
            public boolean areaExists(OrgAreaType type, UUID id) {
                return switch (type) {
                    case LOCALITY -> id.equals(LOC_A) || id.equals(LOC_B);
                    case SECTOR -> id.equals(SECTOR_A) || id.equals(SECTOR_B);
                    case ROUTE -> id.equals(ROUTE_A) || id.equals(ROUTE_B);
                };
            }

            public Set<UUID> sectorIdsOfLocality(UUID localityId) {
                return localityId.equals(LOC_A) ? Set.of(SECTOR_A) : Set.of(SECTOR_B);
            }

            public Set<UUID> routeIdsOfLocality(UUID localityId) {
                return localityId.equals(LOC_A) ? Set.of(ROUTE_A) : Set.of(ROUTE_B);
            }

            public Set<UUID> routeIdsOfSector(UUID sectorId) {
                return sectorId.equals(SECTOR_A) ? Set.of(ROUTE_A) : Set.of(ROUTE_B);
            }
        };
        // As âncoras não são exercitadas neste teste (o filtro é testado pela
        // âncora direta em requireReadable/requireWritable): o fake devolve
        // âncora vazia presente para qualquer registro.
        OrgScopePorts.AnchorPort anchors = new OrgScopePorts.AnchorPort() {
            public Optional<AreaAnchor> anchorForConsumerUnit(UUID id) {
                return Optional.of(AreaAnchor.EMPTY);
            }

            public Optional<AreaAnchor> anchorForProtocol(UUID id) {
                return Optional.of(AreaAnchor.EMPTY);
            }

            public Optional<AreaAnchor> anchorForServiceOrder(UUID id) {
                return Optional.of(AreaAnchor.EMPTY);
            }

            public Optional<AreaAnchor> anchorForBill(UUID id) {
                return Optional.of(AreaAnchor.EMPTY);
            }

            public Optional<AreaAnchor> anchorForWorkObject(String type, UUID id) {
                return Optional.of(AreaAnchor.EMPTY);
            }
        };
        OrgScopePorts.OrderPort orders = new OrgScopePorts.OrderPort() {
            public List<OrgScopePorts.OrderRef> ordersOfProtocol(UUID protocolId) {
                if (protocolId.equals(PROTOCOL_1)) {
                    return List.of(new OrgScopePorts.OrderRef(ORDER_1, "OPEN"));
                }
                if (protocolId.equals(PROTOCOL_2)) {
                    return List.of(new OrgScopePorts.OrderRef(ORDER_2, "SCHEDULED"));
                }
                return List.of();
            }

            public boolean protocolExists(UUID protocolId) {
                return protocolId.equals(PROTOCOL_1) || protocolId.equals(PROTOCOL_2);
            }
        };
        UserPermissions permissions = (tenantId, userId) -> {
            if (userId.equals(ADMIN)) {
                return Set.of("user:read", "user:write", "protocol:read", "protocol:write");
            }
            if (userId.equals(USER_A)) {
                return Set.of("protocol:read", "protocol:write");
            }
            return Set.of();
        };
        service = new OrgScopeService(TENANT, store, territory, anchors, orders, permissions,
                Clock.fixed(NOW, ZoneOffset.UTC));

        // Bootstrap: o ADMIN nasce irrestrito pela implantação (fora do serviço,
        // como a semente de plataforma) — inserido direto na loja.
        store.insertGrant(UserScopeGrant.unrestrictedGrant(UUID.randomUUID(), TENANT, ADMIN, ADMIN, NOW));

        OrgUnit root = service.createUnit(ADMIN, null, "MATRIZ", "Matriz");
        OrgUnit a = service.createUnit(ADMIN, root.id(), "ESC-A", "Escritório A");
        OrgUnit b = service.createUnit(ADMIN, root.id(), "ESC-B", "Escritório B");
        unitA = a.id();
        unitB = b.id();
        service.linkArea(ADMIN, unitA, OrgAreaType.LOCALITY, LOC_A);
        service.linkArea(ADMIN, unitB, OrgAreaType.LOCALITY, LOC_B);
        // USER_A recebe a localidade A das mãos do ADMIN.
        service.grantArea(ADMIN, USER_A, OrgAreaType.LOCALITY, LOC_A);
    }

    @Test
    @DisplayName("expansão descendente: localidade traz setor e rota; setor NÃO sobe à localidade (sem vazamento)")
    void expansionIsDownwardOnly() {
        EffectiveScope scopeA = service.effectiveScope(USER_A);
        assertThat(scopeA.localityIds()).containsExactly(LOC_A);
        assertThat(scopeA.sectorIds()).containsExactly(SECTOR_A);
        assertThat(scopeA.routeIds()).containsExactly(ROUTE_A);

        service.grantArea(ADMIN, USER_C, OrgAreaType.SECTOR, SECTOR_B);
        EffectiveScope sectorScope = service.effectiveScope(USER_C);
        assertThat(sectorScope.sectorIds()).containsExactly(SECTOR_B);
        assertThat(sectorScope.localityIds()).isEmpty();
        // Registro de OUTRO setor da mesma localidade B não é coberto pelo setor B1.
        assertThat(ScopeRule.covers(sectorScope, new AreaAnchor(LOC_B, SECTOR_A, ROUTE_A))).isFalse();
        assertThat(ScopeRule.covers(sectorScope, new AreaAnchor(LOC_B, SECTOR_B, ROUTE_B))).isTrue();
    }

    @Test
    @DisplayName("aceite: escopo na localidade A NÃO lê nem altera registro da localidade B (404 nos dois caminhos)")
    void scopeABlocksReadAndWriteOfB() {
        AreaAnchor anchorA = new AreaAnchor(LOC_A, SECTOR_A, ROUTE_A);
        AreaAnchor anchorB = new AreaAnchor(LOC_B, SECTOR_B, ROUTE_B);
        assertThatCode(() -> service.requireReadable(USER_A, anchorA)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireWritable(USER_A, anchorA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireReadable(USER_A, anchorB))
                .isInstanceOf(ApiException.NotFound.class);
        assertThatThrownBy(() -> service.requireWritable(USER_A, anchorB))
                .isInstanceOf(ApiException.NotFound.class);
        // Sem concessão nenhuma: nem a própria A.
        assertThatThrownBy(() -> service.requireReadable(NOBODY, anchorA))
                .isInstanceOf(ApiException.NotFound.class);
        // Irrestrito lê e altera qualquer uma.
        assertThatCode(() -> service.requireWritable(ADMIN, anchorB)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("aceite: concessão limitada — USER_A não concede a área B nem irrestrito; o ADMIN concede a A")
    void grantLimitedToGrantorScope() {
        assertThatThrownBy(() -> service.grantArea(USER_A, USER_C, OrgAreaType.LOCALITY, LOC_B))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.grantUnrestricted(USER_A, USER_C))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> service.grantArea(USER_A, USER_C, OrgAreaType.SECTOR, SECTOR_A))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.grantOrgUnit(ADMIN, USER_C, unitA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.grantOrgUnit(USER_A, USER_C, unitB))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("aceite: ADMIN só concede permissão que possui (régua exposta à gestão de usuários)")
    void grantLimitedToGrantorPermissions() {
        assertThatCode(() -> service.assertGrantorOwnsPermissions(ADMIN, Set.of("protocol:write")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertGrantorOwnsPermissions(
                ADMIN, Set.of(OrgScopePermissions.PROTOCOL_TRANSFER_ANY_UNIT)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.assertGrantorOwnsPermissions(
                USER_A, Set.of("user:write")))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("concessão por unidade cobre a subárvore: unidade A dá A e não dá B; a raiz daria as duas")
    void orgUnitGrantCoversSubtree() {
        service.grantOrgUnit(ADMIN, USER_C, unitA);
        EffectiveScope scope = service.effectiveScope(USER_C);
        assertThat(ScopeRule.covers(scope, new AreaAnchor(LOC_A, SECTOR_A, ROUTE_A))).isTrue();
        assertThat(ScopeRule.covers(scope, new AreaAnchor(LOC_B, SECTOR_B, ROUTE_B))).isFalse();
    }

    @Test
    @DisplayName("aceite: trâmite move a atribuição, as OS acompanham (ids na trilha) e OS programada bloqueia")
    void transferMovesAssignmentAndOrdersFollow() {
        service.assignProtocolUnit(ADMIN, PROTOCOL_1, unitA);
        List<UUID> moved = service.transferProtocol(ADMIN, PROTOCOL_1, unitB, "Reorganização regional.");
        assertThat(moved).containsExactly(ORDER_1);
        assertThat(store.protocolOrgUnit(PROTOCOL_1)).contains(unitB);
        assertThat(store.transfers).hasSize(1);
        assertThat(store.transfers.get(0).movedOrderIds()).containsExactly(ORDER_1);
        assertThat(store.transfers.get(0).fromOrgUnitId()).isEqualTo(unitA);

        service.assignProtocolUnit(ADMIN, PROTOCOL_2, unitA);
        assertThatThrownBy(() -> service.transferProtocol(ADMIN, PROTOCOL_2, unitB, "Tentativa com OS programada."))
                .isInstanceOf(ApiException.Conflict.class);
        // Bloqueado: a atribuição NÃO se moveu.
        assertThat(store.protocolOrgUnit(PROTOCOL_2)).contains(unitA);
    }

    @Test
    @DisplayName("correção D1: o trâmite passa pela operação ATÔMICA da loja (mover + trilhar juntos), nunca por duas chamadas separadas")
    void transferUsesAtomicStoreOperation() {
        service.assignProtocolUnit(ADMIN, PROTOCOL_1, unitA);
        service.transferProtocol(ADMIN, PROTOCOL_1, unitB, "Reorganização regional.");
        assertThat(store.atomicTransfers).isEqualTo(1);
        assertThat(store.protocolOrgUnit(PROTOCOL_1)).contains(unitB);
        assertThat(store.transfers).hasSize(1);
    }

    @Test
    @DisplayName("correção D3: ator sem escopo no destino NÃO aprende a contagem de OS programadas (escopo antes do bloqueio)")
    void scopeCheckedBeforeScheduledCountLeak() {
        service.assignProtocolUnit(ADMIN, PROTOCOL_2, unitA);
        // PROTOCOL_2 tem OS SCHEDULED: na ordem antiga a resposta seria o
        // 409 COM A CONTAGEM; USER_A não tem escopo em B, então a régua
        // dele é a validação de escopo do destino — sem contagem nenhuma.
        assertThatThrownBy(() -> service.transferProtocol(USER_A, PROTOCOL_2, unitB, "Tentativa sem escopo."))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(store.protocolOrgUnit(PROTOCOL_2)).contains(unitA);
        // E quem não tem escopo nenhum nem aprende que o protocolo existe
        // sem unidade: o "protocol-without-unit" não vaza para ele (404).
        assertThatThrownBy(() -> service.transferProtocol(NOBODY, PROTOCOL_1, unitB, "Tentativa sem escopo."))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    @DisplayName("correção D4: unidade inativa não recebe concessão, atribuição nem trâmite; desativar é operação do serviço")
    void inactiveUnitRefusesGrantAssignAndTransfer() {
        // O protocolo entra em B enquanto B está ATIVA; depois B desativa.
        service.assignProtocolUnit(ADMIN, PROTOCOL_1, unitB);
        OrgUnit deactivated = service.deactivateUnit(ADMIN, unitB);
        assertThat(deactivated.active()).isFalse();
        assertThat(store.findUnit(unitB)).get().extracting(OrgUnit::active).isEqualTo(false);
        // Desativar de novo é idempotente; unidade inexistente é 404.
        assertThatCode(() -> service.deactivateUnit(ADMIN, unitB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.deactivateUnit(ADMIN, UUID.randomUUID()))
                .isInstanceOf(ApiException.NotFound.class);

        assertThatThrownBy(() -> service.grantOrgUnit(ADMIN, USER_C, unitB))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.assignProtocolUnit(ADMIN, PROTOCOL_2, unitB))
                .isInstanceOf(ApiException.Validation.class);
        // Trâmite PARA a inativa é recusado e nada se move.
        service.assignProtocolUnit(ADMIN, PROTOCOL_2, unitA);
        assertThatThrownBy(() -> service.transferProtocol(ADMIN, PROTOCOL_2, unitB, "Para unidade inativa."))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(store.protocolOrgUnit(PROTOCOL_2)).contains(unitA);
        // Tramitar PARA FORA da unidade inativa continua possível (é a saída).
        List<UUID> moved = service.transferProtocol(ADMIN, PROTOCOL_1, unitA, "Saindo da unidade desativada.");
        assertThat(moved).containsExactly(ORDER_1);
        assertThat(store.protocolOrgUnit(PROTOCOL_1)).contains(unitA);
    }

    @Test
    @DisplayName("trâmite por quem só tem escopo na origem é recusado (sem transfer-any-unit, destino fora)")
    void transferByOriginOnlyUserRejected() {
        service.assignProtocolUnit(ADMIN, PROTOCOL_1, unitA);
        assertThatThrownBy(() -> service.transferProtocol(USER_A, PROTOCOL_1, unitB, "Sem escopo no destino."))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("ligar área inexistente é recusado; código de unidade repetido é conflito")
    void linkAndCreateValidations() {
        assertThatThrownBy(() -> service.linkArea(ADMIN, unitA, OrgAreaType.LOCALITY, UUID.randomUUID()))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.createUnit(ADMIN, null, "ESC-A", "Duplicada"))
                .isInstanceOf(ApiException.Conflict.class);
        Set<UUID> seen = new HashSet<>();
        assertThat(seen.add(unitA)).isTrue();
    }
}
