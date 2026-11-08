package br.com.ziipsan.platform.accesscontrol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Loja em memória da ED-06 para os testes de serviço (sem banco, sem Keycloak). */
final class TestStore implements AccessControlPorts.AccessControlStore {

    final List<CompositeProfile> profiles = new ArrayList<>();
    final Map<UUID, List<CompositeProfile>> assignments = new HashMap<>();
    final Map<UUID, Set<String>> factoryProfiles = new HashMap<>();
    final Map<UUID, EffectivePermissions.NegativeRestriction> restrictions = new HashMap<>();
    final List<EffectivePermissions.Delegation> delegations = new ArrayList<>();
    final Map<UUID, LoginGuard.AttemptState> attempts = new HashMap<>();
    final Map<UUID, LoginGuard.ActiveSession> sessions = new HashMap<>();
    final List<NistAcceptance> acceptances = new ArrayList<>();
    final Map<UUID, RecertificationService.Campaign> campaigns = new HashMap<>();
    final List<RecertificationService.Decision> decisions = new ArrayList<>();
    TenantSecurityPolicy policy = TenantSecurityPolicy.factoryDefault();
    TenantSsoConfig sso = TenantSsoConfig.localOnly();

    @Override public List<CompositeProfile> listCompositeProfiles() { return List.copyOf(profiles); }
    @Override public Optional<CompositeProfile> findCompositeProfile(UUID id) {
        return profiles.stream().filter(p -> p.id().equals(id)).findFirst();
    }
    @Override public void insertCompositeProfile(CompositeProfile p) { profiles.add(p); }
    @Override public void assignCompositeProfile(UUID userId, UUID profileId, UUID by, Instant at) {
        findCompositeProfile(profileId).ifPresent(p ->
                assignments.computeIfAbsent(userId, k -> new ArrayList<>()).add(p));
    }
    @Override public List<CompositeProfile> compositeProfilesOfUser(UUID userId) {
        return assignments.getOrDefault(userId, List.of());
    }
    @Override public Set<String> factoryProfilesOfUser(UUID userId) {
        return factoryProfiles.getOrDefault(userId, Set.of());
    }
    @Override public Optional<EffectivePermissions.NegativeRestriction> restrictionOfUser(UUID userId) {
        return Optional.ofNullable(restrictions.get(userId));
    }
    @Override public void upsertRestriction(EffectivePermissions.NegativeRestriction r) {
        restrictions.put(r.userId(), r);
    }
    @Override public List<EffectivePermissions.Delegation> delegationsOfDelegate(UUID delegateId) {
        return delegations.stream().filter(d -> d.delegateId().equals(delegateId)).toList();
    }
    @Override public void insertDelegation(EffectivePermissions.Delegation d) { delegations.add(d); }
    @Override public void revokeDelegation(UUID id, Instant at) {
        for (int i = 0; i < delegations.size(); i++) {
            EffectivePermissions.Delegation d = delegations.get(i);
            if (d.id().equals(id)) {
                delegations.set(i, new EffectivePermissions.Delegation(d.id(), d.tenantId(),
                        d.delegatorId(), d.delegateId(), d.permissions(), d.validFrom(), d.validTo(), at));
            }
        }
    }
    @Override public TenantSecurityPolicy securityPolicy() { return policy; }
    @Override public List<NistAcceptance> nistAcceptances() { return List.copyOf(acceptances); }
    @Override public void insertNistAcceptance(NistAcceptance a) { acceptances.add(a); }
    @Override public TenantSsoConfig ssoConfig() { return sso; }
    @Override public LoginGuard.AttemptState attemptStateOf(UUID userId) {
        return attempts.getOrDefault(userId, LoginGuard.AttemptState.NONE);
    }
    @Override public void saveAttemptState(UUID userId, LoginGuard.AttemptState s) { attempts.put(userId, s); }
    @Override public Optional<LoginGuard.ActiveSession> activeSessionOf(UUID userId) {
        return Optional.ofNullable(sessions.get(userId));
    }
    @Override public void openSession(LoginGuard.ActiveSession s) { sessions.put(s.userId(), s); }
    @Override public void removeSession(String sessionId) {
        sessions.values().removeIf(s -> s.sessionId().equals(sessionId));
    }
    @Override public List<LoginGuard.ActiveSession> activeSessions() {
        return List.copyOf(sessions.values());
    }
    @Override public void insertRecertificationCampaign(RecertificationService.Campaign c) {
        campaigns.put(c.id(), c);
    }
    @Override public Optional<RecertificationService.Campaign> findCampaign(UUID id) {
        return Optional.ofNullable(campaigns.get(id));
    }
    @Override public void insertRecertificationDecision(RecertificationService.Decision d) { decisions.add(d); }
    @Override public List<RecertificationService.Decision> decisionsOfCampaign(UUID campaignId) {
        return decisions.stream().filter(d -> d.campaignId().equals(campaignId)).toList();
    }

    static Set<String> linked(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}
