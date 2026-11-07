package br.com.ziipsan.platform.notification;

/**
 * Mensagem da outbox da P1.9 (core V8) como a central a consome: {@code type} é o nome do
 * evento, {@code payload} é JSON sem dado pessoal (só identificadores e códigos, regra da
 * P1.38) lido SEMPRE pela classe {@code Json} do core. O {@code id} é a identidade da
 * mensagem: a ingestão o grava em {@code notifications.source_message_id} e a reentrega da
 * mesma mensagem devolve o aviso existente, sem duplicar.
 *
 * <p><b>Convenção DESTA porta (correção pós-revisão, declaração explícita):</b> os campos
 * que a central exige DENTRO da carga — {@code eventVersion} (inteiro),
 * {@code recipientUserId} OU {@code recipientCustomerId}, {@code titleCode} e
 * {@code bodyCode} (códigos do catálogo de mensagens, nunca texto livre com dado pessoal)
 * — são convenção desta porta da P3.36, adotada porque o catálogo da P1.38 ainda NÃO foi
 * espelhado neste pacote. Quando ele for, o esquema real da carga manda e esta convenção
 * é revisada contra ele. Ausência de campo obrigatório é recusa explícita, não valor
 * presumido.
 */
public record OutboxMessage(java.util.UUID id, java.util.UUID tenantId, String type, String payloadJson,
                            java.time.Instant createdAt) {
}
