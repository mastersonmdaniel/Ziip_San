package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.DataSubjectRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {

    Optional<DataSubjectRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    // P1.15: registros excluídos (V2_11) não aparecem nas leituras.
    List<DataSubjectRequest> findByTenantIdAndDeadlineAtBeforeAndStatusNotInAndDeletedAtIsNull(
            UUID tenantId,
            OffsetDateTime deadlineAt,
            List<DataSubjectRequest.RequestStatus> status);
}
