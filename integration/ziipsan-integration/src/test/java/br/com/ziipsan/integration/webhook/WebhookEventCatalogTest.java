package br.com.ziipsan.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.26: catálogo de eventos entregáveis por webhook — nome, versão e
 * carga (só identificadores e códigos, nunca dado pessoal).
 */
class WebhookEventCatalogTest {

    private static Map<String, Object> payload(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }

    @Test
    @DisplayName("carga completa e dentro do esquema é aceita")
    void validPayloadAccepted() {
        var def = WebhookEventCatalog.requirePublishable("billing.bill.issued", 1,
                payload("tenantId", "t", "billId", "b", "unitId", "u",
                        "referenceMonth", "2026-10-01", "status", "NORMAL"));
        assertThat(def.name()).isEqualTo("billing.bill.issued");
    }

    @Test
    @DisplayName("evento fora do catálogo, versão não declarada e campo fora do esquema são recusados")
    void unknownEventVersionAndFieldRejected() {
        assertThatThrownBy(() -> WebhookEventCatalog.requirePublishable("billing.invoice.issued", 1,
                payload("tenantId", "t"))).hasMessageContaining("fora do catálogo");
        assertThatThrownBy(() -> WebhookEventCatalog.requirePublishable("billing.bill.issued", 2,
                payload("tenantId", "t", "billId", "b", "unitId", "u",
                        "referenceMonth", "2026-10-01", "status", "NORMAL")))
                .hasMessageContaining("Versão não declarada");
        assertThatThrownBy(() -> WebhookEventCatalog.requirePublishable("billing.bill.issued", 1,
                payload("tenantId", "t", "billId", "b", "unitId", "u",
                        "referenceMonth", "2026-10-01", "status", "NORMAL", "totalValue", "123.45")))
                .hasMessageContaining("fora do esquema");
    }

    @Test
    @DisplayName("campo obrigatório ausente é recusado")
    void missingRequiredFieldRejected() {
        assertThatThrownBy(() -> WebhookEventCatalog.requirePublishable("collection.payment.received", 1,
                payload("tenantId", "t", "paymentId", "p")))
                .hasMessageContaining("obrigatório ausente");
    }

    @Test
    @DisplayName("nenhum evento do catálogo aceita campo de dado pessoal na carga")
    void noPersonalDataFieldInAnyEvent() {
        List<String> personal = List.of("name", "holderName", "customerName", "document",
                "cpf", "cnpj", "address", "phone", "email");
        for (var def : WebhookEventCatalog.ALL) {
            assertThat(def.payloadFields()).as("evento %s", def.name())
                    .doesNotContainAnyElementsOf(personal);
        }
    }

    @Test
    @DisplayName("o evento de teste tem só os campos sintéticos do botão de teste")
    void testEventFields() {
        assertThat(WebhookEventCatalog.find("integration.webhook.test")).isNotNull();
        assertThat(WebhookEventCatalog.find("integration.webhook.test").payloadFields())
                .containsExactlyInAnyOrder("tenantId", "endpointId", "sentAt");
    }
}
