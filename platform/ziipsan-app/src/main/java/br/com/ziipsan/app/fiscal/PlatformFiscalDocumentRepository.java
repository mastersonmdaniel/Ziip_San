package br.com.ziipsan.app.fiscal;

import br.com.ziipsan.commercial.fiscal.port.AuthorizedXmlStore;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementacao, no schema {@code platform}, da porta de persistencia do motor fiscal
 * (P2.13, core V10_1_1) — o espelho, para a NFS-e da venda SaaS, de
 * {@code JdbcFiscalDocumentRepository} (V4_5, schema do tenant).
 *
 * <p>Por que um repositorio proprio, e nao o JDBC do porte: o prestador da NFS-e do SaaS
 * e a propria plataforma, nao o tenant do contrato. As tabelas fiscais do tenant
 * ({@code fiscal_issuers}, {@code fiscal_series}, {@code fiscal_documents}) descrevem o
 * prestador de saneamento emitindo para o consumidor; aqui o emitente e o contexto da
 * plataforma ({@code platform_fiscal_context}) e o tomador e o tenant. Reutilizar o
 * repositorio do tenant exigiria um schema de tenant para a plataforma, que nao existe.
 *
 * <p>O que e identico ao porte, sem atalho: reserva atomica do numero (linha da serie
 * travada com {@code FOR UPDATE} na mesma transacao que grava o documento em RESERVED),
 * numero que nunca volta ao estoque, unicidade de origem (uma NFS-e por fatura) e as
 * mesmas transicoes de estado do servico de emissao. A transacao e a do chamador
 * (o servico de NFS-e do SaaS e {@code @Transactional}): todos os passos da emissao
 * — reserva, assinatura, autorizacao e guarda do XML — participam dela.
 *
 * <p>O {@code tenantId} da porta e o tenant do contrato (o tomador): ele escopa a leitura
 * e a unicidade de origem, mas o emitente vem do contexto da plataforma, nao dele.
 */
public class PlatformFiscalDocumentRepository implements FiscalDocumentRepository, AuthorizedXmlStore {

    private final JdbcTemplate jdbc;

    public PlatformFiscalDocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IssuerRecord> findIssuer(UUID tenantId, UUID issuerId) {
        List<IssuerRecord> rows = jdbc.query(
                "SELECT id, municipality_ibge, document_hash FROM platform.platform_fiscal_context WHERE id = ?",
                (rs, i) -> new IssuerRecord(rs.getObject(1, UUID.class), rs.getString(2),
                        "CNPJ", rs.getString(3)),
                issuerId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void ensureSeries(UUID tenantId, UUID issuerId, String documentType, String series) {
        jdbc.update("INSERT INTO platform.saas_nfse_series (context_id, series) VALUES (?, ?)"
                + " ON CONFLICT (context_id, series) DO NOTHING", issuerId, series);
    }

    @Override
    public ReservedSlot reserveSlot(UUID tenantId, UUID issuerId, String documentType, String series,
                                    String sourceType, UUID sourceId, String environment, Integer tpAmb,
                                    BigDecimal gross) {
        List<SeriesRow> rows = jdbc.query(
                "SELECT id, next_number FROM platform.saas_nfse_series"
                        + " WHERE context_id = ? AND series = ? FOR UPDATE",
                (rs, i) -> new SeriesRow(rs.getObject(1, UUID.class), rs.getLong(2)),
                issuerId, series);
        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Serie fiscal inexistente: crie-a com ensureSeries antes de emitir");
        }
        SeriesRow row = rows.get(0);
        long number = row.nextNumber();
        jdbc.update("UPDATE platform.saas_nfse_series SET next_number = ?, updated_at = NOW() WHERE id = ?",
                number + 1, row.id());
        UUID documentId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO platform.saas_nfse (id, invoice_id, tenant_id, context_id,"
                    + " source_type, source_id, series, number, status, environment, tp_amb, gross)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?, ?, ?)",
                    documentId, sourceId, tenantId, issuerId, sourceType, sourceId, series, number,
                    environment, tpAmb == null ? null : tpAmb, gross);
        } catch (DuplicateKeyException e) {
            // 23505 aqui so pode ser a unicidade de origem: o numero vem da serie travada
            // e e unico por construcao — o mesmo padrao do repositorio do porte.
            throw new DuplicateSourceException(
                    "A origem " + sourceType + "/" + sourceId + " ja gerou um documento " + documentType);
        }
        return new ReservedSlot(documentId, number);
    }

    @Override
    public void signDocument(UUID tenantId, UUID documentId, String dpsId, String unsignedXml,
                             List<TaxLine> taxes) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET dps_id = ?, dps_xml = ?, status = 'SIGNED',"
                + " updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                dpsId, unsignedXml, tenantId, documentId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente para assinar: " + documentId);
        }
        for (TaxLine tax : taxes) {
            jdbc.update("INSERT INTO platform.saas_nfse_tax (nfse_id, tax_code, rate, amount, informative)"
                    + " VALUES (?, ?, ?, ?, ?)",
                    documentId, tax.taxCode(), tax.rate(), tax.amount(), tax.informative());
        }
    }

    /**
     * Congela o tomador da NFS-e do SaaS na linha do documento (P2.13). O motor portado nao
     * grava o tomador: o nome entra so no XML da DPS e o CNPJ do tomador nao entra no XML —
     * mas a leitura da NFS-e ({@code SaaSFiscalRegistry.NfseView}) e a auditoria da emissao
     * exigem o tomador copiado de {@code platform.tenant_profile} no momento da emissao.
     * Fora da porta do motor (metodo proprio deste repositorio, nao do porte): o servico de
     * NFS-e do SaaS o chama na mesma transacao da emissao, apos a autorizacao e antes de
     * resolver {@code saas_invoice.nfse_document_ref}.
     */
    public void recordTaker(UUID tenantId, UUID documentId, String takerName, String takerCnpj) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET taker_name = ?, taker_cnpj = ?,"
                + " updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                takerName, takerCnpj, tenantId, documentId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente para registrar o tomador: " + documentId);
        }
    }

    @Override
    public void markAuthorized(UUID tenantId, UUID documentId, String protocol, String accessKey) {
        updateStatus(tenantId, documentId,
                "UPDATE platform.saas_nfse SET status = 'AUTHORIZED', protocol = ?, access_key = ?,"
                        + " updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                protocol, accessKey);
    }

    @Override
    public void markRejected(UUID tenantId, UUID documentId, String code, String message) {
        updateStatus(tenantId, documentId,
                "UPDATE platform.saas_nfse SET status = 'REJECTED', rejection_code = ?,"
                        + " rejection_message = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                code, message);
    }

    @Override
    public void markPendingReconciliation(UUID tenantId, UUID documentId) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET status = 'PENDING_RECONCILIATION',"
                + " updated_at = NOW() WHERE tenant_id = ? AND id = ?", tenantId, documentId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente: " + documentId);
        }
    }

    @Override
    public void markCancelled(UUID tenantId, UUID documentId) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET status = 'CANCELLED',"
                + " updated_at = NOW() WHERE tenant_id = ? AND id = ?", tenantId, documentId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente: " + documentId);
        }
    }

    @Override
    public void markReplaced(UUID tenantId, UUID originalId, UUID replacementId) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET status = 'REPLACED', replaced_by_id = ?,"
                + " updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                replacementId, tenantId, originalId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente: " + originalId);
        }
    }

    @Override
    public void registerEvent(UUID tenantId, EventRecord event) {
        jdbc.update("INSERT INTO platform.saas_nfse_event (id, nfse_id, event_id, event_type, kind, status,"
                + " reason, requested_by, approved_by, approved_by_second)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                event.id(), event.documentId(), event.eventId(), event.eventType(), event.eventKind(),
                event.status(), event.reason(), event.requestedBy(), event.approvedBy(),
                event.approvedBySecond());
    }

    @Override
    public void storeAuthorizedXml(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256,
                                   String source) {
        int updated = jdbc.update("UPDATE platform.saas_nfse SET xml_gzip = ?, xml_sha256 = ?,"
                + " xml_source = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                xmlGzip, sha256, source, tenantId, documentId);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente para guardar o XML: " + documentId);
        }
    }

    @Override
    public Optional<DocumentRecord> findDocument(UUID tenantId, UUID documentId) {
        List<DocumentRecord> rows = jdbc.query(
                "SELECT id, context_id, series, number, dps_id, access_key, source_type, source_id,"
                        + " status, environment, tp_amb, gross, net, dps_xml, protocol, rejection_code,"
                        + " rejection_message, replaced_by_id FROM platform.saas_nfse"
                        + " WHERE tenant_id = ? AND id = ?",
                (rs, i) -> new DocumentRecord(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        "NFSE_NACIONAL", rs.getString(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getObject(8, UUID.class),
                        rs.getString(9), rs.getString(10),
                        rs.getObject(11, Integer.class), rs.getBigDecimal(12), rs.getBigDecimal(13),
                        rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17),
                        rs.getObject(18, UUID.class)),
                tenantId, documentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<DocumentRecord> findBySource(UUID tenantId, String sourceType, UUID sourceId,
                                                 String documentType) {
        List<DocumentRecord> rows = jdbc.query(
                "SELECT id, context_id, series, number, dps_id, access_key, source_type, source_id,"
                        + " status, environment, tp_amb, gross, net, dps_xml, protocol, rejection_code,"
                        + " rejection_message, replaced_by_id FROM platform.saas_nfse"
                        + " WHERE tenant_id = ? AND source_type = ? AND source_id = ?",
                (rs, i) -> new DocumentRecord(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        "NFSE_NACIONAL", rs.getString(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getObject(8, UUID.class),
                        rs.getString(9), rs.getString(10),
                        rs.getObject(11, Integer.class), rs.getBigDecimal(12), rs.getBigDecimal(13),
                        rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17),
                        rs.getObject(18, UUID.class)),
                tenantId, sourceType, sourceId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<EventRecord> findEventByDocumentAndKind(UUID tenantId, UUID documentId, String eventKind) {
        List<EventRecord> rows = jdbc.query(
                "SELECT e.id, e.nfse_id, e.event_id, e.event_type, e.kind, e.status, e.reason,"
                        + " e.requested_by, e.approved_by, e.approved_by_second"
                        + " FROM platform.saas_nfse_event e JOIN platform.saas_nfse n ON n.id = e.nfse_id"
                        + " WHERE n.tenant_id = ? AND e.nfse_id = ? AND e.kind = ? ORDER BY e.created_at DESC",
                (rs, i) -> new EventRecord(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getObject(8, UUID.class), rs.getObject(9, UUID.class),
                        rs.getObject(10, UUID.class)),
                tenantId, documentId, eventKind);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<TaxRateRow> taxRates(UUID tenantId) {
        return jdbc.query(
                "SELECT tax_code, scope, valid_from, valid_to, rate FROM platform.saas_fiscal_tax_rate",
                (rs, i) -> {
                    Date from = rs.getDate(3);
                    Date to = rs.getDate(4);
                    LocalDate validFrom = from == null ? null : from.toLocalDate();
                    LocalDate validTo = to == null ? null : to.toLocalDate();
                    return new TaxRateRow(rs.getString(1), rs.getString(2), validFrom, validTo,
                            rs.getBigDecimal(5));
                });
    }

    @Override
    public UUID insertTaxRate(UUID tenantId, TaxRateRow row, String legalBasis) {
        // Quebra de integracao P2-A (P2.4 x P2.13): a P2.4 acrescentou
        // insertTaxRate a porta e a P2.13, empilhada sobre a P2.12 sem a
        // P2.4, nao o implementava — o build da app caia. Insert simples,
        // espelho do JdbcFiscalDocumentRepository: a validacao e a
        // checagem de sobreposicao vivem no TaxRateService, antes da
        // chamada. O tenantId nao existe na tabela da plataforma (as
        // aliquotas do SaaS sao globais, como taxRates ja ignora o
        // parametro) e o escopo vem da linha. O caminho de escrita da
        // plataforma continua sendo SaaSFiscalRegistry.registerTaxRate
        // (sobreposicao + auditoria); nenhum servico roteia o
        // TaxRateService para este repositorio hoje.
        return jdbc.queryForObject(
                "INSERT INTO platform.saas_fiscal_tax_rate (tax_code, scope, valid_from, valid_to,"
                        + " rate, legal_basis) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                new Object[]{row.taxCode(), row.scope(), Date.valueOf(row.validFrom()),
                        row.validTo() == null ? null : Date.valueOf(row.validTo()), row.rate(), legalBasis},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.DATE, Types.DATE, Types.NUMERIC,
                        Types.VARCHAR},
                (rs, i) -> rs.getObject(1, UUID.class));
    }

    @Override
    public void store(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source) {
        storeAuthorizedXml(tenantId, documentId, xmlGzip, sha256, source);
    }

    private void updateStatus(UUID tenantId, UUID documentId, String sql, String first, String second) {
        Object[] args = {first, second, tenantId, documentId};
        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.OTHER, Types.OTHER};
        int updated = jdbc.update(sql, args, types);
        if (updated != 1) {
            throw new IllegalStateException("Documento inexistente: " + documentId);
        }
    }

    private record SeriesRow(UUID id, long nextNumber) {
    }
}
