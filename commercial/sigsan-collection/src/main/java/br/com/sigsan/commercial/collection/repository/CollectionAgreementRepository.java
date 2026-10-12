package br.com.sigsan.commercial.collection.repository;

import br.com.sigsan.commercial.collection.domain.CollectionAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CollectionAgreementRepository extends JpaRepository<CollectionAgreement, UUID> {}
