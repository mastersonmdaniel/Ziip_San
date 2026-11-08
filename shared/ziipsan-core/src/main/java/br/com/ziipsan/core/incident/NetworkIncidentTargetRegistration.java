package br.com.ziipsan.core.incident;

import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;

/**
 * Habilita a validacao de existencia do alvo {@code NETWORK_INCIDENT}
 * da P1.31 (aceite da ED-92): registra o verificador SQL sobre a
 * tabela {@code network_incidents} desta entrega, pelo mecanismo
 * proprio do registro ({@code sqlVerifier}) — mesmo padrao com que o
 * motor registra o verificador de {@code CONSUMER_UNIT}. Sem este
 * registro, declarar tipo de OS com alvo NETWORK_INCIDENT segue
 * recusado pelo motor ("no-verifier"), como a P1.31 preve.
 */
public final class NetworkIncidentTargetRegistration {

    private NetworkIncidentTargetRegistration() {}

    public static void register(TargetVerifierRegistry registry, String schema) {
        if (registry == null) throw new IllegalArgumentException("registro de verificadores obrigatorio");
        registry.register(TargetType.NETWORK_INCIDENT,
                TargetVerifierRegistry.sqlVerifier(schema, "network_incidents"));
    }
}
