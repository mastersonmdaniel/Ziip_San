package br.com.ziipsan.platform.portal.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.portal.PortalRealmPolicy;
import br.com.ziipsan.platform.portal.SecondCopyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Rotas do titular autenticado do portal (P3.3): exigem token do realm
 * {@code ziipsan-portal} (a trava de realm roda antes de qualquer dado).
 * A 2a via sai por POST sobre o id da unidade (UUID, nunca documento nem
 * matricula na URL alem do id interno vinculado).
 */
@RestController
@RequestMapping("/api/v1/portal")
@PreAuthorize("isAuthenticated()")
public class PortalConsumerController {

    private final SecondCopyService secondCopy;

    public PortalConsumerController(SecondCopyService secondCopy) {
        this.secondCopy = secondCopy;
    }

    public record PixResponse(String pixCopyPaste) { }

    @PostMapping("/units/{unitId}/second-copy")
    public ResponseEntity<byte[]> secondCopy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID unitId) {
        PortalRealmPolicy.requirePortalToken(jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
        SecondCopyService.SecondCopy copy =
                secondCopy.secondCopy(TenantContextHolder.getTenantId(), jwt.getSubject(), unitId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(copy.pdf());
    }

    /**
     * Pix copia e cola da mesma conta, em rota propria com o payload no
     * CORPO da resposta (revisao cruzada da P3.3): payload de pagamento em
     * header HTTP (como estava) vaza para logs de proxy/APM e fere o
     * espirito da RNF-52.
     */
    @PostMapping("/units/{unitId}/second-copy/pix")
    public PixResponse secondCopyPix(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID unitId) {
        PortalRealmPolicy.requirePortalToken(jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
        SecondCopyService.SecondCopy copy =
                secondCopy.secondCopy(TenantContextHolder.getTenantId(), jwt.getSubject(), unitId);
        return new PixResponse(copy.pixCopyPaste());
    }
}
