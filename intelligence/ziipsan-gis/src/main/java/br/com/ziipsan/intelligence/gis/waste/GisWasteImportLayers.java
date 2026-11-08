package br.com.ziipsan.intelligence.gis.waste;

import java.util.List;
import java.util.Optional;

/**
 * Camadas de resíduos na importação (SG-1340, PARTE da GIS-14): o
 * mapeamento camada-de-origem -> tipo de resíduos, para o perfil
 * GENÉRICO da GIS-07 ({@code gis_cad_layer_map} da V13_11). É a parte
 * que cabe à GIS-14: as camadas de ÁGUA/ESGOTO dos perfis (Corsan
 * Anexo A, §9.2) são da GIS-07/P3.26 e NÃO são tocadas aqui; o
 * perfil Saneago segue sem seed (conteúdo "a validar na leitura da
 * IN 00.0229", §9.2) — nenhuma camada de resíduos é inventada para
 * ele.
 *
 * <p>Espelho Java das linhas que o SQL pendente da ED-65 insere em
 * {@code gis_cad_layer_map} (perfil GENERICO): os nomes de camada
 * são os códigos dos tipos do §4.5 — no perfil genérico, montado na
 * tela e salvo por cliente (§9.2), a convenção desta entrega é a
 * camada vir nomeada pelo tipo; o tenant renomeia no mapeamento
 * quando a origem usa outro nome, como em qualquer camada genérica.
 * Linha de SETOR vira ZONE (gis_zone); ECOPOINT vira STRUCTURE
 * (decisão do catálogo); as demais viram POI (gis_poi). Nenhuma
 * camada de resíduos é pessoal (personal_data = false): ponto de
 * coleta e contêiner não são dado pessoal.
 */
public final class GisWasteImportLayers {

    /** Uma linha de mapeamento do perfil GENÉRICO (SG-1340, parte). */
    public record WasteLayerMap(String sourceLayer, String targetKind,
                                String featureTable, String featureTypeCode) {
    }

    public static final List<WasteLayerMap> GENERIC_PROFILE_LAYERS = List.of(
            layer("COLLECTION_POINT"),
            layer("CONTAINER"),
            layer("DROP_OFF_POINT"),
            layer("ECOPOINT"),
            layer("LITTER_BIN"),
            layer("ILLEGAL_DUMP"),
            layer("COLLECTION_SECTOR"),
            layer("TRANSFER_STATION"),
            layer("SORTING_FACILITY"),
            layer("COMPOSTING_SITE"),
            layer("SANITARY_LANDFILL"),
            layer("INERT_LANDFILL"),
            layer("CONSTRUCTION_WASTE_FACILITY"));

    private static WasteLayerMap layer(String typeCode) {
        GisWasteCatalog.WasteType type = GisWasteCatalog.requireType(typeCode);
        return new WasteLayerMap(typeCode, "FEATURE", type.featureTable(), type.factoryCode());
    }

    private GisWasteImportLayers() {
    }

    public static Optional<WasteLayerMap> bySourceLayer(String sourceLayer) {
        if (sourceLayer == null) {
            return Optional.empty();
        }
        return GENERIC_PROFILE_LAYERS.stream()
                .filter(l -> l.sourceLayer().equals(sourceLayer)).findFirst();
    }

    /**
     * Completude SG-1340 (parte): todo tipo de feição do §4.5 tem
     * camada no perfil genérico e toda camada aponta para um tipo
     * existente com a tabela certa. A rota (objeto operacional) NÃO
     * é camada de importação — rota nasce de setor + grafo, não de
     * desenho.
     */
    public static boolean coversAllFeatureTypes() {
        for (GisWasteCatalog.WasteType type : GisWasteCatalog.TYPES) {
            Optional<WasteLayerMap> found = bySourceLayer(type.code());
            if (found.isEmpty()
                    || !found.get().featureTypeCode().equals(type.factoryCode())
                    || !found.get().featureTable().equals(type.featureTable())) {
                return false;
            }
        }
        return GENERIC_PROFILE_LAYERS.size() == GisWasteCatalog.TYPES.size();
    }
}
