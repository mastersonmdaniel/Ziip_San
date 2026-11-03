package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.GeneratedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeneratedFileRepository extends JpaRepository<GeneratedFile, UUID> {

    /** Arquivos do tenant, os mais recentes primeiro (a RLS já isola por tenant). */
    List<GeneratedFile> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<GeneratedFile> findByIdAndTenantId(UUID id, UUID tenantId);
}
