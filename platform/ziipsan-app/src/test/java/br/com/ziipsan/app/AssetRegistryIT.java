package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.operations.asset.AssetService;
import br.com.ziipsan.operations.asset.AssetService.AssetInput;
import br.com.ziipsan.operations.asset.AssetTargetVerifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.35 (V27_1) no PostgreSQL real, sobre o tenant provisionado.
 *
 * <p>Prova: cadeia de seis niveis criada so por OS interna; nivel incoerente
 * com o pai, componente fora de equipamento e ciclo recusados; CIB so no bem
 * individual (25 digitos) e UP com 2 e 3 digitos aceitos (ressalva do manual
 * divergente); escrita direta sem OS recusada pelo banco (NOT NULL de
 * proveniencia) e escrita por OS de campo recusada pelo servico; os quatro
 * verificadores de alvo registrados no motor da P1.31 (alvo inexistente e
 * alvo de outro tenant recusados; classe sem verificador nao declaravel);
 * feicao do GIS e hidrometro com FK para o ativo, e gatilho recusando codigo
 * MCPSB no JSONB da feicao; fronteira de classe sem sobreposicao (um tipo,
 * uma classe); relatorio de espacos confinados incluindo desativado.
 *
 * <p>Os codigos de teste (LOC, NTS-SDS, UP) sao valores de formato, sem
 * vinculo a municipio, natureza ou unidade patrimonial reais.
 */
class AssetRegistryIT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    private static ServiceOrderService engine;
    private static AssetService assets;
    private static UUID internalOrderId;
    private static UUID fieldOrderId;
    private static UUID municipalityId;
    private static UUID systemId;
    private static UUID natureId;
    private static UUID installationId;
    private static UUID equipmentId;
    private static UUID componentId;

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        engine = new ServiceOrderService(ProvisionedTenant.appDs, SCHEMA, TENANT);
        AssetTargetVerifiers.registerAll(engine, SCHEMA);
        assets = new AssetService(ProvisionedTenant.appDs, SCHEMA, TENANT);

        UUID actor = UUID.randomUUID();
        String regCode = uniq("IT-ASSET-REG");
        String fieldCode = uniq("IT-ASSET-FIELD");
        engine.declareType(new ServiceOrderService.ServiceTypeInput(
                regCode, "Registro de ativo", "REGISTRO", "IT_ASSET_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                List.of(TargetType.LOCATION_ONLY), null, null, null,
                false, false, false, false, List.of()), actor);
        engine.declareType(new ServiceOrderService.ServiceTypeInput(
                fieldCode, "Servico de campo", "CAMPO", "IT_FIELD_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null,
                List.of(TargetType.LOCATION_ONLY), null, null, null,
                false, false, false, false, List.of()), actor);
        internalOrderId = engine.createOrder(new ServiceOrderService.OrderInput(
                regCode, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.INTERNAL, ServiceOrderEnums.Priority.NORMAL,
                actor, null, null, null, null)).id();
        fieldOrderId = engine.createOrder(new ServiceOrderService.OrderInput(
                fieldCode, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.FIELD, ServiceOrderEnums.Priority.NORMAL,
                actor, null, null, null, null)).id();

        municipalityId = assets.createAsset(internalOrderId,
                input(uniq("AT-MUN"), null, 1, null, "MUNICIPALITY", false, "INDIVIDUAL", null), actor);
        systemId = assets.createAsset(internalOrderId,
                input(uniq("AT-SIS"), municipalityId, 2, null, "SYSTEM", false, "INDIVIDUAL", null), actor);
        natureId = assets.createAsset(internalOrderId,
                input(uniq("AT-NAT"), systemId, 3, null, "SERVICE_NATURE", false, "INDIVIDUAL", null), actor);
        installationId = assets.createAsset(internalOrderId,
                input(uniq("AT-INS"), natureId, 4, null, "RESERVOIR", true, "INDIVIDUAL", null), actor);
        assets.registerTypeMapping("CENTRIFUGAL_PUMP", "PUMPING", "12", null, actor);
        assets.registerTypeMapping("MECHANICAL_SEAL", "PUMPING", null, null, actor);
        equipmentId = assets.createAsset(internalOrderId,
                input(uniq("AT-EQP"), installationId, 5, "PUMPING", "CENTRIFUGAL_PUMP",
                        false, "INDIVIDUAL", "12"), actor);
        componentId = assets.createAsset(internalOrderId,
                input(uniq("AT-CMP"), equipmentId, 6, "PUMPING", "MECHANICAL_SEAL",
                        false, "INDIVIDUAL", null), actor);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static Connection asTenant(UUID tenantOverride) throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + tenantOverride + "', true)");
        }
        return c;
    }

    /** Entrada valida; NTS-SDS so do nivel 3 em diante; CIB so no individual. */
    private static AssetInput input(String code, UUID parent, int level, String assetClass,
                                    String catalogType, boolean confined, String control, String up) {
        return new AssetInput(code, parent, level, assetClass, catalogType, null, null,
                false, confined,
                "INDIVIDUAL".equals(control) ? "1".repeat(25) : null,
                "1234567", null, level >= 3 ? "FIXTURE_NTS_01" : null, up, null,
                "00", "00", "00", "00", "00", control,
                "MASS".equals(control) ? "un" : null, false, true,
                "Fabricante ficticio", "Modelo T", "SN-1", 2020, null, null, null,
                null, level == 4 ? "IN_SERVICE" : "PLANNED", "Local de teste");
    }

    private static AssetInput copyWith(AssetInput b, UUID parent, int level, String assetClass,
                                       String catalogType, String up) {
        return new AssetInput(b.code(), parent, level, assetClass, catalogType,
                b.gisFeatureKind(), b.gisFeatureId(), b.isBuilding(), b.confinedSpace(),
                b.cib(), b.locIbge(), b.ac(), b.ntsSds(), up, b.uar(),
                b.at1(), b.at2(), b.at3(), b.at4(), b.at5(), b.controlMode(), b.unitOfMeasure(),
                b.bcp(), b.brv(), b.manufacturer(), b.model(), b.serialNumber(),
                b.manufactureYear(), b.powerKw(), b.capacity(), b.capacityUnit(),
                b.inServiceOn(), b.lifecycleStatus(), b.locationText());
    }

    // ------------------------------------------------------------------
    // Hierarquia e escrita por OS interna
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cadeia de seis niveis existe com os pais certos")
    void sixLevelChain() {
        assertThat(assets.getAsset(municipalityId).hierarchyLevel()).isEqualTo(1);
        assertThat(assets.getAsset(systemId).parentAssetId()).isEqualTo(municipalityId);
        assertThat(assets.getAsset(natureId).parentAssetId()).isEqualTo(systemId);
        assertThat(assets.getAsset(installationId).parentAssetId()).isEqualTo(natureId);
        assertThat(assets.getAsset(equipmentId).parentAssetId()).isEqualTo(installationId);
        assertThat(assets.getAsset(componentId).parentAssetId()).isEqualTo(equipmentId);
        assertThat(assets.getAsset(componentId).hierarchyLevel()).isEqualTo(6);
    }

    @Test
    @DisplayName("Nivel incoerente com o pai, componente fora de equipamento e ciclo recusados")
    void hierarchyRules() {
        UUID actor = UUID.randomUUID();
        // Equipamento (5) sob sistema (2): exige pai nivel 4.
        assertThatThrownBy(() -> assets.createAsset(internalOrderId,
                input(uniq("AT-BAD"), systemId, 5, "PUMPING", "CENTRIFUGAL_PUMP",
                        false, "INDIVIDUAL", "12"), actor))
                .isInstanceOf(ApiException.Validation.class);
        // Componente (6) sob instalacao (4): exige pai equipamento (5).
        assertThatThrownBy(() -> assets.createAsset(internalOrderId,
                input(uniq("AT-BAD"), installationId, 6, "PUMPING", "MECHANICAL_SEAL",
                        false, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.Validation.class);
        // Instalacao passa a ter como pai o proprio equipamento (descendente): recusado.
        AssetInput installation = toInput(assets.getAsset(installationId));
        assertThatThrownBy(() -> assets.updateAsset(internalOrderId, installationId,
                copyWith(installation, equipmentId, 4, null, "RESERVOIR", null), actor))
                .isInstanceOf(ApiException.Validation.class);
    }

    private static AssetInput toInput(AssetService.AssetView v) {
        return new AssetInput(v.code(), v.parentAssetId(), v.hierarchyLevel(), v.assetClass(),
                v.catalogTypeCode(), v.gisFeatureKind(), v.gisFeatureId(), v.isBuilding(),
                v.confinedSpace(), v.cib(), v.locIbge(), v.ac(), v.ntsSds(), v.up(), v.uar(),
                v.at1(), v.at2(), v.at3(), v.at4(), v.at5(), v.controlMode(), v.unitOfMeasure(),
                v.bcp(), v.brv(), v.manufacturer(), v.model(), v.serialNumber(),
                v.manufactureYear(), v.powerKw(), v.capacity(), v.capacityUnit(),
                v.inServiceOn(), v.lifecycleStatus(), v.locationText());
    }

    @Test
    @DisplayName("Escrita sem OS e por OS de campo recusadas; por OS interna aceita")
    void onlyInternalOrderWrites() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> assets.createAsset(null,
                input(uniq("AT-BAD"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> assets.createAsset(fieldOrderId,
                input(uniq("AT-BAD"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> assets.createAsset(UUID.randomUUID(),
                input(uniq("AT-BAD"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.NotFound.class);
        // Alteracao por OS interna aceita; por OS de campo, recusada.
        AssetInput current = toInput(assets.getAsset(equipmentId));
        AssetInput changed = new AssetInput(current.code(), current.parentAssetId(),
                current.hierarchyLevel(), current.assetClass(), current.catalogTypeCode(),
                current.gisFeatureKind(), current.gisFeatureId(), current.isBuilding(),
                current.confinedSpace(), current.cib(), current.locIbge(), current.ac(),
                current.ntsSds(), current.up(), current.uar(), current.at1(), current.at2(),
                current.at3(), current.at4(), current.at5(), current.controlMode(),
                current.unitOfMeasure(), current.bcp(), current.brv(), current.manufacturer(),
                current.model(), current.serialNumber(), current.manufactureYear(),
                current.powerKw(), current.capacity(), current.capacityUnit(),
                current.inServiceOn(), current.lifecycleStatus(), "Local alterado");
        assets.updateAsset(internalOrderId, equipmentId, changed, actor);
        assertThat(assets.getAsset(equipmentId).locationText()).isEqualTo("Local alterado");
        assertThatThrownBy(() -> assets.updateAsset(fieldOrderId, equipmentId, changed, actor))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("INSERT direto sem OS falha no banco (proveniencia NOT NULL)")
    void directInsertRefusedByDatabase() throws SQLException {
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + SCHEMA + ".asset (id, tenant_id, code, parent_asset_id,"
                             + " hierarchy_level, catalog_type_code, loc_ibge, nts_sds, control_mode,"
                             + " cib, created_by, updated_by) VALUES (?, ?, ?, ?, 4, 'RESERVOIR',"
                             + " '1234567', 'FIXTURE_NTS_01', 'INDIVIDUAL', ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("AT-DIRECT"));
            ps.setObject(4, natureId);
            ps.setString(5, "1".repeat(25));
            ps.setObject(6, UUID.randomUUID());
            ps.setObject(7, UUID.randomUUID());
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            c.rollback();
        }
    }

    // ------------------------------------------------------------------
    // MCPSB, classes e espacos confinados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("UP com 2 e com 3 digitos aceitos; com 4, recusado; massa sem CIB aceita, com CIB recusada")
    void mcpsbRules() {
        UUID actor = UUID.randomUUID();
        UUID two = assets.createAsset(internalOrderId,
                input(uniq("AT-UP2"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", "12"), actor);
        assertThat(assets.getAsset(two).up()).isEqualTo("12");
        UUID three = assets.createAsset(internalOrderId,
                input(uniq("AT-UP3"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", "123"), actor);
        assertThat(assets.getAsset(three).up()).isEqualTo("123");
        assertThatThrownBy(() -> assets.createAsset(internalOrderId,
                input(uniq("AT-UP4"), natureId, 4, null, "RESERVOIR", false, "INDIVIDUAL", "1234"), actor))
                .isInstanceOf(ApiException.Validation.class);
        UUID mass = assets.createAsset(internalOrderId,
                input(uniq("AT-MASS"), natureId, 4, null, "RESERVOIR", false, "MASS", null), actor);
        assertThat(assets.getAsset(mass).cib()).isNull();
        AssetInput massWithCib = new AssetInput(uniq("AT-MASS"), natureId, 4, null, "RESERVOIR",
                null, null, false, false, "1".repeat(25), "1234567", null, "FIXTURE_NTS_01",
                null, null, "00", "00", "00", "00", "00", "MASS", "un", false, false,
                null, null, null, null, null, null, null, null, "PLANNED", null);
        assertThatThrownBy(() -> assets.createAsset(internalOrderId, massWithCib, actor))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("Sete classes de fabrica com fronteira; um tipo pertence a no maximo uma classe")
    void classBoundariesWithoutOverlap() {
        assertThat(assets.listEquipmentClasses()).hasSize(7);
        assertThat(assets.listEquipmentClasses()).allMatch(c -> !c.boundaryText().isBlank());
        UUID actor = UUID.randomUUID();
        assets.registerTypeMapping("OVERLAP_TYPE", "PUMPING", null, null, actor);
        assets.registerTypeMapping("OVERLAP_TYPE", "ELECTRICAL", null, null, actor);
        assertThatThrownBy(() -> assets.createAsset(internalOrderId,
                input(uniq("AT-OVL"), installationId, 5, "PUMPING", "OVERLAP_TYPE",
                        false, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.Validation.class);
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + SCHEMA + ".asset_type_mcpsb"
                             + " WHERE tenant_id = ? AND catalog_type_code = 'OVERLAP_TYPE'")) {
            ps.setObject(1, TENANT);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Relatorio de espacos confinados inclui o desativado")
    void confinedSpacesReportIncludesDecommissioned() {
        UUID actor = UUID.randomUUID();
        UUID active = assets.createAsset(internalOrderId,
                input(uniq("AT-CF-A"), natureId, 4, null, "RESERVOIR", true, "INDIVIDUAL", null), actor);
        AssetInput decommissioned = new AssetInput(uniq("AT-CF-D"), natureId, 4, null, "MANHOLE",
                null, null, false, true, "1".repeat(25), "1234567", null, "FIXTURE_NTS_01",
                null, null, "00", "00", "00", "00", "00", "INDIVIDUAL", null, false, false,
                null, null, null, null, null, null, null, null, "DECOMMISSIONED", "Poco desativado");
        UUID dead = assets.createAsset(internalOrderId, decommissioned, actor);
        List<AssetService.ConfinedSpaceRow> rows = assets.listConfinedSpaces();
        assertThat(rows.stream().map(AssetService.ConfinedSpaceRow::id))
                .contains(active, installationId, dead);
        assertThat(rows.stream().filter(r -> r.id().equals(dead)).findFirst().orElseThrow()
                .lifecycleStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(assets.isConfinedSpace(dead)).isTrue();
        assertThat(assets.isConfinedSpace(equipmentId)).isFalse();
        // Tipo fora da lista nao pode ser espaco confinado.
        assertThatThrownBy(() -> assets.createAsset(internalOrderId,
                input(uniq("AT-CF-X"), natureId, 4, null, "WTP", true, "INDIVIDUAL", null), actor))
                .isInstanceOf(ApiException.Validation.class);
    }

    // ------------------------------------------------------------------
    // Alvos da P1.31, GIS e hidrometro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Verificadores: alvo existente aceito, inexistente e de outro tenant recusados")
    void targetVerifiers() throws Exception {
        UUID actor = UUID.randomUUID();
        String tgtCode = uniq("IT-ASSET-TGT");
        engine.declareType(new ServiceOrderService.ServiceTypeInput(
                tgtCode, "Manutencao de bomba", "MANUTENCAO", "IT_ASSET_EFFECT", null,
                ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                List.of(TargetType.EQUIPMENT, TargetType.COMPONENT, TargetType.OPERATIONAL_SITE,
                        TargetType.BUILDING), null, null, null,
                false, false, false, false, List.of()), actor);
        UUID orderId = engine.createOrder(new ServiceOrderService.OrderInput(
                tgtCode, ServiceOrderEnums.Origin.INTERNAL,
                ServiceOrderEnums.ExecutionMode.INTERNAL, ServiceOrderEnums.Priority.NORMAL,
                actor, null, null, null, null)).id();
        assertThat(engine.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.EQUIPMENT, equipmentId, null, null, null, null), actor)).isNotNull();
        assertThatThrownBy(() -> engine.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.EQUIPMENT, UUID.randomUUID(), null, null, null, null), actor))
                .isInstanceOf(ApiException.NotFound.class);
        // Instalacao (nao edificacao) nao resolve BUILDING; edificacao sim.
        assertThatThrownBy(() -> engine.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.BUILDING, installationId, null, null, null, null), actor))
                .isInstanceOf(ApiException.NotFound.class);
        UUID building = assets.createAsset(internalOrderId,
                new AssetInput(uniq("AT-BLD"), natureId, 4, null, "WTP", null, null,
                        true, false, "1".repeat(25), "1234567", null, "FIXTURE_NTS_01",
                        null, null, "00", "00", "00", "00", "00", "INDIVIDUAL", null,
                        false, false, null, null, null, null, null, null, null, null,
                        "IN_SERVICE", "Casa de bombas"), actor);
        assertThat(engine.addTarget(orderId, new ServiceOrderService.TargetInput(
                TargetType.BUILDING, building, null, null, null, null), actor)).isNotNull();
        // Outro tenant: o verificador responde false para o id deste tenant.
        UUID otherTenant = UUID.randomUUID();
        try (Connection c = asTenant(otherTenant)) {
            assertThat(engine.verifiers().verifier(TargetType.EQUIPMENT)
                    .exists(c, otherTenant, equipmentId)).isFalse();
        }
        // Classe sem verificador (FLEET_ASSET, da ED-59) nao pode ser declarada.
        assertThatThrownBy(() -> engine.declareType(new ServiceOrderService.ServiceTypeInput(
                uniq("IT-ASSET-NOV"), "Sem verificador", "X", "IT_X", null,
                ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null,
                List.of(TargetType.FLEET_ASSET), null, null, null,
                false, false, false, false, List.of()), actor))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("Feicao e hidrometro referenciam o ativo por FK; codigo MCPSB no JSONB da feicao recusado")
    void gisAndMeterReferenceAsset() throws Exception {
        UUID nodeId = insertGisNode();
        // Codigo patrimonial no JSONB da feicao: recusado pelo gatilho.
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + SCHEMA + ".gis_node SET asset_codes = ?::jsonb"
                             + " WHERE tenant_id = ? AND id = ?")) {
            ps.setString(1, "{\"cib\": \"" + "1".repeat(25) + "\"}");
            ps.setObject(2, TENANT);
            ps.setObject(3, nodeId);
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            c.rollback();
        }
        // Ligacao por FK aceita; o codigo so se resolve pelo join com asset.
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + SCHEMA + ".gis_node SET asset_id = ? WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, equipmentId);
            ps.setObject(2, TENANT);
            ps.setObject(3, nodeId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
            c.commit();
        }
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT a.cib FROM " + SCHEMA + ".gis_node n JOIN " + SCHEMA + ".asset a"
                             + " ON a.tenant_id = n.tenant_id AND a.id = n.asset_id"
                             + " WHERE n.tenant_id = ? AND n.id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("1".repeat(25));
            }
        }
        // FK para ativo inexistente recusada, nos dois lados (feicao e hidrometro).
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + SCHEMA + ".gis_node SET asset_id = ? WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setObject(3, nodeId);
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            c.rollback();
        }
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + SCHEMA + ".meters (id, tenant_id, meter_number, digits, status, asset_id)"
                             + " VALUES (?, ?, ?, 5, 'AVAILABLE', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("IT-MTR"));
            ps.setObject(4, equipmentId);
            assertThat(ps.executeUpdate()).isEqualTo(1);
            c.commit();
        }
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + SCHEMA + ".meters (id, tenant_id, meter_number, digits, status, asset_id)"
                             + " VALUES (?, ?, ?, 5, 'AVAILABLE', ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT);
            ps.setString(3, uniq("IT-MTR"));
            ps.setObject(4, UUID.randomUUID());
            assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            c.rollback();
        }
        // Nenhuma tabela de feicao tem coluna de codigo patrimonial.
        try (Connection c = asTenant();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ?"
                             + " AND table_name LIKE 'gis_%' AND column_name IN"
                             + " ('cib', 'loc_ibge', 'up', 'uar', 'nts_sds')")) {
            ps.setString(1, SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    private static UUID insertGisNode() throws SQLException {
        try (Connection m = ProvisionedTenant.migratorDs.getConnection();
             Statement st = m.createStatement()) {
            m.setAutoCommit(false);
            st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                    + " VALUES ('" + TENANT + "', 'sanitation', 'ACTIVE', NOW()) ON CONFLICT DO NOTHING");
            m.commit();
        }
        try (Connection c = asTenant()) {
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL search_path = " + SCHEMA + ", public");
            }
            UUID domainId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".gis_domain (id, tenant_id, key, package, label,"
                            + " map_color_token, sort_order) VALUES (?, ?, ?, 'sanitation', 'Agua', 'map-water', 1)")) {
                ps.setObject(1, domainId);
                ps.setObject(2, TENANT);
                ps.setString(3, uniq("IT-DOM"));
                ps.executeUpdate();
            }
            String domainKey;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT key FROM " + SCHEMA + ".gis_domain WHERE tenant_id = ?"
                            + " ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    domainKey = rs.getString(1);
                }
            }
            UUID classId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".gis_feature_class (id, tenant_id, code, domain,"
                            + " topology_role, geometry_type, origin) VALUES (?, ?, ?, ?, 'NODE', 'POINT', 'TENANT')")) {
                ps.setObject(1, classId);
                ps.setObject(2, TENANT);
                ps.setString(3, uniq("IT-CLS"));
                ps.setString(4, domainKey);
                ps.executeUpdate();
            }
            UUID typeId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".gis_feature_type (id, tenant_id, class_id, code, label, origin)"
                            + " VALUES (?, ?, ?, ?, 'Tipo de teste', 'TENANT')")) {
                ps.setObject(1, typeId);
                ps.setObject(2, TENANT);
                ps.setObject(3, classId);
                ps.setString(4, uniq("IT-FT"));
                ps.executeUpdate();
            }
            UUID nodeId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".gis_node (id, tenant_id, code, feature_type_id,"
                            + " capture_method, geom) VALUES (?, ?, ?, ?, 'DIGITIZED',"
                            + " ST_GeomFromText('POINTZ(-49 -16 0)', 4674))")) {
                ps.setObject(1, nodeId);
                ps.setObject(2, TENANT);
                ps.setString(3, uniq("IT-NODE"));
                ps.setObject(4, typeId);
                ps.executeUpdate();
            }
            c.commit();
            return nodeId;
        }
    }
}
