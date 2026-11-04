package br.com.ziipsan.app.web;

import br.com.ziipsan.app.brand.BrandProvider;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.about.AboutService;
import br.com.ziipsan.platform.deployment.AboutInfo;
import br.com.ziipsan.platform.deployment.DeploymentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sobre (P0.42 mínimo, P1.24 completo): versão, commit, ambiente e data da
 * implantação registrada, mais marca, documentos e aceite do usuário.
 *
 * <p>Sem {@code @PreAuthorize} de propósito, como {@code GET /api/v1/me}: a
 * resposta não contém dado de terceiros — só metadados da implantação, da
 * marca e os aceites DO PRÓPRIO usuário — e a rota continua exigindo
 * autenticação (as duas cadeias exigem {@code authenticated()} para
 * {@code /api/v1/**}). Por isso ela está na allowlist explícita do
 * {@code RoutePermissionTest}, ao lado de {@code /me}. Como {@code /me},
 * esta rota passa pelo filtro de tenant: token de plataforma (sem
 * {@code tenant_id}) recebe 403 {@code tenant-missing} aqui — o operador de
 * plataforma lê implantações em {@code /api/v1/platform/deployments}, não aqui.
 *
 * <p>A composição vive em {@link AboutInfo}: registro gravado vence; sem registro,
 * build-info/git.properties quando presentes, senão {@code "dev"}/desconhecido.
 *
 * <p>P1.24, honestidade sobre o que NÃO está aqui: o bloco legal da empresa
 * (razão social, CNPJ, contato, encarregado, AJUDA §9) NÃO aparece porque o
 * pacote de marca não tem valores reais aprovados para ele (P0.34: "nunca
 * inventar CNPJ, e-mail ou domínio") e o BrandProvider já não o expõe. Quando
 * existirem, entram no pacote de marca e esta resposta ganha o bloco — sem
 * inventar nada no meantime. A lista de documentos também começa vazia: sem
 * texto legal publicado no catálogo, não há versão vigente a mostrar.
 */
@RestController
public class AboutController {

    public record Brand(String productName, String shortName, String description, String aboutText) {
    }

    public record Document(String code, String title, String currentVersion,
                           String acceptedVersion, OffsetDateTime acceptedAt, boolean upToDate) {
    }

    public record About(String version, String commit, String environment, OffsetDateTime deployedAt,
                        Brand brand, List<Document> documents) {
    }

    private final DeploymentRepository deployments;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final Environment environment;
    private final AboutService aboutService;
    private final BrandProvider brandProvider;

    public AboutController(DeploymentRepository deployments,
                           ObjectProvider<BuildProperties> buildProperties,
                           ObjectProvider<GitProperties> gitProperties,
                           Environment environment,
                           AboutService aboutService) {
        this(deployments, buildProperties, gitProperties, environment, aboutService,
                BrandProvider.fromEnvironment());
    }

    AboutController(DeploymentRepository deployments,
                    ObjectProvider<BuildProperties> buildProperties,
                    ObjectProvider<GitProperties> gitProperties,
                    Environment environment,
                    AboutService aboutService,
                    BrandProvider brandProvider) {
        this.deployments = deployments;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.environment = environment;
        this.aboutService = aboutService;
        this.brandProvider = brandProvider;
    }

    @GetMapping("/api/v1/about")
    public About about() {
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        AboutInfo info = AboutInfo.resolve(
                deployments.latest(),
                build != null ? build.getVersion() : null,
                git != null ? git.getShortCommitId() : null,
                environment.getActiveProfiles());
        var brand = brandProvider.brand();
        var texts = brandProvider.texts();
        List<Document> documents = aboutService.documentsFor(
                TenantContextHolder.getTenantId(), TenantContextHolder.getUserId()).stream()
                .map(d -> new Document(d.code(), d.title(), d.currentVersion(),
                        d.acceptedVersion(), d.acceptedAt(), d.upToDate()))
                .toList();
        return new About(info.version(), info.commit(), info.environment(), info.deployedAt(),
                new Brand(brand.productName(), brand.shortName(), brand.description(), texts.aboutText()),
                documents);
    }
}
