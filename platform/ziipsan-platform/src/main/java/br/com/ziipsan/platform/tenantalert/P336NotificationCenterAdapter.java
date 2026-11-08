package br.com.ziipsan.platform.tenantalert;

import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.notification.OutboxMessage;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-07 (correção D1 pós-reprovação): o adaptador REAL entre o pedido de
 * alerta da ED-07 e a central de avisos da P3.36 — não é dublê nem promessa
 * de fiação. Ele converte {@link TenantAlertPorts.NotificationRequest} na
 * {@link OutboxMessage} REAL da P3.36 e chama
 * {@link NotificationCenterService#ingest(OutboxMessage)} de verdade; quem
 * valida catálogo, destinatário e deduplicação é a regra/serviço reais da
 * dona ({@code NotificationIngestRule} + {@code ingest} idempotente por
 * {@code source_message_id}).
 *
 * <p><b>Convenção da carga</b> — a MESMA convenção que a porta da P3.36
 * declara em {@code OutboxMessage}/{@code NotificationIngestRule}:
 * {@code eventVersion} (inteiro), {@code recipientUserId},
 * {@code titleCode} e {@code bodyCode} DENTRO do JSON, sem dado pessoal. O
 * {@code id} da mensagem é a identidade de deduplicação: no caminho de
 * evento do catálogo, o {@code sourceRef} do pedido É o id da mensagem da
 * outbox (UUID), reusado como está; fora desse formato, deriva-se um UUID
 * determinístico de (tenant, sourceRef) — nunca um aleatório, que quebraria
 * a idempotência da reentrega.
 *
 * <p><b>Falha de lote — a contradição resolvida sem fingir.</b> Um pedido
 * {@code BATCH_FAILURE} tem {@code eventName == null} de propósito (a falha
 * de lote não é evento do catálogo da P1.38) e, por construção, NÃO é
 * ingerível pela central real: {@code NotificationIngestRule.draft} recusa
 * qualquer tipo fora do catálogo e o serviço não oferece outra entrada.
 * Este adaptador, portanto, RECUSA esse pedido com
 * {@link NotificationRoutingException} em vez de inventar um nome de
 * evento. A rota real e demonstrável da falha de lote é
 * {@link #deliverBatchFailureWithDeclaredEvent}: quando a integração
 * declarar o evento no vertical do pacote batch (decisão da P1.38, fora
 * desta entrega), o aviso tipado entra pela MESMA ingestão real, com o
 * nome/versão que a FIACÃO fornecer — este pacote não fixa nome nenhum.
 * Até lá, o serviço entrega a falha pela porta tipada
 * {@link TenantAlertPorts.BatchFailureNotificationPort} e o resíduo fica
 * declarado como pendência fundamentada no RELATORIO-ED-07.
 */
public final class P336NotificationCenterAdapter implements TenantAlertPorts.NotificationCenterPort {

    private final NotificationCenterService center;
    private final Clock clock;

    public P336NotificationCenterAdapter(NotificationCenterService center, Clock clock) {
        this.center = Objects.requireNonNull(center, "a central real da P3.36 é obrigatória");
        this.clock = Objects.requireNonNull(clock, "o relógio é obrigatório");
    }

    /**
     * Entrega um alerta de EVENTO DO CATÁLOGO na central real. Pedido de
     * falha de lote (ou sem evento) é recusado com
     * {@link NotificationRoutingException} ANTES de tocar a central.
     * Supressão por preferência do destinatário é política da central
     * (a ingestão devolve vazio) e não é erro desta entrega.
     */
    @Override
    public void deliver(TenantAlertPorts.NotificationRequest request) {
        center.ingest(toOutboxMessage(request, clock.instant()));
    }

    /**
     * A rota da falha de lote QUANDO a integração declarar o evento: o nome
     * e a versão vêm do chamador (fiação/vertical do pacote batch) — se o
     * par não estiver no catálogo real, a própria ingestão da P3.36 recusa
     * com {@code ApiException.Conflict event-not-in-catalog}. Devolve o id
     * do aviso na central, ou vazio se a preferência do responsável suprimiu
     * a linha (política da P3.36).
     */
    public Optional<UUID> deliverBatchFailureWithDeclaredEvent(TenantAlertPorts.BatchFailureNotice notice,
                                                               String declaredEventName, int declaredEventVersion) {
        Objects.requireNonNull(notice, "o aviso de falha de lote é obrigatório");
        return center.ingest(batchToOutboxMessage(notice, declaredEventName, declaredEventVersion, clock.instant()));
    }

    /** Conversão pura pedido → mensagem da outbox da P3.36 (testável contra a regra real da dona). */
    public static OutboxMessage toOutboxMessage(TenantAlertPorts.NotificationRequest request, Instant createdAt) {
        Objects.requireNonNull(request, "o pedido de notificação é obrigatório");
        Objects.requireNonNull(createdAt, "o instante da mensagem é obrigatório");
        if (request.kind() == TenantAlert.Kind.BATCH_FAILURE) {
            throw NotificationRoutingException.batchFailureHasNoCatalogEvent();
        }
        if (request.eventName() == null || request.eventName().isBlank() || request.eventVersion() == null) {
            throw NotificationRoutingException.requestWithoutCatalogEvent(request.kind());
        }
        String payload = payloadJson(request.eventVersion(), request.recipientUserId(),
                request.titleCode(), request.bodyCode());
        return new OutboxMessage(messageIdOf(request.tenantId(), request.sourceRef()),
                request.tenantId(), request.eventName(), payload, createdAt);
    }

    /** Conversão do aviso tipado de lote para a ingestão real, com o evento DECLARADO pela fiação. */
    public static OutboxMessage batchToOutboxMessage(TenantAlertPorts.BatchFailureNotice notice,
                                                     String declaredEventName, int declaredEventVersion,
                                                     Instant createdAt) {
        Objects.requireNonNull(notice, "o aviso de falha de lote é obrigatório");
        Objects.requireNonNull(createdAt, "o instante da mensagem é obrigatório");
        if (declaredEventName == null || declaredEventName.isBlank() || declaredEventVersion < 1) {
            throw new IllegalArgumentException(
                    "a rota de lote à central exige o nome e a versão do evento DECLARADO pela integração");
        }
        String payload = payloadJson(declaredEventVersion, notice.recipientUserId(),
                notice.titleCode(), notice.bodyCode());
        return new OutboxMessage(messageIdOf(notice.tenantId(), notice.runRef()),
                notice.tenantId(), declaredEventName, payload, createdAt);
    }

    /** A carga na convenção da porta da P3.36: só identificadores e códigos, sem dado pessoal. */
    static String payloadJson(int eventVersion, UUID recipientUserId, String titleCode, String bodyCode) {
        Objects.requireNonNull(recipientUserId, "a carga da central exige o destinatário");
        return "{\"eventVersion\":" + eventVersion
                + ",\"recipientUserId\":\"" + recipientUserId + "\""
                + ",\"titleCode\":\"" + jsonEscape(titleCode) + "\""
                + ",\"bodyCode\":\"" + jsonEscape(bodyCode) + "\"}";
    }

    /** Identidade da mensagem: o sourceRef UUID como está; senão, UUID determinístico de (tenant, sourceRef). */
    static UUID messageIdOf(UUID tenantId, String sourceRef) {
        if (sourceRef != null) {
            try {
                return UUID.fromString(sourceRef);
            } catch (IllegalArgumentException notUuid) {
                // sourceRef não-UUID: cai para a derivação determinística abaixo.
            }
        }
        return UUID.nameUUIDFromBytes((tenantId + "|" + sourceRef).getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonEscape(String value) {
        Objects.requireNonNull(value, "código de mensagem é obrigatório na carga");
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
