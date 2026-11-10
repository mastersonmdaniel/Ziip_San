package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.ChargeDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargeDefinitionRepository extends JpaRepository<ChargeDefinitionEntity, UUID> {

    /**
     * Definicoes pela chave de negocio do catalogo: (tenant, codigo da
     * cobranca). Desde o item 45 o codigo NAO e unico por tenant — e
     * unico por (tenant, escopo, codigo) —, logo a busca por codigo
     * devolve a lista das definicoes do codigo em todos os escopos
     * (empresa e municipios).
     */
    List<ChargeDefinitionEntity> findByTenantIdAndChargeCode(UUID tenantId, String chargeCode);

    /** Definicao por (tenant, escopo municipal, codigo); escopo nulo = escopo "empresa". */
    Optional<ChargeDefinitionEntity> findByTenantIdAndMunicipalityIbgeAndChargeCode(
            UUID tenantId, String municipalityIbge, String chargeCode);

    /** Todas as definicoes do tenant, para a montagem do catalogo em memoria. */
    List<ChargeDefinitionEntity> findByTenantId(UUID tenantId);
}
