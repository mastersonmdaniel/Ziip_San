package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.sac.AssignmentChange;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Redistribuicao de OS entre pessoas (ED-36) PELA REGRA DA ED-35: delega a
 * {@link AssignmentChange} (core/sac, ED-35) com o alvo SERVICE_ORDER —
 * motivo obrigatorio, mesma pessoa recusada e registro completo na trilha
 * sao daquela regra e nao sao reimplementados aqui.
 */
public final class OrderRedistribution {

    private OrderRedistribution() {}

    public static AssignmentChange.TrailEntry redistributeOrder(UUID orderId, UUID fromPersonId, UUID toPersonId,
                                                                String reason, UUID changedBy, LocalDateTime when) {
        return AssignmentChange.redistribute(AssignmentChange.TargetType.SERVICE_ORDER,
                orderId, fromPersonId, toPersonId, reason, changedBy, when);
    }
}
