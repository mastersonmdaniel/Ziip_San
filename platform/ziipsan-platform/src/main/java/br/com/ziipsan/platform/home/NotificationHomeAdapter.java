package br.com.ziipsan.platform.home;

import br.com.ziipsan.platform.notification.NotificationCenterService;

import java.util.List;
import java.util.UUID;

/**
 * Adaptador da porta de avisos da pagina inicial para a central da P3.36:
 * a lista e a que a central ENTREGA ao usuario (aviso retido por resumo ou
 * silencio ainda nao aparece nem la nem aqui) e o contador e o de nao
 * lidos dela. Nenhuma regra de preferencia e reaplicada aqui.
 */
public final class NotificationHomeAdapter implements HomePorts.Notifications {

    private final NotificationCenterService service;

    public NotificationHomeAdapter(NotificationCenterService service) {
        if (service == null) {
            throw new IllegalArgumentException("NotificationCenterService e obrigatorio");
        }
        this.service = service;
    }

    @Override
    public List<NotificationCenterService.NotificationView> recentOf(UUID userId, int limit) {
        return service.listForUser(userId, limit);
    }

    @Override
    public long unreadCountOf(UUID userId) {
        return service.unreadCount(userId);
    }
}
