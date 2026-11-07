package br.com.ziipsan.intelligence.gis.publication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-06 (P3.25): a chave de cache do §10.1 — (tenant, camada, z, x,
 * y, versão). É ESTE teste que ancora o aceite "tile e cache nunca
 * servidos a outro tenant (teste com a chave de cache)": a chave é o
 * objeto que o serviço usa para ler e gravar, e tenants diferentes
 * produzem chaves diferentes para o mesmo tile.
 */
class GisTileCacheKeyTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    @DisplayName("mesmo tile, tenants diferentes: chaves diferentes")
    void tenantIsPartOfTheKey() {
        GisTileCacheKey a = new GisTileCacheKey(TENANT_A, "water-arcs", 3, 15, 100, 200);
        GisTileCacheKey b = new GisTileCacheKey(TENANT_B, "water-arcs", 3, 15, 100, 200);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.asString()).isNotEqualTo(b.asString());
        assertThat(a.asString()).contains(TENANT_A.toString());
    }

    @Test
    @DisplayName("versão da camada diferente: chave diferente (invalidação)")
    void versionIsPartOfTheKey() {
        GisTileCacheKey v1 = new GisTileCacheKey(TENANT_A, "water-arcs", 1, 15, 100, 200);
        GisTileCacheKey v2 = new GisTileCacheKey(TENANT_A, "water-arcs", 2, 15, 100, 200);
        assertThat(v1).isNotEqualTo(v2);
        assertThat(v1.etag()).isNotEqualTo(v2.etag());
    }

    @Test
    @DisplayName("camada, z, x e y diferentes: chaves diferentes")
    void coordinatesArePartOfTheKey() {
        GisTileCacheKey base = new GisTileCacheKey(TENANT_A, "water-arcs", 1, 15, 100, 200);
        assertThat(base).isNotEqualTo(
                new GisTileCacheKey(TENANT_A, "sewer-arcs", 1, 15, 100, 200));
        assertThat(base).isNotEqualTo(
                new GisTileCacheKey(TENANT_A, "water-arcs", 1, 16, 100, 200));
        assertThat(base).isNotEqualTo(
                new GisTileCacheKey(TENANT_A, "water-arcs", 1, 15, 101, 200));
        assertThat(base).isNotEqualTo(
                new GisTileCacheKey(TENANT_A, "water-arcs", 1, 15, 100, 201));
    }

    @Test
    @DisplayName("z/x/y fora da grade são recusados na construção")
    void outOfGridRejected() {
        assertThatThrownBy(() -> new GisTileCacheKey(TENANT_A, "water-arcs", 1, 23, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GisTileCacheKey(TENANT_A, "water-arcs", 1, 2, 4, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora da grade");
        assertThatThrownBy(() -> new GisTileCacheKey(TENANT_A, "water-arcs", 0, 2, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ETag carrega tenant, camada e versão")
    void etagCarriesVersion() {
        GisTileCacheKey key = new GisTileCacheKey(TENANT_A, "water-nodes", 7, 10, 512, 340);
        assertThat(key.etag()).contains("water-nodes").contains("v7")
                .contains(TENANT_A.toString());
    }
}
