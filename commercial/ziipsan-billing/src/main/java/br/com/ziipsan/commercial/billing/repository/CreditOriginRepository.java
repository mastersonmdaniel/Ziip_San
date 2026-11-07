package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CreditOriginEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditOriginRepository extends JpaRepository<CreditOriginEntity, UUID> {

    Optional<CreditOriginEntity> findByCode(String code);
}
