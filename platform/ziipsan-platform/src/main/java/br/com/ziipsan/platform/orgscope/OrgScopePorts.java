package br.com.ziipsan.platform.orgscope;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Portas da P3.30 (persistência e leituras vizinhas atrás de interfaces —
 * a regra de negócio não conhece JDBC; a implementação JDBC é
 * {@link JdbcOrgScopeStore} e os testes usam lojas em memória).
 *
 * <p>Convenções de ausência, iguais às do restante do produto: registro
 * inexistente devolve {@link Optional#empty()} — NUNCA um objeto presumido.
 * Âncora é {@code Optional<AreaAnchor>}: vazio = o registro não existe
 * (404); presente com {@link AreaAnchor#EMPTY} = o registro existe mas não
 * tem âncora territorial resolvível (o {@link ScopeRule} decide, e só o
 * irrestrito passa).
 */
public final class OrgScopePorts {

    private OrgScopePorts() {
    }

    /** Persistência das tabelas da V1_8. */
    public interface OrgScopeStore {
        Optional<OrgUnit> findUnit(UUID unitId);

        List<OrgUnit> listUnits();

        void insertUnit(OrgUnit unit);

        /** Atualiza a unidade (hoje: a desativação da P3.30-correção D4). */
        void updateUnit(OrgUnit unit);

        List<OrgAreaLink> areasOfUnit(UUID unitId);

        /** Áreas de VÁRIAS unidades de uma vez (a expansão anda na subárvore inteira). */
        List<OrgAreaLink> areasOfUnits(Set<UUID> unitIds);

        void insertAreaLink(OrgAreaLink link);

        List<UserScopeGrant> grantsOfUser(UUID userId);

        Optional<UserScopeGrant> findGrant(UUID grantId);

        void insertGrant(UserScopeGrant grant);

        void deleteGrant(UUID grantId);

        /** A unidade organizacional ATUAL do protocolo, se já atribuída. */
        Optional<UUID> protocolOrgUnit(UUID protocolId);

        void upsertProtocolAssignment(UUID protocolId, UUID orgUnitId, UUID actorId);

        void insertTransfer(ProtocolTransfer transfer);

        /**
         * O TRÂMITE como UMA operação da loja (correção D1 da revisão
         * independente): mover a atribuição do protocolo E gravar a trilha
         * do trâmite, atomicamente — nunca uma sem a outra. A implementação
         * JDBC sobrescreve este método com UMA transação só para as duas
         * escritas. Este corpo padrão (duas chamadas separadas) existe só
         * para lojas simples de teste que não têm transação; ele NÃO é
         * atômico e nenhuma loja de produção pode herdá-lo.
         */
        default void transferAssignmentAndRecord(ProtocolTransfer transfer) {
            upsertProtocolAssignment(transfer.protocolId(), transfer.toOrgUnitId(), transfer.movedBy());
            insertTransfer(transfer);
        }
    }

    /** Território do registry (V3_1/V3_6): existência e vizinhança das áreas. */
    public interface TerritoryPort {
        boolean areaExists(OrgAreaType type, UUID areaId);

        Set<UUID> sectorIdsOfLocality(UUID localityId);

        Set<UUID> routeIdsOfLocality(UUID localityId);

        Set<UUID> routeIdsOfSector(UUID sectorId);
    }

    /** Resolução da âncora territorial dos registros recortados. */
    public interface AnchorPort {
        Optional<AreaAnchor> anchorForConsumerUnit(UUID consumerUnitId);

        Optional<AreaAnchor> anchorForProtocol(UUID protocolId);

        Optional<AreaAnchor> anchorForServiceOrder(UUID serviceOrderId);

        Optional<AreaAnchor> anchorForBill(UUID billId);

        /**
         * Âncora do OBJETO de um item da caixa da P2.28, pelo
         * {@code object_type} que o módulo de origem declarou. Tipo de objeto
         * sem resolução territorial conhecida devolve âncora VAZIA presente
         * (o item existe; só o irrestrito o vê) — nunca "sem recorte".
         */
        Optional<AreaAnchor> anchorForWorkObject(String objectType, UUID objectId);
    }

    /** Uma OS do protocolo (id e estado do motor da P1.31, como texto do domínio). */
    public record OrderRef(UUID id, String status) {
    }

    /** As OS de um protocolo, para o trâmite (bloqueio e acompanhamento). */
    public interface OrderPort {
        List<OrderRef> ordersOfProtocol(UUID protocolId);

        boolean protocolExists(UUID protocolId);
    }
}
