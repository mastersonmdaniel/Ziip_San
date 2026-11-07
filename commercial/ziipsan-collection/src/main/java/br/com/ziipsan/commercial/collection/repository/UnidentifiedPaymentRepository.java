package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.UnidentifiedPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UnidentifiedPaymentRepository extends JpaRepository<UnidentifiedPaymentEntity, UUID> {}
