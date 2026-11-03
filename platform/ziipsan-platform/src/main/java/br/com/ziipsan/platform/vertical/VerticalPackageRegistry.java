package br.com.ziipsan.platform.vertical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registro de pacotes em memória (P0.47), no lugar do {@code ModuleRegistry} apagado na
 * P0.25. A persistência é o core V6_1 ({@code platform.vertical} e
 * {@code platform.tenant_vertical}); esta classe é a semântica de ativação, sem banco:
 * um pacote ativo pode ser arquivado, nunca apagado (PLATAFORMA-E-VERTICAIS §4) — a
 * verificação de "pacote com dados no tenant" é do console (P1.6), que conhece os dados;
 * arquivar aqui sem essa verificação seria apagar por acidente, por isso o método exige
 * a confirmação explícita de quem chama.
 */
public final class VerticalPackageRegistry {

    /** Estado de ativação de um pacote num tenant (espelha platform.tenant_vertical). */
    public enum ActivationState {
        ACTIVE,
        ARCHIVED
    }

    private final Map<String, VerticalManifest> packages = new LinkedHashMap<>();
    private final Map<String, ActivationState> states = new LinkedHashMap<>();

    /** Regista o manifesto; o pacote nasce ativo (todo tenant existente nasce com sanitation ativo). */
    public void register(VerticalManifest manifest) {
        if (manifest == null || manifest.id() == null) {
            throw new IllegalArgumentException("O manifesto e obrigatorio");
        }
        VerticalManifestValidator.validate(manifest);
        packages.put(manifest.id(), manifest);
        states.putIfAbsent(manifest.id(), ActivationState.ACTIVE);
    }

    /** Módulos do pacote, resolvidos só para pacotes ativos (menu, parâmetros e permissões). */
    public List<VerticalManifest.ModuleManifest> activeModules(String verticalId) {
        VerticalManifest manifest = packages.get(verticalId);
        if (manifest == null) {
            throw new IllegalArgumentException("Pacote desconhecido: " + verticalId);
        }
        if (states.get(verticalId) != ActivationState.ACTIVE) {
            return List.of();
        }
        return manifest.modules();
    }

    /** Arquiva o pacote. A confirmação de que não há dados (ou de que arquivar é aceitável) é do console. */
    public void archive(String verticalId, boolean noDataConfirmed) {
        if (!packages.containsKey(verticalId)) {
            throw new IllegalArgumentException("Pacote desconhecido: " + verticalId);
        }
        if (!noDataConfirmed) {
            throw new IllegalStateException(
                    "Arquivar exige confirmacao de que o pacote nao tem dados no tenant (P1.6)");
        }
        states.put(verticalId, ActivationState.ARCHIVED);
    }

    public Set<String> verticalIds() {
        return Set.copyOf(packages.keySet());
    }

    public ActivationState state(String verticalId) {
        return states.get(verticalId);
    }
}
