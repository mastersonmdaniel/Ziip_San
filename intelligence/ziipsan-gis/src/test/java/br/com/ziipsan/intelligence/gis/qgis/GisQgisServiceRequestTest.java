package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GIS-10 (ED-55), correcao D6 da revisao cruzada: o pedido da api
 * nasce SOMENTE em platform.gis_db_role_request — nenhum SQL do
 * servico grava em platform.gis_db_role (essa tabela e so da rotina
 * privilegiada, como manda o acceptance). A prova captura todo SQL
 * de escrita que o servico emite ao pedir a role.
 */
class GisQgisServiceRequestTest {

    private static final UUID TENANT =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID REQUESTER =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID TARGET =
            UUID.fromString("99999999-8888-7777-6666-555555555555");

    /** JdbcTemplate que so registra os UPDATEs (nenhum banco). */
    private static final class CapturingJdbc extends JdbcTemplate {
        final List<String> updates = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql);
            return 1;
        }
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("requestRole grava so em gis_db_role_request (PENDING); gis_db_role intocada")
    void requestWritesOnlyRequestTable() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(REQUESTER);
        CapturingJdbc jdbc = new CapturingJdbc();
        GisQgisService service = new GisQgisService(jdbc);

        RoleRequestView view = service.requestRole(
                new RoleRequestInput(TARGET.toString(), null));

        assertEquals(1, jdbc.updates.size());
        String sql = jdbc.updates.get(0);
        assertTrue(sql.contains("INSERT INTO platform.gis_db_role_request"),
                "pedido tem de nascer na estrutura propria: " + sql);
        assertFalse(sql.contains("INSERT INTO platform.gis_db_role "),
                "api nunca grava gis_db_role: " + sql);
        assertFalse(sql.contains("gis_db_role_audit"),
                "auditoria de role e da rotina, nao do pedido: " + sql);
        assertEquals("PENDING", view.status());
        assertEquals(GisQgisRules.roleNameFor(TENANT.toString(), TARGET.toString()),
                view.roleName());
        assertEquals(TARGET, view.userId());
        assertEquals(TENANT, view.tenantId());
    }
}
