package br.com.ziipsan.platform.infra.events;

import java.util.Set;

/**
 * Gate de entrega ao destinatário (P1.38): um destinatário só recebe um evento
 * cuja permissão exigida o usuário ou a integração tem. Negação por padrão:
 * permissão desconhecida, conjunto vazio ou nulo não concede nada — o mesmo
 * princípio do catálogo de permissões da P0.13.
 *
 * <p>Os consumidores declarados no catálogo (central de avisos, notificações,
 * webhooks, minhas tarefas, alertas do tenant) ainda não existem como código;
 * quando existirem, cada entrega passa por aqui antes de sair da fila.
 */
public final class EventRecipientGate {

    private EventRecipientGate() {
    }

    /** Verdadeiro se o conjunto de permissões do destinatário inclui a exigida. */
    public static boolean canDeliver(BusinessEventCatalog.BusinessEvent event,
                                     Set<String> recipientPermissions) {
        if (event == null) {
            return false;
        }
        return canDeliver(event.requiredPermission(), recipientPermissions);
    }

    public static boolean canDeliver(String requiredPermission, Set<String> recipientPermissions) {
        if (requiredPermission == null || requiredPermission.isBlank()
                || recipientPermissions == null || recipientPermissions.isEmpty()) {
            return false;
        }
        return recipientPermissions.contains(requiredPermission);
    }
}
