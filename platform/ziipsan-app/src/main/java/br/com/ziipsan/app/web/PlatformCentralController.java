package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.central.CentralCollectorService;
import br.com.ziipsan.platform.central.CentralService;
import br.com.ziipsan.platform.contract.SaaSContractService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Abas da Central Ziip v0 (P1.17), sobre o console v0 (P1.6).
 *
 * <p>Antes de chegar aqui, a requisicao ja passou pelo gate de MFA (403
 * {@code mfa-required} sem segundo fator) e pelo {@code PlatformAccessFilter}
 * (403 para token com tenant ou sem papel {@code PLATFORM_*}). Leitura:
 * {@code central:read} (os quatro papeis); operacao (coleta manual,
 * reconhecer alerta, soneca, regra): {@code central:manage} (OWNER e OPS).
 * As rotas do console ({@code /tenants/{id}/console}) nao mudam: a Central
 * le o mesmo `platform.*`, nao reconstroi o console.
 */
@RestController
@RequestMapping("/api/v1/platform/central")
public class PlatformCentralController {

    public record SnoozeInput(@NotNull OffsetDateTime until) {
    }

    public record RuleInput(@NotBlank String ruleKey, @NotBlank String metricKey, @NotBlank String comparator,
                            double threshold, int consecutiveReadings, @NotBlank String severity,
                            @NotBlank String scope, @NotBlank String owner) {
    }

    private final CentralService central;
    private final CentralCollectorService collector;
    private final SaaSContractService contracts;

    public PlatformCentralController(CentralService central, CentralCollectorService collector,
                                     SaaSContractService contracts) {
        this.central = central;
        this.collector = collector;
        this.contracts = contracts;
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

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('central:read')")
    public CentralService.Overview overview() {
        return central.overview();
    }

    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('central:read')")
    public CentralService.PlatformTab platform() {
        return central.platformTab();
    }

    @GetMapping("/security")
    @PreAuthorize("hasAuthority('central:read')")
    public CentralService.SecurityTab security() {
        return central.securityTab();
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('central:read')")
    public CentralService.AlertsTab alerts() {
        return central.alertsTab();
    }

    /** Aba Contratos da Central (P2.12, ADR-0019): uma linha por contrato, com margem realizada. */
    @GetMapping("/contracts")
    @PreAuthorize("hasAuthority('central:read')")
    public java.util.List<SaaSContractService.ContractRow> contracts() {
        return contracts.contractsTab();
    }

    @GetMapping("/watchdog")
    @PreAuthorize("hasAuthority('central:read')")
    public CentralCollectorService.WatchdogStatus watchdog() {
        return collector.watchdogStatus();
    }

    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('central:manage')")
    public CentralCollectorService.Report collect() {
        return collector.collect();
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('central:manage')")
    public CentralService.AlertView acknowledge(@PathVariable UUID alertId, JwtAuthenticationToken auth) {
        return central.acknowledgeAlert(alertId, actorId(auth), platformRole(auth));
    }

    @PostMapping("/rules/{ruleKey}/snooze")
    @PreAuthorize("hasAuthority('central:manage')")
    public CentralService.RuleView snooze(@PathVariable String ruleKey, @Valid @RequestBody SnoozeInput in,
                                          JwtAuthenticationToken auth) {
        return central.snoozeRule(ruleKey, in.until(), actorId(auth), platformRole(auth));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('central:read')")
    public java.util.List<CentralService.RuleView> rules() {
        return central.listRules();
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('central:manage')")
    public CentralService.RuleView createRule(@Valid @RequestBody RuleInput in, JwtAuthenticationToken auth) {
        return central.createRule(new CentralService.RuleInput(in.ruleKey(), in.metricKey(), in.comparator(),
                in.threshold(), in.consecutiveReadings(), in.severity(), in.scope(), in.owner()),
                actorId(auth), platformRole(auth));
    }
}
