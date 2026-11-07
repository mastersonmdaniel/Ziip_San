package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.ReturnFileDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReturnFileDetailRepository extends JpaRepository<ReturnFileDetailEntity, UUID> {
    java.util.List<ReturnFileDetailEntity> findByTenantIdAndReturnFileId(UUID tenantId, UUID returnFileId);
}
