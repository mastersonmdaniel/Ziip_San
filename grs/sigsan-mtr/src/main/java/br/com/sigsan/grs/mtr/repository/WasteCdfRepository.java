package br.com.sigsan.grs.mtr.repository;

import br.com.sigsan.grs.mtr.domain.WasteCdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteCdfRepository extends JpaRepository<WasteCdf, UUID> {

    @Query("SELECT c FROM WasteCdf c WHERE c.tenantId = :tenantId AND c.mtr.id = :mtrId")
    Optional<WasteCdf> findByMtrIdAndTenantId(@Param("tenantId") UUID tenantId,
                                              @Param("mtrId") UUID mtrId);
}