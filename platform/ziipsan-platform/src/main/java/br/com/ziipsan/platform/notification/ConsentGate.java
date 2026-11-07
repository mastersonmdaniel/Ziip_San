package br.com.ziipsan.platform.notification;

import java.util.UUID;

/**
 * Porta para o cadastro da P1.3 (CAD-84, DAD-30): a preferência por finalidade do
 * CONSUMIDOR só vale ligada a um contato VERIFICADO e a um consentimento vigente para
 * aquela finalidade (catálogo de finalidades da P1.3, com FK). Sem contato verificado ou
 * sem consentimento, a gravação da preferência é recusada — nunca presumida.
 */
public interface ConsentGate {
    boolean isVerifiedContact(UUID tenantId, UUID contactId);

    /**
     * Verdade se ESTE consentimento — o {@code consentId} gravado na preferência, e não
     * "algum" consentimento do consumidor — está vigente para o contato e a finalidade.
     * Correção pós-revisão: a assinatura anterior não recebia o consentId e aceitava
     * qualquer consentimento ativo do par (cliente, contato, finalidade); agora o gate
     * confere o consentimento exato que a preferência declara.
     */
    boolean hasActiveConsent(UUID tenantId, UUID customerId, UUID contactId, UUID consentId, String purposeCode);
}
