package br.com.sigsan.commercial.collection.repository;

import br.com.sigsan.commercial.collection.domain.BankReturnFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BankReturnFileRepository extends JpaRepository<BankReturnFile, UUID> {}
