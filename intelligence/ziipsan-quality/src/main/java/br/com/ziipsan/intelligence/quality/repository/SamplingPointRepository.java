package br.com.ziipsan.intelligence.quality.repository;

import br.com.ziipsan.intelligence.quality.domain.SamplingPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SamplingPointRepository extends JpaRepository<SamplingPoint, UUID> {
}