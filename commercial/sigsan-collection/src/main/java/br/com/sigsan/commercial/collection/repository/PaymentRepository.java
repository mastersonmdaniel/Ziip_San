package br.com.sigsan.commercial.collection.repository;

import br.com.sigsan.commercial.collection.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {}
