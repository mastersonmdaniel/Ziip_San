package br.com.ziipsan.core.serviceorder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Competency registry contract (P3.31 / ED-59).
 *
 * <p>The plan states the qualification check runs "against the competency
 * registry, through a bound contract, when the registry exists". Until
 * that registry exists, the engine checks the qualification declared on
 * the crew row (code + validity date) and refuses a missing or expired
 * one. When a module binds a registry here, the engine consults it in
 * addition: a person the registry says is not qualified (or whose
 * qualification expired there) blocks the schedule and the close even if
 * the crew row claims otherwise. The registry never loosens the row
 * check: a row without a validity date still blocks.
 */
public interface CompetencyRegistry {

    /** Result of a registry lookup. */
    record Competency(UUID userId, String qualificationCode, LocalDate validUntil, boolean qualified) {
    }

    /**
     * Looks a person's qualification up, or null when the registry knows
     * nothing about this person/qualification. A null answer does not
     * approve: the crew-row check still applies.
     */
    Competency lookup(UUID userId, String qualificationCode);
}
