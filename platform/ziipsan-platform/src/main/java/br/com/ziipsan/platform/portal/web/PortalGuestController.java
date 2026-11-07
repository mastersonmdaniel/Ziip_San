package br.com.ziipsan.platform.portal.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.portal.GuestPaymentService;
import br.com.ziipsan.platform.portal.PortalParameters;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Caminho de convidado do portal (P3.3, ADR-0043): POST com o codigo no
 * CORPO — codigo e documento nunca em URL (RNF-52). Sem sessao: a rota
 * nao cria nem usa sessao persistente. A origem do limite e o endereco
 * da requisicao. Tenant vem do contexto (resolucao por dominio, P2.27).
 */
@RestController
@RequestMapping("/api/v1/portal/guest")
public class PortalGuestController {

    public record GuestInvoiceRequest(String linkingCode, String documentSuffix) { }

    private final GuestPaymentService service;
    private final PortalParameters parameters;
    private final PortalParameters.AccountThrottle factoryThrottle;

    public PortalGuestController(GuestPaymentService service, PortalParameters parameters,
                                 PortalParameters.AccountThrottle factoryThrottle) {
        this.service = service;
        this.parameters = parameters;
        this.factoryThrottle = factoryThrottle;
    }

    @PostMapping("/invoice")
    public GuestPaymentService.GuestInvoiceView invoice(@RequestBody GuestInvoiceRequest request,
                                                        HttpServletRequest http) {
        return service.query(TenantContextHolder.getTenantId(), http.getRemoteAddr(),
                request.linkingCode(), request.documentSuffix(), parameters,
                parameters.accountThrottle(factoryThrottle), Instant.now());
    }
}
