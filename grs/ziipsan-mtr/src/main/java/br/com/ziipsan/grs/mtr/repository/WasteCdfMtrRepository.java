package br.com.ziipsan.grs.mtr.repository;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E2.
import br.com.ziipsan.grs.mtr.domain.WasteCdfMtr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WasteCdfMtrRepository extends JpaRepository<WasteCdfMtr, UUID> {

    @Query("SELECT l FROM WasteCdfMtr l WHERE l.tenantId = :tenantId AND l.cdf.id = :cdfId")
    List<WasteCdfMtr> findByCdfIdAndTenantId(@Param("tenantId") UUID tenantId,
                                             @Param("cdfId") UUID cdfId);

    @Query("SELECT l.mtr.id FROM WasteCdfMtr l WHERE l.tenantId = :tenantId AND l.mtr.id IN :mtrIds")
    List<UUID> findAlreadyLinkedMtrIds(@Param("tenantId") UUID tenantId,
                                       @Param("mtrIds") List<UUID> mtrIds);
}
