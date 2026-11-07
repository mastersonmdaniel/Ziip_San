package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Revisao por vazamento oculto e afericao de hidrometro (P3.28;
 * ADR-0038; NR 11 art. 38).
 *
 * <p>Invariantes:
 * <ul>
 *   <li><b>a revisao por vazamento parte da BILL_REVIEW da P2.20:</b> o
 *       pedido so nasce depois que a conta esta em revisao pela P2.20
 *       ({@link BillReviewPort}), e a OS {@code LEAK_REVIEW} nasce filha
 *       dela ({@code parentOrderId} no motor). Pedido intempestivo
 *       ({@link LeakClaimDeadlineRule}) ou inelegivel
 *       ({@link LeakEligibilityRule}) e recusado ANTES de qualquer OS --
 *       nao deixa rastro nem na P2.20;</li>
 *   <li><b>a decisao do vazamento roda DENTRO do {@code execute} da OS</b>,
 *       pelo handler de efeito do adaptador, como na P2.20. Os dois tipos
 *       sao de CAMPO: o motor real da P1.31 so executa OS de campo a partir
 *       de {@code IN_EXECUTION} e recusa tipo com aprovacao sem
 *       {@code approvedBy}. O condutor desse ciclo e ESTE servico
 *       (correcao pos-revisao, achado B1): {@link #approve},
 *       {@link #schedule} e {@link #startExecution} sao as passagens sobre
 *       o motor, na ordem aprovar (se o tipo exigir) -&gt; agendar -&gt;
 *       iniciar execucao -&gt; decidir/laudar. OS aguardando aprovacao,
 *       agenda ou execucao de campo nao decide nada. A decisao fotografa
 *       os volumes revistos de {@link LeakDiscountRule} na linha do
 *       pedido; a RETIFICACAO da conta com esses volumes e do chamador,
 *       pela P2.20 (BILL_RECTIFICATION) -- esta frente nao duplica o motor
 *       de versoes da conta;</li>
 *   <li><b>a afericao exige aviso tempestivo ja no pedido</b>
 *       ({@link MeterTestRules#requireTimelyNotice}) e laudo para sair de
 *       REQUESTED: veredito, erro medido, data do ensaio e referencia do
 *       laudo sao obrigatorios, e o laudo tambem roda dentro do
 *       {@code execute}. A cobranca decidida e a de
 *       {@code meter.test_charge_only_if_normal}; lancar o debito do preco
 *       e da tabela de precos (ED-34);</li>
 *   <li><b>agenda e fases sao do motor + ED-23:</b> este servico expoe as
 *       transicoes minimas sobre o motor ({@link #approve},
 *       {@link #schedule}, {@link #startExecution}) e o pedido de afericao
 *       ja agenda a OS no motor; telas de agenda, vistoria em campo e
 *       fases do protocolo sao da ED-23.</li>
 * </ul>
 */
@Service
public class LeakMeterService {

    /** Decisao sobre o pedido de vazamento. */
    public enum LeakDecisionKind {
        GRANT,
        DENY
    }

    /** Pedido de vazamento aberto: linha, OS filha e OS mae (BILL_REVIEW). */
    public record LeakReviewOutcome(UUID leakReviewId, UUID serviceOrderId, UUID billReviewOrderId,
                                    UUID billReviewId, LocalDate deadlineOn) {
    }

    /** Decisao aplicada: volumes revistos quando GRANT (null quando DENY). */
    public record LeakDecision(UUID leakReviewId, UUID serviceOrderId, String status,
                               BigDecimal revisedWater, BigDecimal revisedSewer,
                               String effectiveRule) {
    }

    /** Afericao pedida: linha e OS. */
    public record MeterTestOutcome(UUID meterTestId, UUID serviceOrderId, LocalDate earliestTestDate) {
    }

    /** Laudo registrado: veredito, cobranca e referencias a revisar (IRREGULAR). */
    public record MeterTestReportOutcome(UUID meterTestId, UUID serviceOrderId,
                                         MeterTestRules.MeterVerdict verdict, boolean chargeable,
                                         List<YearMonth> reviewReferences) {
    }

    private final LeakReviewRepository leakReviews;
    private final MeterTestRepository meterTests;
    private final LeakMeterOrderGateway orders;
    private final BillReviewPort billReviews;

    public LeakMeterService(LeakReviewRepository leakReviews, MeterTestRepository meterTests,
                            LeakMeterOrderGateway orders, BillReviewPort billReviews) {
        this.leakReviews = leakReviews;
        this.meterTests = meterTests;
        this.orders = orders;
        this.billReviews = billReviews;
    }

    // ------------------------------------------------------------------
    // Vazamento: abertura (parte da BILL_REVIEW da P2.20)
    // ------------------------------------------------------------------

    /**
     * Abre o pedido de vazamento para uma fatura: confere prazo e
     * elegibilidade, garante o tipo {@code LEAK_REVIEW} no tenant, abre a
     * revisao de conta na P2.20, cria a OS {@code LEAK_REVIEW} filha da
     * {@code BILL_REVIEW} e grava o pedido OPEN com prazo e referencias
     * fotografados.
     *
     * <p><b>Ordem (correcao pos-revisao, achado A1):</b> o tipo e garantido
     * ANTES de abrir a {@code BILL_REVIEW} -- uma falha de declaracao do
     * tipo nao deixa revisao de conta orfa aberta pela transacao propria da
     * P2.20. E se a P2.20 devolver a abertura como
     * {@link BillReviewPort.AwaitingApproval} (fabrica com aprovacao e sem
     * aprovador: a linha em {@code bill_reviews} ainda nao existe), este
     * metodo recusa explicitamente SEM criar a filha e SEM gravar o pedido
     * ({@code leak_reviews.bill_review_id} e NOT NULL com FK): a retomada e
     * repetir esta chamada com a mesma {@code idempotencyKey} e um
     * {@code approverId}.
     *
     * @param requestedReferences referencias das faturas que o pedido cobre
     *                            (a da conta e as sequenciais, quando o
     *                            vazamento atravessa mais de uma fatura)
     */
    @Transactional
    public LeakReviewOutcome requestLeakReview(UUID tenantId, UUID billId, LocalDate billReferenceMonth,
                                               UUID unitId, UUID reviewReasonId, UUID protocolId,
                                               String note, UUID actorId, UUID approverId,
                                               String idempotencyKey, LeakMeterParameters parameters,
                                               LocalDate claimDate, LocalDate readingDate, LocalDate dueDate,
                                               List<YearMonth> requestedReferences, LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(claimDate, "claimDate");
        // Prazo e elegibilidade ANTES de qualquer OS (nem a da P2.20 nasce).
        LeakClaimDeadlineRule.DeadlineCheck deadline =
                LeakClaimDeadlineRule.requireTimely(parameters, claimDate, readingDate, dueDate);
        LeakEligibilityRule.requireEligible(parameters, priorDiscountedReferences(tenantId, unitId),
                requestedReferences);
        if (leakReviews.findByTenantIdAndBillIdAndStatus(tenantId, billId, "OPEN").isPresent()) {
            throw new IllegalStateException("Conta ja tem pedido de vazamento aberto");
        }
        // O tipo e garantido ANTES da abertura da P2.20 (sem BILL_REVIEW orfa).
        ensureOrderType(LeakMeterOrderType.LEAK_REVIEW, parameters);
        BillReviewPort.OpenBillReviewResult result = billReviews.openBillReview(tenantId, billId, reviewReasonId,
                protocolId, note, actorId, approverId, idempotencyKey, today);
        if (result instanceof BillReviewPort.AwaitingApproval pending) {
            throw new IllegalStateException("Revisao de conta da P2.20 aguardando aprovacao (OS BILL_REVIEW "
                    + pending.billReviewOrderId() + "): a linha em bill_reviews ainda nao existe e nenhum "
                    + "pedido de vazamento foi criado; retome a abertura com a mesma Idempotency-Key e um "
                    + "approverId para concluir a revisao e abrir o pedido");
        }
        BillReviewPort.Opened opened = (BillReviewPort.Opened) result;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("billId", String.valueOf(billId));
        payload.put("billReviewId", String.valueOf(opened.billReviewId()));
        payload.put("billReviewOrderId", String.valueOf(opened.billReviewOrderId()));
        UUID orderId = orders.createOrder(LeakMeterOrderType.LEAK_REVIEW,
                protocolId != null ? ServiceOrderEnums.Origin.EXTERNAL : ServiceOrderEnums.Origin.INTERNAL,
                protocolId, actorId, unitId, opened.billReviewOrderId(), Json.write(payload));
        LeakReviewEntity review = new LeakReviewEntity();
        review.setTenantId(tenantId);
        review.setBillId(billId);
        review.setBillReferenceMonth(billReferenceMonth);
        review.setUnitId(unitId);
        review.setBillReviewId(opened.billReviewId());
        review.setBillReviewOrderId(opened.billReviewOrderId());
        review.setServiceOrderId(orderId);
        review.setProtocolId(protocolId);
        review.setStatus("OPEN");
        review.setClaimOn(claimDate);
        review.setReadingOn(readingDate);
        review.setDueOn(dueDate);
        review.setDeadlineAnchor(parameters.claimDeadlineAnchor().name());
        review.setDeadlineOn(deadline.deadlineOn());
        review.setCoveredReferences(Json.write(requestedReferences.stream().map(YearMonth::toString).toList()));
        review.setNote(note);
        review.setCreatedBy(actorId);
        review.setUpdatedBy(actorId);
        leakReviews.save(review);
        return new LeakReviewOutcome(review.getId(), orderId, opened.billReviewOrderId(),
                opened.billReviewId(), deadline.deadlineOn());
    }

    // ------------------------------------------------------------------
    // Conducao da OS de campo ate o execute (o condutor e este servico)
    // ------------------------------------------------------------------

    /**
     * Aprova a OS em {@code PENDING_APPROVAL} no motor (correcao
     * pos-revisao, achado B1): sem este passo, o tipo {@code LEAK_REVIEW}
     * (fabrica com aprovacao) nunca chega ao {@code execute} -- o motor
     * recusa aprovador igual ao autor e tipo com aprovacao sem
     * {@code approvedBy}. Quem aprova e decisao do chamador (fluxo de campo
     * da ED-23); a passagem e deste servico.
     */
    public void approve(UUID orderId, UUID approverId) {
        orders.approve(orderId, Objects.requireNonNull(approverId, "approverId"));
    }

    /** Agenda a OS (vazamento ou afericao) no motor -- o minimo de agenda; as telas sao da ED-23. */
    public void schedule(UUID orderId, OffsetDateTime scheduledFor, UUID actorId) {
        orders.schedule(orderId, Objects.requireNonNull(scheduledFor, "scheduledFor"), actorId);
    }

    /**
     * Inicia a execucao em campo da OS no motor (correcao pos-revisao,
     * achado B1): o motor real so executa OS de campo a partir de
     * {@code IN_EXECUTION} -- sem este passo, {@link #decideLeakReview} e
     * {@link #recordMeterTestReport} sao recusados por ele em 100% dos usos.
     */
    public void startExecution(UUID orderId, UUID actorId) {
        orders.startExecution(orderId, actorId);
    }

    // ------------------------------------------------------------------
    // Vazamento: decisao (dentro do execute da OS)
    // ------------------------------------------------------------------

    /**
     * Decide o pedido: GRANT calcula os volumes revistos por
     * {@link LeakDiscountRule} e os fotografa; DENY so registra. A aplicacao
     * roda dentro do {@code execute} da OS {@code LEAK_REVIEW} -- chamar com
     * a OS fora de estado executavel e recusado pelo motor. A conducao ate
     * la e deste servico: {@link #approve} (quando o tipo exige),
     * {@link #schedule} e {@link #startExecution}; a vistoria em si e do
     * fluxo de campo da ED-23. A nota da decisao vai para
     * {@code decision_note} -- a nota da abertura nao e sobrescrita
     * (correcao pos-revisao, achado Ba3).
     */
    @Transactional
    public LeakDecision decideLeakReview(UUID tenantId, UUID leakReviewId, LeakDecisionKind decision,
                                         LeakDiscountRule.Volumes volumes, String note, UUID actorId,
                                         LeakMeterParameters parameters, LocalDate today) {
        requireNote(note);
        Objects.requireNonNull(decision, "decision");
        LeakReviewEntity review = leakReviews.findByTenantIdAndId(tenantId, leakReviewId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido de vazamento inexistente: " + leakReviewId));
        if (!"OPEN".equals(review.getStatus())) {
            throw new IllegalStateException("Pedido de vazamento ja decidido: " + review.getStatus());
        }
        if (decision == LeakDecisionKind.GRANT) {
            Objects.requireNonNull(volumes, "volumes (obrigatorios para deferir)");
            Objects.requireNonNull(parameters, "parameters");
        }
        LeakDiscountRule.Result[] applied = new LeakDiscountRule.Result[1];
        boolean[] effectApplied = new boolean[1];
        orders.execute(review.getServiceOrderId(), actorId, () -> {
            if (decision == LeakDecisionKind.GRANT) {
                LeakDiscountRule.Result result = LeakDiscountRule.apply(parameters, volumes);
                applied[0] = result;
                review.setMeasuredWater(volumes.measuredWater());
                review.setAverageWater(volumes.averageWater());
                review.setMeasuredSewer(volumes.measuredSewer());
                review.setAverageSewer(volumes.averageSewer());
                review.setRevisedWater(result.revisedWater());
                review.setRevisedSewer(result.revisedSewer());
                review.setDiscountRule(result.effectiveRule().name());
                // O percentual so e fotografado quando a regra EFETIVA o usou
                // (correcao pos-revisao, achado Ba2): sob AVERAGE_6M ele nao
                // entra no calculo e a sua ausencia nao derruba o deferimento.
                review.setMaxDiscountPctWater(result.effectiveRule() == LeakMeterParameters.DiscountRule.PCT_OF_EXCESS
                        ? parameters.maxDiscountPctWater() : null);
                review.setStatus("GRANTED");
            } else {
                review.setStatus("DENIED");
            }
            review.setDecidedOn(today);
            review.setDecisionNote(note);
            review.setUpdatedBy(actorId);
            leakReviews.save(review);
            effectApplied[0] = true;
        });
        if (decision == LeakDecisionKind.GRANT) {
            Objects.requireNonNull(applied[0], "execute da OS " + review.getServiceOrderId()
                    + " nao aplicou o efeito da decisao (handler nao invocado)");
            return new LeakDecision(review.getId(), review.getServiceOrderId(), "GRANTED",
                    applied[0].revisedWater(), applied[0].revisedSewer(), applied[0].effectiveRule().name());
        }
        // Guarda de efeito aplicado tambem no DENY (correcao pos-revisao,
        // achado M2): um execute no-op (OS ja executada) nao passa por decisao.
        if (!effectApplied[0]) {
            throw new IllegalStateException("execute da OS " + review.getServiceOrderId()
                    + " nao aplicou o efeito da decisao (handler nao invocado)");
        }
        return new LeakDecision(review.getId(), review.getServiceOrderId(), "DENIED", null, null, null);
    }

    // ------------------------------------------------------------------
    // Afericao: pedido e laudo
    // ------------------------------------------------------------------

    /**
     * Pede a afericao: o aviso precisa permitir a data marcada
     * ({@link MeterTestRules#requireTimelyNotice}); cria a OS
     * {@code METER_TEST}, AGENDA a OS no motor para a data marcada
     * (correcao pos-revisao, achado M3: a agenda do motor e a data do
     * pedido nao divergem mais) e grava o pedido REQUESTED.
     *
     * <p>A data marcada e um {@link LocalDate} (a tabela propria guarda a
     * data); no motor ela vira o inicio do dia em UTC. Se o tenant exigir
     * aprovacao para {@code METER_TEST} (sobreposicao de
     * {@code service_type.internal_approval}), a OS nasce
     * {@code PENDING_APPROVAL} e o motor recusa o agendamento nessa hora --
     * erro explicito, sem traducao: aprove antes por {@link #approve} e
     * agende por {@link #schedule}.
     */
    @Transactional
    public MeterTestOutcome requestMeterTest(UUID tenantId, UUID meterId, UUID unitId, UUID protocolId,
                                             String note, UUID actorId, LeakMeterParameters parameters,
                                             LocalDate noticeDate, LocalDate scheduledDate) {
        requireNote(note);
        Objects.requireNonNull(parameters, "parameters");
        MeterTestRules.requireTimelyNotice(parameters, noticeDate, scheduledDate);
        ensureOrderType(LeakMeterOrderType.METER_TEST, parameters);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("meterId", String.valueOf(meterId));
        payload.put("unitId", String.valueOf(unitId));
        UUID orderId = orders.createOrder(LeakMeterOrderType.METER_TEST,
                protocolId != null ? ServiceOrderEnums.Origin.EXTERNAL : ServiceOrderEnums.Origin.INTERNAL,
                protocolId, actorId, unitId, null, Json.write(payload));
        orders.schedule(orderId, scheduledDate.atStartOfDay().atOffset(ZoneOffset.UTC), actorId);
        MeterTestEntity test = new MeterTestEntity();
        test.setTenantId(tenantId);
        test.setMeterId(meterId);
        test.setUnitId(unitId);
        test.setServiceOrderId(orderId);
        test.setProtocolId(protocolId);
        test.setStatus("REQUESTED");
        test.setNoticeOn(noticeDate);
        test.setScheduledFor(scheduledDate);
        test.setNote(note);
        test.setCreatedBy(actorId);
        test.setUpdatedBy(actorId);
        meterTests.save(test);
        return new MeterTestOutcome(test.getId(), orderId,
                MeterTestRules.earliestTestDate(parameters, noticeDate));
    }

    /**
     * Registra o laudo da afericao dentro do {@code execute} da OS: veredito,
     * erro medido, data do ensaio e referencia do laudo sao obrigatorios
     * ("afericao COM laudo"). A OS precisa estar em {@code IN_EXECUTION} --
     * a conducao e {@link #approve} (se o tipo exigir), {@link #schedule} e
     * {@link #startExecution}. Devolve a decisao de cobranca e, para
     * IRREGULAR, as referencias a revisar pela P2.20.
     */
    @Transactional
    public MeterTestReportOutcome recordMeterTestReport(UUID tenantId, UUID meterTestId,
                                                        MeterTestRules.MeterVerdict verdict,
                                                        BigDecimal measuredErrorPct, String reportRef,
                                                        LocalDate testedOn, UUID actorId,
                                                        LeakMeterParameters parameters) {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(parameters, "parameters");
        if (reportRef == null || reportRef.isBlank()) {
            throw new IllegalArgumentException("Afericao exige laudo: reportRef (referencia do laudo) e obrigatorio");
        }
        if (measuredErrorPct == null) {
            throw new IllegalArgumentException("Afericao exige o erro medido declarado no laudo (measuredErrorPct)");
        }
        if (testedOn == null) {
            // Erro explicito no servico (correcao pos-revisao, achado Ba1):
            // sem ele, o caso IRREGULAR morria em NPE e os demais, no CHECK do banco.
            throw new IllegalArgumentException("Afericao exige a data do ensaio declarada no laudo (testedOn)");
        }
        MeterTestEntity test = meterTests.findByTenantIdAndId(tenantId, meterTestId)
                .orElseThrow(() -> new IllegalArgumentException("Afericao inexistente: " + meterTestId));
        if (!"REQUESTED".equals(test.getStatus())) {
            throw new IllegalStateException("Afericao ja tem laudo ou foi cancelada: " + test.getStatus());
        }
        MeterTestRules.ChargeDecision[] charge = new MeterTestRules.ChargeDecision[1];
        orders.execute(test.getServiceOrderId(), actorId, () -> {
            charge[0] = MeterTestRules.chargeDecision(parameters, verdict);
            test.setVerdict(verdict.name());
            test.setMeasuredErrorPct(measuredErrorPct);
            test.setReportRef(reportRef);
            test.setTestedOn(testedOn);
            test.setChargeable(charge[0].chargeable());
            test.setStatus("REPORTED");
            test.setUpdatedBy(actorId);
            meterTests.save(test);
        });
        Objects.requireNonNull(charge[0], "execute da OS " + test.getServiceOrderId()
                + " nao aplicou o efeito do laudo (handler nao invocado)");
        return new MeterTestReportOutcome(test.getId(), test.getServiceOrderId(), verdict,
                charge[0].chargeable(), MeterTestRules.reviewReferences(parameters, verdict, testedOn));
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    /** Referencias ja beneficiadas da unidade: as cobertas dos pedidos GRANTED. */
    private List<YearMonth> priorDiscountedReferences(UUID tenantId, UUID unitId) {
        List<YearMonth> references = new ArrayList<>();
        for (LeakReviewEntity granted : leakReviews.findByTenantIdAndUnitIdAndStatus(tenantId, unitId, "GRANTED")) {
            JsonNode covered = Json.readTree(granted.getCoveredReferences());
            if (covered.isArray()) {
                for (JsonNode item : covered) {
                    references.add(YearMonth.parse(item.asText()));
                }
            }
        }
        return references;
    }

    /**
     * Garante o tipo no tenant com a exigencia resolvida (sobreposicao do
     * tenant sobre a fabrica), como na P2.20: tipo existente com fabrica
     * divergente da exigencia resolvida e erro explicito ANTES de criar
     * qualquer ordem -- o motor nao redeclara tipo existente.
     */
    private LeakMeterOrderGateway.TypeInfo ensureOrderType(LeakMeterOrderType type,
                                                           LeakMeterParameters parameters) {
        boolean required = parameters.approvalRequired(type, type.factoryRequiresApproval());
        LeakMeterOrderGateway.TypeInfo existing = orders.findType(type);
        if (existing == null) {
            return orders.ensureType(type, required);
        }
        if (!existing.active()) {
            throw new IllegalStateException("Tipo de OS " + type.code() + " inativo no tenant");
        }
        if (existing.requiresApproval() != required) {
            throw new IllegalStateException("Tipo de OS " + type.code() + " declarado com requires_approval="
                    + existing.requiresApproval() + ", mas a exigencia resolvida (service_type.internal_approval"
                    + " sobre a fabrica) e " + required + ": redeclare o tipo para alinhar");
        }
        return existing;
    }

    private static void requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Anotacao (note) e obrigatoria");
        }
    }
}
