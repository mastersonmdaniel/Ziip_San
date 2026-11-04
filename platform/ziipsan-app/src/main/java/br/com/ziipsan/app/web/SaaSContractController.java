package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.contract.SaaSContractAlerts;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Rotas de cobranca do SaaS por contrato (P2.12, ADR-0019).
 *
 * <p>Antes de chegar aqui, a requisicao ja passou pelo gate de MFA (403
 * {@code mfa-required} sem segundo fator) e pelo {@code PlatformAccessFilter}
 * (403 para token com tenant ou sem papel {@code PLATFORM_*}). Permissoes:
 * leitura {@code contract:read} (os quatro papeis); gestao
 * {@code contract:manage} (OWNER e FINANCE); restricao
 * {@code contract:restrict} (OWNER e FINANCE, com a matriz fina no
 * servico: so por inadimplencia, com fatura vencida e aviso previo
 * registrados). A aba Contratos da Central vive em
 * {@code PlatformCentralController} ({@code central:read}).
 */
@RestController
@RequestMapping("/api/v1/platform")
public class SaaSContractController {

    public record ContractInput(String contractNumber, String biddingProcess, String biddingModality,
                                String contractorCnpj, LocalDate startsAt, LocalDate endsAt,
                                BigDecimal monthlyValue, String indexKey, LocalDate indexBaseDate,
                                String slaPolicyKey, String quotasJson) {
    }

    public record AddendumInput(@NotBlank String addendumNumber, @NotBlank String kind,
                                @NotBlank String description, @NotNull LocalDate effectiveFrom,
                                LocalDate effectiveTo, BigDecimal valueDelta) {
    }

    public record CommitmentInput(@NotBlank String commitmentNumber, @NotNull BigDecimal amount,
                                  @NotNull LocalDate issuedAt) {
    }

    public record ReadjustmentInput(@NotBlank String indexKey, @NotNull BigDecimal indexValuePct,
                                    @NotNull BigDecimal proposedMonthlyValue) {
    }

    public record ApplyReadjustmentInput(@NotNull UUID addendumId) {
    }

    public record InvoiceInput(UUID commitmentId, @NotBlank String competence,
                               @NotNull BigDecimal total, @NotNull LocalDate dueDate) {
    }

    public record PaymentInput(@NotNull UUID tenantId, UUID invoiceId, @NotNull BigDecimal amount,
                               @NotNull LocalDate paidAt, String bankOrderRef) {
    }

    public record RestrictionNoticeInput(@NotNull UUID tenantId, @NotNull UUID contractId,
                                         @NotNull UUID invoiceId, @NotBlank String justification,
                                         boolean stepUpConfirmed, @NotNull OffsetDateTime effectiveAt) {
    }

    public record RestrictionActivateInput(boolean stepUpConfirmed) {
    }

    public record UsageInput(@NotNull UUID tenantId, @NotNull LocalDate periodMonth,
                             @NotBlank String metricKey, @NotNull BigDecimal quantity,
                             @NotBlank String unit) {
    }

    public record SnapshotInput(@NotNull UUID tenantId, @NotNull LocalDate periodMonth,
                                long activeConnections) {
    }

    public record ContestInput(@NotBlank String note) {
    }

    public record CostInput(@NotNull UUID tenantId, @NotNull LocalDate periodMonth,
                            @NotBlank String costKey, @NotBlank String basis,
                            @NotNull BigDecimal amount, String notes) {
    }

    private final SaaSContractService contracts;
    private final SaaSContractAlerts alerts;

    public SaaSContractController(SaaSContractService contracts, SaaSContractAlerts alerts) {
        this.contracts = contracts;
        this.alerts = alerts;
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

    @PostMapping("/contracts")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.ContractView createContract(@RequestParam UUID tenantId,
                                                           @Valid @RequestBody ContractInput in,
                                                           JwtAuthenticationToken auth) {
        return contracts.createContract(tenantId, new SaaSContractService.ContractInput(
                in.contractNumber(), in.biddingProcess(), in.biddingModality(), in.contractorCnpj(),
                in.startsAt(), in.endsAt(), in.monthlyValue(), in.indexKey(), in.indexBaseDate(),
                in.slaPolicyKey(), in.quotasJson()), actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/{contractId}/activate")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.ContractView activateContract(@PathVariable UUID contractId,
                                                             JwtAuthenticationToken auth) {
        return contracts.activateContract(contractId, actorId(auth), platformRole(auth));
    }

    @GetMapping("/contracts/{contractId}")
    @PreAuthorize("hasAuthority('contract:read')")
    public SaaSContractService.ContractView contract(@PathVariable UUID contractId) {
        return contracts.contract(contractId);
    }

    @GetMapping("/contracts")
    @PreAuthorize("hasAuthority('contract:read')")
    public List<SaaSContractService.ContractView> contractsByTenant(@RequestParam UUID tenantId) {
        return contracts.contractsByTenant(tenantId);
    }

    @PostMapping("/contracts/{contractId}/addenda")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.AddendumView addAddendum(@PathVariable UUID contractId,
                                                       @Valid @RequestBody AddendumInput in,
                                                       JwtAuthenticationToken auth) {
        return contracts.addAddendum(contractId, in.addendumNumber(), in.kind(), in.description(),
                in.effectiveFrom(), in.effectiveTo(), in.valueDelta(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/{contractId}/commitments")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.CommitmentView addCommitment(@PathVariable UUID contractId,
                                                           @Valid @RequestBody CommitmentInput in,
                                                           JwtAuthenticationToken auth) {
        return contracts.addCommitment(contractId, in.commitmentNumber(), in.amount(), in.issuedAt(),
                actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/{contractId}/readjustments")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.ReadjustmentView proposeReadjustment(@PathVariable UUID contractId,
                                                                   @Valid @RequestBody ReadjustmentInput in,
                                                                   JwtAuthenticationToken auth) {
        return contracts.proposeReadjustment(contractId, in.indexKey(), in.indexValuePct(),
                in.proposedMonthlyValue(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/readjustments/{readjustmentId}/apply")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.ReadjustmentView applyReadjustment(@PathVariable UUID readjustmentId,
                                                                 @Valid @RequestBody ApplyReadjustmentInput in,
                                                                 JwtAuthenticationToken auth) {
        return contracts.applyReadjustment(readjustmentId, in.addendumId(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/{contractId}/invoices")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.InvoiceView issueInvoice(@PathVariable UUID contractId,
                                                       @Valid @RequestBody InvoiceInput in,
                                                       JwtAuthenticationToken auth) {
        return contracts.issueInvoice(contractId, in.commitmentId(), in.competence(), in.total(),
                in.dueDate(), actorId(auth), platformRole(auth));
    }

    @GetMapping("/contracts/invoices/{invoiceId}")
    @PreAuthorize("hasAuthority('contract:read')")
    public SaaSContractService.InvoiceView invoice(@PathVariable UUID invoiceId) {
        return contracts.invoice(invoiceId);
    }

    @PostMapping("/contracts/payments")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.PaymentView recordPayment(@Valid @RequestBody PaymentInput in,
                                                        JwtAuthenticationToken auth) {
        return contracts.recordPayment(in.tenantId(), in.invoiceId(), in.amount(), in.paidAt(),
                in.bankOrderRef(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/contracts/invoices/{invoiceId}/reconcile")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.InvoiceView reconcileInvoice(@PathVariable UUID invoiceId,
                                                           JwtAuthenticationToken auth) {
        return contracts.reconcileInvoice(invoiceId, actorId(auth), platformRole(auth));
    }

    @GetMapping("/contracts/tenants/{tenantId}/payments/unmatched")
    @PreAuthorize("hasAuthority('contract:read')")
    public List<SaaSContractService.PaymentView> unmatchedPayments(@PathVariable UUID tenantId) {
        return contracts.unmatchedPayments(tenantId);
    }

    @PostMapping("/contracts/restriction-notices")
    @PreAuthorize("hasAuthority('contract:restrict')")
    public SaaSContractService.RestrictionNoticeView requestRestrictionNotice(
            @Valid @RequestBody RestrictionNoticeInput in, JwtAuthenticationToken auth) {
        return contracts.requestRestrictionNotice(in.tenantId(), in.contractId(), in.invoiceId(),
                in.justification(), in.stepUpConfirmed(), in.effectiveAt(),
                platformRole(auth), actorId(auth));
    }

    @PostMapping("/contracts/restriction-notices/{noticeId}/activate")
    @PreAuthorize("hasAuthority('contract:restrict')")
    public SaaSContractService.RestrictionNoticeView activateRestriction(@PathVariable UUID noticeId,
                                                                        @RequestBody RestrictionActivateInput in,
                                                                        JwtAuthenticationToken auth) {
        return contracts.activateRestriction(noticeId, in.stepUpConfirmed(), platformRole(auth), actorId(auth));
    }

    @PostMapping("/contracts/alerts/evaluate")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractAlerts.Report evaluateAlerts() {
        return alerts.evaluateAll();
    }

    @PostMapping("/usage")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.UsageView recordUsage(@Valid @RequestBody UsageInput in,
                                                    JwtAuthenticationToken auth) {
        return contracts.recordUsage(in.tenantId(), in.periodMonth(), in.metricKey(), in.quantity(),
                in.unit(), actorId(auth), platformRole(auth));
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('contract:read')")
    public List<SaaSContractService.UsageView> usageFor(@RequestParam UUID tenantId,
                                                       @RequestParam LocalDate periodMonth) {
        return contracts.usageFor(tenantId, periodMonth);
    }

    @PostMapping("/usage/snapshots")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.SnapshotView takeSnapshot(@Valid @RequestBody SnapshotInput in,
                                                        JwtAuthenticationToken auth) {
        return contracts.takeSnapshot(in.tenantId(), in.periodMonth(), in.activeConnections(),
                actorId(auth), platformRole(auth));
    }

    @GetMapping("/usage/snapshots/{snapshotId}/verify")
    @PreAuthorize("hasAuthority('contract:read')")
    public SaaSContractService.SnapshotView verifySnapshot(@PathVariable UUID snapshotId) {
        return contracts.verifySnapshot(snapshotId);
    }

    @PostMapping("/usage/statements/deliver")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.StatementView deliverStatement(@RequestParam UUID tenantId,
                                                             @RequestParam LocalDate periodMonth,
                                                             JwtAuthenticationToken auth) {
        return contracts.deliverStatement(tenantId, periodMonth, actorId(auth), platformRole(auth));
    }

    @PostMapping("/usage/statements/{statementId}/contest")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.StatementView contestStatement(@PathVariable UUID statementId,
                                                             @Valid @RequestBody ContestInput in,
                                                             JwtAuthenticationToken auth) {
        return contracts.contestStatement(statementId, in.note(), actorId(auth), platformRole(auth));
    }

    @PostMapping("/usage/statements/{statementId}/confirm")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.StatementView confirmStatement(@PathVariable UUID statementId,
                                                             JwtAuthenticationToken auth) {
        return contracts.confirmStatement(statementId, actorId(auth), platformRole(auth));
    }

    @PostMapping("/usage/costs")
    @PreAuthorize("hasAuthority('contract:manage')")
    public SaaSContractService.CostView recordProviderCost(@Valid @RequestBody CostInput in,
                                                          JwtAuthenticationToken auth) {
        return contracts.recordProviderCost(in.tenantId(), in.periodMonth(), in.costKey(), in.basis(),
                in.amount(), in.notes(), actorId(auth), platformRole(auth));
    }
}
