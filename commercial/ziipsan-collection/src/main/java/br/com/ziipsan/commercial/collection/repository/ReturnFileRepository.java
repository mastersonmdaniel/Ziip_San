package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.ReturnFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnFileRepository extends JpaRepository<ReturnFileEntity, UUID> {
    Optional<ReturnFileEntity> findByTenantIdAndNsaAndFileHash(UUID tenantId, String nsa, String fileHash);
}
