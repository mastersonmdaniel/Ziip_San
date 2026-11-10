package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.ChargeVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargeVersionRepository extends JpaRepository<ChargeVersionEntity, UUID> {

    /** Versoes de uma definicao do tenant, para a montagem do catalogo em memoria. */
    List<ChargeVersionEntity> findByTenantIdAndChargeDefinitionId(UUID tenantId, UUID chargeDefinitionId);

    /** Uma versao pela chave de negocio: (tenant, definicao, numero da versao). */
    Optional<ChargeVersionEntity> findByTenantIdAndChargeDefinitionIdAndVersionNumber(
            UUID tenantId, UUID chargeDefinitionId, int versionNumber);
}
