package br.com.ziipsan.intelligence.gis.asset;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs da superficie da GIS-19 (visoes, sem entidade de banco). */
public final class AssetDtos {

    private AssetDtos() {
    }

    public record AssetView(UUID id, String patrimonialCode, String name, String level,
                            String equipmentClassCode, String lifecycle, String criticalityClass,
                            Integer usefulLifeYears, boolean sharedAsset, boolean reversibleAsset,
                            String up, String uar, String regionCode) {
        public static AssetView of(AssetInventoryModel.AssetRecord a) {
            return new AssetView(a.id(), a.patrimonialCode(), a.name(), a.level().name(),
                    a.equipmentClassCode(), a.lifecycle().name(), a.criticalityClass(),
                    a.usefulLifeYears(), a.sharedAsset(), a.reversibleAsset(),
                    a.codes().up(), a.codes().uar(), a.regionCode());
        }
    }

    public record PmDueView(UUID assetId, String status, LocalDate dueDate) {
    }

    public record ValveProgramView(UUID assetId, LocalDate nextExerciseDue,
                                   Double inoperablePercent, List<UUID> replacementQueue) {
    }

    /**
     * Risco exposto a ED-72 com o P×C DECOMPOSTO (correcao D8 da
     * revisao da ED-56): probabilidade, consequencia e score — a
     * superficie original omitia a consequencia.
     */
    public record RiskView(UUID assetId, Integer probability, Integer consequence,
                           Integer score, String band, String excludedReason) {
        public static RiskView of(RiskRenewalService.RiskAssessment a) {
            return new RiskView(a.assetId(), a.probability(), a.consequence(), a.score(),
                    a.band() == null ? null : a.band().name(), a.excludedReason());
        }
    }

    public record ReconciliationView(String reference, int totalLines, long divergentLines) {
    }
}
