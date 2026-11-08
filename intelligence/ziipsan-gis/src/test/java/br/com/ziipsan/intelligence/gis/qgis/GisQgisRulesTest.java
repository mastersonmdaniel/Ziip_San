package br.com.ziipsan.intelligence.gis.qgis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GIS-10 (ED-55): regras puras da role QGIS (§7.1 itens 1, 2, 5). */
class GisQgisRulesTest {

    private static final String TENANT = "11111111-2222-3333-4444-555555555555";
    private static final String USER = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test
    @DisplayName("nome da role e individual e deterministico por tenant+usuario")
    void roleNameIsIndividual() {
        String a = GisQgisRules.roleNameFor(TENANT, USER);
        assertEquals(a, GisQgisRules.roleNameFor(TENANT, USER));
        assertTrue(GisQgisRules.isQgisRoleName(a));
        assertFalse(a.equals(GisQgisRules.roleNameFor(TENANT,
                "aaaaaaaa-bbbb-cccc-dddd-ffffffffffff")));
        assertFalse(a.equals(GisQgisRules.roleNameFor(
                "99999999-2222-3333-4444-555555555555", USER)));
    }

    @Test
    @DisplayName("aprovacao da role e de outra pessoa (quatro olhos)")
    void approvalIsFourEyes() {
        GisQgisRules.ensureFourEyes("u1", "u2");
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureFourEyes("u1", "u1"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureFourEyes("u1", null));
    }

    @Test
    @DisplayName("acesso so ACTIVE dentro da validade; revogada/vencida nao acessa")
    void accessOnlyActiveAndValid() {
        Instant now = Instant.parse("2026-11-07T00:00:00Z");
        assertTrue(GisQgisRules.hasAccess("ACTIVE",
                Instant.parse("2026-12-01T00:00:00Z"), now));
        assertFalse(GisQgisRules.hasAccess("REVOKED",
                Instant.parse("2026-12-01T00:00:00Z"), now));
        assertFalse(GisQgisRules.hasAccess("SUSPENDED",
                Instant.parse("2026-12-01T00:00:00Z"), now));
        assertFalse(GisQgisRules.hasAccess("ACTIVE",
                Instant.parse("2026-10-01T00:00:00Z"), now));
        assertFalse(GisQgisRules.hasAccess("ACTIVE", null, now));
    }

    @Test
    @DisplayName("endpoint: TLS verify-full, fora do pooler, VPN ou CIDR")
    void endpointPolicy() {
        GisQgisRules.ensureEndpointPolicy(true, "verify-full", false, true, null);
        GisQgisRules.ensureEndpointPolicy(true, "verify-full", false, false, "10.0.0.0/8");
        assertThrows(IllegalArgumentException.class, () ->
                GisQgisRules.ensureEndpointPolicy(true, "require", false, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                GisQgisRules.ensureEndpointPolicy(false, "verify-full", false, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                GisQgisRules.ensureEndpointPolicy(true, "verify-full", true, true, null));
        assertThrows(IllegalArgumentException.class, () ->
                GisQgisRules.ensureEndpointPolicy(true, "verify-full", false, false, null));
    }

    @Test
    @DisplayName("grant so nas views qgis_*; tabela base e recusada")
    void grantsOnlyOnViews() {
        GisQgisRules.ensureGrantTarget("qgis_arc");
        GisQgisRules.ensureGrantTarget("qgis_edit_node");
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureGrantTarget("gis_arc"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureGrantTarget("gis_change_set"));
    }

    @Test
    @DisplayName("base sob security_invoker (correcao A1): so SELECT minimo nas 5 tabelas das views; escrita na base recusada")
    void baseTableGrantsAreMinimalSelectOnly() {
        // A prescricao anterior ("NADA nas tabelas base") era
        // incompativel com security_invoker: sem SELECT na base, a
        // role recebia permission denied em toda leitura/edicao pelas
        // views (provado em PostgreSQL real). O minimo e SELECT nas
        // cinco tabelas sob as views de leitura — e nada alem disso.
        for (String table : java.util.List.of("gis_node", "gis_arc",
                "gis_structure", "gis_connection", "gis_zone")) {
            GisQgisRules.ensureBaseTableGrant(table, "SELECT");
        }
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_node", "INSERT"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_node", "UPDATE"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_node", "DELETE"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_change_set", "SELECT"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_change_set_item", "SELECT"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("gis_poi", "SELECT"));
        assertThrows(IllegalArgumentException.class,
                () -> GisQgisRules.ensureBaseTableGrant("qgis_node", "SELECT"));
    }
}
