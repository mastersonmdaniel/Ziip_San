package br.com.sigsan.core.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);

    List<AuditLog> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            UUID tenantId, UUID userId);

    List<AuditLog> findByTenantIdAndCreatedAtBetween(
            UUID tenantId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND a.correlationId = :correlationId ORDER BY a.createdAt ASC")
    List<AuditLog> findByCorrelationId(@Param("tenantId") UUID tenantId, @Param("correlationId") UUID correlationId);

    @Query("SELECT count(a) FROM AuditLog a WHERE a.tenantId = :tenantId AND a.success = false AND a.createdAt >= :since")
    long countFailedOperations(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);
}