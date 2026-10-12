package br.com.sigsan.commercial.registry.repository;

import br.com.sigsan.commercial.registry.domain.CommercialSector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CommercialSectorRepository extends JpaRepository<CommercialSector, UUID> {}
