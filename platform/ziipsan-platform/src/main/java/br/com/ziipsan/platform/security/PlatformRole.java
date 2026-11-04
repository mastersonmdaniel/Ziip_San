package br.com.ziipsan.platform.security;

import java.util.Map;
import java.util.Set;

/**
 * Papéis de plataforma (P0.43, CENTRAL-ZIIP §2, ADR-0018).
 *
 * <p>Os quatro papéis vivem no realm {@code ziipsan} (infra/keycloak/realms/ziipsan-realm.json)
 * e NUNCA carregam {@code tenant_id}: o token de plataforma não abre rota de tenant
 * ({@link PlatformAccessFilter} recusa token com tenant em rota de plataforma).
 * Usuário de plataforma é marcado no realm pelo atributo {@code ziipsan_user_type=platform}
 * (ver {@link MfaPolicy}), o que obriga o segundo fator no login; na API, o segundo
 * fator é verificado no token ({@code amr}) pelo {@link MfaGateFilter}.
 *
 * <p>Matriz §2, na forma que a P0.43 fiscaliza em código (o console completo é P1.6):
 * <ul>
 *   <li>{@code PLATFORM_OWNER} — tudo; o único que elimina dados de tenant (P1.6, não aqui);</li>
 *   <li>{@code PLATFORM_OPS} — painéis técnicos, alertas, implantação, migrações, flags de
 *       rollout; revogação de sessão e bloqueio de dispositivo do app de campo (ADR-0030);</li>
 *   <li>{@code PLATFORM_SUPPORT} — painéis do tenant, fila de chamados, pedido de acesso assistido;</li>
 *   <li>{@code PLATFORM_FINANCE} — contratos, cobranças, pagamentos, cotas; suspensão e bloqueio
 *       só por inadimplência contratual (P1.6, não aqui).</li>
 * </ul>
 * <p>Permissão nova entra no catálogo junto da rota que a usa (regra da P0.13). Por isso,
 * nesta tarefa, a única permissão de plataforma no catálogo é {@code deployment:read},
 * usada por {@code GET /api/v1/platform/deployments}: OWNER e OPS a têm; SUPPORT e
 * FINANCE, não — ler o registro de implantação é operação técnica, não de suporte
 * nem de financeiro. As demais capacidades do §2 ganham permissões próprias quando
 * as rotas que as usam existirem (P1.6/P1.17), e não antes: permissão sem rota é
 * superfície de autorização sem prova.
 *
 * <p>P1.6 (console): as rotas do console existem agora, e com elas as permissões
 * {@code console:read} (ler perfil, jornada, ciclo de vida e auditoria — os quatro
 * papéis, porque suporte e financeiro leem painéis do tenant), {@code console:manage}
 * (planos, flags, jornada e holds de eliminação — OWNER e OPS), {@code console:suspend}
 * (suspensão — OWNER e FINANCE; o motivo é conferido no serviço, porque a
 * permissão sozinha não distingue inadimplência de outro motivo) e
 * {@code console:delete} (exportação e eliminação — só OWNER). A matriz fina
 * (FINANCE só com motivo inadimplência, eliminação só OWNER) continua no serviço
 * e nos testes, não só na permissão.
 *
 * <p>P1.17 (Central): {@code central:read} (abas, API de métricas v1, alertas —
 * os quatro papéis) e {@code central:manage} (coleta manual, reconhecer
 * alerta, soneca e regras — OWNER e OPS; suporte e financeiro leem, mas
 * não operam a Central).
 *
 * <p>P2.12 (contratos SaaS, ADR-0019): {@code contract:read} (contratos,
 * faturas, uso e custo — os quatro papéis), {@code contract:manage}
 * (contrato, aditivo, empenho, reajuste, fatura, pagamento, uso e custo
 * — OWNER e FINANCE) e {@code contract:restrict} (aviso previo e
 * ativacao da restricao — OWNER e FINANCE; a matriz fina, com fatura
 * vencida e aviso registrados, vive no servico).
 */
public final class PlatformRole {

    public static final String OWNER = "PLATFORM_OWNER";
    public static final String OPS = "PLATFORM_OPS";
    public static final String SUPPORT = "PLATFORM_SUPPORT";
    public static final String FINANCE = "PLATFORM_FINANCE";

    /** Todos os papéis de plataforma, na ordem do §2. */
    public static final Set<String> ALL = Set.of(OWNER, OPS, SUPPORT, FINANCE);

    /**
     * Permissões do catálogo concedidas a cada papel de plataforma. Negação por padrão:
     * papel desconhecido (ou de tenant) não concede permissão de plataforma nenhuma.
     */
    private static final Map<String, Set<String>> PERMISSIONS = Map.of(
            OWNER, Set.of("deployment:read", "console:read", "console:manage", "console:suspend", "console:delete",
                    "central:read", "central:manage", "support:queue",
                    "contract:read", "contract:manage", "contract:restrict"),
            OPS, Set.of("deployment:read", "console:read", "console:manage", "central:read", "central:manage", "support:queue",
                    "contract:read"),
            SUPPORT, Set.of("console:read", "central:read", "support:queue", "contract:read"),
            FINANCE, Set.of("console:read", "console:suspend", "central:read",
                    "contract:read", "contract:manage", "contract:restrict"));

    private PlatformRole() {
    }

    public static boolean isPlatformRole(String role) {
        return role != null && ALL.contains(role);
    }

    /** Permissões do catálogo para o papel dado; vazio quando não é papel de plataforma. */
    public static Set<String> permissionsFor(String role) {
        if (role == null) {
            return Set.of();
        }
        return PERMISSIONS.getOrDefault(role, Set.of());
    }
}
