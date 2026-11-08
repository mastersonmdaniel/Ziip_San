package br.com.ziipsan.platform.ai;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Parametros de IA da ED-43 (aceite). Classes do catalogo de parametros:
 * ai.enabled_features (b, padrao DESLIGADO), ai.provider_region e ai.log_retention_days (c),
 * ai.human_review_required (a, TRAVADO para corte/negativacao/tarifa social),
 * bill.explain_enabled (GES-26), assist.voice_enabled e assist.provider (OPE-40).
 * Provedor/modelo sao configuracao do tenant: SEM default de fabrica — ausencia = IA inoperante (fail-closed). */
public interface AiParameters {
    Set<AiFeature> enabledFeatures(UUID tenantId);
    /** Regiao de processamento configurada (ex.: valor cru do tenant); vazio = nao configurado. */
    Optional<String> providerRegion(UUID tenantId);
    Optional<Integer> logRetentionDays(UUID tenantId);
    /** Revisao humana exigida para a funcao; para temas proibidos a resposta e SEMPRE true (travado, classe a). */
    boolean humanReviewRequired(UUID tenantId, AiFeature feature);
    boolean billExplainEnabled(UUID tenantId);
    boolean voiceEnabled(UUID tenantId);
    /** Identificador do provedor de assistencia configurado pelo tenant; vazio = nao configurado. */
    Optional<String> assistProvider(UUID tenantId);
    /** Identificador do modelo registrado configurado pelo tenant; vazio = nao configurado. SEM default. */
    Optional<String> modelId(UUID tenantId);
    /** Referencia da credencial no cofre (vault://...); NUNCA o valor. Vazio = sem credencial. */
    Optional<String> credentialReference(UUID tenantId);
    /** Cota de tokens e teto de custo por tenant/periodo; vazio = sem cota configurada => consumo recusado (OWASP consumo ilimitado). */
    Optional<Long> tokenQuota(UUID tenantId);
    Optional<java.math.BigDecimal> costCeiling(UUID tenantId);
}
