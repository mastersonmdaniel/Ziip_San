package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillMaintenanceOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillMaintenanceOperationRepository extends JpaRepository<BillMaintenanceOperationEntity, UUID> {

    Optional<BillMaintenanceOperationEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    /** Base da contagem do teto de retificacoes contra o usuario: por unidade e ano (a chave do parametro e maxPerUnitAndYear). */
    long countByTenantIdAndUnitIdAndAgainstUserTrueAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID tenantId, UUID unitId, String status, OffsetDateTime from, OffsetDateTime to);

    /**
     * Base da contagem do teto de alteracao de vencimento: por CONTA -- a
     * cadeia de versoes inteira (os ids de todas as versoes da mesma conta,
     * mesma unidade e referencia), no ano civil (correcao A6 da revisao:
     * a semantica do parametro e por conta/cadeia, nao por unidade).
     */
    long countByTenantIdAndBillIdInAndOperationAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID tenantId, Collection<UUID> billIds, String operation, String status,
            OffsetDateTime from, OffsetDateTime to);
}
