package br.com.ziipsan.grs.mtr.repository;

import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.service.MtrSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteMtrRepository extends JpaRepository<WasteMtr, UUID> {

    Optional<WasteMtr> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT m FROM WasteMtr m WHERE m.tenantId = :tenantId AND m.mtrNumber = :mtrNumber")
    Optional<WasteMtr> findByMtrNumberAndTenantId(@Param("tenantId") UUID tenantId,
                                                 @Param("mtrNumber") String mtrNumber);

    @Query("SELECT m FROM WasteMtr m WHERE m.tenantId = :tenantId"
            + " AND (:#{#criteria.mtrNumber} IS NULL OR m.mtrNumber LIKE CONCAT('%', :#{#criteria.mtrNumber}, '%'))"
            + " AND (:#{#criteria.status} IS NULL OR m.status = :#{#criteria.status})"
            + " AND (:#{#criteria.generatorId} IS NULL OR m.generator.id = :#{#criteria.generatorId})"
            + " AND (:#{#criteria.transporterId} IS NULL OR m.transporter.id = :#{#criteria.transporterId})"
            + " AND (:#{#criteria.destinationId} IS NULL OR m.destination.id = :#{#criteria.destinationId})"
            + " AND (:#{#criteria.wasteClassId} IS NULL OR m.wasteClass.id = :#{#criteria.wasteClassId})"
            + " AND (:#{#criteria.vehiclePlate} IS NULL OR UPPER(m.vehiclePlate) = UPPER(:#{#criteria.vehiclePlate}))"
            + " AND (:#{#criteria.issuedFrom} IS NULL OR m.issuedAt >= :#{#criteria.issuedFrom})"
            + " AND (:#{#criteria.issuedTo} IS NULL OR m.issuedAt <= :#{#criteria.issuedTo})"
            + " AND (:#{#criteria.sinirSyncStatus} IS NULL OR m.sinirSyncStatus = :#{#criteria.sinirSyncStatus})")
    Page<WasteMtr> findByCriteria(@Param("tenantId") UUID tenantId,
                                  @Param("criteria") MtrSearchCriteria criteria,
                                  Pageable pageable);

    long countByTenantId(UUID tenantId);
}