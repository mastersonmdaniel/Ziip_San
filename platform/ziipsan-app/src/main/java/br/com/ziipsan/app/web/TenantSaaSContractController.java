package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.contract.SaaSContractService;
import br.com.ziipsan.platform.contract.TenantSaaSContractService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Area do tenant: contrato com o Ziip (P2.14, ADR-0022).
 *
 * <p>O tenant e sempre o do token, nunca parametro: nao ha como pedir
 * o contrato de outro tenant por esta rota — e, por defesa em
 * profundidade, o servico responde 404 a qualquer id que nao seja do
 * tenant do token. Permissao fina {@code contract:read-own} (perfis
 * de fabrica ADMIN, GESTOR, CONTADOR e AUDITOR_INTERNO). Cada acesso
 * servido e auditado em {@code platform.audit_event} pelo servico.
 *
 * <p>Downloads: a fatura em PDF exige fatura PAID; a NFS-e exige
 * referencia resolvida. O XML baixado e conferido pelo hash quando a
 * referencia o carrega (ver o servico); o hash calculado vai sempre
 * no cabecalho {@code X-Content-Sha256}. A forma da referencia da
 * NFS-e e confirmada na integracao com a P2.13 (relatorio da P2.14).
 */
@RestController
@RequestMapping("/api/v1/tenant/my-contract")
@PreAuthorize("hasAuthority('contract:read-own')")
public class TenantSaaSContractController {

    private final TenantSaaSContractService service;

    public TenantSaaSContractController(TenantSaaSContractService service) {
        this.service = service;
    }

    private static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden("Esta rota exige um token de tenant.");
        }
        return tenantId;
    }

    private static UUID actorId(JwtAuthenticationToken auth) {
        try {
            return UUID.fromString(auth.getToken().getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    private static String actorRole(JwtAuthenticationToken auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name.startsWith("ROLE_") && !name.startsWith("ROLE_PLATFORM_")) {
                return name.substring("ROLE_".length());
            }
        }
        return "TENANT";
    }

    @GetMapping("/contracts")
    public List<SaaSContractService.ContractView> contracts(JwtAuthenticationToken auth) {
        return service.contracts(tenantId(), actorId(auth), actorRole(auth));
    }

    @GetMapping("/contracts/{contractId}")
    public SaaSContractService.ContractView contract(@PathVariable UUID contractId,
                                                     JwtAuthenticationToken auth) {
        return service.contract(tenantId(), contractId, actorId(auth), actorRole(auth));
    }

    @GetMapping("/contracts/{contractId}/invoices")
    public List<SaaSContractService.InvoiceView> invoices(@PathVariable UUID contractId,
                                                          JwtAuthenticationToken auth) {
        return service.invoices(tenantId(), contractId, actorId(auth), actorRole(auth));
    }

    @GetMapping("/contracts/{contractId}/payments")
    public List<SaaSContractService.PaymentView> paymentsForContract(@PathVariable UUID contractId,
                                                                     JwtAuthenticationToken auth) {
        return service.paymentsForContract(tenantId(), contractId, actorId(auth), actorRole(auth));
    }

    @GetMapping("/invoices/{invoiceId}")
    public SaaSContractService.InvoiceView invoice(@PathVariable UUID invoiceId,
                                                   JwtAuthenticationToken auth) {
        return service.invoice(tenantId(), invoiceId, actorId(auth), actorRole(auth));
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<SaaSContractService.PaymentView> paymentsForInvoice(@PathVariable UUID invoiceId,
                                                                    JwtAuthenticationToken auth) {
        return service.paymentsForInvoice(tenantId(), invoiceId, actorId(auth), actorRole(auth));
    }

    @GetMapping("/payments/unmatched")
    public List<SaaSContractService.PaymentView> unmatchedPayments(JwtAuthenticationToken auth) {
        return service.unmatchedPayments(tenantId(), actorId(auth), actorRole(auth));
    }

    @GetMapping("/usage/statements")
    public SaaSContractService.StatementView statement(@RequestParam LocalDate periodMonth,
                                                       JwtAuthenticationToken auth) {
        return service.statement(tenantId(), periodMonth, actorId(auth), actorRole(auth));
    }

    @GetMapping("/invoices/{invoiceId}/documents/invoice.pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable UUID invoiceId, JwtAuthenticationToken auth) {
        return download(invoiceId, TenantSaaSContractService.DocumentKind.INVOICE,
                TenantSaaSContractService.DocumentFormat.PDF, auth);
    }

    @GetMapping("/invoices/{invoiceId}/documents/invoice.xml")
    public ResponseEntity<byte[]> invoiceXml(@PathVariable UUID invoiceId, JwtAuthenticationToken auth) {
        return download(invoiceId, TenantSaaSContractService.DocumentKind.INVOICE,
                TenantSaaSContractService.DocumentFormat.XML, auth);
    }

    @GetMapping("/invoices/{invoiceId}/documents/nfse.pdf")
    public ResponseEntity<byte[]> nfsePdf(@PathVariable UUID invoiceId, JwtAuthenticationToken auth) {
        return download(invoiceId, TenantSaaSContractService.DocumentKind.NFSE,
                TenantSaaSContractService.DocumentFormat.PDF, auth);
    }

    @GetMapping("/invoices/{invoiceId}/documents/nfse.xml")
    public ResponseEntity<byte[]> nfseXml(@PathVariable UUID invoiceId, JwtAuthenticationToken auth) {
        return download(invoiceId, TenantSaaSContractService.DocumentKind.NFSE,
                TenantSaaSContractService.DocumentFormat.XML, auth);
    }

    private ResponseEntity<byte[]> download(UUID invoiceId, TenantSaaSContractService.DocumentKind kind,
                                            TenantSaaSContractService.DocumentFormat format,
                                            JwtAuthenticationToken auth) {
        TenantSaaSContractService.Download file = service.download(
                tenantId(), invoiceId, kind, format, actorId(auth), actorRole(auth));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header("X-Content-Sha256", file.sha256Hex())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
