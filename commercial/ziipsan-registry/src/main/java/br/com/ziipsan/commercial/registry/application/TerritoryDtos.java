package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.NetworkCoverage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Corpo e resposta da hierarquia territorial. Os códigos vão até 999 porque a inscrição usa o layout
 * padrão 3.3.3.4.3 ({@code Inscription.Layout.DEFAULT}); o layout por tenant ainda não é configurável.
 * {@code version} só é exigido no PUT.
 */
public final class TerritoryDtos {

    private TerritoryDtos() {
    }

    public record LocalityView(UUID id, Long version, int code, String name, String municipalityIbge,
                               boolean headquarters, boolean computerized, boolean blocked) {
    }

    public record LocalityInput(
            @NotNull(message = "Informe o código.") @Min(0) @Max(999) Integer code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            @Pattern(regexp = "\\d{7}", message = "O código IBGE do município tem 7 dígitos.") String municipalityIbge,
            Boolean headquarters,
            Boolean computerized,
            Boolean blocked,
            Long version) {
    }

    public record SectorView(UUID id, Long version, UUID localityId, Ref locality, int code, String name,
                             boolean alternative, boolean blocked) {
    }

    public record SectorInput(
            @NotNull(message = "Informe a localidade.") UUID localityId,
            @NotNull(message = "Informe o código.") @Min(0) @Max(999) Integer code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            Boolean alternative,
            Boolean blocked,
            Long version) {
    }

    public record BlockView(UUID id, Long version, UUID sectorId, Ref sector, UUID localityId, Ref locality, int code,
                            UUID readingRouteId, Ref readingRoute, boolean hasWaterNetwork, boolean hasSewerNetwork,
                            NetworkCoverage waterNetwork, NetworkCoverage sewerNetwork, String censusSector,
                            boolean autoIncrementLot, boolean blocked) {
    }

    public record BlockInput(
            @NotNull(message = "Informe o setor.") UUID sectorId,
            @NotNull(message = "Informe o código.") @Min(0) @Max(999) Integer code,
            UUID readingRouteId,
            Boolean hasWaterNetwork,
            Boolean hasSewerNetwork,
            NetworkCoverage waterNetwork,
            NetworkCoverage sewerNetwork,
            @Size(max = 20, message = "O setor censitário tem no máximo 20 caracteres.") String censusSector,
            Boolean autoIncrementLot,
            Boolean blocked,
            Long version) {
    }

    public record RouteView(UUID id, Long version, UUID localityId, Ref locality, UUID sectorId, Ref sector,
                            int code, String name, Integer maxUnits, boolean active) {
    }

    public record RouteInput(
            @NotNull(message = "Informe o código.") @Min(0) Integer code,
            @NotBlank(message = "Informe o nome.") @Size(max = 100) String name,
            UUID localityId,
            UUID sectorId,
            @Min(value = 1, message = "O limite de imóveis na rota é de no mínimo 1.") Integer maxUnits,
            Boolean active,
            Long version) {
    }
}
