package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-06 (P3.25): o serviço de tiles contra um JdbcTemplate falso
 * que conta gerações e devolve corpo distinto por tenant — prova,
 * sem banco, os três aceites de comportamento do tile:
 * (1) isolamento por tenant com a chave de cache (o mesmo tile
 * pedido por outro tenant NÃO vem do cache do primeiro);
 * (2) camada pessoal (imóveis/ligações) só com gis.view-personal;
 * (3) invalidação: subiu a versão da camada, o tile regenera.
 * O SQL real (ST_AsMVT) é provado pela Gis06PublicationIT.
 */
class GisTileServiceTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID USER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    /** JdbcTemplate falso: versão por tenant + geração contada. */
    static final class FakeJdbc extends JdbcTemplate {
        final Map<UUID, Long> versions = new HashMap<>();
        final AtomicInteger generations = new AtomicInteger();

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            UUID tenant = (UUID) args[0];
            return (List<T>) List.of(versions.getOrDefault(tenant, 1L));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            generations.incrementAndGet();
            UUID tenant = (UUID) args[3];
            return (T) ("tile-of-" + tenant).getBytes(StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private static void asTenant(UUID tenant) {
        TenantContextHolder.setTenantId(tenant);
        TenantContextHolder.setUserId(USER);
    }

    @Test
    @DisplayName("isolamento: o mesmo tile de outro tenant não vem do cache")
    void cacheNeverCrossesTenants() {
        FakeJdbc jdbc = new FakeJdbc();
        GisTileService service = new GisTileService(jdbc, new GisTileCache(), () -> true);

        asTenant(TENANT_A);
        byte[] a1 = service.tile("water-arcs", 15, 100, 200).body();
        byte[] a2 = service.tile("water-arcs", 15, 100, 200).body();
        asTenant(TENANT_B);
        byte[] b1 = service.tile("water-arcs", 15, 100, 200).body();

        assertThat(new String(a1, StandardCharsets.UTF_8)).contains(TENANT_A.toString());
        assertThat(new String(b1, StandardCharsets.UTF_8)).contains(TENANT_B.toString());
        assertThat(a2).isEqualTo(a1);
        // Duas gerações (uma por tenant); o segundo pedido de A foi cache.
        assertThat(jdbc.generations.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("camada pessoal sem gis.view-personal: 403 antes de gerar")
    void personalLayerForbiddenWithoutPermission() {
        FakeJdbc jdbc = new FakeJdbc();
        GisTileService service = new GisTileService(jdbc, new GisTileCache(), () -> false);
        asTenant(TENANT_A);

        assertThatThrownBy(() -> service.tile("service-lines", 18, 100, 200))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> service.tile("properties", 15, 100, 200))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(jdbc.generations.get()).isZero();
        // Camada comum continua servida para o mesmo usuário.
        assertThat(service.tile("water-arcs", 15, 100, 200).body()).isNotEmpty();
    }

    @Test
    @DisplayName("camada pessoal com gis.view-personal: servida")
    void personalLayerServedWithPermission() {
        FakeJdbc jdbc = new FakeJdbc();
        GisTileService service = new GisTileService(jdbc, new GisTileCache(), () -> true);
        asTenant(TENANT_A);
        assertThat(service.tile("properties", 15, 100, 200).body()).isNotEmpty();
    }

    @Test
    @DisplayName("invalidação: versão nova da camada regenera o tile")
    void versionBumpRegenerates() {
        FakeJdbc jdbc = new FakeJdbc();
        GisTileService service = new GisTileService(jdbc, new GisTileCache(), () -> true);
        asTenant(TENANT_A);

        String etag1 = service.tile("water-arcs", 15, 100, 200).etag();
        service.tile("water-arcs", 15, 100, 200);
        assertThat(jdbc.generations.get()).isEqualTo(1);

        jdbc.versions.put(TENANT_A, 2L);
        String etag2 = service.tile("water-arcs", 15, 100, 200).etag();
        assertThat(jdbc.generations.get()).isEqualTo(2);
        assertThat(etag2).isNotEqualTo(etag1);
    }

    @Test
    @DisplayName("ramais abaixo de z17: tile vazio, sem gerar nem cachear erro")
    void serviceLinesBelowMinZoomAreEmpty() {
        FakeJdbc jdbc = new FakeJdbc();
        GisTileService service = new GisTileService(jdbc, new GisTileCache(), () -> true);
        asTenant(TENANT_A);
        GisTileService.TileResult result = service.tile("service-lines", 16, 100, 200);
        assertThat(result.empty()).isTrue();
        assertThat(jdbc.generations.get()).isZero();
        assertThat(service.tile("service-lines", 17, 100, 200).empty()).isFalse();
    }

    @Test
    @DisplayName("camada desconhecida: 404")
    void unknownLayerIs404() {
        GisTileService service = new GisTileService(new FakeJdbc(), new GisTileCache(),
                () -> true);
        asTenant(TENANT_A);
        assertThatThrownBy(() -> service.tile("camada-inventada", 15, 0, 0))
                .isInstanceOf(ApiException.NotFound.class);
    }
}
