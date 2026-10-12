package br.com.sigsan.commercial.collection.repository;

import br.com.sigsan.commercial.collection.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {}
