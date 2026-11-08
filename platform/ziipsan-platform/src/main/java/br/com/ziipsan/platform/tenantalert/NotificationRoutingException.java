package br.com.ziipsan.platform.tenantalert;

/**
 * ED-07 (correção D1 pós-reprovação): o pedido NÃO tem rota de ingestão na
 * central real da P3.36 — em particular, a falha de lote, que não é evento
 * do catálogo da P1.38. É recusa explícita e nomeada, nunca um evento
 * inventado para "caber" na ingestão: a {@code NotificationIngestRule} real
 * da P3.36 exige evento do catálogo ({@code catalog.find} não vazio) e
 * destinatário na carga, e o {@code NotificationCenterService} real só
 * expõe {@code ingest(OutboxMessage)} — não existe, na API real, método de
 * notificação direta que aceite um aviso sem evento do catálogo.
 */
public final class NotificationRoutingException extends RuntimeException {

    private final String code;

    public NotificationRoutingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static NotificationRoutingException batchFailureHasNoCatalogEvent() {
        return new NotificationRoutingException("batch-failure-not-in-catalog",
                "Falha de lote não é evento do catálogo da P1.38 e a central da P3.36 só ingere "
                        + "evento do catálogo: sem nome de evento declarado pela integração, "
                        + "não há ingestão honesta — a rota é a porta tipada de falha de lote "
                        + "(TenantAlertPorts.BatchFailureNotificationPort).");
    }

    public static NotificationRoutingException requestWithoutCatalogEvent(TenantAlert.Kind kind) {
        return new NotificationRoutingException("notification-without-catalog-event",
                "Pedido de notificação sem nome/versão de evento do catálogo não é ingerível "
                        + "pela central da P3.36 (kind=" + kind + ").");
    }
}
