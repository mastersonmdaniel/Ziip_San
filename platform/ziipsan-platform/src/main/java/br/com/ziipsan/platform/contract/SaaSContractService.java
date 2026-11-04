package br.com.ziipsan.platform.contract;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cobranca do SaaS por contrato (P2.12, ADR-0019, core V10_1).
 *
 * <p>O que este servico faz: contrato com aditivos, empenhos, reajuste
 * (proposta ate haver apostilamento ou aditivo), faturas e pagamentos
 * conciliados, registro de uso mensal sem dado pessoal, fotografia
 * mensal de ligacoes ativas com hash, relatorio entregue ao cliente
 * com janela de contestacao, custo do provedor por tenant e aba
 * Contratos da Central (com margem por contrato).
 *
 * <p>O que este servico NAO faz, por desenho (ADR-0019, item 5): nenhum
 * metodo aqui suspende, bloqueia ou restringe um tenant sozinho.
 * Emitir fatura, vencer fatura, estourar cota, vencer contrato ou
 * avaliar alertas nunca escreve em {@code tenant_lifecycle}. A unica
 * restricao passa por {@link #requestRestrictionNotice} +
 * {@link #activateRestriction}: manual, por uma pessoa com MFA (filtro)
 * e step-up confirmado, com justificativa, fatura vencida e aviso
 * previo registrados — e recusada sem qualquer um deles. A reativacao
 * apos a restricao nao existe nesta tarefa (a P1.6 tambem nao a tem):
 * ponto de extensao documentado no relatorio da P2.12.
 *
 * <p>Limites honestos:
 * <ul>
 *   <li>A politica de SLA e so uma referencia
 *       ({@code sla_policy_key}): {@code support.sla_policy} nasce na
 *       P2.15 (core V10_2), que valida a referencia.</li>
 *   <li>As referencias de documento da fatura e da NFS-e sao texto: o
 *       documento em si chega na P2.13 (motor fiscal) e na P2.14 (area
 *       do tenant), que resolvem as referencias.</li>
 *   <li>O registro de uso do mes e um valor substituido a cada
 *       gravacao (nao um acumulado de eventos): quem mede informa o
 *       total do mes. A fotografia de ligacoes tambem alimenta o
 *       ledger, para a cota medir o mesmo registro.</li>
 *   <li>O numero da fatura vem de uma sequencia global
 *       ({@code saas_invoice_seq}): unico sob concorrencia, sem
 *       promessa de sequencia por contrato (a P2.13 pode numerar por
 *       serie fiscal quando o motor entrar).</li>
 * </ul>
 * <p>Auditoria: toda mutacao insere uma linha em
 * {@code platform.audit_event} na mesma transacao (tabela append-only
 * para a aplicacao, padrao da V9).
 */
@Service
public class SaaSContractService {

    public record ContractInput(String contractNumber, String biddingProcess, String biddingModality,
                                String contractorCnpj, LocalDate startsAt, LocalDate endsAt,
                                BigDecimal monthlyValue, String indexKey, LocalDate indexBaseDate,
                                String slaPolicyKey, String quotasJson) {
    }

    public record ContractView(UUID id, UUID tenantId, String contractNumber, String biddingProcess,
                               String biddingModality, String contractorCnpj, LocalDate startsAt,
                               LocalDate endsAt, String status, BigDecimal monthlyValue, String indexKey,
                               LocalDate indexBaseDate, String slaPolicyKey, String quotasJson) {
    }

    public record AddendumView(UUID id, UUID contractId, String addendumNumber, String kind,
                               String description, LocalDate effectiveFrom, LocalDate effectiveTo,
                               BigDecimal valueDelta) {
    }

    public record CommitmentView(UUID id, UUID contractId, String commitmentNumber,
                                 BigDecimal amount, BigDecimal balance, LocalDate issuedAt) {
    }

    public record ReadjustmentView(UUID id, UUID contractId, String indexKey, BigDecimal indexValuePct,
                                   BigDecimal baseMonthlyValue, BigDecimal proposedMonthlyValue,
                                   String status, UUID appliedAddendumId) {
    }

    public record InvoiceView(UUID id, UUID contractId, UUID commitmentId, String invoiceNumber,
                              String competence, BigDecimal total, BigDecimal paidAmount,
                              LocalDate dueDate, String status, String invoiceDocumentRef,
                              String nfseDocumentRef, boolean overdue) {
    }

    public record PaymentView(UUID id, UUID invoiceId, UUID tenantId, BigDecimal amount,
                              LocalDate paidAt, String bankOrderRef, boolean matched) {
    }

    public record RestrictionNoticeView(UUID id, UUID tenantId, UUID contractId, UUID invoiceId,
                                        String justification, OffsetDateTime noticeAt,
                                        OffsetDateTime effectiveAt, String status) {
    }

    public record UsageView(UUID tenantId, LocalDate periodMonth, String metricKey,
                            BigDecimal quantity, String unit) {
    }

    public record SnapshotView(UUID id, UUID tenantId, LocalDate periodMonth,
                               long activeConnections, String snapshotHash, boolean valid) {
    }

    public record StatementView(UUID id, UUID tenantId, LocalDate periodMonth, UUID snapshotId,
                                String status, OffsetDateTime deliveredAt, OffsetDateTime contestUntil,
                                OffsetDateTime contestedAt, String contestNote) {
    }

    public record CostView(UUID id, UUID tenantId, LocalDate periodMonth, String costKey,
                           String basis, BigDecimal amount) {
    }

    /** Linha da aba Contratos da Central: vigencia, empenho, faturas, custo e margem. */
    public record ContractRow(UUID tenantId, UUID contractId, String contractNumber, String status,
                              LocalDate startsAt, LocalDate endsAt, Long daysToExpiry,
                              BigDecimal commitmentBalance, int openInvoices, BigDecimal openInvoicesTotal,
                              int overdueInvoices, BigDecimal providerCostMonth, BigDecimal invoicedMonth,
                              BigDecimal margin) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public SaaSContractService(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    SaaSContractService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Contrato, aditivo, empenho
    // ------------------------------------------------------------------

    @Transactional
    public ContractView createContract(UUID tenantId, ContractInput in, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (in == null || in.contractNumber() == null || in.contractNumber().isBlank()) {
            throw validation("O numero do contrato e obrigatorio.");
        }
        String cnpj = in.contractorCnpj() == null ? "" : DocumentNumber.normalize(in.contractorCnpj());
        if (!DocumentNumber.isValidCnpj(cnpj)) {
            throw validation("O CNPJ do contratante precisa ser valido (14 posicoes, com digitos verificadores).");
        }
        if (in.startsAt() == null || in.endsAt() == null || in.endsAt().isBefore(in.startsAt())) {
            throw validation("A vigencia do contrato precisa ter inicio e fim, com fim apos o inicio.");
        }
        if (in.monthlyValue() != null && in.monthlyValue().compareTo(BigDecimal.ZERO) < 0) {
            throw validation("O valor mensal do contrato nao pode ser negativo.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO platform.saas_contract (id, tenant_id, contract_number, bidding_process,
                    bidding_modality, contractor_cnpj, starts_at, ends_at, status, monthly_value,
                    index_key, index_base_date, sla_policy_key, quotas)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?::jsonb)
                """,
                id, tenantId, in.contractNumber(), in.biddingProcess(), in.biddingModality(), cnpj,
                Date.valueOf(in.startsAt()), Date.valueOf(in.endsAt()),
                in.monthlyValue() == null ? BigDecimal.ZERO : in.monthlyValue(),
                in.indexKey(), in.indexBaseDate() == null ? null : Date.valueOf(in.indexBaseDate()),
                in.slaPolicyKey(), in.quotasJson() == null ? "{}" : in.quotasJson());
        audit(tenantId, actorId, actorRole, "SAAS_CONTRACT_CREATED",
                Map.of("contract_number", in.contractNumber()));
        return contract(id);
    }

    /** Ativa um contrato em rascunho. Nada aqui e automatico: a ativacao e ato do financeiro. */
    @Transactional
    public ContractView activateContract(UUID contractId, UUID actorId, String actorRole) {
        ContractView current = contract(contractId);
        if (!"DRAFT".equals(current.status())) {
            throw conflict("So contrato em rascunho pode ser ativado.");
        }
        jdbc.update("UPDATE platform.saas_contract SET status = 'ACTIVE', updated_at = NOW() WHERE id = ?",
                contractId);
        audit(current.tenantId(), actorId, actorRole, "SAAS_CONTRACT_ACTIVATED",
                Map.of("contract_number", current.contractNumber()));
        return contract(contractId);
    }

    public ContractView contract(UUID contractId) {
        List<ContractView> rows = jdbc.query(
                "SELECT id, tenant_id, contract_number, bidding_process, bidding_modality, contractor_cnpj,"
                        + " starts_at, ends_at, status, monthly_value, index_key, index_base_date,"
                        + " sla_policy_key, quotas::text FROM platform.saas_contract WHERE id = ?",
                (rs, i) -> new ContractView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        toDate(rs.getDate(7)), toDate(rs.getDate(8)), rs.getString(9),
                        rs.getBigDecimal(10), rs.getString(11), toDate(rs.getDate(12)),
                        rs.getString(13), rs.getString(14)),
                contractId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O contrato SaaS nao existe.");
        }
        return rows.get(0);
    }

    public List<ContractView> contractsByTenant(UUID tenantId) {
        return jdbc.query(
                "SELECT id, tenant_id, contract_number, bidding_process, bidding_modality, contractor_cnpj,"
                        + " starts_at, ends_at, status, monthly_value, index_key, index_base_date,"
                        + " sla_policy_key, quotas::text FROM platform.saas_contract"
                        + " WHERE tenant_id = ? ORDER BY created_at",
                (rs, i) -> new ContractView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        toDate(rs.getDate(7)), toDate(rs.getDate(8)), rs.getString(9),
                        rs.getBigDecimal(10), rs.getString(11), toDate(rs.getDate(12)),
                        rs.getString(13), rs.getString(14)),
                tenantId);
    }

    @Transactional
    public AddendumView addAddendum(UUID contractId, String addendumNumber, String kind, String description,
                                    LocalDate effectiveFrom, LocalDate effectiveTo, BigDecimal valueDelta,
                                    UUID actorId, String actorRole) {
        ContractView current = contract(contractId);
        if (addendumNumber == null || addendumNumber.isBlank() || description == null || description.isBlank()) {
            throw validation("O numero e a descricao do aditivo sao obrigatorios.");
        }
        if (kind == null || !List.of("TERM", "VALUE", "OBJECT", "READJUSTMENT").contains(kind)) {
            throw validation("O tipo do aditivo precisa ser TERM, VALUE, OBJECT ou READJUSTMENT.");
        }
        if (effectiveFrom == null || (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))) {
            throw validation("A vigencia do aditivo precisa ter inicio, com fim apos o inicio.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_contract_addendum (id, contract_id, addendum_number, kind,"
                + " description, effective_from, effective_to, value_delta) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, contractId, addendumNumber, kind, description, Date.valueOf(effectiveFrom),
                effectiveTo == null ? null : Date.valueOf(effectiveTo), valueDelta);
        audit(current.tenantId(), actorId, actorRole, "SAAS_ADDENDUM_CREATED",
                Map.of("contract_number", current.contractNumber(), "kind", kind));
        return new AddendumView(id, contractId, addendumNumber, kind, description,
                effectiveFrom, effectiveTo, valueDelta);
    }

    @Transactional
    public CommitmentView addCommitment(UUID contractId, String commitmentNumber, BigDecimal amount,
                                        LocalDate issuedAt, UUID actorId, String actorRole) {
        ContractView current = contract(contractId);
        if (commitmentNumber == null || commitmentNumber.isBlank()) {
            throw validation("O numero do empenho e obrigatorio.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw validation("O valor do empenho nao pode ser negativo.");
        }
        if (issuedAt == null) {
            throw validation("A data do empenho e obrigatoria.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_commitment (id, contract_id, commitment_number, amount, balance,"
                + " issued_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, contractId, commitmentNumber, amount, amount, Date.valueOf(issuedAt));
        audit(current.tenantId(), actorId, actorRole, "SAAS_COMMITMENT_CREATED",
                Map.of("contract_number", current.contractNumber()));
        return new CommitmentView(id, contractId, commitmentNumber, amount, amount, issuedAt);
    }

    // ------------------------------------------------------------------
    // Reajuste: proposta ate haver apostilamento ou aditivo
    // ------------------------------------------------------------------

    @Transactional
    public ReadjustmentView proposeReadjustment(UUID contractId, String indexKey, BigDecimal indexValuePct,
                                                BigDecimal proposedMonthlyValue, UUID actorId, String actorRole) {
        ContractView current = contract(contractId);
        if (indexKey == null || indexKey.isBlank() || indexValuePct == null || proposedMonthlyValue == null
                || proposedMonthlyValue.compareTo(BigDecimal.ZERO) < 0) {
            throw validation("O reajuste exige indice, percentual e valor mensal proposto nao negativo.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_readjustment (id, contract_id, index_key, index_value_pct,"
                + " base_monthly_value, proposed_monthly_value, status) VALUES (?, ?, ?, ?, ?, ?, 'PROPOSED')",
                id, contractId, indexKey, indexValuePct, current.monthlyValue(), proposedMonthlyValue);
        audit(current.tenantId(), actorId, actorRole, "SAAS_READJUSTMENT_PROPOSED",
                Map.of("contract_number", current.contractNumber()));
        return readjustment(id);
    }

    /**
     * Aplica o reajuste: exige o aditivo/apostilamento registrado, do
     * mesmo contrato. Sem ele, a proposta continua proposta (o CHECK da
     * V10_1 trava o mesmo no banco).
     */
    @Transactional
    public ReadjustmentView applyReadjustment(UUID readjustmentId, UUID addendumId,
                                              UUID actorId, String actorRole) {
        ReadjustmentView current = readjustment(readjustmentId);
        if (!"PROPOSED".equals(current.status())) {
            throw conflict("So reajuste proposto pode ser aplicado.");
        }
        if (addendumId == null || !SaaSContractRules.readjustmentMayApply(addendumId != null)) {
            throw validation("O reajuste so vale depois de registrado o apostilamento ou aditivo.");
        }
        List<UUID> owner = jdbc.query(
                "SELECT contract_id FROM platform.saas_contract_addendum WHERE id = ?",
                (rs, i) -> rs.getObject(1, UUID.class), addendumId);
        if (owner.isEmpty() || !owner.get(0).equals(current.contractId())) {
            throw validation("O apostilamento ou aditivo precisa ser do mesmo contrato.");
        }
        jdbc.update("UPDATE platform.saas_readjustment SET status = 'APPLIED', applied_addendum_id = ?,"
                + " applied_at = NOW() WHERE id = ?", addendumId, readjustmentId);
        jdbc.update("UPDATE platform.saas_contract SET monthly_value = ?, updated_at = NOW() WHERE id = ?",
                current.proposedMonthlyValue(), current.contractId());
        ContractView contract = contract(current.contractId());
        audit(contract.tenantId(), actorId, actorRole, "SAAS_READJUSTMENT_APPLIED",
                Map.of("contract_number", contract.contractNumber()));
        return readjustment(readjustmentId);
    }

    public ReadjustmentView readjustment(UUID readjustmentId) {
        List<ReadjustmentView> rows = jdbc.query(
                "SELECT id, contract_id, index_key, index_value_pct, base_monthly_value,"
                        + " proposed_monthly_value, status, applied_addendum_id"
                        + " FROM platform.saas_readjustment WHERE id = ?",
                (rs, i) -> new ReadjustmentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6),
                        rs.getString(7), rs.getObject(8, UUID.class)),
                readjustmentId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O reajuste nao existe.");
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------------
    // Faturas e pagamentos conciliados
    // ------------------------------------------------------------------

    @Transactional
    public InvoiceView issueInvoice(UUID contractId, UUID commitmentId, String competence, BigDecimal total,
                                    LocalDate dueDate, UUID actorId, String actorRole) {
        ContractView current = contract(contractId);
        if (!SaaSContractRules.isCompetence(competence)) {
            throw validation("A competencia precisa estar no formato AAAA-MM.");
        }
        if (total == null || total.compareTo(BigDecimal.ZERO) < 0) {
            throw validation("O total da fatura nao pode ser negativo.");
        }
        if (dueDate == null) {
            throw validation("A data de vencimento da fatura e obrigatoria.");
        }
        if (commitmentId != null) {
            List<UUID> owner = jdbc.query(
                    "SELECT contract_id FROM platform.saas_commitment WHERE id = ?",
                    (rs, i) -> rs.getObject(1, UUID.class), commitmentId);
            if (owner.isEmpty() || !owner.get(0).equals(contractId)) {
                throw validation("O empenho precisa ser do mesmo contrato da fatura.");
            }
        }
        Long seq = jdbc.queryForObject("SELECT nextval('platform.saas_invoice_seq')", Long.class);
        String number = "FAT-" + seq;
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_invoice (id, contract_id, commitment_id, invoice_number,"
                + " competence, total, due_date, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN')",
                id, contractId, commitmentId, number, competence, total, Date.valueOf(dueDate));
        if (commitmentId != null) {
            // Saldo do empenho: abate sem recusar saldo negativo (ver o
            // cabecalho da V10_1); o alerta de saldo baixo sinaliza.
            jdbc.update("UPDATE platform.saas_commitment SET balance = balance - ? WHERE id = ?",
                    total, commitmentId);
        }
        audit(current.tenantId(), actorId, actorRole, "SAAS_INVOICE_ISSUED",
                Map.of("contract_number", current.contractNumber(), "invoice_number", number));
        return invoice(id);
    }

    /**
     * Resolve a referencia da NFS-e da fatura (P2.13): apos a emissao pelo motor fiscal, a
     * fatura passa a apontar para o Id da DPS autorizada. Recusada se a fatura ja apontar
     * para outro documento — a NFS-e de uma fatura e unica, e a segunda emissao devolve a
     * existente em vez de vincular outra. Auditoria SAAS_NFSE_LINKED na mesma transacao.
     */
    @Transactional
    public InvoiceView linkNfse(UUID invoiceId, String dpsRef, String takerCnpj,
                                UUID actorId, String actorRole) {
        InvoiceView current = invoice(invoiceId);
        if (dpsRef == null || dpsRef.isBlank()) {
            throw validation("A referencia da NFS-e e obrigatoria.");
        }
        if (current.nfseDocumentRef() != null && !current.nfseDocumentRef().equals(dpsRef)) {
            throw conflict("A fatura ja tem outra NFS-e vinculada.");
        }
        jdbc.update("UPDATE platform.saas_invoice SET nfse_document_ref = ?, updated_at = NOW() WHERE id = ?",
                dpsRef, invoiceId);
        ContractView contract = contract(current.contractId());
        boolean divergent = contract.contractorCnpj() != null
                && takerCnpj != null && !contract.contractorCnpj().equals(takerCnpj);
        audit(contract.tenantId(), actorId, actorRole, "SAAS_NFSE_LINKED",
                Map.of("invoice_number", current.invoiceNumber(), "nfse_dps_id", dpsRef,
                        "tomador_do_perfil", divergent ? "diverge_do_contrato" : "igual_ao_contrato"));
        return invoice(invoiceId);
    }

    /** Registra um pagamento informado (ordem bancaria); sem fatura, fica sem conciliar — nunca inventa fatura. */
    @Transactional
    public PaymentView recordPayment(UUID tenantId, UUID invoiceId, BigDecimal amount, LocalDate paidAt,
                                     String bankOrderRef, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validation("O valor do pagamento precisa ser positivo.");
        }
        if (paidAt == null) {
            throw validation("A data do pagamento e obrigatoria.");
        }
        if (invoiceId != null) {
            InvoiceView invoice = invoice(invoiceId);
            ContractView contract = contract(invoice.contractId());
            if (!contract.tenantId().equals(tenantId)) {
                throw validation("O pagamento precisa ser do mesmo tenant da fatura.");
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_payment (id, invoice_id, tenant_id, amount, paid_at, bank_order_ref)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
                id, invoiceId, tenantId, amount, Date.valueOf(paidAt), bankOrderRef);
        if (invoiceId != null) {
            reconcileInvoiceRow(invoiceId);
        }
        audit(tenantId, actorId, actorRole, "SAAS_PAYMENT_RECORDED",
                Map.of("matched", Boolean.toString(invoiceId != null)));
        return payment(id);
    }

    /** Reconcilia a fatura: soma os pagamentos vinculados e atualiza pago e status. */
    @Transactional
    public InvoiceView reconcileInvoice(UUID invoiceId, UUID actorId, String actorRole) {
        InvoiceView current = invoice(invoiceId);
        reconcileInvoiceRow(invoiceId);
        ContractView contract = contract(current.contractId());
        audit(contract.tenantId(), actorId, actorRole, "SAAS_INVOICE_RECONCILED",
                Map.of("invoice_number", current.invoiceNumber()));
        return invoice(invoiceId);
    }

    private void reconcileInvoiceRow(UUID invoiceId) {
        BigDecimal paid = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM platform.saas_payment WHERE invoice_id = ?",
                BigDecimal.class, invoiceId);
        BigDecimal total = jdbc.queryForObject(
                "SELECT total FROM platform.saas_invoice WHERE id = ?", BigDecimal.class, invoiceId);
        String status = SaaSContractRules.reconciledStatus(total, paid);
        jdbc.update("UPDATE platform.saas_invoice SET paid_amount = ?, status = ?, updated_at = NOW()"
                + " WHERE id = ? AND status <> 'CANCELLED'", paid, status, invoiceId);
    }

    public InvoiceView invoice(UUID invoiceId) {
        List<InvoiceView> rows = jdbc.query(
                "SELECT id, contract_id, commitment_id, invoice_number, competence, total, paid_amount,"
                        + " due_date, status, invoice_document_ref, nfse_document_ref"
                        + " FROM platform.saas_invoice WHERE id = ?",
                (rs, i) -> {
                    String status = rs.getString(9);
                    LocalDate due = toDate(rs.getDate(8));
                    boolean overdue = SaaSContractRules.isOverdue(status, due, today());
                    return new InvoiceView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                            rs.getBigDecimal(6), rs.getBigDecimal(7), due, status,
                            rs.getString(10), rs.getString(11), overdue);
                },
                invoiceId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("A fatura nao existe.");
        }
        return rows.get(0);
    }

    public PaymentView payment(UUID paymentId) {
        List<PaymentView> rows = jdbc.query(
                "SELECT id, invoice_id, tenant_id, amount, paid_at, bank_order_ref"
                        + " FROM platform.saas_payment WHERE id = ?",
                (rs, i) -> new PaymentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getBigDecimal(4), toDate(rs.getDate(5)),
                        rs.getString(6), rs.getObject(2, UUID.class) != null),
                paymentId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O pagamento nao existe.");
        }
        return rows.get(0);
    }

    public List<PaymentView> unmatchedPayments(UUID tenantId) {
        return jdbc.query(
                "SELECT id, invoice_id, tenant_id, amount, paid_at, bank_order_ref"
                        + " FROM platform.saas_payment WHERE tenant_id = ? AND invoice_id IS NULL"
                        + " ORDER BY created_at",
                (rs, i) -> new PaymentView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getBigDecimal(4), toDate(rs.getDate(5)),
                        rs.getString(6), false),
                tenantId);
    }

    // ------------------------------------------------------------------
    // Restricao manual (aviso previo + ativacao)
    // ------------------------------------------------------------------

    /**
     * Aviso previo de restricao por inadimplencia. Exige: papel OWNER ou
     * FINANCE com motivo inadimplencia, step-up confirmado, justificativa,
     * vigencia futura — e fatura vencida do contrato do tenant. Sem
     * fatura vencida, sem aviso: recusado. O aviso deixa o ciclo de vida
     * em SUSPENSION_NOTICE (backoffice ainda opera); so a ativacao, apos
     * o prazo, torna a restricao vigente.
     */
    @Transactional
    public RestrictionNoticeView requestRestrictionNotice(UUID tenantId, UUID contractId, UUID invoiceId,
                                                          String justification, boolean stepUpConfirmed,
                                                          OffsetDateTime effectiveAt,
                                                          String role, UUID actorId) {
        if (!SaaSContractRules.canRestrict(role, SaaSContractRules.CONTRACT_DEFAULT_REASON)) {
            throw new ApiException.Forbidden(
                    "Restricao recusada: so PLATFORM_OWNER ou PLATFORM_FINANCE, por inadimplencia contratual.");
        }
        if (!stepUpConfirmed) {
            throw new ApiException.Forbidden("Restricao recusada: confirme o step-up antes de restringir.");
        }
        if (justification == null || justification.isBlank()) {
            throw validation("A restricao exige justificativa.");
        }
        if (effectiveAt == null || !effectiveAt.toInstant().isAfter(clock.instant())) {
            throw validation("A restricao exige uma data de vigencia futura (aviso previo).");
        }
        ContractView current = contract(contractId);
        if (!current.tenantId().equals(tenantId)) {
            throw validation("O contrato precisa ser do tenant.");
        }
        InvoiceView invoice = invoice(invoiceId);
        if (!invoice.contractId().equals(contractId)) {
            throw validation("A fatura precisa ser do contrato.");
        }
        if (!invoice.overdue()) {
            throw validation("A restricao exige fatura vencida e nao paga.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.saas_restriction_notice (id, tenant_id, contract_id, invoice_id,"
                + " justification, actor_id, effective_at, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')",
                id, tenantId, contractId, invoiceId, justification, actorId,
                Timestamp.from(effectiveAt.toInstant()));
        jdbc.update("INSERT INTO platform.tenant_lifecycle (tenant_id, status, suspension_reason,"
                + " suspension_notice_at, suspension_effective_at, suspension_justification, suspended_by)"
                + " VALUES (?, 'SUSPENSION_NOTICE', 'CONTRACT_DEFAULT', NOW(), ?, ?, ?)"
                + " ON CONFLICT (tenant_id) DO UPDATE SET status = 'SUSPENSION_NOTICE',"
                + " suspension_reason = 'CONTRACT_DEFAULT',"
                + " suspension_notice_at = EXCLUDED.suspension_notice_at,"
                + " suspension_effective_at = EXCLUDED.suspension_effective_at,"
                + " suspension_justification = EXCLUDED.suspension_justification,"
                + " suspended_by = EXCLUDED.suspended_by, updated_at = NOW()",
                tenantId, Timestamp.from(effectiveAt.toInstant()), justification, actorId);
        audit(tenantId, actorId, role, "SAAS_RESTRICTION_NOTICE",
                Map.of("contract_number", current.contractNumber(), "invoice_number", invoice.invoiceNumber()));
        return restrictionNotice(id);
    }

    /** Ativa a restricao: aviso com prazo vencido, fatura ainda vencida, mesmo papel e step-up. */
    @Transactional
    public RestrictionNoticeView activateRestriction(UUID noticeId, boolean stepUpConfirmed,
                                                     String role, UUID actorId) {
        RestrictionNoticeView notice = restrictionNotice(noticeId);
        if (!SaaSContractRules.canRestrict(role, SaaSContractRules.CONTRACT_DEFAULT_REASON)) {
            throw new ApiException.Forbidden(
                    "Restricao recusada: so PLATFORM_OWNER ou PLATFORM_FINANCE, por inadimplencia contratual.");
        }
        if (!stepUpConfirmed) {
            throw new ApiException.Forbidden("Restricao recusada: confirme o step-up antes de restringir.");
        }
        if (!"PENDING".equals(notice.status())) {
            throw conflict("Nao ha aviso previo pendente de restricao para ativar.");
        }
        if (notice.effectiveAt() == null || clock.instant().isBefore(notice.effectiveAt().toInstant())) {
            throw conflict("O aviso previo ainda nao venceu; a restricao nao pode vigorar.");
        }
        InvoiceView invoice = invoice(notice.invoiceId());
        if (!invoice.overdue()) {
            throw conflict("A fatura nao esta mais vencida e nao paga; a restricao nao pode vigorar.");
        }
        jdbc.update("UPDATE platform.saas_restriction_notice SET status = 'EFFECTIVE', activated_at = NOW()"
                + " WHERE id = ?", noticeId);
        jdbc.update("UPDATE platform.tenant_lifecycle SET status = 'SUSPENDED', updated_at = NOW()"
                + " WHERE tenant_id = ?", notice.tenantId());
        audit(notice.tenantId(), actorId, role, "SAAS_RESTRICTION_ACTIVATED",
                Map.of("invoice_number", invoice.invoiceNumber()));
        return restrictionNotice(noticeId);
    }

    public RestrictionNoticeView restrictionNotice(UUID noticeId) {
        List<RestrictionNoticeView> rows = jdbc.query(
                "SELECT id, tenant_id, contract_id, invoice_id, justification, notice_at, effective_at, status"
                        + " FROM platform.saas_restriction_notice WHERE id = ?",
                (rs, i) -> new RestrictionNoticeView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                        toOffset(rs.getTimestamp(6)), toOffset(rs.getTimestamp(7)), rs.getString(8)),
                noticeId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O aviso previo de restricao nao existe.");
        }
        return rows.get(0);
    }

    // ------------------------------------------------------------------
    // Uso, fotografia, relatorio e custo do provedor
    // ------------------------------------------------------------------

    /** Grava (substitui) o registro de uso do mes para a metrica. Sem dado pessoal: so quantidade. */
    @Transactional
    public UsageView recordUsage(UUID tenantId, LocalDate periodMonth, String metricKey, BigDecimal quantity,
                                 String unit, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (periodMonth == null || !periodMonth.equals(periodMonth.withDayOfMonth(1))) {
            throw validation("O mes do registro de uso precisa ser o primeiro dia do mes.");
        }
        if (!SaaSContractRules.isLedgerMetric(metricKey)) {
            throw validation("A metrica de uso nao esta no catalogo do ledger.");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw validation("A quantidade de uso nao pode ser negativa.");
        }
        if (unit == null || unit.isBlank()) {
            throw validation("A unidade do registro de uso e obrigatoria.");
        }
        jdbc.update("INSERT INTO platform.usage_ledger (tenant_id, period_month, metric_key, quantity, unit)"
                + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (tenant_id, period_month, metric_key) DO UPDATE SET"
                + " quantity = EXCLUDED.quantity, unit = EXCLUDED.unit, updated_at = NOW()",
                tenantId, Date.valueOf(periodMonth), metricKey, quantity, unit);
        audit(tenantId, actorId, actorRole, "SAAS_USAGE_RECORDED",
                Map.of("metric_key", metricKey, "period_month", periodMonth.toString()));
        return new UsageView(tenantId, periodMonth, metricKey, quantity, unit);
    }

    public List<UsageView> usageFor(UUID tenantId, LocalDate periodMonth) {
        return jdbc.query(
                "SELECT tenant_id, period_month, metric_key, quantity, unit FROM platform.usage_ledger"
                        + " WHERE tenant_id = ? AND period_month = ? ORDER BY metric_key",
                (rs, i) -> new UsageView(rs.getObject(1, UUID.class), toDate(rs.getDate(2)),
                        rs.getString(3), rs.getBigDecimal(4), rs.getString(5)),
                tenantId, Date.valueOf(periodMonth));
    }

    /**
     * Fotografia mensal das ligacoes ativas: guarda a quantidade e o
     * hash do conteudo canonico, e alimenta o ledger com a mesma
     * quantidade (a cota quota.active_connections mede este registro).
     */
    @Transactional
    public SnapshotView takeSnapshot(UUID tenantId, LocalDate periodMonth, long activeConnections,
                                     UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (periodMonth == null || !periodMonth.equals(periodMonth.withDayOfMonth(1))) {
            throw validation("O mes da fotografia precisa ser o primeiro dia do mes.");
        }
        if (activeConnections < 0) {
            throw validation("A quantidade de ligacoes ativas nao pode ser negativa.");
        }
        String hash = SaaSContractRules.snapshotHash(tenantId.toString(), periodMonth, activeConnections);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.usage_connection_snapshot (id, tenant_id, period_month,"
                + " active_connections, snapshot_hash, created_by) VALUES (?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (tenant_id, period_month) DO UPDATE SET"
                + " active_connections = EXCLUDED.active_connections,"
                + " snapshot_hash = EXCLUDED.snapshot_hash, created_by = EXCLUDED.created_by",
                id, tenantId, Date.valueOf(periodMonth), activeConnections, hash, actorId);
        jdbc.update("INSERT INTO platform.usage_ledger (tenant_id, period_month, metric_key, quantity, unit)"
                + " VALUES (?, ?, 'active_connections', ?, 'count')"
                + " ON CONFLICT (tenant_id, period_month, metric_key) DO UPDATE SET"
                + " quantity = EXCLUDED.quantity, unit = EXCLUDED.unit, updated_at = NOW()",
                tenantId, Date.valueOf(periodMonth), BigDecimal.valueOf(activeConnections));
        audit(tenantId, actorId, actorRole, "SAAS_SNAPSHOT_TAKEN",
                Map.of("period_month", periodMonth.toString()));
        List<SnapshotView> rows = jdbc.query(
                "SELECT id, tenant_id, period_month, active_connections, snapshot_hash"
                        + " FROM platform.usage_connection_snapshot WHERE tenant_id = ? AND period_month = ?",
                (rs, i) -> new SnapshotView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        toDate(rs.getDate(3)), rs.getLong(4), rs.getString(5), true),
                tenantId, Date.valueOf(periodMonth));
        return rows.get(0);
    }

    /** Confere a fotografia: qualquer alteracao posterior sem recalcular o hash invalida. */
    public SnapshotView verifySnapshot(UUID snapshotId) {
        List<SnapshotView> rows = jdbc.query(
                "SELECT id, tenant_id, period_month, active_connections, snapshot_hash"
                        + " FROM platform.usage_connection_snapshot WHERE id = ?",
                (rs, i) -> {
                    UUID tenant = rs.getObject(2, UUID.class);
                    LocalDate month = toDate(rs.getDate(3));
                    long qty = rs.getLong(4);
                    String hash = rs.getString(5);
                    boolean valid = SaaSContractRules.snapshotValid(
                            tenant.toString(), month, qty, hash);
                    return new SnapshotView(rs.getObject(1, UUID.class), tenant, month, qty, hash, valid);
                },
                snapshotId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("A fotografia de ligacoes nao existe.");
        }
        return rows.get(0);
    }

    /** Entrega o relatorio mensal ao cliente: exige a fotografia e abre a janela de contestacao. */
    @Transactional
    public StatementView deliverStatement(UUID tenantId, LocalDate periodMonth, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        List<UUID> snapshots = jdbc.query(
                "SELECT id FROM platform.usage_connection_snapshot WHERE tenant_id = ? AND period_month = ?",
                (rs, i) -> rs.getObject(1, UUID.class), tenantId, Date.valueOf(periodMonth));
        if (snapshots.isEmpty()) {
            throw conflict("O relatorio exige a fotografia mensal de ligacoes ativas.");
        }
        int contestDays = statementContestDays();
        OffsetDateTime deliveredAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        OffsetDateTime contestUntil = deliveredAt.plusDays(contestDays);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.usage_statement (id, tenant_id, period_month, snapshot_id, status,"
                + " delivered_at, contest_until) VALUES (?, ?, ?, ?, 'DELIVERED', ?, ?)"
                + " ON CONFLICT (tenant_id, period_month) DO UPDATE SET snapshot_id = EXCLUDED.snapshot_id,"
                + " status = 'DELIVERED', delivered_at = EXCLUDED.delivered_at,"
                + " contest_until = EXCLUDED.contest_until, contested_at = NULL, contest_note = NULL,"
                + " confirmed_at = NULL",
                id, tenantId, Date.valueOf(periodMonth), snapshots.get(0),
                Timestamp.from(deliveredAt.toInstant()), Timestamp.from(contestUntil.toInstant()));
        audit(tenantId, actorId, actorRole, "SAAS_STATEMENT_DELIVERED",
                Map.of("period_month", periodMonth.toString()));
        return statement(tenantId, periodMonth);
    }

    /** Contesta dentro da janela; fora dela, recusado. */
    @Transactional
    public StatementView contestStatement(UUID statementId, String note, UUID actorId, String actorRole) {
        StatementView current = statementById(statementId);
        if (current.contestUntil() == null || clock.instant().isAfter(current.contestUntil().toInstant())) {
            throw conflict("A janela de contestacao do relatorio ja fechou.");
        }
        if (note == null || note.isBlank()) {
            throw validation("A contestacao exige o motivo.");
        }
        jdbc.update("UPDATE platform.usage_statement SET status = 'CONTESTED', contested_at = NOW(),"
                + " contest_note = ? WHERE id = ?", note, statementId);
        audit(current.tenantId(), actorId, actorRole, "SAAS_STATEMENT_CONTESTED",
                Map.of("period_month", current.periodMonth().toString()));
        return statementById(statementId);
    }

    @Transactional
    public StatementView confirmStatement(UUID statementId, UUID actorId, String actorRole) {
        StatementView current = statementById(statementId);
        if ("CONFIRMED".equals(current.status())) {
            throw conflict("O relatorio ja esta confirmado.");
        }
        jdbc.update("UPDATE platform.usage_statement SET status = 'CONFIRMED', confirmed_at = NOW() WHERE id = ?",
                statementId);
        audit(current.tenantId(), actorId, actorRole, "SAAS_STATEMENT_CONFIRMED",
                Map.of("period_month", current.periodMonth().toString()));
        return statementById(statementId);
    }

    public StatementView statement(UUID tenantId, LocalDate periodMonth) {
        List<StatementView> rows = jdbc.query(
                "SELECT id, tenant_id, period_month, snapshot_id, status, delivered_at, contest_until,"
                        + " contested_at, contest_note FROM platform.usage_statement"
                        + " WHERE tenant_id = ? AND period_month = ?",
                (rs, i) -> new StatementView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        toDate(rs.getDate(3)), rs.getObject(4, UUID.class), rs.getString(5),
                        toOffset(rs.getTimestamp(6)), toOffset(rs.getTimestamp(7)),
                        toOffset(rs.getTimestamp(8)), rs.getString(9)),
                tenantId, Date.valueOf(periodMonth));
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O relatorio de uso nao existe.");
        }
        return rows.get(0);
    }

    public StatementView statementById(UUID statementId) {
        List<StatementView> rows = jdbc.query(
                "SELECT id, tenant_id, period_month, snapshot_id, status, delivered_at, contest_until,"
                        + " contested_at, contest_note FROM platform.usage_statement WHERE id = ?",
                (rs, i) -> new StatementView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        toDate(rs.getDate(3)), rs.getObject(4, UUID.class), rs.getString(5),
                        toOffset(rs.getTimestamp(6)), toOffset(rs.getTimestamp(7)),
                        toOffset(rs.getTimestamp(8)), rs.getString(9)),
                statementId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O relatorio de uso nao existe.");
        }
        return rows.get(0);
    }

    /** Dias da janela de contestacao: padrao de fabrica do parametro (classe d), nunca numero no codigo. */
    public int statementContestDays() {
        List<String> defaults = jdbc.query(
                "SELECT factory_default::text FROM public.parameter_definitions"
                        + " WHERE definition_key = 'usage.statement_contest_days'",
                (rs, i) -> rs.getString(1));
        if (defaults.isEmpty() || defaults.get(0) == null) {
            throw new ApiException.NotFound("O parametro usage.statement_contest_days nao tem definicao.");
        }
        try {
            return Integer.parseInt(defaults.get(0).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("O padrao de fabrica de usage.statement_contest_days nao e inteiro.");
        }
    }

    /** Custo do provedor atribuido ao tenant: por etiqueta (TAG) ou por proxy (tamanho, mensagens, tokens). */
    @Transactional
    public CostView recordProviderCost(UUID tenantId, LocalDate periodMonth, String costKey, String basis,
                                       BigDecimal amount, String notes, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (periodMonth == null || !periodMonth.equals(periodMonth.withDayOfMonth(1))) {
            throw validation("O mes do custo precisa ser o primeiro dia do mes.");
        }
        if (costKey == null || costKey.isBlank()
                || basis == null || !List.of("TAG", "PROXY_SCHEMA_SIZE", "PROXY_MESSAGES", "PROXY_TOKENS")
                        .contains(basis)) {
            throw validation("O custo exige chave e base TAG ou de proxy (tamanho, mensagens, tokens).");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw validation("O custo nao pode ser negativo.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.provider_cost_allocation (id, tenant_id, period_month, cost_key, basis,"
                + " amount, notes) VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON CONFLICT (tenant_id, period_month, cost_key, basis) DO UPDATE SET"
                + " amount = EXCLUDED.amount, notes = EXCLUDED.notes",
                id, tenantId, Date.valueOf(periodMonth), costKey, basis, amount, notes);
        audit(tenantId, actorId, actorRole, "SAAS_PROVIDER_COST_RECORDED",
                Map.of("cost_key", costKey, "basis", basis));
        return new CostView(id, tenantId, periodMonth, costKey, basis, amount);
    }

    public BigDecimal providerCostFor(UUID tenantId, LocalDate periodMonth) {
        BigDecimal sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM platform.provider_cost_allocation"
                        + " WHERE tenant_id = ? AND period_month = ?",
                BigDecimal.class, tenantId, Date.valueOf(periodMonth));
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // ------------------------------------------------------------------
    // Aba Contratos da Central
    // ------------------------------------------------------------------

    /** Uma linha por contrato: vigencia, saldo de empenho, faturas, custo do mes e margem realizada. */
    public List<ContractRow> contractsTab() {
        LocalDate today = today();
        LocalDate month = today.withDayOfMonth(1);
        String competence = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        List<ContractView> contracts = jdbc.query(
                "SELECT id, tenant_id, contract_number, bidding_process, bidding_modality, contractor_cnpj,"
                        + " starts_at, ends_at, status, monthly_value, index_key, index_base_date,"
                        + " sla_policy_key, quotas::text FROM platform.saas_contract ORDER BY created_at",
                (rs, i) -> new ContractView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        toDate(rs.getDate(7)), toDate(rs.getDate(8)), rs.getString(9),
                        rs.getBigDecimal(10), rs.getString(11), toDate(rs.getDate(12)),
                        rs.getString(13), rs.getString(14)));
        List<ContractRow> rows = new java.util.ArrayList<>();
        for (ContractView c : contracts) {
            BigDecimal balance = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(balance), 0) FROM platform.saas_commitment WHERE contract_id = ?",
                    BigDecimal.class, c.id());
            Integer open = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform.saas_invoice WHERE contract_id = ?"
                            + " AND status IN ('OPEN', 'PARTIALLY_PAID')",
                    Integer.class, c.id());
            BigDecimal openTotal = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total - paid_amount), 0) FROM platform.saas_invoice"
                            + " WHERE contract_id = ? AND status IN ('OPEN', 'PARTIALLY_PAID')",
                    BigDecimal.class, c.id());
            Integer overdue = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM platform.saas_invoice WHERE contract_id = ?"
                            + " AND status IN ('OPEN', 'PARTIALLY_PAID') AND due_date < ?",
                    Integer.class, c.id(), Date.valueOf(today));
            BigDecimal invoicedMonth = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(total), 0) FROM platform.saas_invoice"
                            + " WHERE contract_id = ? AND competence = ? AND status <> 'CANCELLED'",
                    BigDecimal.class, c.id(), competence);
            BigDecimal cost = providerCostFor(c.tenantId(), month);
            rows.add(new ContractRow(c.tenantId(), c.id(), c.contractNumber(), c.status(),
                    c.startsAt(), c.endsAt(), SaaSContractRules.daysToExpiry(c.endsAt(), today),
                    balance == null ? BigDecimal.ZERO : balance,
                    open == null ? 0 : open, openTotal == null ? BigDecimal.ZERO : openTotal,
                    overdue == null ? 0 : overdue, cost, invoicedMonth,
                    SaaSContractRules.margin(invoicedMonth, cost)));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void requireTenant(UUID tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM public.tenant_catalog WHERE id = ?",
                Integer.class, tenantId);
        if (n == null || n == 0) {
            throw new ApiException.NotFound("O tenant nao existe no catalogo.");
        }
    }

    private void audit(UUID tenantId, UUID actorId, String actorRole, String action, Map<String, String> details) {
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
                + " VALUES (?, ?, ?, ?, ?::jsonb)", tenantId, actorId, actorRole, action, json.toString());
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

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }

    private static ApiException.Conflict conflict(String message) {
        return new ApiException.Conflict("state-conflict", message);
    }
}
