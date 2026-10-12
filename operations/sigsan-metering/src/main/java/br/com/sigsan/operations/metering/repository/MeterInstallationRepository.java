package br.com.sigsan.operations.metering.repository;

import br.com.sigsan.operations.metering.domain.MeterInstallation;
import br.com.sigsan.operations.metering.rules.MeasurementKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterInstallationRepository extends JpaRepository<MeterInstallation, UUID> {

    /** Historico da unidade e do tipo, do mais recente para o mais antigo. */
    List<MeterInstallation> findByUnitIdAndKindOrderByInstalledOnDesc(UUID unitId, MeasurementKind kind);

    /** Instalacao vigente (sem retirada) da unidade e do tipo. */
    Optional<MeterInstallation> findByUnitIdAndKindAndRemovedOnIsNull(UUID unitId, MeasurementKind kind);
}
