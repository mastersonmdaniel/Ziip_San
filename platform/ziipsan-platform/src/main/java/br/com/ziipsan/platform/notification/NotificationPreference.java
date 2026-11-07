package br.com.ziipsan.platform.notification;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Linha do registro único de preferências (tabela {@code notification_preferences} da V1_15,
 * lida pela P3.5 e pela ED-41 — COE-19: a P3.36 é a dona do registro).
 *
 * <p>Dois sujeitos, uma tabela só: {@code USER} (usuário da aplicação; {@code userId}) e
 * {@code CONSUMER} (consumidor do portal; {@code customerId} + {@code contactId} verificado
 * + {@code consentId} da P1.3 — CAD-84/DAD-30). Exatamente UM sujeito por linha: os dois
 * ids juntos (ou nenhum) é ambiguidade recusada — no banco pelo CHECK da V1_15 e aqui por
 * {@link #subjectKind()} (correção pós-revisão: antes, com os dois presentes, o método
 * devolvia USER e a guarda de consentimento do consumidor era silenciosamente pulada).
 * {@code digestMode} nulo = herda {@code notify.digest_default} do tenant. Silêncio: par
 * início/fim, ambos ou nenhum.
 */
public record NotificationPreference(
        UUID id, UUID userId, UUID customerId, UUID contactId, UUID consentId,
        String purposeCode, NotificationCategory category, NotificationChannel channel,
        boolean enabled, DigestMode digestMode, LocalTime silenceStart, LocalTime silenceEnd) {

    public enum SubjectKind { USER, CONSUMER }

    /**
     * O sujeito da preferência. NUNCA presume: os dois ids presentes, ou os dois ausentes,
     * é erro explícito — o chamador decide a recusa (o serviço responde 422) em vez de o
     * gate de consentimento ser ignorado em silêncio.
     */
    public SubjectKind subjectKind() {
        if (userId != null && customerId == null) {
            return SubjectKind.USER;
        }
        if (customerId != null && userId == null) {
            return SubjectKind.CONSUMER;
        }
        throw new IllegalStateException(
                "Preferência com sujeito ambíguo ou ausente: exatamente um de userId/customerId é obrigatório.");
    }
}
