package br.com.ziipsan.operations.metering.supervisory;

import java.util.UUID;

/**
 * Porta do segredo de assinatura do agente do supervisório, por tenant e
 * agente. A implementação lê do cofre de credenciais do tenant (o mesmo
 * cofre das credenciais de integração); este módulo nunca guarda o segredo.
 */
public interface SupervisoryAgentSecrets {

    /** O segredo vigente do agente naquele tenant; nulo quando o agente não está cadastrado. */
    String sharedSecret(UUID tenantId, String agentId);
}
