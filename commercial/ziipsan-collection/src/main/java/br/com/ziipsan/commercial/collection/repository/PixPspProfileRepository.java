package br.com.ziipsan.commercial.collection.repository;

import br.com.ziipsan.commercial.collection.domain.PixPspProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PixPspProfileRepository extends JpaRepository<PixPspProfileEntity, UUID> {}
