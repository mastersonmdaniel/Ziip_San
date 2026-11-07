package br.com.ziipsan.platform.notification.web;

import br.com.ziipsan.platform.notification.DigestMode;
import br.com.ziipsan.platform.notification.NotificationCategory;
import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.NotificationPreference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * API da central (P3.36), no mesmo espírito do {@code /api/v1/me/preferences} da P1.18:
 * o usuário SÓ enxerga e SÓ altera o que é dele — o {@code sub} do token é o dono de toda
 * leitura/escrita, e o serviço ainda filtra por ele (defesa em profundidade com a RLS).
 * O frontend do sino consome {@code unread-count} e a lista; a página inicial é da P3.39.
 *
 * <p>Escopo HTTP declarado (correção pós-revisão): este controller só grava preferência
 * de sujeito USER (o dono é o {@code sub} do token). O caminho CONSUMER via HTTP depende
 * de endpoint PRÓPRIO — o do portal do consumidor, com a sua autenticação e o vínculo
 * contato/consentimento da P1.3 — e NÃO foi inventado aqui; até ele existir, preferência
 * de consumidor só nasce por dentro do serviço, onde a guarda de consentimento se aplica.
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    public record PreferenceRequest(String purposeCode, String category, String channel, boolean enabled,
                                    String digestMode, LocalTime silenceStart, LocalTime silenceEnd) {
    }

    public record ReadResult(Instant readAt) {
    }

    public record UnreadCount(long unread) {
    }

    private final NotificationCenterService service;

    public NotificationController(NotificationCenterService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    public List<NotificationCenterService.NotificationView> list(
            @AuthenticationPrincipal(expression = "subject") String subject,
            @RequestParam(defaultValue = "50") int limit) {
        return service.listForUser(UUID.fromString(subject), limit);
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal(expression = "subject") String subject) {
        return new UnreadCount(service.unreadCount(UUID.fromString(subject)));
    }

    @PostMapping("/notifications/{id}/read")
    public ReadResult markRead(@AuthenticationPrincipal(expression = "subject") String subject,
                               @PathVariable UUID id) {
        return new ReadResult(service.markRead(UUID.fromString(subject), id));
    }

    @GetMapping("/notification-preferences")
    public List<NotificationPreference> preferences(@AuthenticationPrincipal(expression = "subject") String subject) {
        return service.preferencesOfUser(UUID.fromString(subject));
    }

    @PutMapping("/notification-preferences")
    public void savePreference(@AuthenticationPrincipal(expression = "subject") String subject,
                               @RequestBody PreferenceRequest request) {
        service.savePreference(new NotificationPreference(null, UUID.fromString(subject), null, null, null,
                request.purposeCode(), NotificationCategory.valueOf(request.category()),
                NotificationChannel.valueOf(request.channel()), request.enabled(),
                request.digestMode() == null ? null : DigestMode.valueOf(request.digestMode()),
                request.silenceStart(), request.silenceEnd()));
    }
}
