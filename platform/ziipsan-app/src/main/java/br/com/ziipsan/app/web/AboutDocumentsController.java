package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.about.AboutRecords.DocumentAcceptance;
import br.com.ziipsan.platform.about.AboutService;
import br.com.ziipsan.platform.about.DocumentNotPublishedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * P1.24: aceite da versão vigente de um documento legal (ADR-0021).
 *
 * <p>Na allowlist do {@code RoutePermissionTest} (sem {@code @PreAuthorize}):
 * a operação só escreve o aceite DO PRÓPRIO usuário (tenant e usuário vêm
 * do token, nunca do corpo — o corpo é vazio), como {@code /me} só lê os
 * próprios dados. A rota continua exigindo autenticação e tenant.
 *
 * <p>Idempotente: aceitar duas vezes a mesma versão vigente devolve o
 * aceite original, sem criar outro nem mudar a data. O IP é o observado
 * pelo servidor ({@code getRemoteAddr}), nunca um valor enviado pelo
 * cliente; o método de autenticação é o JWT do BFF. Os dois, mais o hash
 * do texto e a data, são a evidência que a assinatura simples da P3.34
 * usa sobre este aceite.
 */
@RestController
public class AboutDocumentsController {

    public record Acceptance(String documentCode, String documentVersion, String contentHash,
                             OffsetDateTime acceptedAt) {
    }

    private final AboutService aboutService;

    public AboutDocumentsController(AboutService aboutService) {
        this.aboutService = aboutService;
    }

    @PostMapping("/api/v1/about/documents/{code}/accept")
    public Acceptance accept(@PathVariable String code, HttpServletRequest request) {
        try {
            DocumentAcceptance acceptance = aboutService.accept(
                    TenantContextHolder.getTenantId(),
                    TenantContextHolder.getUserId(),
                    code,
                    request.getRemoteAddr(),
                    "jwt");
            return new Acceptance(acceptance.documentCode(), acceptance.documentVersion(),
                    acceptance.contentHash(), acceptance.acceptedAt());
        } catch (DocumentNotPublishedException e) {
            throw new ApiException.NotFound("Documento não publicado: " + code);
        }
    }
}
