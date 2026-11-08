package br.com.ziipsan.platform.notification;

/**
 * Canal de entrega de um aviso (P3.36; AMPLIADO pela ED-41).
 * {@code IN_APP} é a própria central (sino/lista); {@code EMAIL} é o canal da
 * P3.5; {@code SMS}, {@code WHATSAPP} e {@code PUSH} são os canais externos
 * da ED-41 (notificações multicanal).
 *
 * <p><b>Alteração sobre arquivo da P3.36:</b> a ED-41 acrescenta {@code SMS} e
 * {@code WHATSAPP} a este enum — sem eles, nem o registro de preferências da
 * P3.36 nem a central comportam o opt-in por canal que o aceite da ED-41
 * exige. A ampliação dos CHECKs {@code channel} das tabelas
 * {@code notifications} e {@code notification_preferences} (V1_15) vai no SQL
 * pendente da ED-41 ({@code db/pendente/ED-41__multichannel_notifications.sql});
 * sem ele, o banco continua recusando as linhas dos canais novos.
 */
public enum NotificationChannel {
    IN_APP, EMAIL, SMS, WHATSAPP, PUSH
}
