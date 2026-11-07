package br.com.ziipsan.platform.notification;

/**
 * Situação de uma tentativa de entrega registrada (P3.5). Nasce {@code SENT} quando o
 * gateway de e-mail aceita a mensagem e é resolvida pelo PRIMEIRO evento terminal do
 * provedor; resolvida, não regride nem aceita segundo evento terminal — a única
 * transição posterior admitida é {@code DELIVERED} → {@code COMPLAINED} (a reclamação
 * pressupõe a entrega). {@code DELIVERED} é a prova de entrega; {@code BOUNCED} e
 * {@code COMPLAINED} são a prova contrária — contam como aviso NÃO entregue na regra
 * de prova ({@link DeliveryProofRule}) e disparam os efeitos no cadastro da P1.3.
 * (Redação ajustada na revisão cruzada: o texto original dizia "muda UMA vez", mas
 * nenhuma guarda impedia a sobrescrita — a guarda vive em
 * {@code NotificationDispatchService.onEmailEvent}.)
 */
public enum DeliveryStatus {
    SENT, DELIVERED, BOUNCED, COMPLAINED
}
