package br.com.ziipsan.grs.mtr.repository;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2: a consulta por MTR
// passa pela ligação waste_cdf_mtr (o CDF deixou de ser 1:1 com o MTR).
import br.com.ziipsan.grs.mtr.domain.WasteCdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteCdfRepository extends JpaRepository<WasteCdf, UUID> {

    @Query("SELECT l.cdf FROM WasteCdfMtr l WHERE l.tenantId = :tenantId AND l.mtr.id = :mtrId")
    Optional<WasteCdf> findByMtrIdAndTenantId(@Param("tenantId") UUID tenantId,
                                              @Param("mtrId") UUID mtrId);

    Optional<WasteCdf> findByIdAndTenantId(UUID id, UUID tenantId);
}
