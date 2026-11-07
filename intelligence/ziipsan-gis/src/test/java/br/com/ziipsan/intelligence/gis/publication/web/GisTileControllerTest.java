package br.com.ziipsan.intelligence.gis.publication.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.publication.GisTileCache;
import br.com.ziipsan.intelligence.gis.publication.GisTileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GIS-06 (P3.25), correção pós-revisão A1 (bloqueante): os
 * cabeçalhos HTTP do tile. A URL do tile não carrega tenant nem
 * versão, então NENHUMA resposta (200, 204 ou 304) pode sair com
 * frescor implícito ({@code max-age} longo): o cliente revalida
 * sempre por ETag, que carrega tenant + camada + versão. Sem
 * isso, a invalidação de até 1 min do aceite não vale end-to-end
 * e um navegador compartilhado poderia servir o tile de outro
 * usuário/tenant a partir do cache local.
 */
class GisTileControllerTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    /** JdbcTemplate falso: versão 1 e corpo por camada (nós = vazio). */
    static final class FakeJdbc extends JdbcTemplate {

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            return (List<T>) List.of(1L);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            // tileArgs FEATURE: (z, x, y, tenant, z, layerKey).
            Object layerKey = args[args.length - 1];
            if ("water-nodes".equals(layerKey)) {
                return (T) new byte[0];
            }
            UUID tenant = (UUID) args[3];
            return (T) ("tile-of-" + tenant).getBytes(StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private GisTileController controller() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        return new GisTileController(new GisTileService(new FakeJdbc(), new GisTileCache()));
    }

    private static void assertRevalidationHeaders(ResponseEntity<byte[]> response) {
        String cacheControl = response.getHeaders().getCacheControl();
        assertThat(cacheControl).as("Cache-Control de %s", response.getStatusCode())
                .contains("no-cache")
                .contains("private")
                .contains("must-revalidate")
                .doesNotContain("max-age=3600")
                .doesNotContain("max-age=300");
        assertThat(response.getHeaders().getETag()).isNotBlank();
    }

    @Test
    @DisplayName("A1: tile 200 sai private + no-cache + must-revalidate, com ETag")
    void okTileRevalidates() {
        ResponseEntity<byte[]> response = controller().tile("water-arcs", 15, 100, 200, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        assertRevalidationHeaders(response);
    }

    @Test
    @DisplayName("A1: 304 (If-None-Match igual) mantém o mesmo Cache-Control de revalidação")
    void notModifiedRevalidates() {
        GisTileController controller = controller();
        String etag = controller.tile("water-arcs", 15, 100, 200, null)
                .getHeaders().getETag();
        ResponseEntity<byte[]> response = controller.tile("water-arcs", 15, 100, 200, etag);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertRevalidationHeaders(response);
    }

    @Test
    @DisplayName("A1: tile vazio 204 também revalida (não esconde feição recém-aprovada)")
    void emptyTileRevalidates() {
        ResponseEntity<byte[]> response = controller().tile("water-nodes", 15, 100, 200, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertRevalidationHeaders(response);
    }

    @Test
    @DisplayName("A1: a política compartilhada não declara max-age positivo")
    void cachePolicyHasNoPositiveMaxAge() {
        assertThat(GisTileController.tileCacheControl().getHeaderValue())
                .contains("no-cache")
                .doesNotContain("max-age");
    }
}
