package br.com.ziipsan.app.brand;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0.34: marca publica do produto, sem autenticacao (o frontend a le antes
 * do login, junto do config.json da P0.5). Nao devolve dado de tenant, dado
 * legal nem segredo: so o que a interface exibe. A rota e permitAll nas
 * duas cadeias de seguranca (BFF e resource server); a spec OpenAPI gerada
 * muda com esta rota e precisa ser regenerada na integracao (o teste
 * OpenApiSpecTest compara com docs/openapi/openapi.yaml, que esta frente
 * nao toca por ordem do coordenador).
 */
@RestController
public class BrandController {

    public record BrandResponse(String productName, String shortName, String description, String brandPrimary,
                                String themeColorLight, String themeColorDark, String footer, String emailSignature,
                                String aboutText, String attributionText) {
    }

    private final BrandProvider provider;

    public BrandController() {
        this(BrandProvider.fromEnvironment());
    }

    BrandController(BrandProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/api/v1/public/brand")
    public BrandResponse brand() {
        var brand = provider.brand();
        var texts = provider.texts();
        return new BrandResponse(
                brand.productName(),
                brand.shortName(),
                brand.description(),
                brand.brandPrimary(),
                brand.themeColor().light(),
                brand.themeColor().dark(),
                texts.footer(),
                texts.emailSignature(),
                texts.aboutText(),
                brand.attribution().enabled()
                        ? brand.attribution().text().replace("{productName}", brand.productName())
                        : null);
    }
}
