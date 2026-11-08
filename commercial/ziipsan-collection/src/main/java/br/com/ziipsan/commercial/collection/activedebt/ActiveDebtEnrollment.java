package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inscricao em divida ativa (ED-31; POC-34, alinea k do VISAN): individual
 * e periodica (em lote). Cada debito inscrito vira um termo no LIVRO de
 * divida ativa (sequencial, somente acrescimo). Debito prescrito — ou de
 * prescricao indeterminada — NUNCA e inscrito: na inscricao individual a
 * operacao e recusada; na periodica, o item volta registrado como
 * bloqueado, com o motivo da P4.1, e os demais seguem (o lote nao esconde
 * o bloqueio nem inscreve por silencio).
 */
public final class ActiveDebtEnrollment {

    /** Debito candidato a inscricao (conta vencida e nao paga). */
    public record DebtItem(UUID billId, LocalDate dueDate, BigDecimal amount, LocalDate lastAcknowledgment) {
        public DebtItem {
            if (billId == null || dueDate == null || amount == null || amount.signum() <= 0)
                throw new IllegalArgumentException("debito exige conta, vencimento e valor positivo");
        }
    }

    public enum Status { ACTIVE, SETTLED, CANCELLED, UNDER_INSTALLMENT, FILED }

    /** Termo do livro: uma inscricao. O numero do termo e sequencial do livro. */
    public record Enrollment(UUID id, UUID tenantId, UUID unitId, UUID customerId, long bookTermNumber,
                             List<DebtItem> items, BigDecimal total, LocalDate enrolledOn, Status status) {
        public Enrollment {
            if (id == null || tenantId == null || unitId == null || customerId == null
                    || items == null || items.isEmpty() || total == null || enrolledOn == null || status == null)
                throw new IllegalArgumentException("inscricao exige id, tenant, unidade, titular, itens, total, data e situacao");
            items = List.copyOf(items);
            // Invariante (correcao D5 da revisao): o total NAO e um dado
            // livre — tem de ser exatamente a soma dos itens (comparacao
            // por valor, sem distincao de escala). Sem isso, uma inscricao
            // montada a mao furava o minimo de ajuizamento com total
            // arbitrario sobre itens pequenos.
            BigDecimal sum = items.stream().map(DebtItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(sum) != 0)
                throw new IllegalArgumentException("total da inscricao (" + total + ") difere da soma dos itens (" + sum + ")");
        }
        public Enrollment withStatus(Status s) {
            return new Enrollment(id, tenantId, unitId, customerId, bookTermNumber, items, total, enrolledOn, s);
        }
    }

    /** Item recusado na inscricao periodica, com o motivo (nunca descartado em silencio). */
    public record BlockedItem(DebtItem item, String reason) {}

    public record BatchResult(List<Enrollment> enrolled, List<BlockedItem> blocked) {
        public BatchResult { enrolled = List.copyOf(enrolled); blocked = List.copyOf(blocked); }
    }

    /** Livro de divida ativa em memoria: somente acrescimo, termos sequenciais. */
    public static final class Book {
        private final List<Enrollment> entries = new ArrayList<>();
        private long nextTerm = 1;
        public synchronized Enrollment append(UUID tenantId, UUID unitId, UUID customerId,
                                              List<DebtItem> items, LocalDate enrolledOn) {
            BigDecimal total = items.stream().map(DebtItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var e = new Enrollment(UUID.randomUUID(), tenantId, unitId, customerId, nextTerm++, items, total, enrolledOn, Status.ACTIVE);
            entries.add(e);
            return e;
        }
        public List<Enrollment> entries() { return List.copyOf(entries); }
        public Enrollment find(UUID id) {
            return entries.stream().filter(e -> e.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("inscricao nao encontrada no livro: " + id));
        }
        public synchronized void replace(Enrollment updated) {
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(updated.id())) { entries.set(i, updated); return; }
            throw new IllegalArgumentException("inscricao nao encontrada no livro: " + updated.id());
        }
    }

    private final PrescriptionGate gate;
    private final Book book;
    private final Prescription.ProviderNature nature;
    private final Prescription.ClaimType claim;

    public ActiveDebtEnrollment(PrescriptionGate gate, Book book,
                                Prescription.ProviderNature nature, Prescription.ClaimType claim) {
        this.gate = Objects.requireNonNull(gate); this.book = Objects.requireNonNull(book);
        this.nature = Objects.requireNonNull(nature); this.claim = Objects.requireNonNull(claim);
    }

    /** Inscricao INDIVIDUAL (POC-34): um titular, um ou mais debitos; prescrito recusa tudo. */
    public Enrollment enrollIndividual(UUID tenantId, UUID unitId, UUID customerId, List<DebtItem> items,
                                       boolean legalAnalysisRecorded, Set<java.time.YearMonth> frozenMonths) {
        Objects.requireNonNull(items, "itens sao obrigatorios");
        if (items.isEmpty()) throw new IllegalArgumentException("inscricao exige ao menos um debito");
        LocalDate today = gate.today();
        if (frozenMonths != null && frozenMonths.contains(java.time.YearMonth.from(today)))
            throw new IllegalStateException("mes " + java.time.YearMonth.from(today) + " congelado: inscricao nova recusada (saldo mensal congelado)");
        for (DebtItem item : items)
            gate.assertAllowed(PrescriptionGate.Operation.ENROLLMENT, nature, claim,
                    item.dueDate(), item.lastAcknowledgment(), legalAnalysisRecorded);
        return book.append(tenantId, unitId, customerId, items, today);
    }

    /**
     * Inscricao PERIODICA (POC-34): lote por titular; bloqueados voltam
     * com motivo. Correcao D3 da revisao: a periodica recebe os meses
     * congelados e sofre a MESMA recusa da individual — com o mes
     * corrente congelado, o lote inteiro e recusado (nenhuma inscricao
     * nova entra no universo de um mes ja fechado).
     */
    public BatchResult enrollPeriodic(UUID tenantId, List<EnrollmentRequest> requests,
                                      boolean legalAnalysisRecorded, Set<java.time.YearMonth> frozenMonths) {
        Objects.requireNonNull(requests, "lote e obrigatorio");
        LocalDate todayPeriodic = gate.today();
        if (frozenMonths != null && frozenMonths.contains(java.time.YearMonth.from(todayPeriodic)))
            throw new IllegalStateException("mes " + java.time.YearMonth.from(todayPeriodic) + " congelado: inscricao periodica recusada (saldo mensal congelado)");
        List<Enrollment> enrolled = new ArrayList<>();
        List<BlockedItem> blocked = new ArrayList<>();
        for (EnrollmentRequest req : requests) {
            List<DebtItem> ok = new ArrayList<>();
            for (DebtItem item : req.items()) {
                var d = gate.check(PrescriptionGate.Operation.ENROLLMENT, nature, claim,
                        item.dueDate(), item.lastAcknowledgment(), legalAnalysisRecorded);
                if (d.allowed()) ok.add(item);
                else blocked.add(new BlockedItem(item, d.evaluation().outcome() + " — " + d.evaluation().reason()));
            }
            if (!ok.isEmpty()) enrolled.add(book.append(tenantId, req.unitId(), req.customerId(), ok, gate.today()));
        }
        return new BatchResult(enrolled, blocked);
    }

    public record EnrollmentRequest(UUID unitId, UUID customerId, List<DebtItem> items) {
        public EnrollmentRequest { items = items == null ? List.of() : List.copyOf(items); }
    }

    /** Titulares distintos com inscricao ativa (apoio a consultas do livro). */
    public Set<UUID> debtorsWithActiveEnrollment() {
        return book.entries().stream().filter(e -> e.status() == Status.ACTIVE).map(Enrollment::customerId).collect(Collectors.toSet());
    }
}
