package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.AcceptInput;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.IssueView;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.PageView;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.RuleCount;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.SeverityCount;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.SummaryView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Painel de qualidade (§5.5): leitura paginada dos problemas de
 * {@code gis_validation_issue}, agregados para os cartões do painel e
 * aceite com justificativa.
 *
 * <p>O tenant vem do contexto da requisição (token), como no catálogo —
 * nunca de parâmetro. O aceite grava justificativa, usuário e instante,
 * e só vale para problema OPEN (regra pura em
 * {@link GisValidationAcceptance}).
 */
@Service
@Transactional(readOnly = true)
public class GisQualityService {

    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public GisQualityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageView<IssueView> listIssues(String severity, String status, String ruleCode,
                                          String featureTable, UUID changeSetId, int page, int size) {
        UUID tenantId = GisValidationContext.tenantId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (severity != null && !severity.isBlank()) {
            where.append(" AND severity = ?");
            args.add(severity.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (ruleCode != null && !ruleCode.isBlank()) {
            where.append(" AND rule_code = ?");
            args.add(ruleCode.trim());
        }
        if (featureTable != null && !featureTable.isBlank()) {
            where.append(" AND feature_table = ?");
            args.add(featureTable.trim());
        }
        if (changeSetId != null) {
            where.append(" AND change_set_id = ?");
            args.add(changeSetId);
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM gis_validation_issue" + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<IssueView> content = jdbc.query("""
                SELECT id, rule_code, severity, feature_table, feature_id, feature2_id,
                       change_set_id, status, justification, accepted_by, accepted_at,
                       detected_at, resolved_at, ST_AsText(envelope) AS envelope_wkt
                FROM gis_validation_issue
                """ + where + " ORDER BY detected_at DESC, id LIMIT ? OFFSET ?",
                this::mapIssue, pageArgs.toArray());
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        return new PageView<>(content, safePage, safeSize, totalElements, totalPages);
    }

    public SummaryView summary() {
        UUID tenantId = GisValidationContext.tenantId();
        List<SeverityCount> bySeverity = jdbc.query("""
                SELECT severity, count(*) AS total FROM gis_validation_issue
                WHERE tenant_id = ? AND status = 'OPEN' GROUP BY severity ORDER BY severity
                """, (rs, row) -> new SeverityCount(rs.getString("severity"), rs.getLong("total")), tenantId);
        List<RuleCount> byRule = jdbc.query("""
                SELECT rule_code, count(*) AS total FROM gis_validation_issue
                WHERE tenant_id = ? AND status = 'OPEN' GROUP BY rule_code ORDER BY total DESC, rule_code
                """, (rs, row) -> new RuleCount(rs.getString("rule_code"), rs.getLong("total")), tenantId);
        return new SummaryView(
                countByStatus(tenantId, "OPEN"), countByStatus(tenantId, "ACCEPTED"),
                countByStatus(tenantId, "RESOLVED"), bySeverity, byRule);
    }

    @Transactional
    public IssueView accept(UUID issueId, AcceptInput in) {
        UUID tenantId = GisValidationContext.tenantId();
        UUID userId = GisValidationContext.userId();
        String justification = GisValidationAcceptance.requireJustification(
                in == null ? null : in.justification());
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM gis_validation_issue WHERE id = ? AND tenant_id = ?",
                String.class, issueId, tenantId);
        if (statuses.isEmpty()) {
            throw new ApiException.NotFound("Problema de validação não encontrado.");
        }
        GisValidationAcceptance.requireOpen(statuses.get(0));
        int updated = jdbc.update("""
                UPDATE gis_validation_issue
                SET status = 'ACCEPTED', justification = ?, accepted_by = ?, accepted_at = now()
                WHERE id = ? AND tenant_id = ? AND status = 'OPEN'
                """, justification, userId, issueId, tenantId);
        if (updated == 0) {
            throw new ApiException.Conflict("problema-nao-aberto",
                    "Só é possível aceitar um problema aberto.");
        }
        return getIssue(tenantId, issueId);
    }

    private IssueView getIssue(UUID tenantId, UUID issueId) {
        List<IssueView> found = jdbc.query("""
                SELECT id, rule_code, severity, feature_table, feature_id, feature2_id,
                       change_set_id, status, justification, accepted_by, accepted_at,
                       detected_at, resolved_at, ST_AsText(envelope) AS envelope_wkt
                FROM gis_validation_issue WHERE id = ? AND tenant_id = ?
                """, this::mapIssue, issueId, tenantId);
        if (found.isEmpty()) {
            throw new ApiException.NotFound("Problema de validação não encontrado.");
        }
        return found.get(0);
    }

    private long countByStatus(UUID tenantId, String status) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM gis_validation_issue WHERE tenant_id = ? AND status = ?",
                Long.class, tenantId, status);
        return count == null ? 0 : count;
    }

    private IssueView mapIssue(ResultSet rs, int row) throws SQLException {
        return new IssueView(
                rs.getObject("id", UUID.class), rs.getString("rule_code"), rs.getString("severity"),
                rs.getString("feature_table"), rs.getObject("feature_id", UUID.class),
                rs.getObject("feature2_id", UUID.class), rs.getObject("change_set_id", UUID.class),
                rs.getString("status"), rs.getString("justification"),
                rs.getObject("accepted_by", UUID.class),
                rs.getObject("accepted_at", java.time.OffsetDateTime.class),
                rs.getObject("detected_at", java.time.OffsetDateTime.class),
                rs.getObject("resolved_at", java.time.OffsetDateTime.class),
                rs.getString("envelope_wkt"));
    }
}
