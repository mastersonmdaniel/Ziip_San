package br.com.ziipsan.platform.engagement;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Portas de persistencia da ED-101 (padrao das frentes puras do projeto:
 * registros + portas; a persistencia JDBC/JPA sobre o SQL pendente desta
 * entrega e fiacao da integracao — ver o doc de integracao).
 */
public final class EngagementStores {

    private EngagementStores() {
    }

    /** Adesoes voluntarias por (tenant, cliente, unidade). */
    public interface EnrollmentStore {
        Optional<UserEnrollment> find(UUID tenantId, UUID customerId, UUID unitId);

        void save(UserEnrollment enrollment);
    }

    /** Metas de consumo por (tenant, unidade, periodo). */
    public interface GoalStore {
        Optional<ConsumptionGoal> find(UUID tenantId, UUID unitId, YearMonth period);

        void save(ConsumptionGoal goal);
    }
}
