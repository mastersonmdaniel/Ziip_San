package br.com.ziipsan.platform.accesscontrol.web;

import br.com.ziipsan.platform.accesscontrol.AccessControlService;
import br.com.ziipsan.platform.accesscontrol.AuditTrailService;
import br.com.ziipsan.platform.accesscontrol.CompositeProfile;
import br.com.ziipsan.platform.accesscontrol.EffectivePermissions;
import br.com.ziipsan.platform.accesscontrol.LoginGuard;
import br.com.ziipsan.platform.accesscontrol.NistAcceptance;
import br.com.ziipsan.platform.accesscontrol.RecertificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API do controle de acesso de edital (ED-06). Toda rota declara a permissão
 * do catálogo da P0.13 (o RoutePermissionTest recusa rota sem declaração):
 * gestão de perfis compostos/restrição/delegação e aceites NIST exigem
 * {@code access:manage-profiles}; recertificação, {@code access:recertify};
 * trilha e sessões, {@code access:audit-read} (revogar sessão é escrita
 * administrativa: {@code access:manage-profiles}). O ator é sempre o
 * {@code sub} do token — nunca um id do corpo.
 */
@RestController
@RequestMapping("/api/v1")
public class AccessControlController {

    public record CreateProfileRequest(String code, String name, Set<String> factoryProfileCodes,
                                       Set<String> directPermissions) {
    }

    public record AssignProfileRequest(UUID userId) {
    }

    public record RestrictRequest(UUID userId, Set<String> deniedPermissions) {
    }

    public record DelegateRequest(UUID delegateId, Set<String> permissions,
                                  Instant validFrom, Instant validTo) {
    }

    public record AcceptNistRequest(String optionKey) {
    }

    public record OpenCampaignRequest(String name, Instant dueAt) {
    }

    public record DecideRequest(UUID subjectUserId, RecertificationService.DecisionKind kind, String note) {
    }

    private final AccessControlService service;
    private final RecertificationService recertification;
    private final AuditTrailService trail;

    public AccessControlController(AccessControlService service, RecertificationService recertification,
                                   AuditTrailService trail) {
        this.service = service;
        this.recertification = recertification;
        this.trail = trail;
    }

    @GetMapping("/access-profiles")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public List<CompositeProfile> listProfiles() {
        return service.listCompositeProfiles();
    }

    @PostMapping("/access-profiles")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public CompositeProfile createProfile(@AuthenticationPrincipal(expression = "subject") String subject,
                                          @RequestBody CreateProfileRequest request) {
        return service.createCompositeProfile(UUID.fromString(subject), request.code(), request.name(),
                request.factoryProfileCodes(), request.directPermissions());
    }

    @PostMapping("/access-profiles/{id}/assign")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public void assignProfile(@AuthenticationPrincipal(expression = "subject") String subject,
                              @PathVariable UUID id, @RequestBody AssignProfileRequest request) {
        service.assignCompositeProfile(UUID.fromString(subject), request.userId(), id);
    }

    @GetMapping("/users/{userId}/effective-permissions")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public Set<String> effectivePermissions(@PathVariable UUID userId) {
        return service.effectivePermissionsOf(userId);
    }

    @PostMapping("/access-restrictions")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public void restrict(@AuthenticationPrincipal(expression = "subject") String subject,
                         @RequestBody RestrictRequest request) {
        service.restrictUser(UUID.fromString(subject), request.userId(), request.deniedPermissions());
    }

    @PostMapping("/access-delegations")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public EffectivePermissions.Delegation delegate(
            @AuthenticationPrincipal(expression = "subject") String subject,
            @RequestBody DelegateRequest request) {
        return service.delegate(UUID.fromString(subject), request.delegateId(), request.permissions(),
                request.validFrom(), request.validTo());
    }

    @DeleteMapping("/access-delegations/{id}")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public void revokeDelegation(@PathVariable UUID id) {
        service.revokeDelegation(id);
    }

    @PostMapping("/access-policy/nist-acceptances")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public NistAcceptance acceptNist(@AuthenticationPrincipal(expression = "subject") String subject,
                                     @RequestBody AcceptNistRequest request) {
        return service.acceptNistContraryOption(UUID.fromString(subject), request.optionKey());
    }

    @PostMapping("/access-recertifications")
    @PreAuthorize("hasAuthority('access:recertify')")
    public RecertificationService.Campaign openCampaign(
            @AuthenticationPrincipal(expression = "subject") String subject,
            @RequestBody OpenCampaignRequest request) {
        return recertification.openCampaign(UUID.fromString(subject), request.name(), request.dueAt());
    }

    @PostMapping("/access-recertifications/{id}/decisions")
    @PreAuthorize("hasAuthority('access:recertify')")
    public RecertificationService.Decision decide(
            @AuthenticationPrincipal(expression = "subject") String subject,
            @PathVariable UUID id, @RequestBody DecideRequest request) {
        return recertification.decide(UUID.fromString(subject), id, request.subjectUserId(),
                request.kind(), request.note());
    }

    @GetMapping("/users/{userId}/audit-trail")
    @PreAuthorize("hasAuthority('access:audit-read')")
    public List<AuditTrailService.AuditEventView> trailOfUser(@PathVariable UUID userId,
                                                              @RequestParam Instant from,
                                                              @RequestParam Instant to) {
        return trail.trailOfUser(userId, from, to);
    }

    @GetMapping("/users/{userId}/audit-trail/export")
    @PreAuthorize("hasAuthority('access:audit-read')")
    public AuditTrailService.TrailExport exportTrail(@PathVariable UUID userId,
                                                     @RequestParam Instant from, @RequestParam Instant to) {
        return trail.exportTrailOfUser(userId, from, to);
    }

    @GetMapping("/access-sessions")
    @PreAuthorize("hasAuthority('access:audit-read')")
    public List<LoginGuard.ActiveSession> sessions() {
        return trail.activeSessions();
    }

    @DeleteMapping("/access-sessions/{sessionId}")
    @PreAuthorize("hasAuthority('access:manage-profiles')")
    public void revokeSession(@PathVariable String sessionId) {
        trail.revokeSession(sessionId);
    }
}
