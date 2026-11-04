package br.com.ziipsan.app.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.brand.JdbcTenantBrandStore;
import br.com.ziipsan.platform.brand.TenantBrandService;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.util.Base64;
import java.util.UUID;

/**
 * Administração da marca do tenant (P1.19, ADR-0016, MARCA-E-TEMAS §3).
 *
 * <p>Permissão fina {@code brand:manage} (concessão direta, nenhum perfil
 * de fábrica — como {@code parameters:manage} da P1.6; perfis por função
 * são da P1.37). O tenant vem do token, nunca do corpo.
 *
 * <p>PUT é atualização parcial: campo ausente não muda; campo presente é
 * validado e gravado; a cor passa pela regra de contraste do §3.4 (abaixo
 * de 4,5:1 sobre branco, o sistema escurece e avisa). String vazia em
 * {@code subdomainSlug} limpa o slug. Os logos NÃO passam pelo PUT: têm
 * upload e remoção próprios, com troca e remoção auditadas (§3.2).
 *
 * <p>Upload é JSON + base64 (não multipart: não há multipart no código e
 * o limite de corpo de 1 MiB cobre os 512 KB do logo). Respostas do
 * upload: 200 com o ativo CLEAN, 202 com o ativo em quarentena quando o
 * varredor está indisponível (a marca anterior segue valendo), 422 quando
 * o arquivo é inválido, poliglota ou recusado pela varredura.
 */
@RestController
@RequestMapping("/api/v1/tenant/brand")
@PreAuthorize("hasAuthority('brand:manage')")
public class TenantBrandController {

    public record SaveBrandRequest(String displayName, String shortName, String color, JsonNode contact,
                                   String documentFooter, String subdomainSlug) {
    }

    public record UploadLogoRequest(String kind, String contentBase64) {
    }

    private final TenantBrandService service;

    public TenantBrandController(DataSource dataSource, FileScanner scanner) {
        this.service = new TenantBrandService(new JdbcTenantBrandStore(dataSource), scanner);
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

    @GetMapping
    public TenantBrandService.AdminBrandView current() {
        return service.currentBrand(tenantId());
    }

    @PutMapping
    public TenantBrandService.SaveBrandResult save(@RequestBody SaveBrandRequest in,
                                                   JwtAuthenticationToken auth) {
        return service.saveBrand(tenantId(), actorId(auth), new TenantBrandService.SaveBrandInput(
                in.displayName(), in.shortName(), in.color(), in.contact(),
                in.documentFooter(), in.subdomainSlug()));
    }

    @PostMapping("/logo")
    public ResponseEntity<TenantBrandService.UploadResult> uploadLogo(@RequestBody UploadLogoRequest in,
                                                                      JwtAuthenticationToken auth) {
        if (in.contentBase64() == null || in.contentBase64().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "contentBase64", "empty", "O conteúdo do logo é obrigatório."));
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(in.contentBase64());
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "contentBase64", "invalid-base64", "O conteúdo do logo não está em base64 válido."));
        }
        TenantBrandService.UploadResult result =
                service.uploadLogo(tenantId(), actorId(auth), in.kind(), content);
        int status = "CLEAN".equals(result.scanStatus()) ? 200 : 202;
        return ResponseEntity.status(status).body(result);
    }

    @DeleteMapping("/logo")
    public ResponseEntity<Void> deleteLogo(@RequestParam String kind, JwtAuthenticationToken auth) {
        boolean removed = service.deleteLogo(tenantId(), actorId(auth), kind);
        if (!removed) {
            throw new ApiException.NotFound("Não há logo para remover.");
        }
        return ResponseEntity.noContent().build();
    }
}
