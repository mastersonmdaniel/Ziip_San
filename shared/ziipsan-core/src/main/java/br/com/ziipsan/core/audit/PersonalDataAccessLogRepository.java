package br.com.ziipsan.core.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PersonalDataAccessLogRepository extends JpaRepository<PersonalDataAccessLog, UUID> {

    /**
     * Buscas por documento concluídas (FOUND ou NOT_FOUND) do usuário na janela: é o consumo
     * da cota da P0.11. Tentativas negadas (DENIED) e estouros (QUOTA_EXCEEDED) não consomem
     * cota — senão um atacante sem permissão esgotaria a cota de outro usuário.
     *
     * <p>{@code user_id} nulo (token sem subject UUID) forma o próprio balde do tenant.
     */
    @Query("SELECT count(l) FROM PersonalDataAccessLog l WHERE l.tenantId = :tenantId"
            + " AND ((:userId IS NULL AND l.userId IS NULL) OR l.userId = :userId)"
            + " AND l.accessType = 'DOCUMENT_SEARCH' AND l.result IN ('FOUND', 'NOT_FOUND')"
            + " AND l.accessedAt >= :since")
    long countDocumentSearchesInWindow(@Param("tenantId") UUID tenantId,
                                       @Param("userId") UUID userId,
                                       @Param("since") Instant since);

    /**
     * Gancho para a detecção de anomalia da P3.10 (ADR-0008, RNF-41): estouros de cota
     * recentes, por tenant, sem critério próprio de anomalia nesta tarefa.
     */
    @Query("SELECT count(l) FROM PersonalDataAccessLog l WHERE l.tenantId = :tenantId"
            + " AND l.result = 'QUOTA_EXCEEDED' AND l.accessedAt >= :since")
    long countQuotaExceeded(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
