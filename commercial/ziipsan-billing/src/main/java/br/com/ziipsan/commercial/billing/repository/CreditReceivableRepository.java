package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CreditReceivableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Creditos a realizar (a devolucao ao saldo do cancelamento escreve aqui). */
@Repository
public interface CreditReceivableRepository extends JpaRepository<CreditReceivableEntity, UUID> {

    Optional<CreditReceivableEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
