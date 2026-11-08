package br.com.ziipsan.compliance.lgpd.privacy.web;

import br.com.ziipsan.compliance.lgpd.privacy.AccessDelivery;
import br.com.ziipsan.compliance.lgpd.privacy.DataSubjectRight;
import br.com.ziipsan.compliance.lgpd.privacy.PrivacyFacade;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canal do titular no portal — área "Privacidade" do titular autenticado (aceite da
 * P3.40, sobre o portal da P3.3): encarregado, política publicada, abertura de pedido
 * (que nasce como protocolo da P1.36 via facade), revogação de consentimento e entrega
 * de resposta/arquivo. Controller FINO: nenhuma regra mora aqui — as réguas estão nas
 * classes de {@code privacy} e a persistência atrás de {@link PrivacyFacade}.
 *
 * <p>O titular é o da sessão do portal (realm {@code ziipsan-portal} da P3.3): o
 * {@code subjectId} chega no cabeçalho {@code X-Portal-Subject} posto pelo BFF a partir
 * da sessão autenticada e do vínculo da conta — NUNCA do corpo da requisição, porque
 * subjectId vindo do cliente permitiria pedir/revogar em nome de outro titular (a
 * conferência consentId × titular da P1.15 é a segunda trava, não a única). Documento
 * em claro não aparece em URL nem em log (regra da P3.3).
 *
 * <p>SCAN: este controller só existe para a aplicação quando o módulo lgpd entrar no
 * scan — ver {@code docs/PRIVACIDADE-DO-TITULAR.md} e a pendência de integração.
 */
@RestController
@RequestMapping(path = "/portal/privacidade", produces = MediaType.APPLICATION_JSON_VALUE)
public class PrivacyPortalController {

    private final PrivacyFacade facade;

    public PrivacyPortalController(PrivacyFacade facade) {
        this.facade = Objects.requireNonNull(facade, "facade é obrigatória");
    }

    /** Encarregado de dados do tenant, da fonte única (Res. CD/ANPD 18/2024). */
    @GetMapping("/encarregado")
    public String dpo() {
        return facade.dpoForPortal();
    }

    /** Política de privacidade publicada e versionada do tenant. */
    @GetMapping("/politica")
    public String policy() {
        return facade.currentPolicyJson();
    }

    /** Abre pedido do titular autenticado; nasce como protocolo da P1.36. */
    @PostMapping("/pedidos")
    public String openRequest(@RequestHeader("X-Portal-Subject") UUID subjectId,
                              @RequestBody Map<String, String> body) {
        DataSubjectRight right = DataSubjectRight.valueOf(Objects.requireNonNull(body.get("right"),
                "right é obrigatório (direito do art. 18)"));
        return facade.openRequestFromPortal(subjectId, right, body.get("description"));
    }

    /** Revoga o consentimento do próprio titular (P1.15: consentId conferido contra ele). */
    @PostMapping("/consentimentos/{consentId}/revogar")
    public String revoke(@RequestHeader("X-Portal-Subject") UUID subjectId,
                         @PathVariable UUID consentId) {
        return facade.revokeConsent(subjectId, consentId, "PORTAL");
    }

    /**
     * Entrega a resposta ou o arquivo (acesso/portabilidade) do pedido, com log.
     * O {@code subjectId} vem do cabeçalho da sessão (mesmo vínculo da abertura e da
     * revogação): a facade confere o chamador contra o titular DO PEDIDO antes de
     * entregar — conhecer o {@code requestId} alheio não dispara entrega.
     */
    @PostMapping("/pedidos/{requestId}/entrega")
    public String deliver(@RequestHeader("X-Portal-Subject") UUID subjectId,
                          @PathVariable UUID requestId, @RequestBody Map<String, String> body) {
        AccessDelivery.Kind kind = AccessDelivery.Kind.valueOf(Objects.requireNonNull(body.get("kind"),
                "kind é obrigatória (RESPONSE, ACCESS_FILE ou PORTABILITY_FILE)"));
        return facade.deliver(subjectId, requestId, kind);
    }
}
