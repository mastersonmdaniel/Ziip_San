package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.console.PlatformConsoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Rotas do console da plataforma v0 (P1.6).
 *
 * <p>Antes de chegar aqui, a requisição já passou pelo gate de MFA (403
 * {@code mfa-required} sem segundo fator) e pelo {@code PlatformAccessFilter}
 * (403 para token com tenant ou sem papel {@code PLATFORM_*}). As permissões
 * finas são as de {@code PlatformRole} (console:read/manage/suspend/delete);
 * a matriz fina (FINANCE só por inadimplência, eliminação só OWNER) vive no
 * serviço e é provada nos testes, não só na permissão.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants/{tenantId}/console")
public class PlatformConsoleController {

    public record ProfileInput(String legalName, String tradeName, String cnpj, String legalNature,
                               String addressStreet, String addressNumber, String addressDistrict,
                               String addressCity, String addressState, String addressPostalCode,
                               String municipalityIbge, String stateRegistration, String taxRegime,
                               String dpoName, String dpoPublicContact) {
    }

    public record PlanInput(String code, String name, String quotas) {
    }

    public record SubscriptionInput(UUID planId) {
    }

    public record FlagInput(String flagKey, String description, boolean defaultEnabled) {
    }

    public record TenantFlagInput(String flagKey, boolean enabled) {
    }

    public record SuspensionNoticeInput(String reason, String justification, boolean stepUpConfirmed,
                                        OffsetDateTime effectiveAt) {
    }

    public record DeletionInput(String justification) {
    }

    public record DeletionHoldInput(String holdReason, String reference) {
    }

    public record Seeded(int inserted) {
    }

    public record FlagValue(boolean enabled) {
    }

    public record ReadOnly(boolean readOnly) {
    }

    private final PlatformConsoleService console;

    public PlatformConsoleController(PlatformConsoleService console) {
        this.console = console;
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String platformRole(JwtAuthenticationToken auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.startsWith("ROLE_PLATFORM_")) {
                return name.substring("ROLE_".length());
            }
        }
        return null;
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('console:read')")
    public PlatformConsoleService.TenantProfile profile(@PathVariable UUID tenantId) {
        return console.profile(tenantId);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('console:manage')")
    public PlatformConsoleService.TenantProfile profile(@PathVariable UUID tenantId,
                                                        @RequestBody ProfileInput in,
                                                        JwtAuthenticationToken auth) {
        return console.upsertProfile(tenantId, new PlatformConsoleService.TenantProfileInput(
                in.legalName(), in.tradeName(), in.cnpj(), in.legalNature(), in.addressStreet(),
                in.addressNumber(), in.addressDistrict(), in.addressCity(), in.addressState(),
                in.addressPostalCode(), in.municipalityIbge(), in.stateRegistration(), in.taxRegime(),
                in.dpoName(), in.dpoPublicContact()), actorId(auth), platformRole(auth));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('console:manage')")
    public PlatformConsoleService.PlanView plan(@RequestBody PlanInput in, JwtAuthenticationToken auth) {
        return console.createPlan(in.code(), in.name(), in.quotas(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('console:manage')")
    public PlatformConsoleService.SubscriptionView subscribe(@PathVariable UUID tenantId,
                                                             @RequestBody SubscriptionInput in,
                                                             JwtAuthenticationToken auth) {
        return console.subscribe(tenantId, in.planId(), actorId(auth), platformRole(auth));
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAuthority('console:read')")
    public PlatformConsoleService.SubscriptionView subscription(@PathVariable UUID tenantId) {
        return console.subscription(tenantId);
    }

    @PostMapping("/flags")
    @PreAuthorize("hasAuthority('console:manage')")
    public void flag(@RequestBody FlagInput in, JwtAuthenticationToken auth) {
        console.createFlag(in.flagKey(), in.description(), in.defaultEnabled(),
                actorId(auth), platformRole(auth));
    }

    @PostMapping("/flags/tenant")
    @PreAuthorize("hasAuthority('console:manage')")
    public void tenantFlag(@PathVariable UUID tenantId, @RequestBody TenantFlagInput in,
                           JwtAuthenticationToken auth) {
        console.setTenantFlag(tenantId, in.flagKey(), in.enabled(), actorId(auth), platformRole(auth));
    }

    @GetMapping("/flags/tenant")
    @PreAuthorize("hasAuthority('console:read')")
    public FlagValue tenantFlag(@PathVariable UUID tenantId, @RequestParam String flagKey) {
        return new FlagValue(console.tenantFlag(tenantId, flagKey));
    }

    @PostMapping("/onboarding/seed")
    @PreAuthorize("hasAuthority('console:manage')")
    public Seeded seedOnboarding(@PathVariable UUID tenantId, JwtAuthenticationToken auth) {
        return new Seeded(console.seedOnboarding(tenantId, actorId(auth), platformRole(auth)));
    }

    @GetMapping("/onboarding")
    @PreAuthorize("hasAuthority('console:read')")
    public PlatformConsoleService.OnboardingPanel onboarding(@PathVariable UUID tenantId) {
        return console.onboardingPanel(tenantId);
    }

    @PostMapping("/onboarding/steps/{stepKey}/complete")
    @PreAuthorize("hasAuthority('console:manage')")
    public void completeStep(@PathVariable UUID tenantId, @PathVariable String stepKey,
                             JwtAuthenticationToken auth) {
        console.completeStep(tenantId, stepKey, actorId(auth), platformRole(auth));
    }

    @PostMapping("/suspension-notice")
    @PreAuthorize("hasAuthority('console:suspend')")
    public PlatformConsoleService.LifecycleView suspensionNotice(@PathVariable UUID tenantId,
                                                                 @RequestBody SuspensionNoticeInput in,
                                                                 JwtAuthenticationToken auth) {
        return console.requestSuspensionNotice(tenantId, platformRole(auth), actorId(auth),
                in.reason(), in.justification(), in.stepUpConfirmed(), in.effectiveAt());
    }

    @PostMapping("/suspension-activate")
    @PreAuthorize("hasAuthority('console:suspend')")
    public PlatformConsoleService.LifecycleView suspensionActivate(@PathVariable UUID tenantId,
                                                                   JwtAuthenticationToken auth) {
        return console.activateSuspension(tenantId, platformRole(auth), actorId(auth));
    }

    @GetMapping("/backoffice-read-only")
    @PreAuthorize("hasAuthority('console:read')")
    public ReadOnly backofficeReadOnly(@PathVariable UUID tenantId) {
        return new ReadOnly(console.backofficeReadOnly(tenantId));
    }

    @GetMapping("/lifecycle")
    @PreAuthorize("hasAuthority('console:read')")
    public PlatformConsoleService.LifecycleView lifecycle(@PathVariable UUID tenantId) {
        return console.lifecycle(tenantId);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('console:delete')")
    public PlatformConsoleService.ExportView export(@PathVariable UUID tenantId, JwtAuthenticationToken auth) {
        return console.exportTenant(tenantId, actorId(auth), platformRole(auth));
    }

    @PostMapping("/deletion-request")
    @PreAuthorize("hasAuthority('console:delete')")
    public PlatformConsoleService.LifecycleView deletionRequest(@PathVariable UUID tenantId,
                                                                @RequestBody DeletionInput in,
                                                                JwtAuthenticationToken auth) {
        return console.requestDeletion(tenantId, platformRole(auth), actorId(auth), in.justification());
    }

    @PostMapping("/deletion-hold")
    @PreAuthorize("hasAuthority('console:manage')")
    public PlatformConsoleService.LifecycleView deletionHold(@PathVariable UUID tenantId,
                                                             @RequestBody DeletionHoldInput in,
                                                             JwtAuthenticationToken auth) {
        return console.registerDeletionHold(tenantId, platformRole(auth), actorId(auth),
                in.holdReason(), in.reference());
    }

    @PostMapping("/deletion-release")
    @PreAuthorize("hasAuthority('console:manage')")
    public PlatformConsoleService.LifecycleView deletionRelease(@PathVariable UUID tenantId,
                                                                JwtAuthenticationToken auth) {
        return console.releaseDeletionHold(tenantId, platformRole(auth), actorId(auth));
    }

    @PostMapping("/deletion-complete")
    @PreAuthorize("hasAuthority('console:delete')")
    public PlatformConsoleService.LifecycleView deletionComplete(@PathVariable UUID tenantId,
                                                                 JwtAuthenticationToken auth) {
        return console.completeDeletion(tenantId, platformRole(auth), actorId(auth));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('console:read')")
    public List<PlatformConsoleService.AuditView> audit(@PathVariable UUID tenantId,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return console.auditEvents(tenantId, limit);
    }
}
