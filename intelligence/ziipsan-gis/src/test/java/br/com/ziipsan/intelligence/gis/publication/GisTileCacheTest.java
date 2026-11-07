package br.com.ziipsan.intelligence.gis.publication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25): o cache físico — evicção por camada despeja só o
 * tenant+camada pedidos; evicção de tile sujo mira a chave exata.
 */
class GisTileCacheTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    @DisplayName("evictLayer não toca outro tenant nem outra camada")
    void evictLayerIsScoped() {
        GisTileCache cache = new GisTileCache();
        cache.put(new GisTileCacheKey(A, "water-arcs", 1, 10, 1, 1), new byte[]{1});
        cache.put(new GisTileCacheKey(B, "water-arcs", 1, 10, 1, 1), new byte[]{2});
        cache.put(new GisTileCacheKey(A, "sewer-arcs", 1, 10, 1, 1), new byte[]{3});

        cache.evictLayer(A, "water-arcs");

        assertThat(cache.get(new GisTileCacheKey(A, "water-arcs", 1, 10, 1, 1))).isEmpty();
        assertThat(cache.get(new GisTileCacheKey(B, "water-arcs", 1, 10, 1, 1))).isPresent();
        assertThat(cache.get(new GisTileCacheKey(A, "sewer-arcs", 1, 10, 1, 1))).isPresent();
    }

    @Test
    @DisplayName("evictDirty remove só a versão/chave indicada")
    void evictDirtyIsExact() {
        GisTileCache cache = new GisTileCache();
        cache.put(new GisTileCacheKey(A, "water-arcs", 1, 10, 1, 1), new byte[]{1});
        cache.put(new GisTileCacheKey(A, "water-arcs", 2, 10, 1, 1), new byte[]{2});
        cache.evictDirty(A, "water-arcs", 1, 10, 1, 1);
        assertThat(cache.get(new GisTileCacheKey(A, "water-arcs", 1, 10, 1, 1))).isEmpty();
        assertThat(cache.get(new GisTileCacheKey(A, "water-arcs", 2, 10, 1, 1))).isPresent();
    }
}
