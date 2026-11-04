package br.com.ziipsan.platform.infra.events;

import br.com.ziipsan.core.json.Json;

import java.util.List;

/**
 * Catálogo de runtime dos eventos de chamado (P1.23).
 *
 * <p>A fonte da verdade é {@code packages/sanitation/vertical.json} (seção
 * {@code events}), de onde sai o documento gerado
 * {@code docs/arquitetura/business-event-catalog.md}. A imagem de produção
 * NÃO embarca {@code packages/} (ver Dockerfile: só os módulos Maven são
 * copiados), então o publicador não pode ler o manifesto em runtime — e
 * esta classe carrega a mesma declaração, em código. A paridade é fiscalizada
 * em teste ({@code SupportEventCatalogParityTest}: nome, versão, esquema da
 * carga, permissão, consumidores e tarefa de origem têm de ser idênticos aos
 * do manifesto); divergência barra o build. Quando a imagem passar a embarcar
 * o manifesto, este catálogo deve ser substituído pela leitura dele
 * (integração), não duplicado uma terceira vez.
 */
public final class SupportEventCatalog {

    private SupportEventCatalog() {
    }

    public static BusinessEventCatalog catalog() {
        BusinessEventCatalog.DeclaredConsumer central =
                new BusinessEventCatalog.DeclaredConsumer("P1.17", "Central Ziip — fila de chamados");
        List<BusinessEventCatalog.BusinessEvent> events = List.of(
                new BusinessEventCatalog.BusinessEvent(
                        "support.ticket.created", 1,
                        "Chamado de suporte aberto pelo tenant (P1.23). A carga leva só identificadores e códigos; o e-mail de aviso leva protocolo, estado e link, sem conteúdo nem anexo (ADR-0020 decisão 5).",
                        Json.readTree("""
                                {"type":"object","properties":{
                                "ticketId":{"type":"string","format":"uuid"},
                                "ticketNumber":{"type":"string"},
                                "tenantId":{"type":"string","format":"uuid"},
                                "status":{"type":"string","enum":["NEW"]},
                                "category":{"type":"string","enum":["QUESTION","INCIDENT","REQUEST","IMPROVEMENT","SECURITY","PRIVACY"]},
                                "severity":{"type":"string","enum":["S1","S2","S3","S4","S5"]}},
                                "required":["ticketId","ticketNumber","tenantId","status","category","severity"]}"""),
                        "support:queue", List.of("P1.17"), "P1.23"),
                new BusinessEventCatalog.BusinessEvent(
                        "support.ticket.message", 1,
                        "Mensagem nova num chamado de suporte (P1.23). Sem corpo na carga: o consumidor busca a conversa pela API, com a visibilidade devida (nota interna nunca vai ao tenant).",
                        Json.readTree("""
                                {"type":"object","properties":{
                                "ticketId":{"type":"string","format":"uuid"},
                                "ticketNumber":{"type":"string"},
                                "tenantId":{"type":"string","format":"uuid"},
                                "messageId":{"type":"string","format":"uuid"},
                                "authorType":{"type":"string","enum":["REQUESTER","AGENT","SYSTEM"]},
                                "visibility":{"type":"string","enum":["PUBLIC","INTERNAL"]}},
                                "required":["ticketId","ticketNumber","tenantId","messageId","authorType","visibility"]}"""),
                        "support:queue", List.of("P1.17"), "P1.23"),
                new BusinessEventCatalog.BusinessEvent(
                        "support.ticket.status", 1,
                        "Estado do chamado alterado pelo agente ou pelo solicitante (P1.23). A carga leva só os estados anterior e novo, em código.",
                        Json.readTree("""
                                {"type":"object","properties":{
                                "ticketId":{"type":"string","format":"uuid"},
                                "ticketNumber":{"type":"string"},
                                "tenantId":{"type":"string","format":"uuid"},
                                "fromStatus":{"type":"string","enum":["NEW","IN_PROGRESS","WAITING_CUSTOMER","RESOLVED","CLOSED","CANCELLED"]},
                                "toStatus":{"type":"string","enum":["NEW","IN_PROGRESS","WAITING_CUSTOMER","RESOLVED","CLOSED","CANCELLED"]}},
                                "required":["ticketId","ticketNumber","tenantId","fromStatus","toStatus"]}"""),
                        "support:queue", List.of("P1.17"), "P1.23"));
        BusinessEventCatalog catalog = new BusinessEventCatalog(events, List.of(central), List.of());
        BusinessEventCatalogValidator.validate(catalog);
        return catalog;
    }
}
