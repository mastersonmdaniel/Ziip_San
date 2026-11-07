package br.com.ziipsan.platform.notification;

/**
 * Eventos de retorno da interface de e-mail da P0.45, como o provedor os reporta
 * (na referência da LC.2, as notificações do Amazon SES): a entrega confirmada, a
 * rejeição (bounce) e a reclamação de spam (complaint). São estes três eventos —
 * e só eles — que alimentam a prova de entrega da P3.5.
 */
public enum EmailEventType {
    DELIVERED, BOUNCED, COMPLAINED
}
