package br.com.ziipsan.platform.deployment;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Composição de {@code GET /api/v1/about} (P0.42), sem Spring: testável sem banco
 * e sem contexto.
 *
 * <p>Precedência, sem inventar nada:
 * <ol>
 *   <li>se há registro em {@code platform.deployment}, ele vence: versão, commit,
 *       ambiente e data são os GRAVADOS pelo job migrate;</li>
 *   <li>sem registro, versão vem do {@code build-info} (quando o artefato o tem),
 *       commit do {@code git.properties} (quando o artefato o tem), ambiente do
 *       perfil ativo — e, na ausência de cada um, os fallbacks literais
 *       {@code "dev"} / {@code "unknown"} / {@code "dev"}, com data nula (não há
 *       implantação registrada para datar).</li>
 * </ol>
 * <p>Um commit ausente no registro gravado (nulo) também vira {@code "unknown"}
 * na apresentação; o banco continua guardando nulo.
 */
public record AboutInfo(String version, String commit, String environment, OffsetDateTime deployedAt) {

    public static AboutInfo resolve(Optional<Deployment> latest, String buildVersion, String gitCommit,
                                    String[] activeProfiles) {
        if (latest.isPresent()) {
            Deployment row = latest.get();
            return new AboutInfo(
                    row.version(),
                    row.commitHash() == null || row.commitHash().isBlank() ? "unknown" : row.commitHash(),
                    row.environment(),
                    row.createdAt());
        }
        return new AboutInfo(
                buildVersion == null || buildVersion.isBlank() ? "dev" : buildVersion,
                gitCommit == null || gitCommit.isBlank() ? "unknown" : gitCommit,
                DeploymentEnvironment.resolve(null, activeProfiles),
                null);
    }
}
