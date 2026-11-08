package br.com.ziipsan.reporting.bi;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ED-47: identidade do usuário do BI, vinda do SSO do Keycloak.
 *
 * <p>O {@code tenantId} é o vínculo que autoriza: ele vem de claim
 * verificada do token OIDC, resolvida pela fiação a partir do cadastro
 * do usuário no tenant (nunca de parâmetro de tela, nunca do nome do
 * schema — a P0.25 eliminou a claim {@code tenant_id} preenchida com
 * schema). Identidade sem tenant é representável de propósito, para a
 * política poder RECUSAR com auditoria em vez de a construção esconder
 * o caso: usuário da plataforma sem vínculo de tenant não usa o BI de
 * tenant algum por esta porta.
 */
public record BiUserIdentity(
        UUID userId,
        UUID tenantId,
        Set<String> roles) {

    public BiUserIdentity {
        Objects.requireNonNull(userId, "userId é obrigatório");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasTenant() {
        return tenantId != null;
    }
}
