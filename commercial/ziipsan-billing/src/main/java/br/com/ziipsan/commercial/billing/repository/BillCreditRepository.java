package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BillCreditRepository extends JpaRepository<BillCreditEntity, UUID> {}
