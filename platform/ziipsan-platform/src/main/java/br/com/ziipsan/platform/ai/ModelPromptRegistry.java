package br.com.ziipsan.platform.ai;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Camada comum de IA (PLT-18): registro de MODELO e de PROMPT com VERSAO. Troca de versao exige
 * avaliacao (evaluationPassed) — modelo/prompt nao avaliado nao entra em uso (ADR-0042: avaliacao de modelo). */
public final class ModelPromptRegistry {
    public record ModelVersion(String modelId, String version, boolean evaluationPassed) {}
    public record PromptVersion(String promptId, String version, String template, boolean evaluationPassed) {}
    private final Map<String, ModelVersion> models = new ConcurrentHashMap<>();
    private final Map<String, PromptVersion> prompts = new ConcurrentHashMap<>();
    public void registerModel(ModelVersion m) { models.put(m.modelId()+"@"+m.version(), m); }
    public void registerPrompt(PromptVersion p) { prompts.put(p.promptId()+"@"+p.version(), p); }
    public Optional<ModelVersion> model(String id, String version) { return Optional.ofNullable(models.get(id+"@"+version)); }
    public Optional<PromptVersion> prompt(String id, String version) { return Optional.ofNullable(prompts.get(id+"@"+version)); }
    public boolean usableModel(String id, String version) { return model(id, version).map(ModelVersion::evaluationPassed).orElse(false); }
    public boolean usablePrompt(String id, String version) { return prompt(id, version).map(PromptVersion::evaluationPassed).orElse(false); }
}
