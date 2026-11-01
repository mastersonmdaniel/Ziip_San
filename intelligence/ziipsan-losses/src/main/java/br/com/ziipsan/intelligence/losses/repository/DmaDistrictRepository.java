package br.com.ziipsan.intelligence.losses.repository;

import br.com.ziipsan.intelligence.losses.domain.DmaDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmaDistrictRepository extends JpaRepository<DmaDistrict, UUID> {

    Optional<DmaDistrict> findByIdAndTenantId(UUID id, UUID tenantId);
}