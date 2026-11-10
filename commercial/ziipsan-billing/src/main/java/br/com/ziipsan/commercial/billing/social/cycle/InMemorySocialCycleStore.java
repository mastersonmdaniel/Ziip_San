package br.com.ziipsan.commercial.billing.social.cycle;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * II-3: implementacao em memoria da loja do ciclo - a prova executavel
 * do contrato de minimizacao de {@link SocialCycleStore} (precedente do
 * pacote: a loja em memoria da ED-103). Sem banco, sem dependencias.
 */
public final class InMemorySocialCycleStore implements SocialCycleStore {

    private final Map<String, FamilyCrossingSummary> summariesByFamily = new LinkedHashMap<>();
    private final Map<UUID, SocialGrantLifecycle.Grant> grantsById = new LinkedHashMap<>();
    private List<SocialActiveSearch.ActiveSearchEntry> activeSearch = List.of();

    @Override
    public void saveCrossing(SocialCrossingEngine.CrossingResult crossing) {
        for (SocialCrossingEngine.CrossedFamily family : crossing.matched()) {
            summariesByFamily.put(family.familyCodeHash(), new FamilyCrossingSummary(
                    family.familyCodeHash(), family.basis().name(), FamilyCrossingSummary.Outcome.MATCHED,
                    family.unitId(), family.municipalityCode(), family.routeCode(), family.personCount()));
        }
        for (SocialCrossingEngine.UnmatchedFamily family : crossing.unmatched()) {
            summariesByFamily.put(family.familyCodeHash(), new FamilyCrossingSummary(
                    family.familyCodeHash(), family.basis().name(), FamilyCrossingSummary.Outcome.UNMATCHED,
                    null, family.municipalityCode(), null, family.personCount()));
        }
        for (SocialCrossingEngine.IneligibleFamily family : crossing.ineligible()) {
            summariesByFamily.putIfAbsent(family.familyCodeHash(), new FamilyCrossingSummary(
                    family.familyCodeHash(), family.basis().name(), FamilyCrossingSummary.Outcome.INELIGIBLE,
                    null, null, null, family.personCount()));
        }
    }

    @Override
    public List<FamilyCrossingSummary> crossingSummaries() {
        return List.copyOf(summariesByFamily.values());
    }

    @Override
    public void saveGrant(SocialGrantLifecycle.Grant grant) {
        grantsById.put(grant.grantId(), grant);
    }

    @Override
    public Optional<SocialGrantLifecycle.Grant> findGrant(UUID grantId) {
        return Optional.ofNullable(grantsById.get(grantId));
    }

    @Override
    public Set<String> enrolledFamilyHashes() {
        Set<String> enrolled = new LinkedHashSet<>();
        for (SocialGrantLifecycle.Grant grant : grantsById.values()) {
            if (grant.status() != SocialGrantLifecycle.GrantStatus.ENDED) {
                enrolled.add(grant.familyCodeHash());
            }
        }
        return Set.copyOf(enrolled);
    }

    @Override
    public void saveActiveSearch(List<SocialActiveSearch.ActiveSearchEntry> entries) {
        activeSearch = List.copyOf(entries);
    }

    @Override
    public List<SocialActiveSearch.ActiveSearchEntry> activeSearch() {
        return activeSearch;
    }

    @Override
    public List<SocialGrantLifecycle.TrailEntry> trailOf(UUID grantId) {
        SocialGrantLifecycle.Grant grant = grantsById.get(grantId);
        return grant == null ? List.of() : grant.trail();
    }
}
