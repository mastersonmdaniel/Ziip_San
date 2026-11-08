package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.EndpointPolicyInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.PgServiceConfView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectDownloadInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectLayerInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestInput;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleRequestView;
import br.com.ziipsan.intelligence.gis.qgis.GisQgisDtos.RoleView;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Acesso QGIS (GIS-10, §7.1) pelo lado da api: registro do PEDIDO de
 * role em estrutura propria ({@code platform.gis_db_role_request},
 * core V12) e leitura do estado das roles materializadas em
 * {@code platform.gis_db_role}.
 *
 * <p>Fronteira do acceptance (correcao D6 da revisao cruzada):
 * {@code platform.gis_db_role} e gravada SO pela rotina privilegiada
 * de implantacao — este servico NAO tem nenhum INSERT/UPDATE nela.
 * A api grava apenas o PEDIDO (status PENDING) em
 * {@code gis_db_role_request}; a rotina le o pedido, aplica o
 * quatro-olhos e materializa a role (linha em gis_db_role + CREATE
 * ROLE/GRANT). A trilha de decisoes sobre a role
 * ({@code gis_db_role_audit}) tambem e da rotina: o pedido nao e uma
 * linha de gis_db_role, logo nao gera auditoria de role.
 *
 * <p>Entrega do projeto (correcao D5): {@link #downloadProject}
 * devolve os bytes do {@code .qgz} gerado sob demanda e
 * {@link #pgServiceConfForCurrentUser} devolve o conteudo do
 * {@code pg_service.conf} — com o nome da role derivado no servidor
 * (tenant+usuario da sessao), nunca vindo do cliente. O estilo de
 * cada camada precisa vir resolvido da GIS-06 no pedido: a fabrica
 * recusa camada sem estilo.
 */
@Service
@Transactional(readOnly = true)
public class GisQgisService {

    private final JdbcTemplate jdbc;

    public GisQgisService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ pedidos

    @Transactional
    public RoleRequestView requestRole(RoleRequestInput in) {
        UUID tenantId = GisQgisContext.tenantId();
        UUID requester = GisQgisContext.userId();
        UUID targetUser = UUID.fromString(in.userId());
        String roleName = GisQgisRules.roleNameFor(
                tenantId.toString(), targetUser.toString());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO platform.gis_db_role_request (id, role_name,
                            tenant_id, user_id, status, valid_until,
                            requested_by)
                    VALUES (?, ?, ?, ?, 'PENDING', ?, ?)
                    """, id, roleName, tenantId, targetUser,
                    in.validUntil() == null ? null
                            : Timestamp.from(in.validUntil()),
                    requester);
        } catch (DataAccessException e) {
            throw new ApiException.Conflict("role-qgis-ja-pedida",
                    "Ja existe pedido pendente ou role QGIS para este usuario neste tenant.");
        }
        return new RoleRequestView(id, roleName, tenantId, targetUser,
                "PENDING", in.validUntil());
    }

    public RoleRequestView getRoleRequest(UUID id) {
        UUID tenantId = GisQgisContext.tenantId();
        List<RoleRequestView> rows = jdbc.query("""
                SELECT id, role_name, tenant_id, user_id, status, valid_until
                  FROM platform.gis_db_role_request
                 WHERE id = ? AND tenant_id = ?
                """, (rs, n) -> new RoleRequestView(
                rs.getObject("id", UUID.class),
                rs.getString("role_name"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("valid_until") == null ? null
                        : rs.getTimestamp("valid_until").toInstant()),
                id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Pedido de role QGIS nao encontrado.");
        }
        return rows.get(0);
    }

    public List<RoleRequestView> listRoleRequests() {
        UUID tenantId = GisQgisContext.tenantId();
        return jdbc.query("""
                SELECT id, role_name, tenant_id, user_id, status, valid_until
                  FROM platform.gis_db_role_request
                 WHERE tenant_id = ?
                 ORDER BY created_at, id
                """, (rs, n) -> new RoleRequestView(
                rs.getObject("id", UUID.class),
                rs.getString("role_name"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("valid_until") == null ? null
                        : rs.getTimestamp("valid_until").toInstant()), tenantId);
    }

    // ------------------------------------------------- roles materializadas

    public RoleView getRole(UUID id) {
        UUID tenantId = GisQgisContext.tenantId();
        List<RoleView> rows = jdbc.query("""
                SELECT id, role_name, tenant_id, user_id, status,
                       valid_until, connection_limit, statement_timeout_ms
                  FROM platform.gis_db_role
                 WHERE id = ? AND tenant_id = ?
                """, (rs, n) -> new RoleView(
                rs.getObject("id", UUID.class),
                rs.getString("role_name"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("valid_until") == null ? null
                        : rs.getTimestamp("valid_until").toInstant(),
                rs.getInt("connection_limit"),
                rs.getInt("statement_timeout_ms")), id, tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("Role QGIS nao encontrada.");
        }
        return rows.get(0);
    }

    public List<RoleView> listRoles() {
        UUID tenantId = GisQgisContext.tenantId();
        return jdbc.query("""
                SELECT id, role_name, tenant_id, user_id, status,
                       valid_until, connection_limit, statement_timeout_ms
                  FROM platform.gis_db_role
                 WHERE tenant_id = ?
                 ORDER BY created_at, id
                """, (rs, n) -> new RoleView(
                rs.getObject("id", UUID.class),
                rs.getString("role_name"),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("valid_until") == null ? null
                        : rs.getTimestamp("valid_until").toInstant(),
                rs.getInt("connection_limit"),
                rs.getInt("statement_timeout_ms")), tenantId);
    }

    /** Valida a politica do endpoint sem aplicar nada (§7.1 item 5). */
    public void validateEndpointPolicy(EndpointPolicyInput in) {
        try {
            GisQgisRules.ensureEndpointPolicy(in.tlsRequired(), in.sslMode(),
                    in.viaPooler(), in.vpnRequired(), in.allowedCidr());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("endpoint-qgis-invalido", e.getMessage());
        }
    }

    /**
     * Metadados do projeto (sem gerar o arquivo): valida que toda
     * camada pedida e uma view qgis_* autorizavel.
     */
    public ProjectView describeProject(List<ProjectLayerInput> layers,
                                       String serviceName) {
        for (ProjectLayerInput layer : layers) {
            try {
                GisQgisRules.ensureGrantTarget(layer.viewName());
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("camada-qgis-invalida", e.getMessage());
            }
        }
        return new ProjectView("ziipsan-qgis.qgz", serviceName,
                layers.stream().map(ProjectLayerInput::viewName).toList());
    }

    /**
     * Entrega do .qgz (correcao D5): gera os bytes com as camadas e
     * os estilos resolvidos da GIS-06 vindos no pedido. Camada fora
     * das views ou sem estilo e recusada pela fabrica.
     */
    public byte[] downloadProject(ProjectDownloadInput in) {
        if (in.layers() == null || in.layers().isEmpty()) {
            throw new ApiException.BadRequest("projeto-qgis-invalido",
                    "Projeto QGIS sem camadas.");
        }
        List<GisQgzProjectFactory.LayerSpec> layers = in.layers().stream()
                .map(l -> new GisQgzProjectFactory.LayerSpec(
                        l.viewName(), l.label(), l.groupLabel(), l.colorToken(),
                        l.lineWidth(), l.dash(),
                        l.fields() == null ? List.of()
                                : l.fields().stream()
                                        .map(f -> new GisQgzProjectFactory.FieldSpec(
                                                f.name(), f.label(), f.valueList()))
                                        .toList(),
                        l.minScaleDenominator()))
                .toList();
        return buildQgz(in.title(), in.serviceName(), in.authCfgId(), layers);
    }

    /**
     * Conteudo do pg_service.conf do usuario da sessao (correcao
     * D5): o nome da role e derivado AQUI de tenant+usuario — o
     * cliente informa so host/porta/banco do endpoint dedicado.
     * Sem senha, com sslmode verify-full (garantido pela fabrica).
     */
    public PgServiceConfView pgServiceConfForCurrentUser(PgServiceConfInput in) {
        UUID tenantId = GisQgisContext.tenantId();
        UUID userId = GisQgisContext.userId();
        String roleName = GisQgisRules.roleNameFor(
                tenantId.toString(), userId.toString());
        try {
            String content = GisQgzProjectFactory.pgServiceConf(
                    in.serviceName(), in.host(), in.port(), in.dbName(), roleName);
            return new PgServiceConfView(in.serviceName(), roleName, content);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("pg-service-invalido", e.getMessage());
        }
    }

    public byte[] buildQgz(String title, String serviceName, String authCfgId,
                           List<GisQgzProjectFactory.LayerSpec> layers) {
        try {
            return GisQgzProjectFactory.projectQgz(title, serviceName, authCfgId, layers);
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("projeto-qgis-invalido", e.getMessage());
        }
    }
}
