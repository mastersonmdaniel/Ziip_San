package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectDownloadInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectFieldInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectLayerSpecInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GIS-10 (ED-55), correcao D5 da revisao cruzada: a ENTREGA do
 * arquivo — downloadProject devolve os bytes do .qgz e o
 * pg_service.conf sai com a role derivada da sessao. Os metodos de
 * entrega nao tocam o banco (JdbcTemplate nulo de proposito: se
 * algum deles passar a consultar o banco, este teste quebra).
 */
class GisQgisServiceDownloadTest {

    private static final UUID TENANT =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private final GisQgisService service = new GisQgisService(null);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private static ProjectLayerSpecInput layer(String colorToken) {
        return new ProjectLayerSpecInput("qgis_arc", "Agua - arcos", "Agua",
                colorToken, 1.5, List.of(),
                List.of(new ProjectFieldInput("material", "Material",
                        List.of("PVC", "FERRO FUNDIDO"))), 0);
    }

    @Test
    @DisplayName("download do .qgz devolve o ZIP com project.qgs da view pedida")
    void downloadReturnsQgzBytes() throws Exception {
        byte[] qgz = service.downloadProject(new ProjectDownloadInput(
                "Tenant X", "ziipsan-qgis", "auth1", List.of(layer("map-water-main"))));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(qgz))) {
            assertEquals("project.qgs", zip.getNextEntry().getName());
            String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("qgis_arc"));
            assertFalse(xml.contains("password"));
        }
    }

    @Test
    @DisplayName("download sem estilo resolvido da GIS-06 e recusado; sem camadas tambem")
    void downloadRefusesUnstyledOrEmpty() {
        assertThrows(ApiException.BadRequest.class, () ->
                service.downloadProject(new ProjectDownloadInput(
                        "T", "svc", "a1", List.of(layer(" ")))));
        assertThrows(ApiException.BadRequest.class, () ->
                service.downloadProject(new ProjectDownloadInput(
                        "T", "svc", "a1", List.of())));
    }

    @Test
    @DisplayName("pg_service.conf: role derivada da sessao (nunca do cliente), verify-full, sem senha")
    void pgServiceConfDerivesRoleFromSession() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        PgServiceConfView view = service.pgServiceConfForCurrentUser(
                new PgServiceConfInput("ziipsan-qgis", "db.local", 5433, "ziipsan"));
        String expectedRole = GisQgisRules.roleNameFor(
                TENANT.toString(), USER.toString());
        assertEquals(expectedRole, view.roleName());
        assertTrue(view.content().contains("user=" + expectedRole));
        assertTrue(view.content().contains("sslmode=verify-full"));
        assertFalse(view.content().contains("password"));
    }
}
