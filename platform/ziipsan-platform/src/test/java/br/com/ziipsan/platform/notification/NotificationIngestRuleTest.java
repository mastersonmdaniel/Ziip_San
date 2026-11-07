package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingestão (P3.36 sobre P1.9/P1.38): catálogo manda; carga incompleta é recusa explícita. */
class NotificationIngestRuleTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final BusinessEventCatalog CATALOG = (name, version) ->
            "billing.bill.issued".equals(name) && version == 1
                    ? Optional.of(new CatalogEvent(name, 1, NotificationCategory.BILLING))
                    : Optional.empty();

    private static OutboxMessage message(String type, String payload) {
        return new OutboxMessage(UUID.randomUUID(), TENANT, type, payload, Instant.parse("2026-03-10T13:00:00Z"));
    }

    private static String payload(int version) {
        return "{\"eventVersion\":" + version + ",\"recipientUserId\":\"" + USER
                + "\",\"titleCode\":\"bill.issued.title\",\"bodyCode\":\"bill.issued.body\"}";
    }

    @Test
    @DisplayName("evento do catálogo, versão declarada: nasce o rascunho com a categoria do catálogo")
    void catalogEventBecomesDraft() {
        NotificationIngestRule.NotificationDraft draft = NotificationIngestRule.draft(message("billing.bill.issued", payload(1)), CATALOG);
        assertThat(draft.category()).isEqualTo(NotificationCategory.BILLING);
        assertThat(draft.recipientUserId()).isEqualTo(USER);
        assertThat(draft.titleCode()).isEqualTo("bill.issued.title");
    }

    @Test
    @DisplayName("o rascunho CARREGA o id da mensagem da outbox (chave de dedup da ingestão idempotente)")
    void draftCarriesSourceMessageId() {
        UUID messageId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        OutboxMessage message = new OutboxMessage(messageId, TENANT, "billing.bill.issued", payload(1),
                Instant.parse("2026-03-10T13:00:00Z"));
        NotificationIngestRule.NotificationDraft draft = NotificationIngestRule.draft(message, CATALOG);
        assertThat(draft.sourceMessageId()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("REGRA DO ACEITE: evento FORA do catálogo é recusado (409) e nenhum aviso nasce")
    void eventOutsideCatalogIsRefused() {
        assertThatThrownBy(() -> NotificationIngestRule.draft(message("unknown.event", payload(1)), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("versão NÃO declarada do evento é recusada como fora do catálogo")
    void undeclaredVersionIsRefused() {
        assertThatThrownBy(() -> NotificationIngestRule.draft(message("billing.bill.issued", payload(2)), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("carga sem destinatário é recusada nomeando o campo (nada é presumido)")
    void missingRecipientIsRefused() {
        String noRecipient = "{\"eventVersion\":1,\"titleCode\":\"t\",\"bodyCode\":\"b\"}";
        assertThatThrownBy(() -> NotificationIngestRule.draft(message("billing.bill.issued", noRecipient), CATALOG))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("carga que não é JSON válido falha pela Json do core (nunca vira aviso vazio)")
    void invalidJsonIsRefused() {
        assertThatThrownBy(() -> NotificationIngestRule.draft(message("billing.bill.issued", "{oops"), CATALOG))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("avisos do ADR-0020 (revogação/sessão assistida) entram pela mesma porta, como categoria obrigatória")
    void assistedAccessWarningsUseSameGate() {
        BusinessEventCatalog catalog = (name, version) -> {
            if (version != 1) {
                return Optional.empty();
            }
            return switch (name) {
                case "access.assisted-session.started" ->
                        Optional.of(new CatalogEvent(name, 1, NotificationCategory.SECURITY));
                case "consent.revoked" ->
                        Optional.of(new CatalogEvent(name, 1, NotificationCategory.LEGAL_NOTICE));
                default -> Optional.empty();
            };
        };
        NotificationIngestRule.NotificationDraft draft = NotificationIngestRule.draft(
                message("consent.revoked", payload(1)), catalog);
        assertThat(draft.category()).isEqualTo(NotificationCategory.LEGAL_NOTICE);
        assertThat(NotificationPolicy.isMandatory(draft.category(), java.util.Set.of())).isTrue();
    }

}
