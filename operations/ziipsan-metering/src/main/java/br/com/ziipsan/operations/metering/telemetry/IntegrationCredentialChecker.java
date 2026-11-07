package br.com.ziipsan.operations.metering.telemetry;

import java.util.UUID;

/**
 * Porta para a credencial de integração da P2.26: diz se o cliente da API
 * pública, naquele tenant, tem o escopo exigido. A implementação vive na
 * fiação da aplicação sobre os clientes da {@code V7_1} (cliente OAuth 2.0
 * por tenant, escopos no catálogo {@code recurso:ação} do ADR-0040, cota e
 * limite de taxa da P2.26); este módulo nunca vê segredo, token ou certificado.
 */
public interface IntegrationCredentialChecker {

    boolean hasScope(UUID tenantId, String clientId, String scope);
}
