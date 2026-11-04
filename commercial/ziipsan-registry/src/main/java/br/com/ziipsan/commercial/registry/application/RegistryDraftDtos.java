package br.com.ziipsan.commercial.registry.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

/**
 * Rascunhos do assistente de cadastro (P1.3, retomada da camada C). O contrato é o do cliente
 * {@code frontend/src/services/registry/drafts.ts}: POST cria, PUT atualiza
 * com versão (409 = mudou), GET retoma, DELETE descarta, POST validate-step
 * valida UMA etapa sem gravar (200 ok; 422 problem+json com errors[] quando
 * há erro bloqueante; só warnings[] pede confirmação com confirmTokens).
 */
public final class RegistryDraftDtos {

    private RegistryDraftDtos() {
    }

    public record DraftInput(JsonNode data, Integer step, UUID targetId) {
    }

    public record DraftUpdate(JsonNode data, Integer step, Long version) {
    }

    public record DraftView(UUID id, Long version, String resource, UUID targetId, int step,
                            JsonNode data, String status) {
    }

    public record ValidateStepInput(
            @NotNull(message = "Informe a etapa.") @Min(value = 0, message = "A etapa começa em 0.") Integer step,
            @NotNull(message = "Informe os dados da etapa.") JsonNode data,
            UUID targetId,
            List<String> confirmTokens) {
    }
}
