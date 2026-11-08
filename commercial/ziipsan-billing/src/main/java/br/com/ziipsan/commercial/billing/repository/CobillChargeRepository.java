package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CobillChargeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Correcao pos-reprovacao (D6): o identificador do documento so e
 * unico POR TENANT (indice {@code idx_cobill_charges_document
 * (tenant_id, document_identifier)}), e o padrao da vizinha ED-22 para
 * busca por chave unica e a forma composta
 * ({@code findByTenantIdAndCode} em {@code GroupedBillRepository}) -
 * a busca sem tenant foi removida: sem ela, a consulta poderia
 * devolver o documento de outro tenant.
 */
@Repository
public interface CobillChargeRepository extends JpaRepository<CobillChargeEntity, UUID> {

    Optional<CobillChargeEntity> findByTenantIdAndDocumentIdentifier(UUID tenantId, String documentIdentifier);

    List<CobillChargeEntity> findByUnitId(UUID unitId);

    List<CobillChargeEntity> findByContractId(UUID contractId);
}
