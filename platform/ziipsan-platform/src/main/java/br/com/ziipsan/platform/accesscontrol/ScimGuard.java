package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.orgscope.GrantRule;

import java.util.Set;
import java.util.UUID;

/**
 * Guardas do provisionamento SCIM (ED-06, ADR-0040 item 5), sobre a API
 * nativa do Keycloak:
 * <ul>
 *   <li><b>Escopo por organização:</b> o token SCIM de um tenant só alcança
 *       a organização DELE — ler ou alterar usuário de outro tenant é 404
 *       (não vaza existência), o mesmo desenho do restante do produto;</li>
 *   <li><b>Teto do ADMIN:</b> o SCIM não concede papel/permissão que o ADMIN
 *       do tenant não possua (a mesma {@link GrantRule} das concessões da
 *       P3.30) — provisionar em massa não pode virar escalada silenciosa;</li>
 *   <li>SCIM desligado ({@code sec.scim_enabled=false}) recusa tudo.</li>
 * </ul>
 * O teste de isolamento entre tenants é repetido a cada atualização do
 * Keycloak (roteiro em docs/seguranca desta entrega).
 *
 * <p>PENDÊNCIA DE FIAÇÃO NOMEADA (correção D4): nesta entrega NENHUM
 * chamador de produção invoca estas guardas — falta a camada que intercepta
 * a API SCIM nativa do Keycloak (filtro/provider de implantação) chamando
 * {@link #assertScimEnabled}, {@link #assertSameTenant} e
 * {@link #assertWithinAdminCeiling} a cada requisição. Até essa fiação
 * existir, as guardas são regra pura testada, NÃO proteção aplicada.
 */
public final class ScimGuard {

    private ScimGuard() {
    }

    public static void assertScimEnabled(TenantSsoConfig config) {
        if (config == null || !config.scimEnabled()) {
            throw new ApiException.NotFound("Recurso não encontrado.");
        }
    }

    /** O tenant do TOKEN precisa ser o tenant do recurso; divergência = 404. */
    public static void assertSameTenant(UUID tokenTenantId, UUID resourceTenantId) {
        if (tokenTenantId == null || !tokenTenantId.equals(resourceTenantId)) {
            throw new ApiException.NotFound("Recurso não encontrado.");
        }
    }

    /** Papéis via SCIM ⊆ permissões do ADMIN do tenant que provisiona. */
    public static void assertWithinAdminCeiling(Set<String> adminPermissions, Set<String> requestedPermissions) {
        GrantRule.assertCanGrantPermissions(adminPermissions, requestedPermissions);
    }
}
