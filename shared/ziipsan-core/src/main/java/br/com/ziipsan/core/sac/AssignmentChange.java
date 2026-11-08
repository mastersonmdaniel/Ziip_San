package br.com.ziipsan.core.sac;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Atribuicao e redistribuicao de protocolos e de OS entre pessoas (ED-35,
 * alem do tramite entre unidades da P3.30): MOTIVO OBRIGATORIO e registro
 * na trilha \u2014 quem mudou, de quem para quem, quando e por que. Mudanca
 * para a mesma pessoa e recusada (nao e redistribuicao). Alvo e pessoas
 * sao {@link UUID} (sac_assignment_trail: target_id/from_person_id/
 * to_person_id/changed_by UUID \u2014 correcao pos-reprovacao, M4).
 */
public final class AssignmentChange {

    public enum TargetType { PROTOCOL, SERVICE_ORDER }

    public record TrailEntry(TargetType targetType, UUID targetId, UUID fromPersonId,
                             UUID toPersonId, String reason, UUID changedBy,
                             LocalDateTime changedAt) {}

    private AssignmentChange() {}

    public static TrailEntry redistribute(TargetType type, UUID targetId, UUID fromPersonId,
                                          UUID toPersonId, String reason, UUID changedBy,
                                          LocalDateTime when) {
        Objects.requireNonNull(type, "tipo do alvo obrigatorio");
        Objects.requireNonNull(targetId, "alvo obrigatorio");
        Objects.requireNonNull(fromPersonId, "responsavel atual obrigatorio");
        Objects.requireNonNull(toPersonId, "novo responsavel obrigatorio");
        Objects.requireNonNull(changedBy, "autor da mudanca obrigatorio");
        Objects.requireNonNull(when, "data/hora obrigatoria");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("redistribuicao exige motivo (trilha obrigatoria)");
        if (fromPersonId.equals(toPersonId))
            throw new IllegalArgumentException("redistribuicao para a mesma pessoa nao e redistribuicao");
        return new TrailEntry(type, targetId, fromPersonId, toPersonId, reason.trim(), changedBy, when);
    }
}
