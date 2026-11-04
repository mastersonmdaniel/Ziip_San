package br.com.ziipsan.app.web;

import br.com.ziipsan.app.brand.BrandProvider;
import br.com.ziipsan.platform.brand.BrandContrast;
import br.com.ziipsan.platform.brand.JdbcTenantBrandStore;
import br.com.ziipsan.platform.brand.SlugResolver;
import br.com.ziipsan.platform.brand.TenantBrandService;
import br.com.ziipsan.platform.brand.TenantBrandStore.PublicLogoRef;
import br.com.ziipsan.platform.brand.TenantBrandStore.TenantBrand;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Marca pré-login do tenant (P1.19, MARCA-E-TEMAS §3.3).
 *
 * <p>Rota pública ({@code permitAll} nas duas cadeias, como
 * {@code /api/v1/public/brand} da P0.34 — por isso entra na allowlist do
 * {@code RoutePermissionTest}, sem {@code @PreAuthorize}): devolve só nome,
 * logos, cor e contato — nada de dado de tenant, de usuário ou segredo.
 * Resolvida pelo host: o primeiro rótulo é o slug candidato. Slug
 * desconhecido (ou host sem slug) devolve a marca do PRODUTO com o MESMO
 * status 200, sem revelar se o slug existe. Cache curto (60 s) e limite
 * por IP: o rate limit global já chaveia por IP quando não há tenant nem
 * usuário (P0.16).
 *
 * <p>O logo sai por URL com o hash ({@code ?v=<12 primeiros do sha256>}),
 * {@code Cache-Control: immutable}, {@code nosniff} e CSP
 * {@code default-src 'none'} (§3.2). Só logo CLEAN é servido; qualquer
 * outro caso é 404, sem revelar existência ou estado da varredura.
 * Domínio próprio do tenant é P2.27: o controller resolve primeiro
 * pelo domínio próprio VERIFIED (função da V10_2_1) e só então pelo
 * slug — sem essa ordem, um host como {@code portal.cliente.example}
 * cairia no slug {@code portal} de outro tenant.
 */
@RestController
public class PublicTenantBrandController {

    public record TenantBrandResponse(String displayName, String shortName, String color, String textOnColor,
                                      JsonNode contact, String logoUrl, String logoDarkUrl,
                                      String attributionText, String source) {
    }

    private final TenantBrandService service;
    private final BrandProvider productBrand;

    public PublicTenantBrandController(DataSource dataSource, @Qualifier("fileScanner") FileScanner scanner) {
        this.service = new TenantBrandService(new JdbcTenantBrandStore(dataSource), scanner);
        this.productBrand = BrandProvider.fromEnvironment();
    }

    @GetMapping("/api/v1/public/tenant-brand")
    public ResponseEntity<TenantBrandResponse> tenantBrand(HttpServletRequest request) {
        String host = request.getHeader("Host");
        TenantBrandResponse response = service.resolveByHost(host)
                .or(() -> SlugResolver.extractSlug(host).flatMap(service::resolveBySlug))
                .map(brand -> new TenantBrandResponse(
                        brand.displayName(),
                        brand.shortName(),
                        brand.color(),
                        brand.color() != null && BrandContrast.isHexColor(brand.color())
                                ? BrandContrast.textOnColor(brand.color()) : null,
                        contactOrNull(brand.contact()),
                        logoUrl(brand.slug(), brand.logo()),
                        logoUrl(brand.slug(), brand.logoDark()),
                        attribution(),
                        "tenant"))
                .orElseGet(this::productBrandResponse);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
                .body(response);
    }

    @GetMapping("/api/v1/public/tenant-brand/{slug}/logo/{assetId}")
    public ResponseEntity<byte[]> logo(@PathVariable String slug, @PathVariable UUID assetId) {
        return service.logoBytes(slug, assetId)
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .contentLength(logo.content().length)
                        .eTag("\"" + logo.sha256() + "\"")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .body(logo.content()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private TenantBrandResponse productBrandResponse() {
        var brand = productBrand.brand();
        return new TenantBrandResponse(
                brand.productName(),
                brand.shortName(),
                brand.brandPrimary(),
                BrandContrast.textOnColor(brand.brandPrimary()),
                null,
                null,
                null,
                attribution(),
                "product");
    }

    private String attribution() {
        var brand = productBrand.brand();
        return brand.attribution().enabled()
                ? brand.attribution().text().replace("{productName}", brand.productName())
                : null;
    }

    private static JsonNode contactOrNull(JsonNode contact) {
        return contact != null && contact.isObject() ? contact : null;
    }

    private static String logoUrl(String slug, PublicLogoRef logo) {
        if (slug == null || logo == null) {
            return null;
        }
        return "/api/v1/public/tenant-brand/" + slug + "/logo/" + logo.id()
                + "?v=" + logo.sha256().substring(0, 12);
    }
}
