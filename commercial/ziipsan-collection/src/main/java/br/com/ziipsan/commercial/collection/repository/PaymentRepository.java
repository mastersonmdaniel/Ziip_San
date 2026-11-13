package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.CanonicalPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repositório do modelo canónico de {@code payments} (decisão 75, PD-1).
 * Antes: {@code JpaRepository<PaymentEntity, UUID>} — o modelo P3.1, retirado com
 * a entidade FEBRABAN ({@code Payment}) na convergência V112.
 */
@Repository
public interface PaymentRepository extends JpaRepository<CanonicalPayment, UUID> {}
