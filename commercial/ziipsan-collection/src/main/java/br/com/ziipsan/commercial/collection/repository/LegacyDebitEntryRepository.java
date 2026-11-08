package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.LegacyDebitEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LegacyDebitEntryRepository extends JpaRepository<LegacyDebitEntryEntity, UUID> {}
