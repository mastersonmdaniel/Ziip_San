package br.com.ziipsan.commercial.registry.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Catálogos com tabela em V3_1: categoria, subcategoria e situações de ligação de água e de esgoto.
 *
 * <p>Nas situações, {@code billable} é a coluna {@code bills} (gera cobrança), nome que o frontend já
 * usa; {@code requiresNetwork} (só vale em quadra com a rede) e {@code connected} (há ligação física)
 * mantêm o sentido das colunas.
 *
 * <p>P0.16 opção 1 (V3_7): os campos que o frontend já envia (catalogDefinitions.ts) passam a ser
 * aceitos e devolvidos. Nenhum deles é obrigatório no backend — um payload sem os novos campos
 * continua funcionando — e nenhuma regra de validação existente muda ({@code code} continua
 * obrigatório). Campos próprios do frontend, sem fusão por conta própria: {@code hasNetwork} /
 * {@code hasConnection} convivem com {@code requiresNetwork} / {@code connected}, e
 * {@code minimumBillingConsumption} / {@code minimumBillingVolume} (numéricos) convivem com
 * {@code minimumConsumption} (flag booleana).
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryView(UUID id, Long version, String code, String name, String abbreviation,
                               Integer minimumConsumption, Integer economiesSoftLimit,
                               boolean requiresSpecialPermission, boolean publicEntity, boolean active) {
    }

    public record CategoryInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            @Size(max = 5) String abbreviation,
            @Min(0) Integer minimumConsumption,
            @Min(1) Integer economiesSoftLimit,
            Boolean requiresSpecialPermission,
            Boolean publicEntity,
            Boolean active,
            Long version) {
    }

    public record SubcategoryView(UUID id, Long version, UUID categoryId, Ref category, String code, String name,
                                  String abbreviation, Integer inspectionFactor, boolean seasonalSupply,
                                  boolean active) {
    }

    public record SubcategoryInput(
            @NotNull(message = "Informe a categoria.") UUID categoryId,
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            @Size(max = 20) String abbreviation,
            @Min(0) Integer inspectionFactor,
            Boolean seasonalSupply,
            Boolean active,
            Long version) {
    }

    public record WaterStatusView(UUID id, Long version, String code, String name, String abbreviation,
                                  Integer minimumBillingConsumption, boolean hasNetwork, boolean hasConnection,
                                  boolean billable, boolean requiresNetwork, boolean connected, boolean supplies,
                                  boolean minimumConsumption, boolean active) {
    }

    public record WaterStatusInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            @Size(max = 5) String abbreviation,
            @Min(0) Integer minimumBillingConsumption,
            Boolean hasNetwork,
            Boolean hasConnection,
            Boolean billable,
            Boolean requiresNetwork,
            Boolean connected,
            Boolean supplies,
            Boolean minimumConsumption,
            Boolean active,
            Long version) {
    }

    public record SewerStatusView(UUID id, Long version, String code, String name, String abbreviation,
                                  BigDecimal minimumBillingVolume, boolean hasNetwork, boolean hasConnection,
                                  boolean billable, boolean requiresNetwork, boolean connected,
                                  boolean minimumConsumption, boolean active) {
    }

    public record SewerStatusInput(
            @NotBlank(message = "Informe o código.") @Size(max = 20) String code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            @Size(max = 5) String abbreviation,
            @DecimalMin("0") BigDecimal minimumBillingVolume,
            Boolean hasNetwork,
            Boolean hasConnection,
            Boolean billable,
            Boolean requiresNetwork,
            Boolean connected,
            Boolean minimumConsumption,
            Boolean active,
            Long version) {
    }
}
