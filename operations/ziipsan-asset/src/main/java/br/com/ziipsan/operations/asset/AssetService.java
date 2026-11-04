package br.com.ziipsan.operations.asset;

import br.com.ziipsan.core.api.ApiException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cadastro minimo de ativos (P1.35; ED-56 e P1.31 v2.21; ADR-0034; ADR-0039;
 * NR 16/2026 da ANA; decisao do dono de 28/10/2026; COE-05, DAD-26).
 *
 * <p>Desenho: o mesmo do motor da P1.31 — JDBC direto, um servico ligado a
 * um schema de tenant, uma transacao por operacao, GUC {@code app.current_tenant}
 * por transacao (a RLS e FORCE tambem para o dono das tabelas).
 *
 * <p>Escrita so por OS interna: {@link #createAsset} e {@link #updateAsset}
 * exigem o id de uma OS existente do tenant em modo {@code INTERNAL}; sem
 * ela, a escrita e recusada pelo servico — e, no banco, as colunas
 * {@code created_via_order_id}/{@code updated_via_order_id} sao NOT NULL com
 * FK para {@code service_orders}, de modo que um INSERT direto sem OS tambem
 * falha. A OS interna e o veiculo do backoffice ("OS interna aplicada ou
 * aprovada pela tela sem ida a campo", P1.31); nao ha handler de efeito de
 * criacao porque o ativo ainda nao existe para ser alvo quando a OS nasce.
 * Vida util, custo historico, inventario regulatorio completo, conciliacao
 * com o patrimonio, manutencao e risco sao da ED-56; ISO 55001, da ED-72;
 * catalogo de tipos, da P1.27 (aqui so referenciado, nunca recriado).
 *
 * <p>Ressalvas que viajam com o codigo (ver o cabecalho da V27_1):
 * UP aceita 2 ou 3 digitos (o manual diverge entre tabela 4 e figura 2, e
 * a versao 2 de 2025 nao foi lida); NTS-SDS valida so o formato (a lista
 * fechada de 19 codigos nao esta reproduzida em documento lido do projeto
 * e entra com a ED-56); nenhum numero de UP/UAR e semeado (a UAR e
 * atribuida pela agencia) — a tabela {@code asset_type_mcpsb} nasce vazia
 * e o par so e exigido quando a linha existe.
 */
public class AssetService {

    private static final Pattern DIGITS_25 = Pattern.compile("^\\d{25}$");
    private static final Pattern DIGITS_7 = Pattern.compile("^\\d{7}$");
    private static final Pattern DIGITS_6 = Pattern.compile("^\\d{6}$");
    private static final Pattern DIGITS_2_OR_3 = Pattern.compile("^\\d{2,3}$");
    private static final Pattern DIGITS_2 = Pattern.compile("^\\d{2}$");
    private static final Pattern NTS_SDS = Pattern.compile("^[A-Z0-9_]{1,30}$");

    private static final Set<String> GIS_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_connection", "gis_structure", "gis_zone", "gis_poi");
    private static final Set<String> POINT_KINDS = Set.of("NODE", "POI");
    private static final Set<String> LIFECYCLE =
            Set.of("PLANNED", "IN_SERVICE", "OUT_OF_SERVICE", "DECOMMISSIONED");

    public record AssetInput(
            String code, UUID parentAssetId, int hierarchyLevel, String assetClass,
            String catalogTypeCode, String gisFeatureKind, UUID gisFeatureId,
            boolean isBuilding, boolean confinedSpace,
            String cib, String locIbge, String ac, String ntsSds, String up, String uar,
            String at1, String at2, String at3, String at4, String at5,
            String controlMode, String unitOfMeasure, boolean bcp, boolean brv,
            String manufacturer, String model, String serialNumber, Integer manufactureYear,
            BigDecimal powerKw, BigDecimal capacity, String capacityUnit,
            LocalDate inServiceOn, String lifecycleStatus, String locationText) {
    }

    public record AssetView(
            UUID id, String code, UUID parentAssetId, int hierarchyLevel, String assetClass,
            String catalogTypeCode, String gisFeatureKind, UUID gisFeatureId,
            boolean isBuilding, boolean confinedSpace,
            String cib, String locIbge, String ac, String ntsSds, String up, String uar,
            String at1, String at2, String at3, String at4, String at5,
            String controlMode, String unitOfMeasure, boolean bcp, boolean brv,
            String manufacturer, String model, String serialNumber, Integer manufactureYear,
            BigDecimal powerKw, BigDecimal capacity, String capacityUnit,
            LocalDate inServiceOn, String lifecycleStatus, String locationText,
            UUID createdViaOrderId, UUID updatedViaOrderId) {
    }

    /** Linha do relatorio de espacos confinados (NR-33, 33.3.3, a). */
    public record ConfinedSpaceRow(
            UUID id, String code, String catalogTypeCode, String lifecycleStatus, String locationText) {
    }

    public record EquipmentClassView(String code, String label, String boundaryText) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public AssetService(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema and tenantId are required");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    // ------------------------------------------------------------------
    // Escrita (so por OS interna)
    // ------------------------------------------------------------------

    public UUID createAsset(UUID orderId, AssetInput input, UUID actorId) {
        return inTx(connection -> {
            requireInternalOrder(connection, orderId);
            validateInput(connection, input, null);
            UUID id = UUID.randomUUID();
            insertAsset(connection, id, orderId, orderId, input, actorId);
            return id;
        });
    }

    public void updateAsset(UUID orderId, UUID assetId, AssetInput input, UUID actorId) {
        inTx(connection -> {
            requireInternalOrder(connection, orderId);
            requireAssetExists(connection, assetId);
            validateInput(connection, input, assetId);
            updateAssetRow(connection, assetId, orderId, input, actorId);
            return null;
        });
    }

    /** Correspondencia tipo -> par UP/UAR (preenchida pelo tenant/agencia). */
    public void registerTypeMapping(String catalogTypeCode, String equipmentClassCode,
                                    String up, String uar, UUID actorId) {
        inTx(connection -> {
            nonBlank(catalogTypeCode, "catalogTypeCode");
            nonBlank(equipmentClassCode, "equipmentClassCode");
            if (up != null && !DIGITS_2_OR_3.matcher(up).matches()) {
                throw validation("up", "INVALID", "UP aceita 2 ou 3 digitos (manual divergente; ver V27_1).");
            }
            if (uar != null && !DIGITS_7.matcher(uar).matches()) {
                throw validation("uar", "INVALID", "UAR tem 7 digitos.");
            }
            if (!existsClass(connection, equipmentClassCode)) {
                throw validation("equipmentClassCode", "NOT_FOUND",
                        "Classe de equipamento inexistente no tenant.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("asset_type_mcpsb")
                            + " (id, tenant_id, catalog_type_code, equipment_class_code, up, uar, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, catalog_type_code) DO UPDATE"
                            + " SET equipment_class_code = EXCLUDED.equipment_class_code,"
                            + " up = EXCLUDED.up, uar = EXCLUDED.uar, updated_by = EXCLUDED.updated_by")) {
                int i = 0;
                ps.setObject(++i, UUID.randomUUID());
                ps.setObject(++i, tenantId);
                ps.setString(++i, catalogTypeCode);
                ps.setString(++i, equipmentClassCode);
                setText(ps, ++i, up);
                setText(ps, ++i, uar);
                setUuid(ps, ++i, actorId);
                setUuid(ps, ++i, actorId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Leitura
    // ------------------------------------------------------------------

    public AssetView getAsset(UUID assetId) {
        return inTx(connection -> {
            AssetView view = loadAsset(connection, assetId);
            if (view == null) {
                throw new ApiException.NotFound("Ativo nao encontrado.");
            }
            return view;
        });
    }

    public List<AssetView> listAssets() {
        return inTx(connection -> {
            var out = new ArrayList<AssetView>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("asset") + " WHERE tenant_id = ? ORDER BY code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readAsset(rs));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Todos os espacos confinados, inclusive desativados (NR-33, 33.3.3, a):
     * nenhum filtro de situacao do ciclo de vida. E este relatorio que os
     * tipos de servico de espaco confinado da P1.31 leem (ver
     * {@link #isConfinedSpace}).
     */
    public List<ConfinedSpaceRow> listConfinedSpaces() {
        return inTx(connection -> {
            var out = new ArrayList<ConfinedSpaceRow>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, code, catalog_type_code, lifecycle_status, location_text FROM "
                            + t("asset") + " WHERE tenant_id = ? AND confined_space ORDER BY code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new ConfinedSpaceRow(uuid(rs, "id"), rs.getString("code"),
                                rs.getString("catalog_type_code"), rs.getString("lifecycle_status"),
                                rs.getString("location_text")));
                    }
                }
            }
            return out;
        });
    }

    /** Flag de espaco confinado de um ativo, para os tipos da P1.31 lerem. */
    public boolean isConfinedSpace(UUID assetId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT confined_space FROM " + t("asset") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, assetId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Ativo nao encontrado.");
                    }
                    return rs.getBoolean(1);
                }
            }
        });
    }

    public List<EquipmentClassView> listEquipmentClasses() {
        return inTx(connection -> {
            var out = new ArrayList<EquipmentClassView>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT code, label, boundary_text FROM " + t("asset_equipment_class")
                            + " WHERE tenant_id = ? AND active ORDER BY code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new EquipmentClassView(rs.getString("code"), rs.getString("label"),
                                rs.getString("boundary_text")));
                    }
                }
            }
            return out;
        });
    }

    // ------------------------------------------------------------------
    // Validacao
    // ------------------------------------------------------------------

    private void requireInternalOrder(Connection connection, UUID orderId) throws SQLException {
        if (orderId == null) {
            throw validation("orderId", "REQUIRED",
                    "Inclusao e alteracao de ativo exigem uma OS interna da P1.31.");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT execution_mode FROM " + t("service_orders")
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("OS nao encontrada para este tenant.");
                }
                if (!"INTERNAL".equals(rs.getString(1))) {
                    throw validation("orderId", "NOT_INTERNAL",
                            "So OS interna da P1.31 inclui ou altera ativo.");
                }
            }
        }
    }

    private void requireAssetExists(Connection connection, UUID assetId) throws SQLException {
        if (loadAsset(connection, assetId) == null) {
            throw new ApiException.NotFound("Ativo nao encontrado.");
        }
    }

    private void validateInput(Connection connection, AssetInput in, UUID selfId) throws SQLException {
        nonBlank(in.code(), "code");
        nonBlank(in.catalogTypeCode(), "catalogTypeCode");
        if (in.hierarchyLevel() < 1 || in.hierarchyLevel() > 6) {
            throw validation("hierarchyLevel", "INVALID", "Nivel da hierarquia entre 1 e 6.");
        }
        AssetLevel level = AssetLevel.of(in.hierarchyLevel());
        // Pai: raiz sem pai; demais, pai existente do tenant exatamente um nivel acima.
        if (level == AssetLevel.MUNICIPALITY) {
            if (in.parentAssetId() != null) {
                throw validation("parentAssetId", "ROOT_HAS_PARENT", "A raiz (nivel 1) nao tem pai.");
            }
        } else {
            if (in.parentAssetId() == null) {
                throw validation("parentAssetId", "REQUIRED", "Nivel " + level.level() + " exige pai.");
            }
            if (in.parentAssetId().equals(selfId)) {
                throw validation("parentAssetId", "CYCLE", "O ativo nao pode ser pai de si mesmo.");
            }
            Integer parentLevel = parentLevel(connection, in.parentAssetId());
            if (parentLevel == null) {
                throw validation("parentAssetId", "NOT_FOUND", "Ativo pai inexistente neste tenant.");
            }
            if (parentLevel != level.level() - 1) {
                throw validation("parentAssetId", "LEVEL_MISMATCH",
                        "Nivel " + level.level() + " exige pai de nivel " + (level.level() - 1)
                                + " (o pai informado e nivel " + parentLevel + ").");
            }
        }
        if (in.isBuilding() && level != AssetLevel.INSTALLATION) {
            throw validation("isBuilding", "LEVEL_MISMATCH", "Edificacao e ativo de nivel 4 (instalacao).");
        }
        // Classe: so equipamento (5) e componente (6) tem classe; ela precisa existir e estar ativa.
        if (level == AssetLevel.EQUIPMENT || level == AssetLevel.COMPONENT) {
            nonBlank(in.assetClass(), "assetClass");
            if (!existsActiveClass(connection, in.assetClass())) {
                throw validation("assetClass", "NOT_FOUND",
                        "Classe de equipamento inexistente ou inativa neste tenant.");
            }
        } else if (in.assetClass() != null) {
            throw validation("assetClass", "LEVEL_MISMATCH",
                    "Classe de equipamento so existe nos niveis 5 e 6.");
        }
        // Tipo: lido do catalogo da P1.27 (gis_feature_type) ou da correspondencia UP/UAR.
        // Niveis 1 a 3 sao nos organizacionais (municipio, sistema, natureza): nao tem
        // tipo de feicao nem de equipamento, e o proprio nivel e o tipo.
        boolean organizationalType = level.level() <= 3 && level.name().equals(in.catalogTypeCode());
        if (!organizationalType
                && !existsCatalogType(connection, in.catalogTypeCode())
                && !existsTypeMapping(connection, in.catalogTypeCode())) {
            throw validation("catalogTypeCode", "NOT_FOUND",
                    "Tipo inexistente no catalogo da P1.27 e na correspondencia UP/UAR deste tenant.");
        }
        // Espaco confinado: so nos tipos da lista de fabrica, em instalacao ou feicao de ponto.
        if (in.confinedSpace()) {
            if (!existsConfinedSpaceType(connection, in.catalogTypeCode())) {
                throw validation("confinedSpace", "TYPE_NOT_CONFINED",
                        "Espaco confinado so nos tipos de instalacao e de ponto da lista (NR-33).");
            }
            if (level != AssetLevel.INSTALLATION
                    && (in.gisFeatureKind() == null || !POINT_KINDS.contains(in.gisFeatureKind()))) {
                throw validation("confinedSpace", "LEVEL_MISMATCH",
                        "Espaco confinado exige instalacao (nivel 4) ou feicao de ponto.");
            }
        }
        // Ligacao a feicao do GIS (P1.26): a feicao precisa existir neste tenant.
        if (in.gisFeatureId() != null) {
            if (in.gisFeatureKind() == null || !Set.of("NODE", "ARC", "CONNECTION", "STRUCTURE", "ZONE", "POI")
                    .contains(in.gisFeatureKind())) {
                throw validation("gisFeatureKind", "INVALID", "Feicao do GIS invalida.");
            }
            if (!existsGisFeature(connection, in.gisFeatureKind(), in.gisFeatureId())) {
                throw validation("gisFeatureId", "NOT_FOUND", "Feicao do GIS inexistente neste tenant.");
            }
        } else if (in.gisFeatureKind() != null) {
            throw validation("gisFeatureKind", "ORPHAN", "Tipo de feicao sem feicao informada.");
        }
        validateMcpsb(in);
        // Correspondencia ao par UP/UAR, quando a linha existe (ressalva V27_1 §3).
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT equipment_class_code, up, uar FROM " + t("asset_type_mcpsb")
                        + " WHERE tenant_id = ? AND catalog_type_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, in.catalogTypeCode());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String mappedUp = rs.getString("up");
                    String mappedUar = rs.getString("uar");
                    if (mappedUp != null && !mappedUp.equals(in.up())) {
                        throw validation("up", "MAPPING_MISMATCH", "UP diverge do par da correspondencia do tipo.");
                    }
                    if (mappedUar != null && !mappedUar.equals(in.uar())) {
                        throw validation("uar", "MAPPING_MISMATCH", "UAR diverge do par da correspondencia do tipo.");
                    }
                    if ((level == AssetLevel.EQUIPMENT || level == AssetLevel.COMPONENT)
                            && !rs.getString("equipment_class_code").equals(in.assetClass())) {
                        throw validation("assetClass", "MAPPING_MISMATCH",
                                "Classe diverge da correspondencia do tipo.");
                    }
                }
            }
        }
        if (in.lifecycleStatus() != null && !LIFECYCLE.contains(in.lifecycleStatus())) {
            throw validation("lifecycleStatus", "INVALID", "Situacao do ciclo de vida invalida.");
        }
        if (in.manufactureYear() != null
                && (in.manufactureYear() < 1900 || in.manufactureYear() > 2200)) {
            throw validation("manufactureYear", "INVALID", "Ano de fabricacao entre 1900 e 2200.");
        }
        if (in.powerKw() != null && in.powerKw().signum() <= 0) {
            throw validation("powerKw", "INVALID", "Potencia positiva.");
        }
        if (in.capacity() != null && in.capacity().signum() < 0) {
            throw validation("capacity", "INVALID", "Capacidade nao negativa.");
        }
    }

    /** Formatos MCPSB (puros, sem banco: cobertos por teste unitario). */
    static void validateMcpsb(AssetInput in) {
        if (in.locIbge() == null || !DIGITS_7.matcher(in.locIbge()).matches()) {
            throw validation("locIbge", "INVALID", "LOC_IBGE tem 7 digitos (codigo do IBGE).");
        }
        if (in.ac() != null && !DIGITS_6.matcher(in.ac()).matches()) {
            throw validation("ac", "INVALID", "AC tem 6 digitos.");
        }
        if (in.up() != null && !DIGITS_2_OR_3.matcher(in.up()).matches()) {
            throw validation("up", "INVALID", "UP aceita 2 ou 3 digitos (manual divergente; ver V27_1).");
        }
        if (in.uar() != null && !DIGITS_7.matcher(in.uar()).matches()) {
            throw validation("uar", "INVALID", "UAR tem 7 digitos.");
        }
        for (String[] at : new String[][]{{"at1", in.at1()}, {"at2", in.at2()}, {"at3", in.at3()},
                {"at4", in.at4()}, {"at5", in.at5()}}) {
            if (at[1] != null && !DIGITS_2.matcher(at[1]).matches()) {
                throw validation(at[0], "INVALID", at[0] + " tem 2 digitos ('00' quando nao se aplica).");
            }
        }
        if (in.ntsSds() != null && !NTS_SDS.matcher(in.ntsSds()).matches()) {
            throw validation("ntsSds", "INVALID", "NTS_SDS em formato de codigo (lista fechada: ED-56).");
        }
        if (in.hierarchyLevel() >= 3 && (in.ntsSds() == null || in.ntsSds().isBlank())) {
            throw validation("ntsSds", "REQUIRED", "Do nivel 3 em diante, NTS_SDS e obrigatorio.");
        }
        if (!"INDIVIDUAL".equals(in.controlMode()) && !"MASS".equals(in.controlMode())) {
            throw validation("controlMode", "INVALID", "Modo de controle INDIVIDUAL ou MASS.");
        }
        if ("INDIVIDUAL".equals(in.controlMode())) {
            if (in.cib() == null || !DIGITS_25.matcher(in.cib()).matches()) {
                throw validation("cib", "INVALID", "CIB tem 25 digitos e so existe no bem individual.");
            }
        } else {
            if (in.cib() != null) {
                throw validation("cib", "MASS_HAS_CIB", "Bem controlado em massa nao tem CIB.");
            }
            if (in.unitOfMeasure() == null || in.unitOfMeasure().isBlank()) {
                throw validation("unitOfMeasure", "REQUIRED", "Bem em massa exige unidade de medida.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Persistencia
    // ------------------------------------------------------------------

    private void insertAsset(Connection connection, UUID id, UUID createdVia, UUID updatedVia,
                             AssetInput in, UUID actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + t("asset") + " (id, tenant_id, code, parent_asset_id, hierarchy_level,"
                        + " asset_class, catalog_type_code, gis_feature_kind, gis_feature_id, is_building,"
                        + " confined_space, cib, loc_ibge, ac, nts_sds, up, uar, at1, at2, at3, at4, at5,"
                        + " control_mode, unit_of_measure, bcp, brv, manufacturer, model, serial_number,"
                        + " manufacture_year, power_kw, capacity, capacity_unit, in_service_on,"
                        + " lifecycle_status, location_text, created_via_order_id, updated_via_order_id,"
                        + " created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
                        + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            ps.setObject(++i, id);
            ps.setObject(++i, tenantId);
            ps.setString(++i, in.code());
            setUuid(ps, ++i, in.parentAssetId());
            ps.setInt(++i, in.hierarchyLevel());
            setText(ps, ++i, in.assetClass());
            ps.setString(++i, in.catalogTypeCode());
            setText(ps, ++i, in.gisFeatureKind());
            setUuid(ps, ++i, in.gisFeatureId());
            ps.setBoolean(++i, in.isBuilding());
            ps.setBoolean(++i, in.confinedSpace());
            setText(ps, ++i, in.cib());
            ps.setString(++i, in.locIbge());
            setText(ps, ++i, in.ac());
            setText(ps, ++i, in.ntsSds());
            setText(ps, ++i, in.up());
            setText(ps, ++i, in.uar());
            setText(ps, ++i, in.at1() == null ? "00" : in.at1());
            setText(ps, ++i, in.at2() == null ? "00" : in.at2());
            setText(ps, ++i, in.at3() == null ? "00" : in.at3());
            setText(ps, ++i, in.at4() == null ? "00" : in.at4());
            setText(ps, ++i, in.at5() == null ? "00" : in.at5());
            ps.setString(++i, in.controlMode());
            setText(ps, ++i, in.unitOfMeasure());
            ps.setBoolean(++i, in.bcp());
            ps.setBoolean(++i, in.brv());
            setText(ps, ++i, in.manufacturer());
            setText(ps, ++i, in.model());
            setText(ps, ++i, in.serialNumber());
            if (in.manufactureYear() == null) {
                ps.setNull(++i, Types.INTEGER);
            } else {
                ps.setInt(++i, in.manufactureYear());
            }
            if (in.powerKw() == null) {
                ps.setNull(++i, Types.NUMERIC);
            } else {
                ps.setBigDecimal(++i, in.powerKw());
            }
            if (in.capacity() == null) {
                ps.setNull(++i, Types.NUMERIC);
            } else {
                ps.setBigDecimal(++i, in.capacity());
            }
            setText(ps, ++i, in.capacityUnit());
            if (in.inServiceOn() == null) {
                ps.setNull(++i, Types.DATE);
            } else {
                ps.setDate(++i, Date.valueOf(in.inServiceOn()));
            }
            ps.setString(++i, in.lifecycleStatus() == null ? "PLANNED" : in.lifecycleStatus());
            setText(ps, ++i, in.locationText());
            ps.setObject(++i, createdVia);
            ps.setObject(++i, updatedVia);
            setUuid(ps, ++i, actorId);
            setUuid(ps, ++i, actorId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateWrite(e);
        }
    }

    private void updateAssetRow(Connection connection, UUID assetId, UUID orderId,
                                AssetInput in, UUID actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + t("asset") + " SET code = ?, parent_asset_id = ?, hierarchy_level = ?,"
                        + " asset_class = ?, catalog_type_code = ?, gis_feature_kind = ?, gis_feature_id = ?,"
                        + " is_building = ?, confined_space = ?, cib = ?, loc_ibge = ?, ac = ?, nts_sds = ?,"
                        + " up = ?, uar = ?, at1 = ?, at2 = ?, at3 = ?, at4 = ?, at5 = ?, control_mode = ?,"
                        + " unit_of_measure = ?, bcp = ?, brv = ?, manufacturer = ?, model = ?, serial_number = ?,"
                        + " manufacture_year = ?, power_kw = ?, capacity = ?, capacity_unit = ?,"
                        + " in_service_on = ?, lifecycle_status = ?, location_text = ?,"
                        + " updated_via_order_id = ?, updated_by = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            int i = 0;
            ps.setString(++i, in.code());
            setUuid(ps, ++i, in.parentAssetId());
            ps.setInt(++i, in.hierarchyLevel());
            setText(ps, ++i, in.assetClass());
            ps.setString(++i, in.catalogTypeCode());
            setText(ps, ++i, in.gisFeatureKind());
            setUuid(ps, ++i, in.gisFeatureId());
            ps.setBoolean(++i, in.isBuilding());
            ps.setBoolean(++i, in.confinedSpace());
            setText(ps, ++i, in.cib());
            ps.setString(++i, in.locIbge());
            setText(ps, ++i, in.ac());
            setText(ps, ++i, in.ntsSds());
            setText(ps, ++i, in.up());
            setText(ps, ++i, in.uar());
            setText(ps, ++i, in.at1() == null ? "00" : in.at1());
            setText(ps, ++i, in.at2() == null ? "00" : in.at2());
            setText(ps, ++i, in.at3() == null ? "00" : in.at3());
            setText(ps, ++i, in.at4() == null ? "00" : in.at4());
            setText(ps, ++i, in.at5() == null ? "00" : in.at5());
            ps.setString(++i, in.controlMode());
            setText(ps, ++i, in.unitOfMeasure());
            ps.setBoolean(++i, in.bcp());
            ps.setBoolean(++i, in.brv());
            setText(ps, ++i, in.manufacturer());
            setText(ps, ++i, in.model());
            setText(ps, ++i, in.serialNumber());
            if (in.manufactureYear() == null) {
                ps.setNull(++i, Types.INTEGER);
            } else {
                ps.setInt(++i, in.manufactureYear());
            }
            if (in.powerKw() == null) {
                ps.setNull(++i, Types.NUMERIC);
            } else {
                ps.setBigDecimal(++i, in.powerKw());
            }
            if (in.capacity() == null) {
                ps.setNull(++i, Types.NUMERIC);
            } else {
                ps.setBigDecimal(++i, in.capacity());
            }
            setText(ps, ++i, in.capacityUnit());
            if (in.inServiceOn() == null) {
                ps.setNull(++i, Types.DATE);
            } else {
                ps.setDate(++i, Date.valueOf(in.inServiceOn()));
            }
            ps.setString(++i, in.lifecycleStatus() == null ? "PLANNED" : in.lifecycleStatus());
            setText(ps, ++i, in.locationText());
            ps.setObject(++i, orderId);
            setUuid(ps, ++i, actorId);
            ps.setObject(++i, tenantId);
            ps.setObject(++i, assetId);
            if (ps.executeUpdate() != 1) {
                throw new ApiException.NotFound("Ativo nao encontrado.");
            }
        } catch (SQLException e) {
            throw translateWrite(e);
        }
    }

    private RuntimeException translateWrite(SQLException e) {
        if ("23505".equals(e.getSQLState())) {
            return ApiException.Validation.of(new ApiException.FieldError("code", "DUPLICATE",
                    "Ja existe ativo com este codigo neste tenant."));
        }
        if ("23514".equals(e.getSQLState()) || "23503".equals(e.getSQLState())) {
            return ApiException.Validation.of(new ApiException.FieldError("asset", "CONSTRAINT",
                    "Ativo recusado por regra do banco: " + e.getMessage()));
        }
        return new IllegalStateException("Asset operation failed: " + e.getMessage(), e);
    }

    // ------------------------------------------------------------------
    // Consultas auxiliares
    // ------------------------------------------------------------------

    private Integer parentLevel(Connection connection, UUID parentId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT hierarchy_level FROM " + t("asset") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private boolean existsClass(Connection connection, String code) throws SQLException {
        return existsScalar(connection,
                "SELECT 1 FROM " + t("asset_equipment_class") + " WHERE tenant_id = ? AND code = ?", code);
    }

    private boolean existsActiveClass(Connection connection, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + t("asset_equipment_class")
                        + " WHERE tenant_id = ? AND code = ? AND active")) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existsCatalogType(Connection connection, String code) throws SQLException {
        return existsScalar(connection,
                "SELECT 1 FROM " + t("gis_feature_type") + " WHERE tenant_id = ? AND code = ?", code);
    }

    private boolean existsTypeMapping(Connection connection, String code) throws SQLException {
        return existsScalar(connection,
                "SELECT 1 FROM " + t("asset_type_mcpsb") + " WHERE tenant_id = ? AND catalog_type_code = ?",
                code);
    }

    private boolean existsConfinedSpaceType(Connection connection, String code) throws SQLException {
        return existsScalar(connection,
                "SELECT 1 FROM " + t("asset_confined_space_type")
                        + " WHERE tenant_id = ? AND catalog_type_code = ?", code);
    }

    private boolean existsScalar(Connection connection, String sql, String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existsGisFeature(Connection connection, String kind, UUID id) throws SQLException {
        String table = "gis_" + kindTable(kind);
        if (!GIS_TABLES.contains(table)) {
            return false;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + t(table) + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String kindTable(String kind) {
        return switch (kind) {
            case "NODE" -> "node";
            case "ARC" -> "arc";
            case "CONNECTION" -> "connection";
            case "STRUCTURE" -> "structure";
            case "ZONE" -> "zone";
            case "POI" -> "poi";
            default -> throw new IllegalArgumentException("feicao do GIS invalida: " + kind);
        };
    }

    private AssetView loadAsset(Connection connection, UUID assetId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("asset") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAsset(rs) : null;
            }
        }
    }

    private static AssetView readAsset(ResultSet rs) throws SQLException {
        Date inService = rs.getDate("in_service_on");
        return new AssetView(
                uuid(rs, "id"), rs.getString("code"), uuid(rs, "parent_asset_id"),
                rs.getInt("hierarchy_level"), rs.getString("asset_class"),
                rs.getString("catalog_type_code"), rs.getString("gis_feature_kind"),
                uuid(rs, "gis_feature_id"), rs.getBoolean("is_building"),
                rs.getBoolean("confined_space"), rs.getString("cib"), rs.getString("loc_ibge"),
                rs.getString("ac"), rs.getString("nts_sds"), rs.getString("up"), rs.getString("uar"),
                rs.getString("at1"), rs.getString("at2"), rs.getString("at3"),
                rs.getString("at4"), rs.getString("at5"), rs.getString("control_mode"),
                rs.getString("unit_of_measure"), rs.getBoolean("bcp"), rs.getBoolean("brv"),
                rs.getString("manufacturer"), rs.getString("model"), rs.getString("serial_number"),
                (Integer) rs.getObject("manufacture_year"), rs.getBigDecimal("power_kw"),
                rs.getBigDecimal("capacity"), rs.getString("capacity_unit"),
                inService == null ? null : inService.toLocalDate(),
                rs.getString("lifecycle_status"), rs.getString("location_text"),
                uuid(rs, "created_via_order_id"), uuid(rs, "updated_via_order_id"));
    }

    // ------------------------------------------------------------------
    // Infra
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Asset operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Asset operation failed: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return (UUID) rs.getObject(column);
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw validation(field, "REQUIRED", "O campo " + field + " e obrigatorio.");
        }
        return value;
    }

    static ApiException.Validation validation(String field, String code, String message) {
        return ApiException.Validation.of(new ApiException.FieldError(field, code, message));
    }
}
