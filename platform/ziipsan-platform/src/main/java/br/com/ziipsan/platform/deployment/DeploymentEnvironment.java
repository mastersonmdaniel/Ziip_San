package br.com.ziipsan.platform.deployment;

import java.util.Set;

/**
 * Ambiente registrado em {@code platform.deployment} e apresentado em
 * {@code GET /api/v1/about} (P0.42).
 *
 * <p>Regra, sem inventar: valor configurado explicitamente
 * ({@code ziipsan.deployment.environment}) vence; senão, o primeiro perfil ativo
 * que não seja perfil de MODO de execução ({@code migrate}, {@code worker}) —
 * o job migrate roda com perfil {@code migrate} e a api de produção com
 * {@code prod}, então o perfil de modo nunca é o ambiente; sem perfil de
 * ambiente, {@code "dev"}. Um ambiente ausente vira {@code "dev"}, nunca um
 * nome de produção adivinhado.
 */
public final class DeploymentEnvironment {

    /** Perfis que descrevem o modo de execução, não o ambiente. */
    public static final Set<String> MODE_PROFILES = Set.of("migrate", "worker");

    private DeploymentEnvironment() {
    }

    public static String resolve(String configured, String[] activeProfiles) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (activeProfiles != null) {
            for (String profile : activeProfiles) {
                if (profile != null && !profile.isBlank() && !MODE_PROFILES.contains(profile.trim())) {
                    return profile.trim();
                }
            }
        }
        return "dev";
    }
}
