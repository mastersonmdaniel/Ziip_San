package br.com.sigsan.commercial.registry.repository;

import br.com.sigsan.commercial.registry.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BlockRepository extends JpaRepository<Block, UUID> {}
