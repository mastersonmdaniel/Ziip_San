package br.com.ziipsan.commercial.registry.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Catálogos com tabela em V3_1: categoria, subcategoria e situações de ligação de água e de esgoto.
 *
 * <p>Nas situações, {@code billable} é a coluna {@code bills} (gera cobrança), nome que o frontend já
 * usa; {@code requiresNetwork} (só vale em quadra com a rede) e {@code connected} (há ligação física)
 * mantêm o sentido das colunas. Não há abreviatura nem consumo mínimo numérico no modelo.
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryView(UUID id, Long version, String code, String name, boolean publicEntity, boolean active) {
    }

    public record CategoryInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            Boolean publicEntity,
            Boolean active,
            Long version) {
    }

    public record SubcategoryView(UUID id, Long version, UUID categoryId, Ref category, String code, String name,
                                  boolean active) {
    }

    public record SubcategoryInput(
            @NotNull(message = "Informe a categoria.") UUID categoryId,
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            Boolean active,
            Long version) {
    }

    public record WaterStatusView(UUID id, Long version, String code, String name, boolean billable,
                                  boolean requiresNetwork, boolean connected, boolean supplies,
                                  boolean minimumConsumption, boolean active) {
    }

    public record WaterStatusInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            Boolean billable,
            Boolean requiresNetwork,
            Boolean connected,
            Boolean supplies,
            Boolean minimumConsumption,
            Boolean active,
            Long version) {
    }

    public record SewerStatusView(UUID id, Long version, String code, String name, boolean billable,
                                  boolean requiresNetwork, boolean connected, boolean minimumConsumption,
                                  boolean active) {
    }

    public record SewerStatusInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            Boolean billable,
            Boolean requiresNetwork,
            Boolean connected,
            Boolean minimumConsumption,
            Boolean active,
            Long version) {
    }
}
