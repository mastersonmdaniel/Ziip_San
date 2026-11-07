package br.com.ziipsan.commercial.billing.cycle;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.domain.BillEntity;
import br.com.ziipsan.commercial.billing.domain.BillingGroupEntity;
import br.com.ziipsan.commercial.billing.domain.BillingReferenceEntity;
import br.com.ziipsan.commercial.billing.domain.CycleActivityEntity;
import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
import br.com.ziipsan.commercial.billing.repository.BillRepository;
import br.com.ziipsan.commercial.billing.repository.BillingGroupRepository;
import br.com.ziipsan.commercial.billing.repository.BillingReferenceRepository;
import br.com.ziipsan.commercial.billing.repository.CycleActivityRepository;
import br.com.ziipsan.commercial.billing.repository.CycleScheduleItemRepository;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.parameters.TenantTime;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servico de ciclo do billing (P2.5/F1, sobre a V4_3): abertura da referencia
 * mensal com o cronograma por grupo, comando e realizacao de atividades na
 * precedencia do catalogo, fechamento da referencia e a guarda de geracao de
 * contas. As decisoes sao da regra pura {@link CycleCommandRule}; este servico
 * le o estado, chama a regra e grava a transicao.
 *
 * <p>Regras aplicadas aqui, todas do aceite da P2.5:
 * <ul>
 *   <li>atividade so e comandada com a precedente realizada;</li>
 *   <li>faturamento de grupo cuja rota de leitura nao foi transmitida e
 *       recusado (decisao ROUTE_NOT_TRANSMITTED da regra);</li>
 *   <li>a referencia so fecha depois de todos os grupos (todas as atividades
 *       obrigatorias realizadas em cada grupo), e referencia fechada nao
 *       admite comando nem geracao;</li>
 *   <li>ciclo repetido nao gera segunda conta: a guarda de geracao compoe a
 *       referencia fechada com {@code BillIdempotency}, cuja garantia final e
 *       o indice unico parcial de contas vivas da V4_1.</li>
 * </ul>
 *
 * <p>O comando do faturamento despacha a execucao pela fundacao de lote da
 * P2.1, atraves de {@link CycleBatchGateway} (a API publica da fundacao, sem
 * reimplementa-la): um despacho por (referencia, grupo), com a rota de
 * leitura do grupo como rota do lote. Disparo duplicado recusado pela
 * fundacao (REFUSED_DUPLICATE) recusa o comando e nao marca o item.
 *
 * <p>Ausencia e erro explicito, nunca padrao silencioso: sem grupo ativo, sem
 * linha do catalogo do produto ou sem data prevista de atividade obrigatoria,
 * a abertura da referencia e recusada antes de gravar qualquer linha.
 */
public class CycleService {

    private final BillingReferenceRepository references;
    private final BillingGroupRepository groups;
    private final CycleActivityRepository activities;
    private final CycleScheduleItemRepository items;
    private final BillRepository bills;
    private final CycleBatchGateway batchGateway;
    private final Clock clock;

    public CycleService(BillingReferenceRepository references, BillingGroupRepository groups,
                        CycleActivityRepository activities, CycleScheduleItemRepository items,
                        BillRepository bills, CycleBatchGateway batchGateway, Clock clock) {
        this.references = Objects.requireNonNull(references, "O repositorio de referencias e obrigatorio");
        this.groups = Objects.requireNonNull(groups, "O repositorio de grupos e obrigatorio");
        this.activities = Objects.requireNonNull(activities, "O repositorio de atividades e obrigatorio");
        this.items = Objects.requireNonNull(items, "O repositorio do cronograma e obrigatorio");
        this.bills = Objects.requireNonNull(bills, "O repositorio de contas e obrigatorio");
        this.batchGateway = Objects.requireNonNull(batchGateway, "A porta de lote da P2.1 e obrigatoria");
        this.clock = Objects.requireNonNull(clock, "O relogio e obrigatorio");
    }

    /**
     * Referencia de ciclo entregue a trava (tenant, ciclo) da fundacao de lote:
     * unica por referencia mensal, grupo e atividade.
     */
    public static String cycleRef(YearMonth referenceMonth, String groupCode, CycleActivityCode activity) {
        return "P2.5-" + referenceMonth + "-" + groupCode + "-" + activity.name();
    }

    /**
     * Carrega o catalogo de atividades do produto ({@link ProductActivityCatalog})
     * para o tenant, criando so as linhas que faltam. A migration V4_3 nao semeia
     * o catalogo: esta e a carga, feita pelo servico.
     */
    public void ensureProductCatalog(UUID tenantId, UUID actor) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Map<CycleActivityCode, CycleActivityEntity> byCode = new EnumMap<>(CycleActivityCode.class);
        for (CycleActivityEntity existing : activities.findByTenantIdOrderBySequenceOrderAsc(tenantId)) {
            byCode.put(CycleActivityCode.valueOf(existing.getCode()), existing);
        }
        for (ProductActivityCatalog.Definition definition : ProductActivityCatalog.definitions()) {
            if (byCode.containsKey(definition.code())) {
                continue;
            }
            CycleActivityEntity entity = new CycleActivityEntity();
            entity.setTenantId(tenantId);
            entity.setCode(definition.code().name());
            entity.setName(definition.name());
            entity.setSequenceOrder((short) definition.sequenceOrder());
            entity.setMandatory(definition.mandatory());
            entity.setRepeatable(definition.repeatable());
            if (definition.predecessor() != null) {
                CycleActivityEntity predecessor = byCode.get(definition.predecessor());
                if (predecessor == null) {
                    throw new ApiException.Conflict("cycle-catalog-predecessor-missing",
                            "Catalogo do produto inconsistente: a precedente de " + definition.code()
                                    + " (" + definition.predecessor() + ") nao foi carregada.");
                }
                entity.setPredecessorId(predecessor.getId());
            }
            entity.setCreatedBy(actor);
            entity.setUpdatedBy(actor);
            byCode.put(definition.code(), activities.save(entity));
        }
    }

    /**
     * Abre a referencia do mes e monta o cronograma: um item PENDING por grupo
     * ativo e atividade do catalogo, com a data prevista informada. Data prevista
     * de atividade obrigatoria ausente recusa a abertura (nada e gravado).
     */
    public BillingReferenceEntity openReference(UUID tenantId, YearMonth referenceMonth,
                                                Map<CycleActivityCode, LocalDate> plannedByActivity, UUID actor) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(referenceMonth, "A referencia e obrigatoria");
        Objects.requireNonNull(plannedByActivity, "As datas previstas sao obrigatorias");
        LocalDate month = referenceMonth.atDay(1);
        if (references.findByTenantIdAndReferenceMonth(tenantId, month).isPresent()) {
            throw new ApiException.Conflict("billing-reference-already-open",
                    "Ja existe referencia aberta ou fechada para " + referenceMonth + ".");
        }
        ensureProductCatalog(tenantId, actor);
        List<BillingGroupEntity> activeGroups = groups.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId);
        if (activeGroups.isEmpty()) {
            throw new ApiException.Conflict("billing-no-active-group",
                    "Nenhum grupo de faturamento ativo: a referencia nao pode ser aberta sem grupos.");
        }
        List<CycleActivityEntity> catalog = activities.findByTenantIdOrderBySequenceOrderAsc(tenantId);
        if (catalog.size() != ProductActivityCatalog.definitions().size()) {
            throw new ApiException.Conflict("cycle-catalog-incomplete",
                    "Catalogo de atividades do produto incompleto para o tenant: esperado "
                            + ProductActivityCatalog.definitions().size() + " atividades, encontrado "
                            + catalog.size() + ".");
        }
        for (CycleActivityEntity activity : catalog) {
            CycleActivityCode code = CycleActivityCode.valueOf(activity.getCode());
            if (activity.isMandatory() && plannedByActivity.get(code) == null) {
                throw new ApiException.Conflict("cycle-planned-date-missing",
                        "Data prevista ausente para a atividade obrigatoria " + code + ".");
            }
        }

        BillingReferenceEntity reference = new BillingReferenceEntity();
        reference.setTenantId(tenantId);
        reference.setReferenceMonth(month);
        reference.setStatus(BillingReferenceStatus.OPEN.name());
        reference.setCreatedBy(actor);
        reference.setUpdatedBy(actor);
        reference = references.save(reference);

        for (BillingGroupEntity group : activeGroups) {
            for (CycleActivityEntity activity : catalog) {
                CycleActivityCode code = CycleActivityCode.valueOf(activity.getCode());
                LocalDate planned = plannedByActivity.get(code);
                if (planned == null) {
                    continue;
                }
                CycleScheduleItemEntity item = new CycleScheduleItemEntity();
                item.setTenantId(tenantId);
                item.setReferenceId(reference.getId());
                item.setGroupId(group.getId());
                item.setActivityId(activity.getId());
                item.setOccurrenceNo((short) 1);
                item.setStatus(ScheduleItemStatus.PENDING.name());
                item.setPlannedOn(planned);
                item.setCreatedBy(actor);
                item.setUpdatedBy(actor);
                items.save(item);
            }
        }
        return reference;
    }

    /**
     * Comanda um item do cronograma. A regra decide pela precedencia e pela
     * situacao da referencia; no faturamento, o comando despacha o lote da P2.1
     * para a rota do grupo e guarda o id da execucao no item.
     */
    public CycleScheduleItemEntity commandActivity(UUID tenantId, UUID itemId, UUID actor, TenantTime tenantTime) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(itemId, "O item do cronograma e obrigatorio");
        CycleScheduleItemEntity item = items.findByTenantIdAndId(tenantId, itemId)
                .orElseThrow(() -> new ApiException.NotFound("Item do cronograma nao encontrado."));
        BillingReferenceEntity reference = references.findByTenantIdAndId(tenantId, item.getReferenceId())
                .orElseThrow(() -> new ApiException.NotFound("Referencia do item nao encontrada."));
        BillingGroupEntity group = groups.findByTenantIdAndId(tenantId, item.getGroupId())
                .orElseThrow(() -> new ApiException.NotFound("Grupo do item nao encontrado."));
        CycleActivityEntity activity = activities.findByTenantIdAndId(tenantId, item.getActivityId())
                .orElseThrow(() -> new ApiException.NotFound("Atividade do item nao encontrada."));
        CycleActivityCode code = CycleActivityCode.valueOf(activity.getCode());

        List<CycleActivityEntity> catalog = activities.findByTenantIdOrderBySequenceOrderAsc(tenantId);
        List<CycleScheduleItemEntity> groupItems =
                items.findByTenantIdAndReferenceIdAndGroupIdOrderByOccurrenceNoAsc(
                        tenantId, reference.getId(), group.getId());
        CycleCommandRule.Snapshot snapshot = snapshot(reference, catalog, groupItems);
        CycleCommandRule.CommandDecision decision =
                CycleCommandRule.evaluateCommand(snapshot, group.getId(), code);
        switch (decision) {
            case ALLOWED, NEW_OCCURRENCE_ALLOWED -> {
                // segue para o comando abaixo
            }
            case REFERENCE_CLOSED -> throw new ApiException.Conflict("billing-reference-closed",
                    "Referencia " + YearMonth.from(reference.getReferenceMonth())
                            + " fechada: nenhuma atividade pode ser comandada.");
            case CATALOG_ENTRY_MISSING -> throw new ApiException.Conflict("cycle-catalog-entry-missing",
                    "Atividade " + code + " fora do catalogo carregado do tenant.");
            case NO_SUCH_INSTANCE -> throw new ApiException.Conflict("cycle-instance-missing",
                    "Sem instancia da atividade " + code + " no cronograma do grupo.");
            case PREDECESSOR_NOT_DONE -> throw new ApiException.Conflict("cycle-predecessor-not-done",
                    "Atividade " + code + " so pode ser comandada com a precedente realizada.");
            case ROUTE_NOT_TRANSMITTED -> throw new ApiException.Conflict("cycle-route-not-transmitted",
                    "Faturamento recusado: a rota de leitura do grupo " + group.getCode()
                            + " nao foi transmitida.");
            case ALREADY_COMMANDED -> throw new ApiException.Conflict("cycle-activity-already-commanded",
                    "Atividade " + code + " ja comandada e ainda nao realizada.");
            case ALREADY_DONE_NOT_REPEATABLE -> throw new ApiException.Conflict("cycle-activity-already-done",
                    "Atividade " + code + " ja realizada e nao admite repeticao.");
            case INSTANCE_CANCELLED -> throw new ApiException.Conflict("cycle-instance-cancelled",
                    "Instancia da atividade " + code + " cancelada: o comando nao e admitido.");
        }

        CycleScheduleItemEntity target = item;
        if (decision == CycleCommandRule.CommandDecision.NEW_OCCURRENCE_ALLOWED) {
            CycleScheduleItemEntity latestDone = null;
            for (CycleScheduleItemEntity candidate : groupItems) {
                if (candidate.getActivityId().equals(activity.getId())
                        && (latestDone == null || candidate.getOccurrenceNo() > latestDone.getOccurrenceNo())) {
                    latestDone = candidate;
                }
            }
            Objects.requireNonNull(latestDone, "A ocorrencia realizada da atividade e obrigatoria");
            target = new CycleScheduleItemEntity();
            target.setTenantId(tenantId);
            target.setReferenceId(reference.getId());
            target.setGroupId(group.getId());
            target.setActivityId(activity.getId());
            target.setOccurrenceNo((short) (latestDone.getOccurrenceNo() + 1));
            target.setStatus(ScheduleItemStatus.PENDING.name());
            target.setPlannedOn(latestDone.getPlannedOn());
            target.setCreatedBy(actor);
        }

        if (code == CycleActivityCode.BILLING) {
            Objects.requireNonNull(tenantTime, "O tempo do tenant e obrigatorio para despachar o faturamento");
            CycleBatchGateway.Dispatch dispatch = batchGateway.dispatch(tenantId,
                    cycleRef(YearMonth.from(reference.getReferenceMonth()), group.getCode(), code),
                    List.of(group.getRouteCode()), tenantTime, actor, null);
            if (dispatch.outcome() == CycleBatchGateway.Outcome.REFUSED_DUPLICATE) {
                throw new ApiException.Conflict("cycle-batch-duplicate",
                        "Ja existe execucao de lote para este faturamento (fundacao da P2.1): "
                                + dispatch.runId() + ". O segundo disparo nao corre.");
            }
            target.setJobRunId(dispatch.runId());
        }

        target.setStatus(ScheduleItemStatus.COMMANDED.name());
        target.setCommandedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        target.setCommandedBy(actor);
        target.setUpdatedBy(actor);
        return items.save(target);
    }

    /** Marca a realizacao de um item comandado. */
    public CycleScheduleItemEntity completeActivity(UUID tenantId, UUID itemId, UUID actor) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(itemId, "O item do cronograma e obrigatorio");
        CycleScheduleItemEntity item = items.findByTenantIdAndId(tenantId, itemId)
                .orElseThrow(() -> new ApiException.NotFound("Item do cronograma nao encontrado."));
        if (!ScheduleItemStatus.COMMANDED.name().equals(item.getStatus())) {
            throw new ApiException.Conflict("cycle-activity-not-commanded",
                    "Atividade so pode ser realizada depois de comandada; situacao atual: "
                            + item.getStatus() + ".");
        }
        item.setStatus(ScheduleItemStatus.DONE.name());
        item.setDoneAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        item.setUpdatedBy(actor);
        return items.save(item);
    }

    /**
     * Encerra o mes por referencia: so fecha quando todos os grupos concluiram
     * todas as atividades obrigatorias. Com qualquer pendencia, recusa e nomeia
     * o que falta.
     */
    public BillingReferenceEntity closeReference(UUID tenantId, UUID referenceId, UUID actor) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(referenceId, "A referencia e obrigatoria");
        BillingReferenceEntity reference = references.findByTenantIdAndId(tenantId, referenceId)
                .orElseThrow(() -> new ApiException.NotFound("Referencia nao encontrada."));
        List<CycleActivityEntity> catalog = activities.findByTenantIdOrderBySequenceOrderAsc(tenantId);
        List<CycleScheduleItemEntity> allItems = items.findByTenantIdAndReferenceId(tenantId, referenceId);
        CycleCommandRule.CloseEvaluation evaluation =
                CycleCommandRule.evaluateClose(snapshot(reference, catalog, allItems));
        switch (evaluation.decision()) {
            case ALLOWED -> {
                // segue para o fechamento abaixo
            }
            case ALREADY_CLOSED -> throw new ApiException.Conflict("billing-reference-already-closed",
                    "Referencia " + YearMonth.from(reference.getReferenceMonth()) + " ja fechada.");
            case MANDATORY_PENDING -> throw new ApiException.Conflict("billing-reference-groups-pending",
                    "A referencia so fecha depois de todos os grupos; pendencias: "
                            + String.join("; ", evaluation.pending()) + ".");
        }
        reference.setStatus(BillingReferenceStatus.CLOSED.name());
        reference.setClosedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        reference.setClosedBy(actor);
        reference.setUpdatedBy(actor);
        return references.save(reference);
    }

    /**
     * Guarda de geracao de conta de uma unidade numa referencia, para o motor de
     * contas chamar antes de calcular: referencia fechada recusa sempre; aberta,
     * {@link CycleCommandRule#evaluateGeneration} aplica {@code BillIdempotency}
     * sobre as contas existentes (ciclo repetido nao gera segunda conta).
     *
     * @return a decisao de criacao quando a geracao e admitida
     */
    public CycleCommandRule.GenerationDecision assertNewBillAllowed(UUID tenantId, UUID unitId, YearMonth referenceMonth) {
        Objects.requireNonNull(tenantId, "O tenant e obrigatorio");
        Objects.requireNonNull(unitId, "A unidade e obrigatoria");
        Objects.requireNonNull(referenceMonth, "A referencia e obrigatoria");
        BillingReferenceEntity reference = references
                .findByTenantIdAndReferenceMonth(tenantId, referenceMonth.atDay(1))
                .orElseThrow(() -> new ApiException.NotFound(
                        "Referencia " + referenceMonth + " nao aberta para o tenant."));
        List<BillStatus> existing = bills
                .findByTenantIdAndUnitIdAndReferenceMonth(tenantId, unitId, referenceMonth.atDay(1))
                .stream()
                .map(BillEntity::getStatus)
                .map(BillStatus::valueOf)
                .toList();
        CycleCommandRule.GenerationDecision decision = CycleCommandRule.evaluateGeneration(
                BillingReferenceStatus.CLOSED.name().equals(reference.getStatus()), existing);
        switch (decision) {
            case CREATE, REPLACE_PRE_BILLED -> {
                return decision;
            }
            case REFERENCE_CLOSED -> throw new ApiException.Conflict("billing-reference-closed",
                    "Referencia " + referenceMonth + " fechada: nenhuma conta pode ser gerada.");
            case ALREADY_BILLED -> throw new ApiException.Conflict("bill-already-billed",
                    "Ja existe conta viva da unidade na referencia " + referenceMonth
                            + ": ciclo repetido nao gera segunda conta.");
        }
        throw new IllegalStateException("Decisao de geracao nao tratada: " + decision);
    }

    private CycleCommandRule.Snapshot snapshot(BillingReferenceEntity reference,
                                               List<CycleActivityEntity> catalog,
                                               List<CycleScheduleItemEntity> scheduleItems) {
        Map<UUID, CycleActivityCode> codeByActivityId = new HashMap<>();
        List<CycleCommandRule.ActivityView> catalogViews = new ArrayList<>();
        for (CycleActivityEntity activity : catalog) {
            CycleActivityCode code = CycleActivityCode.valueOf(activity.getCode());
            codeByActivityId.put(activity.getId(), code);
            CycleActivityCode predecessor = null;
            if (activity.getPredecessorId() != null) {
                for (CycleActivityEntity candidate : catalog) {
                    if (candidate.getId().equals(activity.getPredecessorId())) {
                        predecessor = CycleActivityCode.valueOf(candidate.getCode());
                        break;
                    }
                }
                if (predecessor == null) {
                    throw new ApiException.Conflict("cycle-catalog-predecessor-missing",
                            "Precedente da atividade " + code + " nao esta no catalogo carregado.");
                }
            }
            catalogViews.add(new CycleCommandRule.ActivityView(
                    code, activity.isMandatory(), activity.isRepeatable(), predecessor));
        }
        List<CycleCommandRule.ItemView> itemViews = new ArrayList<>();
        for (CycleScheduleItemEntity item : scheduleItems) {
            CycleActivityCode code = codeByActivityId.get(item.getActivityId());
            if (code == null) {
                throw new ApiException.Conflict("cycle-instance-activity-missing",
                        "Item do cronograma aponta para atividade fora do catalogo carregado.");
            }
            itemViews.add(new CycleCommandRule.ItemView(item.getGroupId(), code,
                    ScheduleItemStatus.valueOf(item.getStatus()), item.getOccurrenceNo()));
        }
        return new CycleCommandRule.Snapshot(
                BillingReferenceStatus.CLOSED.name().equals(reference.getStatus()), catalogViews, itemViews);
    }
}
