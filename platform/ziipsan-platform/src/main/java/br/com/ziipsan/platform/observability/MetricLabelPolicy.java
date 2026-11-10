package br.com.ziipsan.platform.observability;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * II-8 (Onda II do PDCA-01, decisão 64): política de rótulos (labels) das
 * métricas — a convenção RED/USE tornada verificável por código.
 *
 * <p>Contexto: a P0.8 já impõe, com {@code TenantTagGuard} (módulo app), que
 * latência por rota NÃO leva rótulo de tenant e que o rótulo de tenant só
 * existe no contador de negócio listado. Esta política é a camada que
 * faltava para o go-live: ela enumera TODOS os rótulos permitidos da
 * convenção RED/USE e proíbe, sempre e em qualquer métrica, os
 * identificadores por unidade consumidora e demais identificadores de
 * alta cardinalidade ou pessoais. O identificador de uma UC
 * ({@code uc_id} e sinónimos) NUNCA é rótulo: uma série por UC explodiria
 * o orçamento de séries (teto de 10.000 do {@code ziipsan-app}, ver
 * {@code docs/observability/metrics-budget.yml}) e vazaria identificador
 * operacional para o sistema de métricas. UC, cliente, conta e leitura
 * consultam-se por trace/log correlacionado ({@code trace_id}), nunca
 * por série.
 *
 * <p>Três classes de rótulos:
 * <ul>
 *   <li><b>PROIBIDOS SEMPRE</b> ({@link #FORBIDDEN_LABELS}): qualquer
 *       métrica que os carregue é recusada pela guarda de cardinalidade
 *       ({@link CardinalityGuard}) e pela fachada {@link RedUseMetrics}.</li>
 *   <li><b>RESTRITOS</b> ({@link #RESTRICTED_LABELS}): {@code tenant} e
 *       {@code tenant_id} — só nas métricas da allowlist
 *       ({@link #TENANT_LABEL_ALLOWLIST}), que espelha a allowlist da
 *       P0.8 ({@code ziipsan_business_requests_total}). Fora dela, a
 *       política acusa violação; a remoção efectiva nesses medidores
 *       continua a ser a do {@code TenantTagGuard} da P0.8.</li>
 *   <li><b>PERMITIDOS</b> ({@link #ALLOWED_LABELS}): o vocabulário
 *       fechado da convenção RED/USE (ver
 *       {@code docs/observability/red-use-convencao-ii8.md}). Rótulo fora
 *       das três listas é violação: vocabulário novo entra por revisão
 *       desta política e do orçamento de séries, nunca por uso solto.</li>
 * </ul>
 *
 * <p>Classe pura, sem Spring nem Micrometer: quem decide é esta política;
 * quem impõe no registry é {@link CardinalityGuard}.
 */
public final class MetricLabelPolicy {

    /** Rótulos proibidos em qualquer métrica, sem exceção nem allowlist. */
    public static final Set<String> FORBIDDEN_LABELS = Set.of(
            // Identificadores por unidade consumidora / por entidade de domínio.
            "uc_id", "consumer_unit_id", "consumer_unit", "unit_id",
            "customer_id", "cliente_id", "user_id", "usuario_id",
            "bill_id", "conta_id", "reading_id", "leitura_id",
            "meter_id", "hidrometro_id", "service_order_id", "os_id",
            "route_id", "rota_id", "address_id", "endereco_id",
            // Identificadores pessoais / documentais (nunca em métrica).
            "cpf", "cnpj", "email", "telefone", "phone", "name", "nome",
            // Valores livres de alta cardinalidade disfarçados de rótulo.
            "trace_id", "request_id", "span_id", "ip", "url", "path");

    /** Rótulos restritos: só nas métricas da allowlist abaixo. */
    public static final Set<String> RESTRICTED_LABELS = Set.of("tenant", "tenant_id");

    /** Única métrica que pode carregar rótulo de tenant (a mesma da P0.8). */
    public static final Set<String> TENANT_LABEL_ALLOWLIST = Set.of("ziipsan_business_requests_total");

    /**
     * Vocabulário permitido da convenção RED/USE. Cada rótulo tem domínio
     * finito e pequeno, declarado em {@code red-use-convencao-ii8.md}:
     * {@code method} (verbos HTTP), {@code uri} (template de rota, nunca o
     * caminho concreto), {@code status}/{@code outcome}/{@code exception}
     * (classes do Micrometer), {@code route_group} (1º segmento após
     * {@code /api/v1}), {@code outcome} de lote/sync (SUCCESS/FAILURE),
     * {@code resource}/{@code pool} (USE de recursos nomeados e finitos).
     */
    public static final Set<String> ALLOWED_LABELS = Set.of(
            "method", "uri", "status", "outcome", "exception",
            "route_group", "error", "pool", "resource", "state",
            "probe_key", "service", "le", "quantile");

    private MetricLabelPolicy() {
    }

    /** Um problema encontrado num conjunto de rótulos de uma métrica. */
    public record Violation(String label, Kind kind) {
        public enum Kind { FORBIDDEN, RESTRICTED_NOT_ALLOWLISTED, UNKNOWN }
    }

    /**
     * Valida os rótulos de uma métrica. Devolve a lista de violações
     * (vazia = conforme). A ordem segue a ordem de entrada, sem duplicar.
     */
    public static List<Violation> validate(String metricName, Iterable<String> labels) {
        if (metricName == null || metricName.isBlank()) {
            throw new IllegalArgumentException("Nome da métrica é obrigatório para validar rótulos");
        }
        if (labels == null) {
            throw new IllegalArgumentException("Rótulos são obrigatórios (use conjunto vazio, não null)");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Violation> violations = new java.util.ArrayList<>();
        for (String label : labels) {
            if (label == null || !seen.add(label)) {
                continue;
            }
            if (FORBIDDEN_LABELS.contains(label)) {
                violations.add(new Violation(label, Violation.Kind.FORBIDDEN));
            } else if (RESTRICTED_LABELS.contains(label)) {
                if (!TENANT_LABEL_ALLOWLIST.contains(metricName)) {
                    violations.add(new Violation(label, Violation.Kind.RESTRICTED_NOT_ALLOWLISTED));
                }
            } else if (!ALLOWED_LABELS.contains(label)) {
                violations.add(new Violation(label, Violation.Kind.UNKNOWN));
            }
        }
        return List.copyOf(violations);
    }

    /** Atalho: {@code true} se algum rótulo for proibido sempre. */
    public static boolean hasForbiddenLabel(Iterable<String> labels) {
        for (String label : labels) {
            if (FORBIDDEN_LABELS.contains(label)) {
                return true;
            }
        }
        return false;
    }
}
