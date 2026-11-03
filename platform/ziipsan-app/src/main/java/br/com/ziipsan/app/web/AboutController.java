package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.deployment.AboutInfo;
import br.com.ziipsan.platform.deployment.DeploymentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Sobre mínimo (P0.42): versão, commit, ambiente e data da implantação registrada.
 *
 * <p>Sem {@code @PreAuthorize} de propósito, como {@code GET /api/v1/me}: a resposta
 * não contém dado de tenant nem de usuário — só metadados da implantação — e a
 * rota continua exigindo autenticação (as duas cadeias exigem {@code authenticated()}
 * para {@code /api/v1/**}). Por isso ela está na allowlist explícita do
 * {@code RoutePermissionTest}, ao lado de {@code /me}. O Sobre completo, com
 * documentos e novidades por usuário, é P1.24 e não entra aqui.
 *
 * <p>Como {@code /me}, esta rota passa pelo filtro de tenant: token de plataforma
 * (sem {@code tenant_id}) recebe 403 {@code tenant-missing} aqui — o operador de
 * plataforma lê implantações em {@code /api/v1/platform/deployments}, não aqui.
 *
 * <p>A composição vive em {@link AboutInfo}: registro gravado vence; sem registro,
 * build-info/git.properties quando presentes, senão {@code "dev"}/desconhecido.
 */
@RestController
public class AboutController {

    public record About(String version, String commit, String environment, OffsetDateTime deployedAt) {
    }

    private final DeploymentRepository deployments;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final Environment environment;

    public AboutController(DeploymentRepository deployments,
                           ObjectProvider<BuildProperties> buildProperties,
                           ObjectProvider<GitProperties> gitProperties,
                           Environment environment) {
        this.deployments = deployments;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.environment = environment;
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
        return new About(info.version(), info.commit(), info.environment(), info.deployedAt());
    }
}
