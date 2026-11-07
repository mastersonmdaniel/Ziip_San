package br.com.ziipsan.platform.notification;

import java.util.UUID;

/**
 * Contato de e-mail resolvido no cadastro da P1.3 para um envio: o endereço NUNCA
 * vem da carga do evento (a P1.38 só permite identificadores e códigos), e sim
 * desta resolução, ligada ao contato verificado e ao consentimento da finalidade
 * (CAD-84/DAD-30, a mesma guarda das preferências da P3.36).
 */
public record RecipientContact(UUID contactId, UUID customerId, UUID userId, String emailAddress, boolean verified) {

    public RecipientContact {
        if (contactId == null || emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("contato do destinatário exige id, endereço e estado de verificação");
        }
        if (customerId == null && userId == null) {
            throw new IllegalArgumentException("contato do destinatário exige o titular (usuário ou consumidor)");
        }
    }
}
