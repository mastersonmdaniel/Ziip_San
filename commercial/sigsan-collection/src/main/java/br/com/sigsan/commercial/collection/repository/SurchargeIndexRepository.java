package br.com.sigsan.commercial.collection.repository;

import br.com.sigsan.commercial.collection.domain.SurchargeIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SurchargeIndexRepository extends JpaRepository<SurchargeIndex, UUID> {}
