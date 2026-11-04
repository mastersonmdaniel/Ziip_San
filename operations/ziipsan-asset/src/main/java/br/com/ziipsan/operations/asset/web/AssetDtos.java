package br.com.ziipsan.operations.asset.web;

import br.com.ziipsan.operations.asset.AssetService.AssetInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DTOs da API de ativos (P1.35). O tenant vem sempre do contexto, nunca do corpo. */
public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetInputDto(
            @NotNull UUID orderId,
            @NotBlank String code,
            UUID parentAssetId,
            @Min(1) @Max(6) int hierarchyLevel,
            String assetClass,
            @NotBlank String catalogTypeCode,
            String gisFeatureKind,
            UUID gisFeatureId,
            boolean isBuilding,
            boolean confinedSpace,
            String cib,
            @NotBlank String locIbge,
            String ac,
            String ntsSds,
            String up,
            String uar,
            String at1,
            String at2,
            String at3,
            String at4,
            String at5,
            @NotBlank String controlMode,
            String unitOfMeasure,
            boolean bcp,
            boolean brv,
            String manufacturer,
            String model,
            String serialNumber,
            Integer manufactureYear,
            BigDecimal powerKw,
            BigDecimal capacity,
            String capacityUnit,
            LocalDate inServiceOn,
            String lifecycleStatus,
            String locationText) {

        public AssetInput toInput() {
            return new AssetInput(code, parentAssetId, hierarchyLevel, assetClass,
                    catalogTypeCode, gisFeatureKind, gisFeatureId, isBuilding, confinedSpace,
                    cib, locIbge, ac, ntsSds, up, uar, at1, at2, at3, at4, at5,
                    controlMode, unitOfMeasure, bcp, brv, manufacturer, model, serialNumber,
                    manufactureYear, powerKw, capacity, capacityUnit, inServiceOn,
                    lifecycleStatus, locationText);
        }
    }
}
