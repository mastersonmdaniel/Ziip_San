package br.com.sigsan.intelligence.quality.scada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ScadaDataPointRepository extends JpaRepository<ScadaDataPoint, UUID> {
}