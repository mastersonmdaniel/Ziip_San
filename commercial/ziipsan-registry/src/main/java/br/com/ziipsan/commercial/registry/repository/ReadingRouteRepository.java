package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ReadingRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReadingRouteRepository extends JpaRepository<ReadingRoute, UUID> {}
