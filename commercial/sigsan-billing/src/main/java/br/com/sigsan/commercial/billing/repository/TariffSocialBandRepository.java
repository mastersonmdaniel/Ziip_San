package br.com.sigsan.commercial.billing.repository;

import br.com.sigsan.commercial.billing.domain.TariffSocialBandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TariffSocialBandRepository extends JpaRepository<TariffSocialBandEntity, UUID> {}
