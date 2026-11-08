package br.com.ziipsan.platform.portal.verify;

/**
 * Politica de remetente anti-golpe do tenant (P3.35, remetendo a P3.5):
 * DMARC rigido e remetente fixo por tenant. A infraestrutura de envio e a
 * autenticacao SPF/DKIM/DMARC do dominio sao da P3.5
 * ({@code msg.email_from_domain}, {@code SenderDomainStatus}); o que esta
 * tarefa fixa e a REGRA que a protecao contra cobranca falsa exige:
 *
 * <ul>
 *   <li>politica DMARC publicada do dominio de envio = {@code reject}
 *       ({@code p=reject}): {@code quarantine} ou {@code none} NAO bastam —
 *       com politica frouxa, o golpista falsifica o remetente do tenant e a
 *       verificacao de fatura nao protege o canal de e-mail;</li>
 *   <li>remetente fixo: UM endereco de envio por tenant, igual ao
 *       configurado — e-mail legitimo do tenant nunca sai de outro
 *       remetente, e e isso que o texto padrao ensina o consumidor a
 *       conferir;</li>
 *   <li>dominio do remetente = dominio de envio do tenant (P3.5), nunca
 *       dominio compartilhado da plataforma.</li>
 * </ul>
 */
public final class EmailSenderPolicy {

    public enum DmarcPolicy { NONE, QUARANTINE, REJECT }

    public record Assessment(boolean conforming, boolean dmarcStrict, boolean fixedSender,
                             boolean ownDomain) { }

    private EmailSenderPolicy() { }

    /**
     * @param tenantEmailDomain  dominio de {@code msg.email_from_domain} (P3.5)
     * @param publishedDmarc     politica DMARC publicada desse dominio
     * @param configuredSender   o remetente fixo configurado do tenant
     * @param actualSender       o remetente de uma remessa concreta a conferir
     */
    public static Assessment assess(String tenantEmailDomain, DmarcPolicy publishedDmarc,
                                    String configuredSender, String actualSender) {
        if (tenantEmailDomain == null || tenantEmailDomain.isBlank() || publishedDmarc == null
                || configuredSender == null || configuredSender.isBlank()
                || actualSender == null || actualSender.isBlank()) {
            throw new IllegalArgumentException(
                    "avaliacao de remetente exige dominio do tenant, DMARC publicado e remetentes configurado e efetivo");
        }
        boolean dmarcStrict = publishedDmarc == DmarcPolicy.REJECT;
        boolean fixedSender = configuredSender.strip().equalsIgnoreCase(actualSender.strip());
        String senderDomain = domainOf(actualSender);
        boolean ownDomain = senderDomain != null
                && senderDomain.equalsIgnoreCase(tenantEmailDomain.strip());
        return new Assessment(dmarcStrict && fixedSender && ownDomain, dmarcStrict, fixedSender, ownDomain);
    }

    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 || at == email.length() - 1 ? null : email.substring(at + 1).strip();
    }
}
