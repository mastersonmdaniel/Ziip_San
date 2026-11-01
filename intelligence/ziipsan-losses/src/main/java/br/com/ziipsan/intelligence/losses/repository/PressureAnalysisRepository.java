package br.com.ziipsan.intelligence.losses.repository;

import br.com.ziipsan.intelligence.losses.domain.PressureAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PressureAnalysisRepository extends JpaRepository<PressureAnalysis, UUID> {
}