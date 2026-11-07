package br.com.ziipsan.core.support;

import br.com.ziipsan.core.json.Json;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Retencao, expurgo e exportacao de chamados de suporte (P3.19;
 * ADR-0020; RAS-18).
 *
 * <p>Regras (aceite da P3.19, plano v3.0):
 * <ul>
 *   <li>{@code purge_at} de cada chamado = fim da vigencia do contrato
 *       SaaS do tenant ({@code platform.saas_contract.ends_at}, core
 *       {@code V10_1}/P2.12) mais 5 anos. A coluna ja existe desde a
 *       {@code V9_4} (P1.23) e fica NULL ate este servico a fixar; a
 *       P3.19 nao tem migration reservada no mapa do plano, portanto
 *       este servico NAO cria migration nenhuma;</li>
 *   <li>ensaio de expurgo (dry-run) conta o que seria apagado e nao
 *       apaga nada;</li>
 *   <li>os chamados entram na exportacao de fim de contrato
 *       ({@code platform.tenant_export}, P1.6) como a secao
 *       {@code support_tickets} do conteudo JSON;</li>
 *   <li>tenant de natureza publica ({@code reg.legal_nature} =
 *       {@code platform.tenant_profile.legal_nature} diferente de
 *       {@code PRIVATE}): expurgo e eliminacao de fim de contrato so
 *       com a tabela de temporalidade do tenant E a listagem de
 *       eliminacao registradas E aprovadas pelo tenant (Lei
 *       8.159/1991). Sem qualquer um dos quatro registros, a execucao
 *       e RECUSADA ({@link RetentionRefusedException}); ausencia de
 *       registro nunca vira aprovacao por omissao;</li>
 *   <li>natureza desconhecida ou ausente tambem recusa (fail-closed):
 *       o dominio e o da {@code V9} — {@code PRIVATE}, {@code AUTARCHY},
 *       {@code PUBLIC_COMPANY}, {@code SEM} — e qualquer outro valor e
 *       erro de dado, nao "privado por padrao".</li>
 * </ul>
 *
 * <p>Limites declarados (nao omissoes silenciosas):
 * <ul>
 *   <li>a persistencia dos registros de temporalidade/listagem (tabela
 *       propria) depende de migration que o plano nao reservou para a
 *       P3.19; ate la, quem chama le os registros onde eles estiverem
 *       registrados e os entrega em {@link ArchivalApproval}. O portao
 *       nao depende dessa tabela para recusar: sem registro entregue,
 *       ele recusa;</li>
 *   <li>o expurgo apaga o chamado e, em cascata explicita, mensagens,
 *       anexos (registro; os bytes no armazenamento de objetos sao da
 *       P3.16, junto do certificado de descarte) e eventos. O evento de
 *       auditoria do expurgo em si nao pode viver em
 *       {@code ticket_event} (apagado com o chamado): quem agenda o
 *       expurgo registra o resultado em {@code platform.audit_event}
 *       (P1.6);</li>
 *   <li>exportacao inclui mensagens PUBLIC e metadados de anexo (sem
 *       bytes). Nota INTERNAL e trabalho interno do Ziip e nao sai na
 *       exportacao ao tenant, como na API da P1.23 — inclusive o anexo
 *       vinculado a mensagem INTERNAL, cujos metadados tambem nao
 *       saem (correcao da revisao cruzada da P3.19);</li>
 *   <li>PENDENCIA DE GRANT (revisao cruzada da P3.19): a {@code V9_4}
 *       concede a {@code ziipsan_app} apenas SELECT/INSERT/UPDATE em
 *       {@code support.ticket} e SELECT/INSERT em
 *       {@code ticket_message}/{@code ticket_event} — nenhum DELETE
 *       nas quatro tabelas do expurgo. No espelho conferido nao ha
 *       grant posterior de DELETE; o {@link #purge} so executa em
 *       producao depois de migration de grant com numero reservado
 *       no plano (nenhum numero foi inventado aqui) ou de prova, na
 *       integracao, de grant equivalente ja existente fora do
 *       espelho. Sem isso, o banco recusa o DELETE por permissao.</li>
 * </ul>
 */
public final class TicketRetentionService {

    /** Anos de retencao apos o fim da vigencia (aceite P3.19; DPA da P3.9). */
    public static final int RETENTION_YEARS = 5;

    /** Dominio de {@code reg.legal_nature} (core V9, platform.tenant_profile). */
    public static final Set<String> LEGAL_NATURES = Set.of(
            "PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");

    /** Naturezas de direito publico: todas, exceto PRIVATE. */
    public static final Set<String> PUBLIC_LEGAL_NATURES = Set.of(
            "AUTARCHY", "PUBLIC_COMPANY", "SEM");

    /** Recusa de expurgo/eliminacao, com o motivo em codigo estavel. */
    public static final class RetentionRefusedException extends RuntimeException {
        private final String code;

        public RetentionRefusedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * Registros arquivisticos do tenant (Lei 8.159/1991), como lidos da
     * fonte de registro do tenant. Qualquer campo falso = registro
     * ausente ou nao aprovado = recusa para tenant publico.
     */
    public record ArchivalApproval(boolean temporalityTableRegistered,
                                   boolean temporalityTableApprovedByTenant,
                                   boolean eliminationListRegistered,
                                   boolean eliminationListApprovedByTenant) {

        public static final ArchivalApproval NONE = new ArchivalApproval(false, false, false, false);

        public boolean complete() {
            return temporalityTableRegistered && temporalityTableApprovedByTenant
                    && eliminationListRegistered && eliminationListApprovedByTenant;
        }
    }

    /** Contagem do ensaio (dry-run): nada foi apagado. */
    public record PurgeDryRun(UUID tenantId, OffsetDateTime cutoff, long tickets,
                              long messages, long attachments, long events) {
    }

    /** Resultado do expurgo real: o que foi apagado, por tabela. */
    public record PurgeResult(UUID tenantId, OffsetDateTime cutoff, long tickets,
                              long messages, long attachments, long events) {
    }

    private final DataSource dataSource;
    private final UUID tenantId;
    private final Clock clock;

    public TicketRetentionService(DataSource dataSource, UUID tenantId, Clock clock) {
        if (dataSource == null || tenantId == null || clock == null) {
            throw new IllegalArgumentException("dataSource, tenantId e clock sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Regra pura: purge_at e o portao arquivistico
    // ------------------------------------------------------------------

    /** {@code purge_at} (data) = fim da vigencia + {@value #RETENTION_YEARS} anos. */
    public static LocalDate purgeDate(LocalDate contractEndsAt) {
        if (contractEndsAt == null) {
            throw new IllegalArgumentException("contractEndsAt e obrigatorio");
        }
        return contractEndsAt.plusYears(RETENTION_YEARS);
    }

    /** {@code purge_at} (instante) = fim da vigencia + {@value #RETENTION_YEARS} anos. */
    public static OffsetDateTime purgeAt(OffsetDateTime contractEndsAt) {
        if (contractEndsAt == null) {
            throw new IllegalArgumentException("contractEndsAt e obrigatorio");
        }
        return contractEndsAt.plusYears(RETENTION_YEARS);
    }

    /** Elegivel ao expurgo: {@code purge_at} fixado e vencido (a mesma condicao do SQL). */
    public static boolean isPurgeable(OffsetDateTime purgeAt, OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now e obrigatorio");
        }
        return purgeAt != null && !purgeAt.isAfter(now);
    }

    /** Verdadeiro se a natureza e de direito publico; valor fora do dominio recusa. */
    public static boolean isPublicNature(String legalNature) {
        if (legalNature == null || !LEGAL_NATURES.contains(legalNature)) {
            throw new RetentionRefusedException("legal_nature_unknown",
                    "reg.legal_nature ausente ou fora do dominio (PRIVATE, AUTARCHY, PUBLIC_COMPANY, SEM): "
                            + legalNature);
        }
        return PUBLIC_LEGAL_NATURES.contains(legalNature);
    }

    /**
     * Portao do expurgo e da eliminacao de fim de contrato (RAS-18).
     * Tenant privado: liberado sem exigencia arquivistica. Tenant
     * publico: exige tabela de temporalidade e listagem de eliminacao
     * registradas E aprovadas; qualquer ausencia recusa, nomeando o
     * que falta. Natureza desconhecida recusa sempre.
     */
    public static void requirePurgeAllowed(String legalNature, ArchivalApproval approval) {
        if (!isPublicNature(legalNature)) {
            return;
        }
        if (approval == null) {
            approval = ArchivalApproval.NONE;
        }
        List<String> missing = new ArrayList<>();
        if (!approval.temporalityTableRegistered()) {
            missing.add("tabela de temporalidade nao registrada");
        }
        if (!approval.temporalityTableApprovedByTenant()) {
            missing.add("tabela de temporalidade nao aprovada pelo tenant");
        }
        if (!approval.eliminationListRegistered()) {
            missing.add("listagem de eliminacao nao registrada");
        }
        if (!approval.eliminationListApprovedByTenant()) {
            missing.add("listagem de eliminacao nao aprovada pelo tenant");
        }
        if (!missing.isEmpty()) {
            throw new RetentionRefusedException("archival_approval_missing",
                    "Tenant de natureza publica (" + legalNature + "): expurgo de chamados e "
                            + "eliminacao de fim de contrato exigem tabela de temporalidade e "
                            + "listagem de eliminacao registradas e aprovadas pelo tenant "
                            + "(Lei 8.159/1991). Faltando: " + String.join("; ", missing) + ".");
        }
    }

    // ------------------------------------------------------------------
    // Fixacao do purge_at a partir do contrato vigente/encerrado
    // ------------------------------------------------------------------

    /**
     * Fixa {@code purge_at} de todos os chamados do tenant a partir do
     * {@code ends_at} do contrato SaaS mais recente do tenant
     * (qualquer situacao exceto DRAFT). Devolve quantos chamados foram
     * atualizados. Sem contrato, recusa: nao ha fim de vigencia do qual
     * derivar a retencao, e inventar uma data violaria o aceite.
     */
    public int stampPurgeAtFromContract() {
        return inTx(connection -> {
            LocalDate endsAt = latestContractEnd(connection);
            if (endsAt == null) {
                throw new RetentionRefusedException("contract_not_found",
                        "Sem contrato SaaS registrado para o tenant: purge_at nao pode ser derivado.");
            }
            OffsetDateTime purgeAt = purgeDate(endsAt).atStartOfDay().atOffset(ZoneOffset.UTC);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket SET purge_at = ?, updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(purgeAt.toInstant()));
                ps.setObject(2, tenantId);
                return ps.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------
    // Ensaio e expurgo
    // ------------------------------------------------------------------

    /** Ensaio: conta chamados com {@code purge_at} vencido e seus dependentes. Nao apaga nada. */
    public PurgeDryRun dryRun() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inTx(connection -> new PurgeDryRun(tenantId, now,
                count(connection, "support.ticket", now),
                countDependents(connection, "support.ticket_message", now),
                countDependents(connection, "support.ticket_attachment", now),
                countDependents(connection, "support.ticket_event", now)));
    }

    /**
     * Expurgo real: passa pelo portao arquivistico ANTES de abrir a
     * transacao de escrita e apaga, na ordem das FKs, anexos, mensagens,
     * eventos e chamados com {@code purge_at} vencido.
     */
    public PurgeResult purge(String legalNature, ArchivalApproval approval) {
        requirePurgeAllowed(legalNature, approval);
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inTx(connection -> {
            long attachments = deleteDependents(connection, "support.ticket_attachment", now);
            long messages = deleteDependents(connection, "support.ticket_message", now);
            long events = deleteDependents(connection, "support.ticket_event", now);
            long tickets;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM support.ticket WHERE tenant_id = ? AND purge_at IS NOT NULL AND purge_at <= ?")) {
                ps.setObject(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(now.toInstant()));
                tickets = ps.executeUpdate();
            }
            return new PurgeResult(tenantId, now, tickets, messages, attachments, events);
        });
    }

    // ------------------------------------------------------------------
    // Exportacao de fim de contrato (secao support_tickets)
    // ------------------------------------------------------------------

    /**
     * Secao dos chamados para a exportacao de fim de contrato
     * ({@code platform.tenant_export}, P1.6): chamados do tenant com
     * mensagens PUBLIC e metadados de anexo, serializada com a classe
     * {@code Json} do core. Para tenant publico, a eliminacao que se
     * segue a exportacao passa pelo mesmo portao de
     * {@link #requirePurgeAllowed} (ver {@link #purge}).
     */
    public String exportTicketsJson() {
        return inTx(connection -> {
            List<Map<String, Object>> tickets = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, number, requester_id, subject, category, module, severity, status,"
                            + " contains_personal_data, purge_at, created_at, updated_at"
                            + " FROM support.ticket WHERE tenant_id = ? ORDER BY created_at")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> ticket = new LinkedHashMap<>();
                        UUID ticketId = (UUID) rs.getObject(1);
                        ticket.put("id", ticketId.toString());
                        ticket.put("number", rs.getString(2));
                        ticket.put("requester_id", str(rs.getObject(3)));
                        ticket.put("subject", rs.getString(4));
                        ticket.put("category", rs.getString(5));
                        ticket.put("module", rs.getString(6));
                        ticket.put("severity", rs.getString(7));
                        ticket.put("status", rs.getString(8));
                        ticket.put("contains_personal_data", rs.getBoolean(9));
                        ticket.put("purge_at", str(rs.getObject(10)));
                        ticket.put("created_at", str(rs.getObject(11)));
                        ticket.put("updated_at", str(rs.getObject(12)));
                        ticket.put("messages", exportMessages(connection, ticketId));
                        ticket.put("attachments", exportAttachments(connection, ticketId));
                        tickets.add(ticket);
                    }
                }
            }
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("tenant_id", tenantId.toString());
            section.put("exported_at", OffsetDateTime.now(clock).toString());
            section.put("ticket_count", tickets.size());
            section.put("tickets", tickets);
            return Json.write(section);
        });
    }

    private List<Map<String, Object>> exportMessages(Connection connection, UUID ticketId) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, author_type, author_id, body, contains_personal_data, created_at"
                        + " FROM support.ticket_message WHERE tenant_id = ? AND ticket_id = ?"
                        + " AND visibility = 'PUBLIC' ORDER BY created_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", str(rs.getObject(1)));
                    m.put("author_type", rs.getString(2));
                    m.put("author_id", str(rs.getObject(3)));
                    m.put("body", rs.getString(4));
                    m.put("contains_personal_data", rs.getBoolean(5));
                    m.put("created_at", str(rs.getObject(6)));
                    out.add(m);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> exportAttachments(Connection connection, UUID ticketId) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, message_id, file_name, detected_mime, size_bytes, sha256, scan_status, created_at"
                        + " FROM support.ticket_attachment WHERE tenant_id = ? AND ticket_id = ?"
                        + " AND (message_id IS NULL OR message_id IN (SELECT id FROM support.ticket_message"
                        + " WHERE tenant_id = ? AND ticket_id = ? AND visibility = 'PUBLIC'))"
                        + " ORDER BY created_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            ps.setObject(3, tenantId);
            ps.setObject(4, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("id", str(rs.getObject(1)));
                    a.put("message_id", str(rs.getObject(2)));
                    a.put("file_name", rs.getString(3));
                    a.put("detected_mime", rs.getString(4));
                    a.put("size_bytes", rs.getLong(5));
                    a.put("sha256", rs.getString(6));
                    a.put("scan_status", rs.getString(7));
                    a.put("created_at", str(rs.getObject(8)));
                    out.add(a);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Infra interna (mesmo padrao do TicketService: RLS por transacao)
    // ------------------------------------------------------------------

    /** String do valor, ou {@code null} JSON quando a coluna e NULL (nunca a literal "null"). */
    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDate latestContractEnd(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ends_at FROM platform.saas_contract WHERE tenant_id = ? AND status <> 'DRAFT'"
                        + " ORDER BY ends_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Date ends = rs.getDate(1);
                return ends == null ? null : ends.toLocalDate();
            }
        }
    }

    private long count(Connection connection, String table, OffsetDateTime cutoff) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND purge_at IS NOT NULL AND purge_at <= ?")) {
            ps.setObject(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(cutoff.toInstant()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long countDependents(Connection connection, String table, OffsetDateTime cutoff) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " d WHERE d.tenant_id = ? AND EXISTS ("
                        + "SELECT 1 FROM support.ticket t WHERE t.id = d.ticket_id AND t.tenant_id = ?"
                        + " AND t.purge_at IS NOT NULL AND t.purge_at <= ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, tenantId);
            ps.setTimestamp(3, Timestamp.from(cutoff.toInstant()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long deleteDependents(Connection connection, String table, OffsetDateTime cutoff) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM " + table + " d WHERE d.tenant_id = ? AND EXISTS ("
                        + "SELECT 1 FROM support.ticket t WHERE t.id = d.ticket_id AND t.tenant_id = ?"
                        + " AND t.purge_at IS NOT NULL AND t.purge_at <= ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, tenantId);
            ps.setTimestamp(3, Timestamp.from(cutoff.toInstant()));
            return ps.executeUpdate();
        }
    }

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
                throw new IllegalStateException("Ticket retention operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ticket retention operation failed: " + e.getMessage(), e);
        }
    }
}
