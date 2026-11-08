package br.com.ziipsan.intelligence.gis.qgis;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contratos da GIS-10: pedido de role QGIS (a CRIACAO da role e da
 * rotina privilegiada de implantacao — a api registra o pedido em
 * estrutura propria, platform.gis_db_role_request, e le o estado;
 * gis_db_role so a rotina grava, correcao D6), politica do endpoint
 * e entrega do projeto .qgz e do pg_service.conf (correcao D5).
 */
public final class GisQgisDtos {

    private GisQgisDtos() {
    }

    /** Pedido do administrador do tenant (core V12: nasce PENDING em
     *  platform.gis_db_role_request; REQUESTED e status da role ja
     *  materializada em platform.gis_db_role). */
    public record RoleRequestInput(
            @NotBlank String userId,
            Instant validUntil) {
    }

    public record RoleView(
            UUID id,
            String roleName,
            UUID tenantId,
            UUID userId,
            String status,
            Instant validUntil,
            int connectionLimit,
            int statementTimeoutMs) {
    }

    /**
     * Pedido registrado (platform.gis_db_role_request): PENDING ate a
     * rotina privilegiada decidir e materializar a role (correcao D6).
     */
    public record RoleRequestView(
            UUID id,
            String roleName,
            UUID tenantId,
            UUID userId,
            String status,
            Instant validUntil) {
    }

    /** Campo do formulario para a camada do download do .qgz. */
    public record ProjectFieldInput(
            @NotBlank String name,
            @NotBlank String label,
            List<String> valueList) {
    }

    /**
     * Camada do download do .qgz: como o ProjectLayerInput, mas com o
     * estilo JA resolvido da fonte unica da GIS-06 (sem estilo, a
     * fabrica recusa) — a resolucao e do chamador/integracao.
     */
    public record ProjectLayerSpecInput(
            @NotBlank String viewName,
            @NotBlank String label,
            @NotBlank String groupLabel,
            @NotBlank String colorToken,
            double lineWidth,
            List<Double> dash,
            List<ProjectFieldInput> fields,
            int minScaleDenominator) {
    }

    /** Pedido de download do .qgz (correcao D5). */
    public record ProjectDownloadInput(
            @NotBlank String title,
            @NotBlank String serviceName,
            @NotBlank String authCfgId,
            List<ProjectLayerSpecInput> layers) {
    }

    /**
     * Pedido do pg_service.conf (correcao D5): host/porta/banco do
     * endpoint dedicado; o nome da role NUNCA vem do cliente — e
     * derivado no servidor do tenant+usuario da sessao.
     */
    public record PgServiceConfInput(
            @NotBlank String serviceName,
            @NotBlank String host,
            int port,
            @NotBlank String dbName) {
    }

    public record PgServiceConfView(
            String serviceName,
            String roleName,
            String content) {
    }

    /** Politica do endpoint dedicado (§7.1 item 5). */
    public record EndpointPolicyInput(
            boolean tlsRequired,
            @NotBlank String sslMode,
            boolean viaPooler,
            boolean vpnRequired,
            String allowedCidr) {
    }

    /** Camada pedida para o .qgz; o estilo vem resolvido da GIS-06. */
    public record ProjectLayerInput(
            @NotBlank String viewName,
            @NotBlank String label,
            @NotBlank String groupLabel) {
    }

    public record ProjectView(
            String fileName,
            String serviceName,
            List<String> layers) {
    }
}
