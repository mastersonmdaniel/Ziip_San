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

    // ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2/E3:
    // MTRs destinados por um destinador com destined_at dentro do período (agregação do CDF e da DMR).
    @Query("SELECT m FROM WasteMtr m WHERE m.tenantId = :tenantId AND m.destination.id = :destinationId"
            + " AND m.status = br.com.ziipsan.grs.mtr.domain.WasteMtr.MtrStatus.DESTINED"
            + " AND m.destinedAt >= :from AND m.destinedAt < :to ORDER BY m.destinedAt")
    java.util.List<WasteMtr> findDestinedInPeriod(@Param("tenantId") UUID tenantId,
                                                  @Param("destinationId") UUID destinationId,
                                                  @Param("from") java.time.OffsetDateTime from,
                                                  @Param("to") java.time.OffsetDateTime to);

    // ED-104 — E3: todos os MTRs do tenant emitidos no período (agregação da DMR do gerador).
    @Query("SELECT m FROM WasteMtr m WHERE m.tenantId = :tenantId"
            + " AND m.issuedAt >= :from AND m.issuedAt < :to ORDER BY m.issuedAt")
    java.util.List<WasteMtr> findIssuedInPeriod(@Param("tenantId") UUID tenantId,
                                                @Param("from") java.time.OffsetDateTime from,
                                                @Param("to") java.time.OffsetDateTime to);

    // ED-104 — E6: MTR já emitido a partir de um registo de lodo da ED-105 (idempotência da emissão).
    @Query("SELECT m FROM WasteMtr m WHERE m.tenantId = :tenantId AND m.sludgeRecordId = :sludgeRecordId")
    Optional<WasteMtr> findBySludgeRecordIdAndTenantId(@Param("tenantId") UUID tenantId,
                                                       @Param("sludgeRecordId") UUID sludgeRecordId);
}