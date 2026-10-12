package br.com.sigsan.commercial.registry.repository;

import br.com.sigsan.commercial.registry.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {}
