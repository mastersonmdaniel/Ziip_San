package br.com.ziipsan.platform.signing.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.signing.SignatureService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada do servico de assinatura para os consumidores internos (P4.1,
 * ED-19, ED-23, ED-38). Controller FINO: monta o comando e delega ao
 * {@link SignatureService.SignatureFacade}; toda a regra (nivel minimo,
 * portao do tenant, evidencias) mora nas classes de {@code signing}.
 *
 * <p><b>Identidade NUNCA vem do corpo</b> (correcao D3 da revisao cruzada),
 * no padrao dos controllers irmaos do platform (P3.36/P2.28 usam o
 * {@code sub} do token; P3.3 usa {@code TenantContextHolder}):
 * <ul>
 *   <li>o TENANT e o do contexto autenticado da requisicao
 *       ({@link TenantContextHolder}, populado pela fiacao de seguranca a
 *       partir do token) — sem contexto de tenant, a chamada e recusada;</li>
 *   <li>o SIGNATARIO e o sujeito do principal autenticado
 *       ({@code @AuthenticationPrincipal(expression = "subject")}); quando o
 *       sujeito e um UUID, ele e tambem o {@code user_id} da chave do aceite
 *       da P1.24 (D6); sujeito nao-UUID deixa o {@code user_id} nulo e a
 *       assinatura simples recusa com mensagem explicita;</li>
 *   <li>se o corpo trouxer {@code tenantId} ou {@code signerSubject}
 *       DIVERGENTES do contexto autenticado, a chamada e recusada (403):
 *       corpo nao redefine identidade, nem por engano.</li>
 * </ul>
 * Do corpo vem so o documento: tipo, nivel pretendido, referencia, conteudo
 * em Base64 (documento em claro nao trafega em URL) e, para a simples, o
 * {@code document_code}/{@code document_version} do aceite invocado (D6).
 */
@RestController
@RequestMapping(path = "/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
public class SigningController {

    private final SignatureService.SignatureFacade facade;

    public SigningController(SignatureService.SignatureFacade facade) {
        this.facade = Objects.requireNonNull(facade, "facade e obrigatoria");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public String sign(@AuthenticationPrincipal(expression = "subject") String subject,
                       @RequestBody Map<String, String> body) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ApiException.Forbidden(
                    "Assinatura exige contexto de tenant autenticado: o tenant vem do token/contexto "
                            + "da requisicao, nunca do corpo.");
        }
        if (subject == null || subject.isBlank()) {
            throw new ApiException.Forbidden(
                    "Assinatura exige principal autenticado: o signatario e o sujeito do token, "
                            + "nunca um valor declarado no corpo.");
        }
        refuseDivergence(body.get("tenantId"), tenantId.toString(), "tenantId");
        refuseDivergence(body.get("signerSubject"), subject, "signerSubject");
        String type = Objects.requireNonNull(body.get("documentType"), "documentType e obrigatorio");
        String level = Objects.requireNonNull(body.get("level"), "level e obrigatorio");
        String reference = Objects.requireNonNull(body.get("documentReference"), "documentReference e obrigatorio");
        byte[] content = Base64.getDecoder().decode(
                Objects.requireNonNull(body.get("contentBase64"), "contentBase64 e obrigatorio"));
        UUID signerUserId = parseUuidOrNull(subject);
        var context = new br.com.ziipsan.platform.signing.DocumentSigner.SignContext(
                tenantId, br.com.ziipsan.platform.signing.SignableDocumentType.valueOf(type),
                reference, content, subject, signerUserId,
                body.get("acceptanceDocumentCode"), body.get("acceptanceDocumentVersion"));
        return facade.signJson(new SignatureService.SignCommand(
                br.com.ziipsan.platform.signing.SignatureLevel.valueOf(level), context));
    }

    /** Corpo que diverge do contexto autenticado e tentativa de redefinir identidade: 403. */
    private static void refuseDivergence(String bodyValue, String authenticatedValue, String field) {
        if (bodyValue != null && !bodyValue.equals(authenticatedValue)) {
            throw new ApiException.Forbidden("O campo " + field + " do corpo diverge do contexto "
                    + "autenticado: identidade (tenant e signatario) vem do token, nao do corpo.");
        }
    }

    private static UUID parseUuidOrNull(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
