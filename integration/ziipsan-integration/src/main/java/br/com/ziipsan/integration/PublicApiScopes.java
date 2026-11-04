package br.com.ziipsan.integration;

import java.util.Set;

/**
 * Escopos da API pública (P2.26), no catálogo {@code recurso:ação} do
 * ADR-0040 — os mesmos nomes que o {@code PermissionCatalog} registra e
 * que as rotas públicas declaram em {@code @PreAuthorize}. Um cliente só
 * recebe escopos deste conjunto; escopo fora dele é recusado na criação
 * do cliente (422), nunca ignorado em silêncio.
 *
 * <p>Primeira entrega (plano): leitura de properties, bills, payments e
 * service-orders, mais a escrita de service-orders. A ampliação a cada
 * módulo acrescenta escopos aqui e no catálogo, nunca reutiliza um escopo
 * de outro recurso.
 */
public final class PublicApiScopes {

    public static final String PROPERTIES_READ = "properties:read";
    public static final String BILLS_READ = "bills:read";
    public static final String PAYMENTS_READ = "payments:read";
    public static final String SERVICE_ORDERS_READ = "service-orders:read";
    public static final String SERVICE_ORDERS_WRITE = "service-orders:write";

    /** Escopos concedíveis a um cliente da API pública. */
    public static final Set<String> ALL = Set.of(
            PROPERTIES_READ, BILLS_READ, PAYMENTS_READ, SERVICE_ORDERS_READ, SERVICE_ORDERS_WRITE);

    /** Permissão do catálogo que rege as telas do tenant (clientes e webhooks). */
    public static final String INTEGRATION_MANAGE = "integration:manage";

    /** Teto da plataforma para o limite por cliente (plano: classe c, só reduz). */
    public static final int PLATFORM_RATE_LIMIT_PER_MINUTE = 1000;

    /** Validade do token de acesso opaco emitido pelo token endpoint. */
    public static final int ACCESS_TOKEN_TTL_SECONDS = 3600;

    /** Sobreposição da rotação de segredo (cliente e webhook): o segredo
     * anterior continua válido por este período depois da rotação. */
    public static final int SECRET_OVERLAP_HOURS = 24;

    /** Janela de reenvio manual de uma entrega (plano: 30 dias). */
    public static final int DELIVERY_RETRY_DAYS = 30;

    /** Falhas consecutivas que desativam um endpoint automaticamente. */
    public static final int MAX_CONSECUTIVE_FAILURES = 5;

    private PublicApiScopes() {
    }
}
