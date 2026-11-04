package br.com.ziipsan.platform.contract;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.contract.SaaSContractDocumentResolver.ResolvedDocument;
import br.com.ziipsan.platform.contract.SaaSContractService.ContractView;
import br.com.ziipsan.platform.contract.SaaSContractService.InvoiceView;
import br.com.ziipsan.platform.contract.SaaSContractService.PaymentView;
import br.com.ziipsan.platform.contract.SaaSContractService.StatementView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Area do tenant: contrato com o Ziip (P2.14, ADR-0022).
 *
 * <p>Tudo aqui e filtrado pelo tenant do token, nunca por parametro:
 * contrato, fatura ou pagamento de outro tenant responde 404 com a
 * mesma mensagem do inexistente (sem oraculo). As leituras reutilizam
 * o {@link SaaSContractService} da P2.12 (contratos por tenant,
 * fatura, pagamentos sem conciliar, relatorio de uso); as listas por
 * contrato sao consultas novas, no mesmo padrao de mapeamento.
 *
 * <p>Auditoria: cada acesso servido insere uma linha em
 * {@code platform.audit_event} (o mesmo padrao append-only da P2.12):
 * {@code SAAS_MYCONTRACT_VIEWED} para as leituras e
 * {@code SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED} para os downloads, com
 * o hash conferido no detalhe; recusa por hash divergente tambem
 * audita ({@code SAAS_MYCONTRACT_DOCUMENT_REFUSED}), sem servir byte
 * nenhum. A fatura em PDF so e servida quando PAID; a NFS-e, quando
 * ha referencia resolvida. O XML baixado e conferido: quando a
 * referencia carrega o hash da autorizacao, o SHA-256 dos bytes lidos
 * precisa bater, ou o download e recusado (fail-closed, 500
 * {@code internal-error} — falha de integridade do armazenado, nao
 * erro do cliente).
 *
 * <p>Forma da referencia: ver {@link SaaSContractDocumentResolver} e
 * {@link ObjectStorageSaaSContractDocumentResolver}. A NFS-e em si
 * chega na P2.13; a convencao proposta aqui e confirmada ou ajustada
 * na integracao (relatorio da P2.14).
 */
@Service
public class TenantSaaSContractService {

    /** Tipo de documento da fatura: a propria fatura ou a NFS-e dela. */
    public enum DocumentKind {
        INVOICE, NFSE
    }

    /** Formato pedido no download. */
    public enum DocumentFormat {
        PDF, XML
    }

    /** Download pronto para servir, com o hash calculado dos bytes. */
    public record Download(byte[] content, String contentType, String filename, String sha256Hex) {
    }

    private final JdbcTemplate jdbc;
    private final SaaSContractService contracts;
    private final SaaSContractDocumentResolver resolver;

    @Autowired
    public TenantSaaSContractService(DataSource dataSource, SaaSContractDocumentResolver resolver) {
        this(new JdbcTemplate(dataSource), new SaaSContractService(dataSource), resolver);
    }

    TenantSaaSContractService(JdbcTemplate jdbc, SaaSContractService contracts,
                              SaaSContractDocumentResolver resolver) {
        this.jdbc = jdbc;
        this.contracts = contracts;
        this.resolver = resolver;
    }

    // ------------------------------------------------------------------
    // Leituras do proprio contrato
    // ------------------------------------------------------------------

    @Transactional
    public List<ContractView> contracts(UUID tenantId, UUID actorId, String actorRole) {
        List<ContractView> rows = contracts.contractsByTenant(tenantId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "contracts"));
        return rows;
    }

    @Transactional
    public ContractView contract(UUID tenantId, UUID contractId, UUID actorId, String actorRole) {
        ContractView row = ownContract(tenantId, contractId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "contract", "contract_id", contractId.toString()));
        return row;
    }

    @Transactional
    public List<InvoiceView> invoices(UUID tenantId, UUID contractId, UUID actorId, String actorRole) {
        ownContract(tenantId, contractId);
        List<InvoiceView> rows = jdbc.query(
                "SELECT id, contract_id, commitment_id, invoice_number, competence, total, paid_amount,"
                        + " due_date, status, invoice_document_ref, nfse_document_ref"
                        + " FROM platform.saas_invoice WHERE contract_id = ? ORDER BY issued_at",
                (rs, i) -> {
                    String status = rs.getString(9);
                    LocalDate due = toDate(rs.getDate(8));
                    return new InvoiceView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                            rs.getBigDecimal(6), rs.getBigDecimal(7), due, status,
                            rs.getString(10), rs.getString(11),
                            SaaSContractRules.isOverdue(status, due, today()));
                },
                contractId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "invoices", "contract_id", contractId.toString()));
        return rows;
    }

    @Transactional
    public InvoiceView invoice(UUID tenantId, UUID invoiceId, UUID actorId, String actorRole) {
        InvoiceView row = ownInvoice(tenantId, invoiceId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "invoice", "invoice_id", invoiceId.toString()));
        return row;
    }

    @Transactional
    public List<PaymentView> paymentsForContract(UUID tenantId, UUID contractId,
                                                 UUID actorId, String actorRole) {
        ownContract(tenantId, contractId);
        List<PaymentView> rows = jdbc.query(
                "SELECT p.id, p.invoice_id, p.tenant_id, p.amount, p.paid_at, p.bank_order_ref"
                        + " FROM platform.saas_payment p JOIN platform.saas_invoice i ON i.id = p.invoice_id"
                        + " WHERE i.contract_id = ? AND p.tenant_id = ? ORDER BY p.created_at",
                (rs, i) -> new PaymentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getBigDecimal(4), toDate(rs.getDate(5)),
                        rs.getString(6), rs.getObject(2, UUID.class) != null),
                contractId, tenantId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "payments", "contract_id", contractId.toString()));
        return rows;
    }

    @Transactional
    public List<PaymentView> paymentsForInvoice(UUID tenantId, UUID invoiceId,
                                                UUID actorId, String actorRole) {
        ownInvoice(tenantId, invoiceId);
        List<PaymentView> rows = jdbc.query(
                "SELECT id, invoice_id, tenant_id, amount, paid_at, bank_order_ref"
                        + " FROM platform.saas_payment WHERE invoice_id = ? AND tenant_id = ? ORDER BY created_at",
                (rs, i) -> new PaymentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getBigDecimal(4), toDate(rs.getDate(5)),
                        rs.getString(6), true),
                invoiceId, tenantId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "payments", "invoice_id", invoiceId.toString()));
        return rows;
    }

    @Transactional
    public List<PaymentView> unmatchedPayments(UUID tenantId, UUID actorId, String actorRole) {
        List<PaymentView> rows = contracts.unmatchedPayments(tenantId);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "unmatched-payments"));
        return rows;
    }

    @Transactional
    public StatementView statement(UUID tenantId, LocalDate periodMonth, UUID actorId, String actorRole) {
        // O relatorio e mensal: normaliza para o primeiro dia do mes antes de buscar.
        LocalDate month = periodMonth.withDayOfMonth(1);
        StatementView row = contracts.statement(tenantId, month);
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_VIEWED",
                Map.of("resource", "statement", "period_month", month.toString()));
        return row;
    }

    // ------------------------------------------------------------------
    // Download de documento da fatura
    // ------------------------------------------------------------------

    /**
     * Sem {@code @Transactional} de proposito: a auditoria da recusa
     * por hash divergente precisa sobreviver a excecao que recusa o
     * download — com transacao, o rollback a apagaria junto. Cada
     * escrita aqui comita sozinha (o DataSource e auto-commit fora de
     * transacao); as leituras sao consultas pontuais, sem necessidade
     * de atomicidade entre si.
     */
    public Download download(UUID tenantId, UUID invoiceId, DocumentKind kind, DocumentFormat format,
                             UUID actorId, String actorRole) {
        InvoiceView invoice = ownInvoice(tenantId, invoiceId);
        String ref;
        if (kind == DocumentKind.INVOICE) {
            if (!"PAID".equals(invoice.status())) {
                throw new ApiException.Conflict("state-conflict",
                        "A fatura so pode ser baixada depois de paga.");
            }
            ref = invoice.invoiceDocumentRef();
        } else {
            ref = invoice.nfseDocumentRef();
        }
        if (ref == null || ref.isBlank()) {
            throw new ApiException.NotFound("O documento ainda nao esta disponivel para esta fatura.");
        }
        ResolvedDocument resolved = resolver.resolve(ref)
                .orElseThrow(() -> new ApiException.NotFound(
                        "O documento ainda nao esta disponivel para esta fatura."));
        if (!mediaMatches(resolved.contentType(), format)) {
            throw new ApiException.NotFound("O documento nao esta disponivel neste formato.");
        }
        String computed = sha256Hex(resolved.content());
        if (resolved.sha256Hex() != null
                && !resolved.sha256Hex().equalsIgnoreCase(computed)) {
            audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_DOCUMENT_REFUSED",
                    Map.of("invoice_id", invoiceId.toString(), "kind", kind.name(),
                            "format", format.name(), "reason", "hash-mismatch"));
            throw new IllegalStateException("O hash do documento nao confere com o registrado.");
        }
        audit(tenantId, actorId, actorRole, "SAAS_MYCONTRACT_DOCUMENT_DOWNLOADED",
                Map.of("invoice_id", invoiceId.toString(), "kind", kind.name(),
                        "format", format.name(), "sha256", computed));
        return new Download(resolved.content(), resolved.contentType(), resolved.filename(), computed);
    }

    // ------------------------------------------------------------------
    // Escopo: 404 identico para inexistente e para outro tenant
    // ------------------------------------------------------------------

    private ContractView ownContract(UUID tenantId, UUID contractId) {
        ContractView row = contracts.contract(contractId);
        if (!row.tenantId().equals(tenantId)) {
            throw new ApiException.NotFound("O contrato SaaS nao existe.");
        }
        return row;
    }

    private InvoiceView ownInvoice(UUID tenantId, UUID invoiceId) {
        InvoiceView row = contracts.invoice(invoiceId);
        ContractView contract = contracts.contract(row.contractId());
        if (!contract.tenantId().equals(tenantId)) {
            throw new ApiException.NotFound("A fatura nao existe.");
        }
        return row;
    }

    private static boolean mediaMatches(String contentType, DocumentFormat format) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.toLowerCase(java.util.Locale.ROOT).split(";", 2)[0].trim();
        return switch (format) {
            case PDF -> "application/pdf".equals(type);
            case XML -> "application/xml".equals(type) || "text/xml".equals(type);
        };
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void audit(UUID tenantId, UUID actorId, String actorRole, String action,
                       Map<String, String> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : details.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append("\"");
        }
        json.append("}");
        jdbc.update("INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                + " VALUES (?, ?, ?, ?, ?::jsonb)", tenantId, actorId,
                actorRole == null ? "TENANT" : actorRole, action, json.toString());
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static LocalDate toDate(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
