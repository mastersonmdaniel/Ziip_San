package br.com.ziipsan.platform.work;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato por eventos da P1.38 (JOR-20): abertura e encerramento só com evento do
 * catálogo, na versão e no efeito declarados, com a carga completa.
 */
class WorkItemIngestRuleTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID REQUESTER = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();

    private static final WorkEventCatalog CATALOG = (name, version) -> {
        if (name.equals("billing.rectification.approval-requested") && version == 1) {
            return Optional.of(new WorkCatalogEvent(name, version, WorkCatalogEvent.Effect.WORK_ITEM_OPENED));
        }
        if (name.equals("billing.bill.rectified") && version == 1) {
            return Optional.of(new WorkCatalogEvent(name, version, WorkCatalogEvent.Effect.WORK_ITEM_CLOSED));
        }
        return Optional.empty();
    };

    private static WorkOutboxMessage message(String type, Map<String, Object> payload) {
        return new WorkOutboxMessage(UUID.randomUUID(), TENANT, type, Json.write(payload),
                Instant.parse("2026-03-10T13:00:00Z"));
    }

    private static Map<String, Object> openPayload() {
        return Map.of("eventVersion", 1, "workItemType", "BILL_RECTIFICATION_APPROVAL",
                "objectType", "BILL", "objectId", BILL.toString(), "originModule", "billing",
                "requesterId", REQUESTER.toString(), "eligibleApproverIds", List.of(APPROVER.toString()),
                "dueAt", "2026-03-12T15:00:00Z", "recordAddress", "/billing/bills/" + BILL, "approval", true);
    }

    @Test
    @DisplayName("abertura pelo catálogo: o rascunho carrega tipo, objeto, origem, solicitante, elegíveis, prazo, endereço e a approval_task")
    void openDraftFromCatalogEvent() {
        WorkItemIngestRule.OpenDraft draft = WorkItemIngestRule.draftOpen(
                message("billing.rectification.approval-requested", openPayload()), CATALOG);
        assertThat(draft.type()).isEqualTo(WorkItemType.BILL_RECTIFICATION_APPROVAL);
        assertThat(draft.objectId()).isEqualTo(BILL);
        assertThat(draft.originModule()).isEqualTo("billing");
        assertThat(draft.requesterId()).isEqualTo(REQUESTER);
        assertThat(draft.eligibleApproverIds()).containsExactly(APPROVER);
        assertThat(draft.dueAt()).isEqualTo(Instant.parse("2026-03-12T15:00:00Z"));
        assertThat(draft.approval()).isTrue();
        assertThat(draft.sourceEventName()).isEqualTo("billing.rectification.approval-requested");
    }

    @Test
    @DisplayName("evento fora do catálogo ou em versão não declarada é recusado (409) — nenhum item nasce")
    void eventOutsideCatalogRefuses() {
        assertThatThrownBy(() -> WorkItemIngestRule.draftOpen(
                message("billing.unknown.event", openPayload()), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
        Map<String, Object> v2 = new java.util.LinkedHashMap<>(openPayload());
        v2.put("eventVersion", 2);
        assertThatThrownBy(() -> WorkItemIngestRule.draftOpen(
                message("billing.rectification.approval-requested", v2), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("efeito trocado recusa: evento de encerramento não abre item; evento de abertura não encerra")
    void effectMismatchRefuses() {
        assertThatThrownBy(() -> WorkItemIngestRule.draftOpen(
                message("billing.bill.rectified", openPayload()), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> WorkItemIngestRule.draftClose(
                message("billing.rectification.approval-requested", openPayload()), CATALOG))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("carga sem endereçamento (ninguém é dono do item) é recusada nomeando o campo")
    void unaddressedPayloadRefuses() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(openPayload());
        payload.remove("eligibleApproverIds");
        assertThatThrownBy(() -> WorkItemIngestRule.draftOpen(
                message("billing.rectification.approval-requested", payload), CATALOG))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("encerramento pelo catálogo: rascunho com o item e o motivo")
    void closeDraftFromCatalogEvent() {
        UUID itemId = UUID.randomUUID();
        WorkItemIngestRule.CloseDraft draft = WorkItemIngestRule.draftClose(
                message("billing.bill.rectified", Map.of("eventVersion", 1,
                        "workItemId", itemId.toString(), "closeReason", "COMPLETED")), CATALOG);
        assertThat(draft.workItemId()).isEqualTo(itemId);
        assertThat(draft.closeReason()).isEqualTo("COMPLETED");
    }
}
