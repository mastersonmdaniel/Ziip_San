package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.DebitTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebitTypeRepository extends JpaRepository<DebitTypeEntity, UUID> {

    Optional<DebitTypeEntity> findByCode(String code);
}
