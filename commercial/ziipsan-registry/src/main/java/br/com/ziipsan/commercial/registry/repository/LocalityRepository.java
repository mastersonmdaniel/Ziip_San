package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.Locality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocalityRepository extends JpaRepository<Locality, UUID> {}
