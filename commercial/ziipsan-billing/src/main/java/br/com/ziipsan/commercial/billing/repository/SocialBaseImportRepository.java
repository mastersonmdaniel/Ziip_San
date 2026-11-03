package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.SocialBaseImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SocialBaseImportRepository extends JpaRepository<SocialBaseImportEntity, UUID> {}
