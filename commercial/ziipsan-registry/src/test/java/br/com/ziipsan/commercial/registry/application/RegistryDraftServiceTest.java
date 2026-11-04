package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftInput;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.DraftUpdate;
import br.com.ziipsan.commercial.registry.application.RegistryDraftDtos.ValidateStepInput;
import br.com.ziipsan.commercial.registry.domain.RegistryDraft;
import br.com.ziipsan.commercial.registry.repository.RegistryDraftRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1.3: rascunhos do assistente de cadastro (contrato do cliente
 * {@code frontend/src/services/registry/drafts.ts}). Testes com mocks, sem banco: o repositório é
 * mockado e o tenant vem do TenantContextHolder.
 */
class RegistryDraftServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    private RegistryDraftRepository drafts;
    private RegistryDraftService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(UUID.randomUUID());
        drafts = mock(RegistryDraftRepository.class);
        service = new RegistryDraftService(drafts);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private RegistryDraft draft(String resource, String status, Long version) {
        RegistryDraft draft = new RegistryDraft();
        draft.setId(UUID.randomUUID());
        draft.setTenantId(TENANT);
        draft.setResource(resource);
        draft.setStatus(status);
        draft.setVersion(version);
        draft.setPayload("{}");
        draft.setStep(0);
        return draft;
    }

    // ---------- create / get / discard ----------

    @Test
    @DisplayName("create: recurso consumer-units vira CONSUMER_UNIT, etapa 0, status OPEN")
    void createDefaultsStepZeroAndOpen() {
        var view = service.create("consumer-units", new DraftInput(null, null, null));
        assertThat(view.resource()).isEqualTo("CONSUMER_UNIT");
        assertThat(view.step()).isEqualTo(0);
        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.data().toString()).isEqualTo("{}");
        verify(drafts).saveAndFlush(any(RegistryDraft.class));
    }

    @Test
    @DisplayName("create: recurso desconhecido é 400")
    void createUnknownResourceIsBadRequest() {
        assertThatThrownBy(() -> service.create("imoveis", new DraftInput(null, null, null)))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    @DisplayName("get: recurso divergente e tenant divergente são 404 (nunca 403)")
    void getWrongResourceOrTenantIsNotFound() {
        RegistryDraft unit = draft("CONSUMER_UNIT", "OPEN", 1L);
        when(drafts.findById(unit.getId())).thenReturn(Optional.of(unit));
        assertThatThrownBy(() -> service.get("customers", unit.getId()))
                .isInstanceOf(ApiException.NotFound.class);
        RegistryDraft foreign = draft("CUSTOMER", "OPEN", 1L);
        foreign.setTenantId(OTHER_TENANT);
        when(drafts.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.get("customers", foreign.getId()))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    @DisplayName("discard: vira DISCARDED, sem apagar")
    void discardMarksDiscarded() {
        RegistryDraft existing = draft("CUSTOMER", "OPEN", 1L);
        when(drafts.findById(existing.getId())).thenReturn(Optional.of(existing));
        service.discard("customers", existing.getId());
        assertThat(existing.getStatus()).isEqualTo("DISCARDED");
        verify(drafts).saveAndFlush(existing);
    }

    // ---------- update / versão ----------

    @Test
    @DisplayName("update: versão igual grava; versão divergente é 409; versão nula é 422; rascunho fechado é 409")
    void updateVersionRules() {
        RegistryDraft existing = draft("CUSTOMER", "OPEN", 3L);
        when(drafts.findById(existing.getId())).thenReturn(Optional.of(existing));
        var view = service.update("customers", existing.getId(),
                new DraftUpdate(Json.readTree("{\"name\":\"Nova\"}"), 1, 3L));
        assertThat(view.step()).isEqualTo(1);
        assertThatThrownBy(() -> service.update("customers", existing.getId(),
                new DraftUpdate(Json.readTree("{}"), null, 2L)))
                .isInstanceOf(ApiException.Conflict.class);
        assertThatThrownBy(() -> service.update("customers", existing.getId(),
                new DraftUpdate(Json.readTree("{}"), null, null)))
                .isInstanceOf(ApiException.Validation.class);
        existing.setStatus("DISCARDED");
        assertThatThrownBy(() -> service.update("customers", existing.getId(),
                new DraftUpdate(Json.readTree("{}"), null, 3L)))
                .isInstanceOf(ApiException.Conflict.class);
    }

    // ---------- validate-step ----------

    private static ValidateStepInput step(int step, String json) {
        return new ValidateStepInput(step, Json.readTree(json), null, List.of());
    }

    @Test
    @DisplayName("validate-step unidade: lote sem quadra e logradouro ausente são 422; etapa válida passa")
    void validateUnitSteps() {
        assertThatThrownBy(() -> service.validateStep("consumer-units", step(0, "{\"lot\":1,\"sublot\":0}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.validateStep("consumer-units", step(1, "{\"number\":\"100\"}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> service.validateStep("consumer-units",
                step(5, "{\"notes\":\"ok\"}"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate-step cliente: PF com nome fantasia e documento ausente são 422; etapa desconhecida é 400")
    void validateCustomerSteps() {
        assertThatThrownBy(() -> service.validateStep("customers",
                step(0, "{\"name\":\"Maria\",\"personType\":\"PF\",\"tradeName\":\"Loja\"}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.validateStep("customers",
                step(1, "{\"name\":\"Maria\"}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.validateStep("customers",
                step(2, "{\"emails\":[{\"address\":\"nao-email\"}]}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> service.validateStep("customers", step(9, "{}")))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    @DisplayName("validate-step: dados nulos são 422")
    void validateNullDataIsValidation() {
        assertThatThrownBy(() -> service.validateStep("customers",
                new ValidateStepInput(0, null, null, List.of())))
                .isInstanceOf(ApiException.Validation.class);
    }
}
