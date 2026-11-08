package br.com.ziipsan.reporting.bi;

import java.util.Optional;
import java.util.UUID;

/**
 * ED-47: porta de persistência do mapeamento role -&gt; tenant.
 *
 * <p>Sem porta JDBC nesta entrega: a tabela de mapeamento
 * ({@code public.reporting_bi_role_tenant}) é criada pelo SQL de
 * provisionamento na réplica, mas o espelho Java do mapeamento (usado
 * pela política de acesso e pela guarda de fontes) precisa de tabela
 * acessível à aplicação, e a faixa {@code V15_x} não reserva número
 * fechado para a ED-47 no mapa de migrations (§3) — precedente da P4.8:
 * persistência atrás de porta, reserva pedida na integração, nada de
 * número inventado.
 */
public interface BiRoleStore {

    void save(TenantBiRole mapping);

    Optional<TenantBiRole> findByTenant(UUID tenantId);

    Optional<TenantBiRole> findByRoleName(String roleName);
}
