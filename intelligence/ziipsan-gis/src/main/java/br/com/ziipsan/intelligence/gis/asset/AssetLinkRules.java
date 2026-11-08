package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * DAD-26: a feicao do GIS e o hidrometro APONTAM para o ativo; o
 * codigo patrimonial existe SO no ativo. Esta classe e a regra
 * executavel dessa fronteira sobre os retratos de ligacao: um
 * vinculo carrega apenas ids (feicao/hidrometro -> ativo), nunca o
 * codigo; e um mesmo ativo nao pode ser alvo de dois codigos.
 */
public final class AssetLinkRules {

    private AssetLinkRules() {
    }

    public enum LinkKind { GIS_FEATURE, METER }

    /** Vinculo feicao/hidrometro -> ativo (sem codigo patrimonial: DAD-26). */
    public record AssetLink(LinkKind kind, UUID sourceId, UUID assetId) {
        public AssetLink {
            Objects.requireNonNull(kind, "vinculo sem tipo");
            Objects.requireNonNull(sourceId, "vinculo sem origem (feicao ou hidrometro)");
            Objects.requireNonNull(assetId, "vinculo sem ativo");
        }
    }

    /** Problemas de um conjunto de vinculos frente ao inventario. */
    public static List<String> validate(List<AssetLink> links,
                                        AssetInventoryModel.InventorySnapshot inventory) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        java.util.Set<String> seenSources = new java.util.HashSet<>();
        for (AssetLink l : links) {
            if (inventory.byId(l.assetId()).isEmpty())
                problems.add("vinculo " + l.kind() + " para ativo fora do inventario: " + l.assetId());
            if (!seenSources.add(l.kind() + ":" + l.sourceId()))
                problems.add("origem ligada duas vezes: " + l.kind() + " " + l.sourceId());
        }
        return List.copyOf(problems);
    }

    /**
     * Codigos do MCPSB (P1.35) que so existem no ATIVO — nenhum deles
     * pode aparecer como atributo de feicao (DAD-26). Correcao D7 da
     * revisao da ED-56: a guarda original cobria so
     * {@code cib/uar/up}; entram tambem {@code loc_ibge}, {@code ac},
     * {@code nts_sds}, {@code at1..at5} e o alias {@code up_code}.
     */
    private static final java.util.Set<String> PATRIMONIAL_CODE_KEYS = java.util.Set.of(
            "cib", "loc_ibge", "ac", "uar", "up", "up_code", "nts_sds",
            "at1", "at2", "at3", "at4", "at5");

    /**
     * Guarda DAD-26 para qualquer mapa de atributos de feicao: se o
     * mapa trouxer chave de codigo patrimonial, a escrita e recusada
     * (o codigo mora no ativo, nao em JSONB da feicao — regra que a
     * P1.35 manda provar por teste).
     */
    public static void rejectPatrimonialCodeInFeatureAttributes(java.util.Map<String, ?> featureAttributes) {
        if (featureAttributes == null) return;
        for (String key : featureAttributes.keySet()) {
            String k = key.toLowerCase(java.util.Locale.ROOT);
            if (k.contains("patrimonial") || PATRIMONIAL_CODE_KEYS.contains(k))
                throw new IllegalArgumentException(
                        "codigo patrimonial em atributo de feicao viola o DAD-26 (o codigo so existe no ativo): " + key);
        }
    }
}
