package br.com.ziipsan.grs.mtr.repository;

import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteGeneratorRepository extends JpaRepository<WasteGenerator, UUID> {

    Optional<WasteGenerator> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Gerador pelo índice cego do documento (nada é decifrado). */
    Optional<WasteGenerator> findByTenantIdAndDocumentHash(UUID tenantId, String documentHash);
}