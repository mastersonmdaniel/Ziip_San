package br.com.ziipsan.app.web;

import br.com.ziipsan.app.about.ThirdPartyLicensesLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P1.24: licenças de terceiros do Sobre (ADR-0021 decisão 7; AJUDA §9).
 *
 * <p>Na allowlist do {@code RoutePermissionTest} (sem {@code @PreAuthorize}):
 * a resposta é metadado global do produto (componente, versão, licença
 * SPDX), sem dado de tenant, de usuário ou segredo — o mesmo motivo da
 * allowlist de {@code /api/v1/about} e {@code /api/v1/public/brand}.
 *
 * <p>Quando o arquivo SBOM não está embalado na imagem, a resposta diz
 * {@code available: false} com lista vazia (não finge lista, não derruba o
 * boot): embalá-lo nos recursos da aplicação é passo do pipeline de
 * release, declarado no relatório da frente.
 */
@RestController
public class AboutLicensesController {

    public record License(String name, String version, List<String> licenses) {
    }

    public record Licenses(boolean available, List<License> licenses) {
    }

    private final ThirdPartyLicensesLoader loader;

    public AboutLicensesController() {
        this(new ThirdPartyLicensesLoader());
    }

    AboutLicensesController(ThirdPartyLicensesLoader loader) {
        this.loader = loader;
    }

    @GetMapping("/api/v1/about/licenses")
    public Licenses licenses() {
        var loaded = loader.licenses();
        return new Licenses(loaded.available(), loaded.licenses().stream()
                .map(l -> new License(l.name(), l.version(), l.licenses()))
                .toList());
    }
}
