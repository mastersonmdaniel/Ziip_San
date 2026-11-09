package br.com.ziipsan.core.certification.external;

import java.util.Map;
import java.util.Set;

/**
 * Matriz de escopo por perfil do observador externo (ED-75; GES-22,
 * JOR-23) — a regua que o teste de escopo por perfil do aceite prova:
 *
 * <ul>
 *   <li>auditor de certificacao le SOMENTE o pacote de evidencia das
 *       normas ligadas;</li>
 *   <li>regulador/titular do servico le dados e relatorios
 *       regulatorios e o minimo de dado pessoal — e NAO le pacote de
 *       evidencia ISO nem dado pessoal alem do minimo;</li>
 *   <li>dado pessoal alem do minimo nao e legivel por NENHUM perfil
 *       externo.</li>
 * </ul>
 *
 * <p>Negacao e o padrao: perfil ou recurso fora da matriz nao concede
 * nada, como no catalogo de permissoes da P0.13.</p>
 */
public final class ExternalScope {

    private static final Map<ExternalProfile, Set<ExternalResource>> SCOPE = Map.of(
            ExternalProfile.CERTIFICATION_AUDITOR, Set.of(ExternalResource.EVIDENCE_PACK),
            ExternalProfile.REGULATOR_SERVICE_OWNER, Set.of(
                    ExternalResource.REGULATORY_DATA,
                    ExternalResource.REGULATORY_REPORT,
                    ExternalResource.PERSONAL_DATA_MINIMUM));

    private ExternalScope() {
    }

    /** Recursos legiveis pelo perfil (vazio para perfil desconhecido/nulo). */
    public static Set<ExternalResource> readableBy(ExternalProfile profile) {
        if (profile == null) {
            return Set.of();
        }
        return SCOPE.getOrDefault(profile, Set.of());
    }

    /** Verdadeiro somente se a matriz concede a leitura. */
    public static boolean canRead(ExternalProfile profile, ExternalResource resource) {
        return resource != null && readableBy(profile).contains(resource);
    }
}
