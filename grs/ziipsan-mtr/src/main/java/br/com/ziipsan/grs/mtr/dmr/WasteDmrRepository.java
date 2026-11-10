package br.com.ziipsan.grs.mtr.dmr;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E3.
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteDmrRepository extends JpaRepository<WasteDmr, UUID> {

    Optional<WasteDmr> findByTenantIdAndYearAndQuarter(UUID tenantId, int year, int quarter);
}
