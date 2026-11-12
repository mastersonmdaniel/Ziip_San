package br.com.ziipsan.app;

import br.com.ziipsan.core.incident.NetworkIncident;
import br.com.ziipsan.core.incident.NetworkIncidentTargetRegistration;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IT de banco da ED-92: aplica o SQL pendente, abre/encerra ocorrencia
 * validando causa, unicidade do codigo por tenant, RLS e o verificador
 * NETWORK_INCIDENT contra service_orders. Portao: CI.
 *
 * <p>Implementacao (lote correcao-e1-s6, Grupo D): segue o padrao dos ITs
 * irmaos {@code Gis11ManeuverIT}/{@code Gis03TopologyIT} — schema
 * descartavel por teste sobre o banco de integracao
 * ({@link ProvisionedTenant}), migrations legadas + V9_1 (motor de OS da
 * P1.31) + o SQL pendente {@code db/pendente/ED-92__network_incidents.sql},
 * todos do classpath com {@code ${tenant_id}} substituido. A GUC
 * {@code app.current_tenant} e fixada por sessao em cada ligacao (a RLS e
 * FORCE, vale tambem para o dono das tabelas).
 *
 * <p>Camadas da validacao de causa, sem inventar comportamento: no banco,
 * o CHECK {@code ck_ni_closed_cause} recusa CLOSED sem causa nao-vazia; na
 * entidade, {@link NetworkIncident#close} valida a causa contra o catalogo
 * vigente do tenant ({@code outage.cause_catalog}) — as duas sao
 * exercidas. O catalogo NAO e semeado pelo SQL da ED-92 (parametro do
 * tenant); o teste da entidade usa o catalogo declarado no proprio teste,
 * como faria a fiacao que le o parametro.
 */
class NetworkIncidentIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql");

    private static final List<String> INCIDENT_MIGRATIONS = List.of(
            // V9_1: service_orders da P1.31 (alvo do verificador).
            "db/migration/tenant/V9_1__service_order_engine.sql",
            // ED-92 (pendente de numeracao na entrega; na integracao recebe
            // o numero da sequencia global e muda para db/migration/tenant/).
            "db/pendente/ED-92__network_incidents.sql");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private record Scratch(UUID tenant, String schema) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_ed92_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(sql(m, tenant));
        }
        for (String m : INCIDENT_MIGRATIONS) {
            st.execute(sql(m, tenant));
        }
        return new Scratch(tenant, schema);
    }

    private static String sql(String resource, UUID tenant) {
        try (InputStream in = NetworkIncidentIT.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("migration ausente no classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fixa a sessao no tenant (RLS FORCE consulta current_tenant_id()). */
    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', false)");
    }

    private static void insertIncident(Statement st, UUID id, UUID tenant, String code) throws SQLException {
        st.execute("INSERT INTO network_incidents (id, tenant_id, code, incident_type, planned,"
                + " origin, status, started_at) VALUES ('" + id + "', '" + tenant + "', '" + code
                + "', 'RUPTURE', false, 'CREW', 'OPEN', TIMESTAMP '2026-11-08 10:00:00')");
    }

    private static int count(Statement st, String query) throws SQLException {
        ResultSet rs = st.executeQuery(query);
        rs.next();
        return rs.getInt(1);
    }

    @Test
    @DisplayName("abre e encerra ocorrencia: banco recusa CLOSED sem causa nao-vazia e aceita com causa")
    void abreEncerraValidandoCausaNoBanco() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            asTenant(st, s.tenant());
            UUID incident = UUID.randomUUID();
            insertIncident(st, incident, s.tenant(), "INC-ED92-001");

            // Sem causa: o CHECK ck_ni_closed_cause recusa o encerramento.
            assertThrows(SQLException.class, () -> st.execute(
                    "UPDATE network_incidents SET status = 'CLOSED' WHERE id = '" + incident + "'"));
            // Causa em branco tambem recusa (btrim, correcao m1 da ED-92).
            assertThrows(SQLException.class, () -> st.execute(
                    "UPDATE network_incidents SET status = 'CLOSED', cause_code = '   '"
                            + " WHERE id = '" + incident + "'"));

            st.execute("UPDATE network_incidents SET status = 'RESTORED',"
                    + " ended_at = TIMESTAMP '2026-11-08 14:00:00' WHERE id = '" + incident + "'");
            st.execute("UPDATE network_incidents SET status = 'CLOSED', cause_code = 'RUPTURE_PIPE',"
                    + " repair_minutes = 240, lost_volume_m3 = 12.5 WHERE id = '" + incident + "'");

            ResultSet rs = st.executeQuery("SELECT status, cause_code FROM network_incidents"
                    + " WHERE id = '" + incident + "'");
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("CLOSED");
            assertThat(rs.getString(2)).isEqualTo("RUPTURE_PIPE");
            // JOR-33: a vista por causa conta a ocorrencia encerrada.
            assertThat(count(st, "SELECT COUNT(*) FROM network_incident_cause_report"
                    + " WHERE tenant_id = '" + s.tenant() + "' AND cause_code = 'RUPTURE_PIPE'"
                    + " AND incidents = 1")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("entidade valida a causa contra o catalogo do tenant no encerramento")
    void entidadeValidaCausaContraCatalogo() {
        UUID tenant = UUID.randomUUID();
        UUID workOrder = UUID.randomUUID();
        NetworkIncident incident = new NetworkIncident(UUID.randomUUID(), tenant, "INC-ED92-002",
                NetworkIncident.Type.RUPTURE, false, NetworkIncident.Origin.CREW,
                LocalDateTime.of(2026, 11, 8, 10, 0), NetworkIncident.AffectedSnapshot.empty(), null);
        incident.linkWorkOrder(workOrder);
        incident.markRestored(LocalDateTime.of(2026, 11, 8, 14, 0));
        List<String> catalogo = List.of("RUPTURE_PIPE", "ALAGAMENTO");
        // Causa fora do catalogo vigente do tenant: recusada na entidade.
        assertThatThrownBy(() -> incident.close(new NetworkIncident.ClosureData(
                "CAUSA_INEXISTENTE", 12.5, 240L, workOrder), catalogo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAUSA_INEXISTENTE");
        assertThat(incident.status()).isEqualTo(NetworkIncident.Status.RESTORED);
        // Causa do catalogo: encerra.
        incident.close(new NetworkIncident.ClosureData("RUPTURE_PIPE", 12.5, 240L, workOrder), catalogo);
        assertThat(incident.status()).isEqualTo(NetworkIncident.Status.CLOSED);
    }

    @Test
    @DisplayName("codigo da ocorrencia e unico por tenant (mesmo codigo em outro tenant passa)")
    void unicidadeDoCodigoPorTenant() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            UUID otherTenant = UUID.randomUUID();
            asTenant(st, s.tenant());
            insertIncident(st, UUID.randomUUID(), s.tenant(), "INC-ED92-DUP");
            // Mesmo tenant, mesmo codigo: uq_network_incidents_tenant_code recusa.
            assertThrows(SQLException.class, () ->
                    insertIncident(st, UUID.randomUUID(), s.tenant(), "INC-ED92-DUP"));
            // Outro tenant, mesmo codigo: aceito (a unicidade e por tenant).
            asTenant(st, otherTenant);
            insertIncident(st, UUID.randomUUID(), otherTenant, "INC-ED92-DUP");
            assertThat(count(st, "SELECT COUNT(*) FROM network_incidents"
                    + " WHERE code = 'INC-ED92-DUP'")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("RLS isola os tenants: leitura cruzada devolve zero e escrita cruzada e recusada")
    void rlsIsolaEntreTenants() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            UUID otherTenant = UUID.randomUUID();
            asTenant(st, s.tenant());
            insertIncident(st, UUID.randomUUID(), s.tenant(), "INC-ED92-RLS");
            // Sessao do outro tenant: a linha do primeiro e invisivel.
            asTenant(st, otherTenant);
            assertThat(count(st, "SELECT COUNT(*) FROM network_incidents")).isEqualTo(0);
            // E a policy (WITH CHECK) recusa gravar linha do tenant alheio.
            assertThrows(SQLException.class, () ->
                    insertIncident(st, UUID.randomUUID(), s.tenant(), "INC-ED92-ALHEIO"));
            // De volta ao tenant dono: so a linha original.
            asTenant(st, s.tenant());
            assertThat(count(st, "SELECT COUNT(*) FROM network_incidents")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("verificador NETWORK_INCIDENT contra service_orders: alvo real existe, alheio/inexistente nao")
    void verificadorNetworkIncidentContraServiceOrders() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs().getConnection();
             Statement st = c.createStatement()) {
            Scratch s = scratch(st);
            asTenant(st, s.tenant());
            UUID incident = UUID.randomUUID();
            insertIncident(st, incident, s.tenant(), "INC-ED92-ALVO");

            // OS minima do motor (V9_1): tipo operacional com origem
            // NETWORK_INCIDENT e classe de alvo NETWORK_INCIDENT.
            UUID typeId = UUID.randomUUID();
            st.execute("INSERT INTO service_types (id, tenant_id, code, name, operation, indicator,"
                    + " operational, origin, target_classes) VALUES ('" + typeId + "', '" + s.tenant()
                    + "', 'REPARO-REDE', 'Reparo de rede', 'REPAIR', 'FIELD', true, 'NETWORK_INCIDENT',"
                    + " ARRAY['NETWORK_INCIDENT'])");
            UUID orderId = UUID.randomUUID();
            st.execute("INSERT INTO service_orders (id, tenant_id, service_type_id, origin,"
                    + " execution_mode, created_by) VALUES ('" + orderId + "', '" + s.tenant() + "', '"
                    + typeId + "', 'INTERNAL', 'FIELD', '" + UUID.randomUUID() + "')");
            // O alvo tipado NETWORK_INCIDENT e aceite pelo motor (CHECK da V9_1).
            st.execute("INSERT INTO service_order_targets (id, tenant_id, order_id, target_type,"
                    + " target_id) VALUES ('" + UUID.randomUUID() + "', '" + s.tenant() + "', '"
                    + orderId + "', 'NETWORK_INCIDENT', '" + incident + "')");
            // Vinculo ocorrencia <-> OS da ED-92 (FK composta para network_incidents).
            st.execute("INSERT INTO network_incident_work_orders (id, tenant_id, incident_id,"
                    + " work_order_id) VALUES ('" + UUID.randomUUID() + "', '" + s.tenant() + "', '"
                    + incident + "', '" + orderId + "')");

            // O verificador registado pela ED-92 responde sobre a tabela real.
            TargetVerifierRegistry registry = new TargetVerifierRegistry();
            assertThat(registry.hasVerifier(TargetType.NETWORK_INCIDENT)).isFalse();
            NetworkIncidentTargetRegistration.register(registry, s.schema());
            assertThat(registry.hasVerifier(TargetType.NETWORK_INCIDENT)).isTrue();
            var verifier = registry.verifier(TargetType.NETWORK_INCIDENT);
            assertThat(verifier.exists(c, s.tenant(), incident)).isTrue();
            assertThat(verifier.exists(c, s.tenant(), UUID.randomUUID())).isFalse();
            assertThat(verifier.exists(c, UUID.randomUUID(), incident)).isFalse();
        }
    }
}
