package br.com.ziipsan.intelligence.gis.publication;

import java.util.Objects;
import java.util.UUID;

/**
 * Chave de cache de tile da GIS-06 (§10.1): {@code (tenant, camada, z, x,
 * y, versão da camada)} — exatamente a tupla da spec. O tenant entra
 * SEMPRE: um tile gerado para um tenant jamais é servido a outro,
 * porque a chave de quem pede é montada com o tenant do contexto da
 * requisição (nunca da URL) e chaves diferentes não colidem. A versão
 * da camada entra porque é ela que garante a invalidação em até 1 min
 * do aceite: a aprovação de pacote incrementa {@code layer_version}
 * na mesma transação (V13_12), e qualquer entrada velha vira
 * inalcançável no mesmo instante — a evicção física via
 * {@code gis_tile_dirty} é limpeza, não a garantia.
 */
public record GisTileCacheKey(UUID tenantId, String layerKey, long layerVersion,
                              int z, int x, int y) {

    public GisTileCacheKey {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(layerKey, "layerKey");
        if (layerVersion < 1) {
            throw new IllegalArgumentException("layerVersion deve ser >= 1");
        }
        if (z < 0 || z > 22) {
            throw new IllegalArgumentException("z fora de 0..22: " + z);
        }
        int max = (1 << z) - 1;
        if (x < 0 || x > max || y < 0 || y > max) {
            throw new IllegalArgumentException(
                    "x/y fora da grade do zoom " + z + ": " + x + "/" + y);
        }
    }

    /** Forma textual estável (logs, ETag e depuração). */
    public String asString() {
        return "gis-tile:v1:" + tenantId + ":" + layerKey + ":v" + layerVersion
                + ":" + z + "/" + x + "/" + y;
    }

    /** ETag (§10.1: ETag pela versão — aqui, pela chave inteira). */
    public String etag() {
        return "\"" + asString() + "\"";
    }
}
