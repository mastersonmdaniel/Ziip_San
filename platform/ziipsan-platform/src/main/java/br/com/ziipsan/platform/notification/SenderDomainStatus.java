package br.com.ziipsan.platform.notification;

/**
 * Estado de autenticação do domínio de envio de UM tenant, como o provedor de e-mail
 * o reporta: SPF, DKIM e DMARC precisam estar alinhados ao domínio de
 * {@code msg.email_from_domain} para o envio ser aceito (requisito de remetente em
 * massa de Gmail, Yahoo e Microsoft, e regra da P3.5). O alinhamento é por tenant:
 * o domínio é o do tenant, nunca um domínio compartilhado da plataforma.
 */
public record SenderDomainStatus(String domain, boolean spfAligned, boolean dkimAligned, boolean dmarcAligned) {

    public SenderDomainStatus {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("o estado do domínio exige o domínio conferido");
        }
    }

    public boolean fullyAligned() {
        return spfAligned && dkimAligned && dmarcAligned;
    }
}
