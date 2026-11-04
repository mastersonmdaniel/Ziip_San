package br.com.ziipsan.integration.webhook;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eventos entregáveis por webhook (P2.26), espelhando a seção
 * {@code events} do manifesto do pacote (P1.38).
 *
 * <p>Este catálogo em código é o ponto de execução: publicar ou
 * inscrever um evento fora dele é recusado. O manifesto
 * ({@code packages/sanitation/vertical.json}) é a fonte documental e o
 * teste de catálogo confere que todo evento daqui está lá, com o mesmo
 * esquema de carga. A carga leva só identificadores e códigos — nunca
 * dado pessoal: o validador recusa campo de dado pessoal pelo mesmo
 * elenco do validador da P1.38 (nome, documento, endereço, telefone,
 * e-mail), e o teste de carga confirma a ausência deles.
 *
 * <p>Eventos de negócio (conta emitida, pagamento recebido, OS com
 * estado alterado) ainda não têm publicador em produção — os módulos
 * donos (P2.5, P3.2, P2.28) os publicam quando existirem, pelo mesmo
 * {@link WebhookService#publish}. O evento de teste
 * ({@code integration.webhook.test}) existe desde já para o botão
 * "enviar teste" da tela de endpoints.
 */
public final class WebhookEventCatalog {

    public record EventDef(String name, int version, String requiredPermission, Set<String> payloadFields) {
    }

    public static final EventDef WEBHOOK_TEST = new EventDef(
            "integration.webhook.test", 1, "integration:manage",
            Set.of("tenantId", "endpointId", "sentAt"));

    public static final EventDef BILL_ISSUED = new EventDef(
            "billing.bill.issued", 1, "bills:read",
            Set.of("tenantId", "billId", "unitId", "referenceMonth", "status"));

    public static final EventDef PAYMENT_RECEIVED = new EventDef(
            "collection.payment.received", 1, "payments:read",
            Set.of("tenantId", "paymentId", "unitId", "documentType", "documentId", "status"));

    public static final EventDef SERVICE_ORDER_STATUS = new EventDef(
            "serviceorder.status.changed", 1, "service-orders:read",
            Set.of("tenantId", "serviceOrderId", "fromStatus", "toStatus"));

    public static final EventDef SUPPORT_TICKET_STATUS = new EventDef(
            "support.ticket.status", 1, "support:queue",
            Set.of("ticketId", "ticketNumber", "tenantId", "fromStatus", "toStatus"));

    public static final List<EventDef> ALL = List.of(
            WEBHOOK_TEST, BILL_ISSUED, PAYMENT_RECEIVED, SERVICE_ORDER_STATUS, SUPPORT_TICKET_STATUS);

    private static final Map<String, EventDef> BY_NAME = Map.of(
            WEBHOOK_TEST.name(), WEBHOOK_TEST,
            BILL_ISSUED.name(), BILL_ISSUED,
            PAYMENT_RECEIVED.name(), PAYMENT_RECEIVED,
            SERVICE_ORDER_STATUS.name(), SERVICE_ORDER_STATUS,
            SUPPORT_TICKET_STATUS.name(), SUPPORT_TICKET_STATUS);

    private static final Set<String> PERSONAL_DATA_FIELDS = Set.of(
            "name", "holderName", "customerName", "document", "cpf", "cnpj", "address",
            "phone", "email", "socialName", "birthDate");

    private WebhookEventCatalog() {
    }

    public static EventDef find(String name) {
        return BY_NAME.get(name);
    }

    /** Valida nome/versão/campos da carga; lança IllegalArgumentException se fora do catálogo. */
    public static EventDef requirePublishable(String name, int version, Map<String, Object> payload) {
        EventDef def = BY_NAME.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Evento fora do catálogo de webhooks: " + name);
        }
        if (def.version() != version) {
            throw new IllegalArgumentException("Versão não declarada para o evento " + name + ": v" + version);
        }
        if (payload == null) {
            throw new IllegalArgumentException("A carga do evento é obrigatória.");
        }
        for (String field : payload.keySet()) {
            if (!def.payloadFields().contains(field)) {
                throw new IllegalArgumentException("Campo fora do esquema do evento " + name + ": " + field);
            }
            if (PERSONAL_DATA_FIELDS.contains(field)) {
                throw new IllegalArgumentException("A carga do evento não pode ter dado pessoal: " + field);
            }
        }
        for (String required : def.payloadFields()) {
            if (!payload.containsKey(required) || payload.get(required) == null) {
                throw new IllegalArgumentException("Campo obrigatório ausente na carga do evento " + name
                        + ": " + required);
            }
        }
        return def;
    }
}
