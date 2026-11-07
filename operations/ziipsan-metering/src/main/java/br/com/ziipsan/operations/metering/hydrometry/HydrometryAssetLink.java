package br.com.ziipsan.operations.metering.hydrometry;

/**
 * Hidrômetro como ATIVO (ED-14): classe própria no catálogo de ativos da P1.27/P1.35,
 * com a ficha no {@code asset} (V27_1) — {@code meters.asset_id} referencia o ativo e
 * NENHUM código patrimonial é gravado no metering (regra da P1.35: código só no ativo).
 * O código da classe é a chave estável a semear no catálogo na integração com a P1.35.
 */
public final class HydrometryAssetLink {
    private HydrometryAssetLink() {}
    /** Chave estável da classe de ativo do hidrômetro no catálogo (P1.27). */
    public static final String ASSET_CLASS_CODE = "HIDROMETRO";
    /** Nível da hierarquia de seis níveis da P1.35 em que o hidrômetro se registra: EQUIPAMENTO. */
    public static final String ASSET_LEVEL = "EQUIPMENT";

    public static void requireAssetLink(java.util.UUID assetId) {
        if (assetId == null) throw new IllegalArgumentException(
            "Hidrômetro como ativo exige meters.asset_id para o asset da P1.35 (classe " + ASSET_CLASS_CODE + ")");
    }
}
