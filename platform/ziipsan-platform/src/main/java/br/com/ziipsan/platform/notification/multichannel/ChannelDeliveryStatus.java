package br.com.ziipsan.platform.notification.multichannel;

/**
 * Situação de uma tentativa multicanal (ED-41). Nasce {@code SENT} quando o
 * gateway do canal aceita a mensagem e é resolvida pelo PRIMEIRO evento
 * terminal do provedor: {@code DELIVERED} é a prova de entrega;
 * {@code FAILED} (falha de entrega reportada) e {@code REJECTED} (destino
 * recusado/inválido no provedor) são a prova contrária e alimentam a
 * entregabilidade medida. Resolvida, não regride (guarda no serviço).
 */
public enum ChannelDeliveryStatus {
    SENT, DELIVERED, FAILED, REJECTED
}
