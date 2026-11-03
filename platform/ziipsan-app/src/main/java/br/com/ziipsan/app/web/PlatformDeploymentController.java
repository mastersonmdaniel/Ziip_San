package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.deployment.Deployment;
import br.com.ziipsan.platform.deployment.DeploymentRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Primeira rota de plataforma (P0.43): o registro de implantação, em leitura.
 *
 * <p>A rota existe para que a matriz de papéis da P0.43 tenha uma superfície real
 * para fiscalizar — antes dela, o prefixo {@code /api/v1/platform/*} só existia
 * nos filtros e nos testes do gate. Antes deste controller chegar, a requisição
 * já passou pelo gate de MFA (403 {@code mfa-required} sem segundo fator) e pelo
 * {@code PlatformAccessFilter} (403 {@code tenant-not-allowed} para token com
 * tenant, 403 {@code access-denied} sem papel {@code PLATFORM_*}); aqui, a
 * permissão fina é {@code deployment:read}, que só OWNER e OPS têm.
 */
@RestController
public class PlatformDeploymentController {

    public record DeploymentView(UUID id, String version, String commit, String environment,
                                 OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                 OffsetDateTime deployedAt) {
    }

    private final DeploymentRepository deployments;

    public PlatformDeploymentController(DeploymentRepository deployments) {
        this.deployments = deployments;
    }

    @GetMapping("/api/v1/platform/deployments")
    @PreAuthorize("hasAuthority('deployment:read')")
    public List<DeploymentView> deployments() {
        return deployments.recent(50).stream()
                .map(this::view)
                .toList();
    }

    private DeploymentView view(Deployment row) {
        return new DeploymentView(row.id(), row.version(), row.commitHash(), row.environment(),
                row.startedAt(), row.finishedAt(), row.createdAt());
    }
}
