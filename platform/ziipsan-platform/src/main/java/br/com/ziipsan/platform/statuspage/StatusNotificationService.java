package br.com.ziipsan.platform.statuspage;

import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P4.8: envio por e-mail das atualizações da página de status (P3.18) aos
 * assinantes de {@link StatusSubscription}.
 *
 * <p>Uma atualização de incidente (abertura, nova {@code status_update}
 * ou resolução com resumo publicado) gera UMA notificação por assinante
 * ATIVO e interessado em algum componente afetado — pendente de
 * confirmação e cancelado não recebem, e assinante de outro componente
 * também não. O conteúdo é o fato público da P3.18 (título, estado,
 * componentes, corpo da atualização): nada de tenant, nada interno — a
 * mesma visão filtrada da página, por e-mail.
 *
 * <p>O envio em si é a porta {@link StatusEmailPort} (produção: o
 * serviço de e-mail da P3.5/LC.2 via outbox); o corpo é montado SÓ com a
 * classe {@code Json} do core quando serializado. Este serviço não
 * conhece SMTP nem provedor.
 */
public final class StatusNotificationService {

    /** Porta de envio (produção: outbox + serviço de e-mail). */
    public interface StatusEmailPort {
        void send(String toEmail, String subject, String bodyJson);
    }

    /** Atualização pública a notificar (fato da P3.18, já filtrado para o público). */
    public record StatusUpdateNotice(String incidentTitle, String incidentStatus,
                                     List<String> affectedComponentKeys, String body) {
        public StatusUpdateNotice {
            if (incidentTitle == null || incidentTitle.isBlank()) {
                throw new IllegalArgumentException("incidentTitle é obrigatório");
            }
            Objects.requireNonNull(incidentStatus, "incidentStatus é obrigatório");
            affectedComponentKeys = affectedComponentKeys == null ? List.of()
                    : List.copyOf(affectedComponentKeys);
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("body da atualização é obrigatório");
            }
        }
    }

    /** Notificação montada para um assinante (antes do envio). */
    public record Notification(String toEmail, String subject, String bodyJson) {
    }

    private final StatusEmailPort emailPort;

    public StatusNotificationService(StatusEmailPort emailPort) {
        this.emailPort = Objects.requireNonNull(emailPort, "emailPort é obrigatório");
    }

    /** Assinantes que devem receber esta atualização (ativos + interessados). */
    public static List<StatusSubscription> recipientsFor(List<StatusSubscription> subscriptions,
                                                         StatusUpdateNotice notice) {
        Objects.requireNonNull(subscriptions, "subscriptions é obrigatório");
        Objects.requireNonNull(notice, "notice é obrigatório");
        return subscriptions.stream()
                .filter(StatusSubscription::receivesNotifications)
                .filter(s -> s.interestedInAny(notice.affectedComponentKeys()))
                .toList();
    }

    /** Corpo JSON da notificação, só pela classe {@code Json} do core. */
    public static String bodyJson(StatusUpdateNotice notice) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("incidentTitle", notice.incidentTitle());
        m.put("incidentStatus", notice.incidentStatus());
        m.put("affectedComponentKeys", notice.affectedComponentKeys());
        m.put("update", notice.body());
        m.put("source", "status-page");
        return Json.write(m);
    }

    public static String subjectFor(StatusUpdateNotice notice) {
        return "[Ziip SAN] Status: " + notice.incidentTitle() + " — " + notice.incidentStatus();
    }

    /**
     * Notifica todos os destinatários da atualização e devolve as
     * notificações enviadas (para teste e auditoria de quem chamou).
     */
    public List<Notification> notifySubscribers(List<StatusSubscription> subscriptions,
                                                StatusUpdateNotice notice) {
        String body = bodyJson(notice);
        String subject = subjectFor(notice);
        List<Notification> sent = recipientsFor(subscriptions, notice).stream()
                .map(s -> new Notification(s.email(), subject, body))
                .toList();
        sent.forEach(n -> emailPort.send(n.toEmail(), n.subject(), n.bodyJson()));
        return sent;
    }
}
