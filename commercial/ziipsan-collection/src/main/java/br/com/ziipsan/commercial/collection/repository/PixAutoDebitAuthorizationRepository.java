package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.PixAutoDebitAuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PixAutoDebitAuthorizationRepository extends JpaRepository<PixAutoDebitAuthorizationEntity, UUID> {}
